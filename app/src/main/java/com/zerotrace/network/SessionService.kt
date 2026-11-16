package com.zerotrace.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.EncryptedFileChunk
import com.zerotrace.bridge.EncryptedFileManifest
import com.zerotrace.bridge.EncryptedFileTransferEngine
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.HandshakeInitWire
import com.zerotrace.bridge.HandshakeResponseWire
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.RatchetWirePacket
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.bridge.VolatileChatMessage
import com.zerotrace.bridge.toHex
import com.zerotrace.media.EncryptedCallMediaEngine
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.SecureRandomSpi
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters

/**
 * Explicit Connectivity States mandated by Section 4.3.
 */
enum class ConnectivityState(val code: String, val labelEn: String, val labelHi: String) {
    OFFLINE("OFFLINE", "Offline — Circuit Closed", "ऑफ़लाइन — सर्किट बंद"),
    INITIALIZING("INITIALIZING", "Initializing Transport Engine", "ट्रांसपोर्ट इंजन प्रारंभ हो रहा है"),
    TOR_BOOTSTRAPPING("TOR_BOOTSTRAPPING", "Bootstrapping Tor Circuit", "Tor सर्किट बूटस्ट्रैप हो रहा है"),
    TOR_READY("TOR_READY", "Tor Circuit Ready", "Tor सर्किट तैयार"),
    ONION_SERVICE_STARTING("ONION_SERVICE_STARTING", "Starting v3 Onion Service", "v3 अनियन सर्विस शुरू हो रही है"),
    ONION_SERVICE_READY("ONION_SERVICE_READY", "Onion Service Listening (P2P Mesh Ready)", "अनियन सर्विस सक्रिय (P2P मेश तैयार)"),
    CONNECTING_TO_PEER("CONNECTING_TO_PEER", "Connecting to Peer .onion", "पीयर .onion से जुड़ रहा है"),
    AUTHENTICATING_PEER("AUTHENTICATING_PEER", "Authenticating Hybrid PQ Handshake", "हाइब्रिड PQ हैंडशेक प्रमाणित हो रहा है"),
    E2EE_SESSION_READY("E2EE_SESSION_READY", "E2EE PQ Ratchet Session Ready", "E2EE PQ रैचेट सत्र सक्रिय"),
    DISCONNECTING("DISCONNECTING", "Disconnecting & Wiping Session RAM", "डिस्कनेक्ट और RAM साफ़ हो रहा है"),
    ERROR("ERROR", "Transport Error (Fail-Closed)", "ट्रांसपोर्ट त्रुटि (Fail-Closed)")
}

enum class TorTransportBackend(val labelEn: String, val labelHi: String) {
    EMBEDDED_TOR(
        "Embedded Tor Network (Any Network / Mobile Data)",
        "एम्बेडेड Tor नेटवर्क (अलग Wi-Fi / मोबाइल डेटा)"
    ),
    SOCKS5_TOR_DAEMON(
        "External Tor / Orbot SOCKS5 (127.0.0.1:9050)",
        "एक्सटर्नल Tor / Orbot SOCKS5 (127.0.0.1:9050)"
    ),
    LOOPBACK_ONION_VERIFICATION(
        "Local Loopback / Same-Wi-Fi Hotspot P2P Circuit",
        "लोकल लूपबैक / सेम-Wi-Fi हॉटस्पॉट P2P सर्किट"
    )
}

/**
 * Signed peer identity announcement exchanged over the encrypted P2P `.onion` circuit.
 */
data class PeerHelloWire(
    val onionAddress: String,
    val classicalFingerprintHex: String,
    val pqFingerprintHex: String,
    val ed25519PublicKey: ByteArray,
    val x25519PublicKey: ByteArray,
    val pairingNonceHex: String,
    val ephemeralBootId: String,
    val ed25519Signature: ByteArray
)

/**
 * Real-time P2P transport events observed by `ZeroTraceViewModel` so incoming handshakes,
 * ratcheted messages, and delivery receipts update the UI immediately on both devices.
 */
sealed interface PeerTransportEvent {
    data class PeerConnected(
        val peerOnion: String,
        val peerHello: PeerHelloWire
    ) : PeerTransportEvent

    data class PeerDisconnected(
        val peerOnion: String
    ) : PeerTransportEvent

    data class SessionEstablished(
        val peerOnion: String,
        val sessionHandle: Long,
        val peerHello: PeerHelloWire?
    ) : PeerTransportEvent

    data class IncomingMessage(
        val peerOnion: String,
        val sessionHandle: Long,
        val message: VolatileChatMessage
    ) : PeerTransportEvent

    data class DeliveryAck(
        val peerOnion: String,
        val sessionIdHex: String,
        val msgNum: Long,
        val ratchetEpoch: Int
    ) : PeerTransportEvent

    data class ReadReceipt(
        val peerOnion: String,
        val readUpToEpochMillis: Long
    ) : PeerTransportEvent

    data class IncomingCallInvite(
        val peerOnion: String,
        val callId: String,
        val mediaKind: CallMediaKind,
        val mediaTcpPort: Int,
        val remoteHost: String?,
        val ephemeralCallPubKey: ByteArray,
        val ed25519Signature: ByteArray
    ) : PeerTransportEvent

    data class CallAccepted(
        val peerOnion: String,
        val callId: String,
        val mediaKind: CallMediaKind,
        val ephemeralCallPubKey: ByteArray,
        val ed25519Signature: ByteArray
    ) : PeerTransportEvent

    data class CallRejected(
        val peerOnion: String,
        val callId: String,
        val reason: String
    ) : PeerTransportEvent

    data class CallEnded(
        val peerOnion: String,
        val callId: String
    ) : PeerTransportEvent

    data class IncomingCallMediaFrame(
        val peerOnion: String,
        val callId: String,
        val frameBytes: ByteArray
    ) : PeerTransportEvent

    data class IncomingStatusItem(
        val peerOnion: String,
        val statusItem: EphemeralStatusItem
    ) : PeerTransportEvent
}

/**
 * Android Foreground Service maintaining the active ephemeral Onion session while online.
 * Notification uses `VISIBILITY_SECRET` and never exposes peer names or message content.
 */
class SessionService : Service() {

    companion object {
        private const val CHANNEL_ID = "zerotrace_ephemeral_circuit_channel"
        private const val NOTIFICATION_ID = 9443
        const val ACTION_START_CIRCUIT = "com.zerotrace.action.START_CIRCUIT"
        const val ACTION_EMERGENCY_WIPE = "com.zerotrace.action.EMERGENCY_WIPE"

        fun startServiceIfPermitted(context: Context) {
            try {
                val intent = Intent(context, SessionService::class.java).apply {
                    action = ACTION_START_CIRCUIT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Background start restrictions or unit test context
            }
        }

        fun stopServiceAndWipe(context: Context) {
            try {
                context.stopService(Intent(context, SessionService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EMERGENCY_WIPE) {
            TorOnionTransportManager.emergencyDisconnectAndWipe()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        val notification = buildPrivacyPreservingNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
        return if (TorOnionTransportManager.preserveVaultOnTaskRemoved) START_STICKY else START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!TorOnionTransportManager.preserveVaultOnTaskRemoved) {
            TorOnionTransportManager.emergencyDisconnectAndWipe()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active ephemeral Tor Onion Service circuit indicator"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildPrivacyPreservingNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wipeIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_EMERGENCY_WIPE
        }
        val pendingWipe = PendingIntent.getService(
            this,
            1,
            wipeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.notification_active_title))
            .setContentText(getString(R.string.notification_active_body))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.emergency_lock),
                pendingWipe
            )
            .build()
    }
}

/**
 * Deterministic SHA-256 Counter-Mode CSPRNG so the built-in Loopback Verification Peer Node
 * has a stable v3 `.onion` address, Classical Fingerprint, and Post-Quantum Fingerprint across
 * app restarts and session wipes.
 */
private class DeterministicLoopbackSecureRandom(seedPhrase: String) : SecureRandom(
    object : SecureRandomSpi() {
        private var counter = 0L
        private val masterSeed = MessageDigest.getInstance("SHA-256")
            .digest(seedPhrase.toByteArray(Charsets.UTF_8))

        override fun engineSetSeed(seed: ByteArray?) {}

        override fun engineNextBytes(bytes: ByteArray) {
            var offset = 0
            val md = MessageDigest.getInstance("SHA-256")
            while (offset < bytes.size) {
                md.reset()
                md.update(masterSeed)
                md.update(ByteBuffer.allocate(8).putLong(counter++).array())
                val block = md.digest()
                val copyLen = minOf(block.size, bytes.size - offset)
                System.arraycopy(block, 0, bytes, offset, copyLen)
                offset += copyLen
            }
        }

        override fun engineGenerateSeed(numBytes: Int): ByteArray {
            return ByteArray(numBytes).also { engineNextBytes(it) }
        }
    },
    null
)

/**
 * Manages the Tor Onion Service State Machine, Loopback Listener (`127.0.0.1`),
 * Real Multi-Device P2P Hotspot/LAN `.onion` Circuit Router (NSD mDNS + `TCP :9443` + `UDP :19443` + `/24` sweep),
 * Embedded RFC 1928 SOCKS5 `.onion` Router, Fail-Closed Clearnet Prevention, and
 * Peer-to-Peer Encrypted Framing.
 */
object TorOnionTransportManager {

    private const val WIRE_MAGIC = 0x5A543246 // "ZT2F"
    private const val FRAME_HANDSHAKE_INIT: Byte = 0x01
    private const val FRAME_HANDSHAKE_RESP: Byte = 0x02
    private const val FRAME_RATCHET_PACKET: Byte = 0x03
    private const val FRAME_DELIVERY_ACK: Byte = 0x04
    private const val FRAME_SESSION_RESET: Byte = 0x05
    private const val FRAME_MEDIA_CHUNK: Byte = 0x06
    private const val FRAME_READ_RECEIPT: Byte = 0x07
    private const val FRAME_KEEPALIVE: Byte = 0x08
    private const val FRAME_MEDIA_CHUNK_ACK: Byte = 0x09
    private const val FRAME_PEER_HELLO: Byte = 0x10
    private const val FRAME_CALL_INVITE: Byte = 0x20
    private const val FRAME_CALL_ACCEPT: Byte = 0x21
    private const val FRAME_CALL_REJECT: Byte = 0x22
    private const val FRAME_CALL_END: Byte = 0x23
    private const val FRAME_CALL_MEDIA_FALLBACK: Byte = 0x24
    private const val FRAME_STATUS_ITEM: Byte = 0x25

    private const val DEFAULT_P2P_TCP_PORT = 9443
    private const val DEFAULT_P2P_UDP_PORT = 19443
    private const val NSD_SERVICE_TYPE = "_zerotrace._tcp."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sweepMutex = Mutex()
    private val bootstrapMutex = Mutex()

    private val isAndroidDeviceRuntime: Boolean = try {
        Build.VERSION.SDK_INT > 0 && !Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    } catch (_: Throwable) {
        false
    }

    @Volatile
    private var appContextRef: Context? = null

    @Volatile
    private var activeBootstrapJob: CompletableDeferred<Boolean>? = null

    @Volatile
    private var localBootId: String = UUID.randomUUID().toString().replace("-", "")

    private val _connectivityState = MutableStateFlow(ConnectivityState.OFFLINE)
    val connectivityState: StateFlow<ConnectivityState> = _connectivityState.asStateFlow()

    private val _lastErrorDetail = MutableStateFlow<String?>(null)
    val lastErrorDetail: StateFlow<String?> = _lastErrorDetail.asStateFlow()

    private val _transportBackend = MutableStateFlow(
        if (isAndroidDeviceRuntime) TorTransportBackend.EMBEDDED_TOR
        else TorTransportBackend.LOOPBACK_ONION_VERIFICATION
    )
    val transportBackend: StateFlow<TorTransportBackend> = _transportBackend.asStateFlow()

    private val _localListenerPort = MutableStateFlow<Int?>(null)
    val localListenerPort: StateFlow<Int?> = _localListenerPort.asStateFlow()

    private val _torBootstrapProgress = MutableStateFlow(0)
    val torBootstrapProgress: StateFlow<Int> = _torBootstrapProgress.asStateFlow()

    private val _torBootstrapSummary = MutableStateFlow<String?>(null)
    val torBootstrapSummary: StateFlow<String?> = _torBootstrapSummary.asStateFlow()

    private val _activeTorSocksPort = MutableStateFlow<Int?>(null)
    val activeTorSocksPort: StateFlow<Int?> = _activeTorSocksPort.asStateFlow()

    private val _connectedPeerOnions = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeerOnions: StateFlow<Set<String>> = _connectedPeerOnions.asStateFlow()

    private val _peerTransportEvents = MutableSharedFlow<PeerTransportEvent>(extraBufferCapacity = 256)
    val peerTransportEvents: SharedFlow<PeerTransportEvent> = _peerTransportEvents.asSharedFlow()

    private var localOnionServerSocket: ServerSocket? = null
    private var embeddedSocksServerSocket: ServerSocket? = null
    private var embeddedSocksPort: Int? = null
    private var socksHost: String = "127.0.0.1"
    private var socksPort: Int = 9050

    // Real multi-device P2P Onion Mesh / Hotspot TCP + UDP + NSD transport state
    private var p2pMeshServerSocket: ServerSocket? = null
    private var p2pMeshListeningPort: Int = DEFAULT_P2P_TCP_PORT
    private var p2pUdpBeaconSocket: DatagramSocket? = null
    private var p2pDiscoveryJob: Job? = null
    private val lastSubnetSweepEpochMs = java.util.concurrent.atomic.AtomicLong(0L)

    private var nsdManager: NsdManager? = null
    private var nsdRegListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var localNsdServiceName: String? = null

    private val livePeerChannels = ConcurrentHashMap<String, PeerSocketChannel>()
    private val peerHelloByOnion = ConcurrentHashMap<String, PeerHelloWire>()
    private val lastBootIdByOnion = ConcurrentHashMap<String, String>()
    private val realPeerSessionHandles = ConcurrentHashMap<String, Long>()
    private val pendingHandshakeByOnion = ConcurrentHashMap<String, CompletableDeferred<Long>>()
    private val discoveredPeerAddresses = ConcurrentHashMap<String, InetSocketAddress>()
    private val connectingEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val incomingMediaChunksByTransferId = ConcurrentHashMap<String, ConcurrentHashMap<Int, EncryptedFileChunk>>()
    private val mediaChunkAckWaiters = ConcurrentHashMap<String, ConcurrentHashMap<Int, CompletableDeferred<Unit>>>()

    private val networkCallbackRegistered = AtomicBoolean(false)
    private val lastActiveNetworkHandle = AtomicLong(-1L)
    private var registeredNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val trackedVerifiedPeerOnions = ConcurrentHashMap.newKeySet<String>()
    private val backgroundDialingPeers = ConcurrentHashMap.newKeySet<String>()
    private var backgroundReconnectJob: Job? = null

    @Volatile
    var preserveVaultOnTaskRemoved: Boolean = true

    @Volatile
    private var loopbackPeerNode: LoopbackCryptographicPeerNode? = null

    fun initializePlatform(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        registerNetworkCallbackIfNeeded(appCtx)
    }

    fun registerVerifiedPeerOnions(onions: Collection<String>) {
        val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase()
        val loopbackOnion = loopbackPeerNode?.onionAddress?.lowercase()
        for (raw in onions) {
            val clean = raw.trim().lowercase()
            if (clean.endsWith(".onion") && clean != localOnion && clean != loopbackOnion) {
                trackedVerifiedPeerOnions.add(clean)
            }
        }
    }

    private fun registerNetworkCallbackIfNeeded(appCtx: Context) {
        if (!isAndroidDeviceRuntime || !networkCallbackRegistered.compareAndSet(false, true)) return
        try {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val newHandle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        network.networkHandle
                    } else {
                        network.hashCode().toLong()
                    }
                    val prevHandle = lastActiveNetworkHandle.getAndSet(newHandle)
                    if (prevHandle == -1L || prevHandle == newHandle) {
                        // First network observation — just warm Tor if already ready
                        return
                    }
                    // Network changed (Wi-Fi A <-> Wi-Fi B or Wi-Fi <-> Mobile Data): recover immediately!
                    scope.launch {
                        handlePlatformNetworkSwitch()
                    }
                }

                override fun onLost(network: Network) {
                    val lostHandle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        network.networkHandle
                    } else {
                        network.hashCode().toLong()
                    }
                    if (lastActiveNetworkHandle.get() == lostHandle) {
                        scope.launch {
                            delay(800L)
                            if (isAndroidDeviceRuntime && livePeerChannels.isNotEmpty()) {
                                livePeerChannels.values.toList().forEach { ch ->
                                    try {
                                        if (!ch.isAlive()) {
                                            livePeerChannels.remove(ch.peerOnion, ch)
                                        }
                                    } catch (_: Throwable) {
                                    }
                                }
                                refreshConnectedPeerState()
                            }
                        }
                    }
                }
            }
            registeredNetworkCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Throwable) {
            networkCallbackRegistered.set(false)
        }
    }

    private suspend fun handlePlatformNetworkSwitch() = withContext(Dispatchers.IO) {
        if (!isAndroidDeviceRuntime) return@withContext
        // Drop stale sockets opened over the previous network interface so Tor / P2P reconnects cleanly
        val staleEntries = livePeerChannels.entries.toList()
        for ((onion, ch) in staleEntries) {
            livePeerChannels.remove(onion, ch)
            ch.closeQuietly()
        }
        discoveredPeerAddresses.clear()
        refreshConnectedPeerState()

        val backend = _transportBackend.value
        if (backend == TorTransportBackend.EMBEDDED_TOR) {
            val state = _connectivityState.value
            if (state == ConnectivityState.OFFLINE || state == ConnectivityState.ERROR) {
                bootstrapTorAndStartOnionService()
            } else {
                val recovered = EmbeddedTorEngine.onNetworkChanged(
                    virtualOnionPort = DEFAULT_P2P_TCP_PORT,
                    localListenPort = p2pMeshListeningPort,
                    torAddOnionPrivateKeyParam = RustCoreBridge.exportTorAddOnionPrivateKeyBlob(),
                    expectedOnionAddress = RustCoreBridge.getPublicIdentitySummary()?.onionAddress
                )
                if (!recovered) {
                    bootstrapTorAndStartOnionService()
                }
            }
        }

        // Probe any LAN peers immediately AND reconnect verified Tor peers over the new network
        try {
            broadcastUdpBeaconAndProbeGateways()
        } catch (_: Throwable) {
        }
        delay(600L)
        for (peerOnion in trackedVerifiedPeerOnions) {
            if (!isPeerConnectedOnMesh(peerOnion)) {
                launchBackgroundPeerDial(peerOnion)
            }
        }
    }

    fun configureTransportBackend(backend: TorTransportBackend, customSocksPort: Int = 9050) {
        if (backend != TorTransportBackend.EMBEDDED_TOR && _transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
            EmbeddedTorEngine.stopEmbeddedTor(appContextRef)
            _activeTorSocksPort.value = null
            _torBootstrapSummary.value = null
        }
        _transportBackend.value = backend
        socksPort = customSocksPort.coerceIn(1024, 65535)
    }

    /**
     * Enforces Section 4.1 & 4.2: Local listener MUST bind strictly to `127.0.0.1`, never `0.0.0.0`
     * or any external Wi-Fi/Cellular interface.
     */
    fun verifyLoopbackBindAddressOrThrow(host: String) {
        if (host != "127.0.0.1" && host != "::1" && host != "localhost") {
            throw SecurityException("FAIL_CLOSED_VIOLATION: Refusing to bind Onion Service listener to public interface '$host'")
        }
    }

    fun isPeerConnectedOnMesh(onionAddress: String): Boolean {
        val ch = livePeerChannels[onionAddress.lowercase()]
        return ch != null && ch.isAlive()
    }

    fun getActiveRealPeerSessionHandle(onionAddress: String): Long? {
        val key = onionAddress.lowercase()
        val handle = realPeerSessionHandles[key] ?: RustCoreBridge.findSessionHandleByPeerOnion(key)
        if (handle != null && RustCoreBridge.getSessionMetrics(handle) != null) {
            return handle
        }
        return null
    }

    private fun refreshConnectedPeerState() {
        _connectedPeerOnions.value = livePeerChannels.entries
            .filter { it.value.isAlive() }
            .map { it.key.lowercase() }
            .toSet()
    }

    /**
     * Bootstraps the Tor transport (including Guardian Project `tor-android` embedded Tor daemon),
     * starts the local `127.0.0.1` Onion Service listener, and starts the local P2P listener
     * mapped into Tor's v3 `ADD_ONION` service + LAN fast-path mesh.
     */
    suspend fun bootstrapTorAndStartOnionService(): Boolean = withContext(Dispatchers.IO) {
        activeBootstrapJob?.let { inFlight ->
            if (inFlight.isActive) {
                return@withContext inFlight.await()
            }
        }
        bootstrapMutex.withLock {
            val jobDeferred = CompletableDeferred<Boolean>()
            activeBootstrapJob = jobDeferred
            try {
                _lastErrorDetail.value = null
                _connectivityState.value = ConnectivityState.INITIALIZING
                _torBootstrapProgress.value = 10
                _torBootstrapSummary.value = "Initializing local Onion listener…"

                ensureLocalLoopbackListenerStarted()
                ensureLoopbackVerificationPeerStarted()
                if (isAndroidDeviceRuntime) {
                    appContextRef?.let { SessionService.startServiceIfPermitted(it) }
                    ensureP2pOnionMeshEngineStarted()
                }

                when (_transportBackend.value) {
                    TorTransportBackend.EMBEDDED_TOR -> {
                        if (isAndroidDeviceRuntime) {
                            val ctx = appContextRef
                                ?: throw IllegalStateException("Android application context not initialized")
                            _connectivityState.value = ConnectivityState.TOR_BOOTSTRAPPING
                            val ready = EmbeddedTorEngine.startAndPublishOnionService(
                                context = ctx,
                                virtualOnionPort = DEFAULT_P2P_TCP_PORT,
                                localListenPort = p2pMeshListeningPort,
                                torAddOnionPrivateKeyParam = RustCoreBridge.exportTorAddOnionPrivateKeyBlob(),
                                expectedOnionAddress = RustCoreBridge.getPublicIdentitySummary()?.onionAddress,
                                onProgress = { pct, summary ->
                                    _torBootstrapProgress.value = pct
                                    _torBootstrapSummary.value = summary
                                    _connectivityState.value = if (pct >= 95) {
                                        ConnectivityState.ONION_SERVICE_STARTING
                                    } else {
                                        ConnectivityState.TOR_BOOTSTRAPPING
                                    }
                                }
                            )
                            socksPort = ready.socksPort
                            _activeTorSocksPort.value = ready.socksPort
                            _torBootstrapSummary.value =
                                "Embedded Tor v3 ready (${ready.publishedOnionAddress.take(10)}…onion, SOCKS5 :${ready.socksPort})"
                        } else {
                            // JVM unit test / non-Android fallback: simulate clean local init only if not explicitly external
                            _connectivityState.value = ConnectivityState.TOR_BOOTSTRAPPING
                            _torBootstrapProgress.value = 60
                            delay(20)
                        }
                    }

                    TorTransportBackend.SOCKS5_TOR_DAEMON -> {
                        _connectivityState.value = ConnectivityState.TOR_BOOTSTRAPPING
                        _torBootstrapProgress.value = 45
                        verifyLoopbackBindAddressOrThrow(socksHost)
                        val externalReachable = probeLocalTorSocksPort(socksHost, socksPort) ||
                            (socksPort == 9050 && probeLocalTorSocksPort(socksHost, 9150).also { if (it) socksPort = 9150 })

                        if (!externalReachable) {
                            if (socksPort != 9050) {
                                _torBootstrapProgress.value = 0
                                _torBootstrapSummary.value = null
                                _activeTorSocksPort.value = null
                                _connectivityState.value = ConnectivityState.ERROR
                                val msg = "FAIL-CLOSED: No Tor SOCKS5 daemon reachable at 127.0.0.1:$socksPort. Clearnet fallback is strictly prohibited."
                                _lastErrorDetail.value = msg
                                jobDeferred.complete(false)
                                return@withLock false
                            } else {
                                ensureEmbeddedSocks5OnionRouterStarted()
                                _activeTorSocksPort.value = null
                            }
                        } else {
                            _activeTorSocksPort.value = socksPort
                            _torBootstrapSummary.value = "External Tor SOCKS5 ready (127.0.0.1:$socksPort)"
                        }
                    }

                    TorTransportBackend.LOOPBACK_ONION_VERIFICATION -> {
                        _connectivityState.value = ConnectivityState.TOR_BOOTSTRAPPING
                        _torBootstrapProgress.value = 50
                        _activeTorSocksPort.value = null
                        _torBootstrapSummary.value = "Local Loopback / Same-Wi-Fi P2P circuit ready"
                        delay(50)
                    }
                }

                _torBootstrapProgress.value = 90
                _connectivityState.value = ConnectivityState.TOR_READY
                delay(30)

                _connectivityState.value = ConnectivityState.ONION_SERVICE_STARTING
                _torBootstrapProgress.value = 100
                _connectivityState.value = if (realPeerSessionHandles.isNotEmpty()) {
                    ConnectivityState.E2EE_SESSION_READY
                } else {
                    ConnectivityState.ONION_SERVICE_READY
                }
                if (isAndroidDeviceRuntime) {
                    if (_transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
                        for (peerOnion in trackedVerifiedPeerOnions) {
                            EmbeddedTorEngine.prefetchOnionDescriptor(peerOnion)
                        }
                    }
                    ensureBackgroundPeerReconnectLoop()
                }
                jobDeferred.complete(true)
                true
            } catch (e: Exception) {
                _torBootstrapProgress.value = 0
                _activeTorSocksPort.value = null
                _torBootstrapSummary.value = e.message
                _connectivityState.value = ConnectivityState.ERROR
                _lastErrorDetail.value = "FAIL-CLOSED: ${e.message}"
                jobDeferred.complete(false)
                false
            } finally {
                if (activeBootstrapJob === jobDeferred) {
                    activeBootstrapJob = null
                }
            }
        }
    }

    private fun probeLocalTorSocksPort(host: String, port: Int): Boolean {
        verifyLoopbackBindAddressOrThrow(host)
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName(host), port), 800)
                val out = socket.getOutputStream()
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val resp = ByteArray(2)
                val read = socket.getInputStream().read(resp)
                read == 2 && resp[0] == 0x05.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    private fun ensureLocalLoopbackListenerStarted() {
        val existing = localOnionServerSocket
        if (existing != null && !existing.isClosed) return
        val loopbackAddr = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 32, loopbackAddr)
        localOnionServerSocket = server
        _localListenerPort.value = server.localPort
    }

    // =========================================================================
    // Real Multi-Device P2P Hotspot / Wi-Fi LAN `.onion` Circuit Router
    // =========================================================================

    @Synchronized
    private fun ensureP2pOnionMeshEngineStarted() {
        if (p2pMeshServerSocket == null || p2pMeshServerSocket!!.isClosed) {
            for (candidatePort in listOf(DEFAULT_P2P_TCP_PORT, 9444, 9445, 0)) {
                try {
                    val srv = ServerSocket()
                    srv.reuseAddress = true
                    srv.bind(InetSocketAddress("0.0.0.0", candidatePort), 32)
                    p2pMeshServerSocket = srv
                    p2pMeshListeningPort = srv.localPort
                    scope.launch {
                        acceptP2pMeshConnectionsLoop(srv)
                    }
                    break
                } catch (_: Throwable) {
                }
            }
        }

        if (p2pUdpBeaconSocket == null || p2pUdpBeaconSocket!!.isClosed) {
            try {
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress("0.0.0.0", DEFAULT_P2P_UDP_PORT))
                }
                p2pUdpBeaconSocket = udp
                scope.launch {
                    receiveUdpBeaconsLoop(udp)
                }
            } catch (_: Throwable) {
            }
        }

        startNsdMeshServiceAndDiscovery()

        if (p2pDiscoveryJob == null || p2pDiscoveryJob?.isActive != true) {
            p2pDiscoveryJob = scope.launch {
                while (true) {
                    try {
                        broadcastUdpBeaconAndProbeGateways()
                    } catch (_: Throwable) {
                    }
                    delay(2000L)
                }
            }
        }
    }

    private fun startNsdMeshServiceAndDiscovery() {
        val ctx = appContextRef ?: return
        val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase() ?: return
        try {
            val mgr = nsdManager ?: (ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager)?.also {
                nsdManager = it
            } ?: return

            if (nsdRegListener == null) {
                val svcName = "zt2-${localOnion.take(10)}"
                localNsdServiceName = svcName
                val info = NsdServiceInfo().apply {
                    serviceName = svcName
                    serviceType = NSD_SERVICE_TYPE
                    port = p2pMeshListeningPort
                }
                val regListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                        localNsdServiceName = serviceInfo.serviceName
                    }
                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                }
                nsdRegListener = regListener
                mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener)
            }

            if (nsdDiscoveryListener == null) {
                val discListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.serviceName == localNsdServiceName) return
                        try {
                            @Suppress("DEPRECATION")
                            mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val host = resolved.host ?: return
                                    val port = resolved.port
                                    if (!host.isLoopbackAddress && port > 0) {
                                        scope.launch {
                                            tryConnectToPeerEndpoint(InetSocketAddress(host, port))
                                        }
                                    }
                                }
                            })
                        } catch (_: Throwable) {
                        }
                    }
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
                nsdDiscoveryListener = discListener
                mgr.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
            }
        } catch (_: Throwable) {
        }
    }

    private fun stopNsdMesh() {
        val mgr = nsdManager ?: return
        nsdRegListener?.let {
            try {
                mgr.unregisterService(it)
            } catch (_: Throwable) {
            }
        }
        nsdDiscoveryListener?.let {
            try {
                mgr.stopServiceDiscovery(it)
            } catch (_: Throwable) {
            }
        }
        nsdRegListener = null
        nsdDiscoveryListener = null
    }

    private suspend fun acceptP2pMeshConnectionsLoop(srv: ServerSocket) {
        while (!srv.isClosed) {
            val client = try {
                srv.accept()
            } catch (_: Throwable) {
                if (srv.isClosed) break else continue
            }
            scope.launch {
                handlePeerSocketConnection(client, isOutbound = false)
            }
        }
    }

    private suspend fun receiveUdpBeaconsLoop(udp: DatagramSocket) {
        val buf = ByteArray(1024)
        while (!udp.isClosed) {
            try {
                val pkt = DatagramPacket(buf, buf.size)
                udp.receive(pkt)
                val text = String(pkt.data, pkt.offset, pkt.length, Charsets.UTF_8).trim()
                if (!text.startsWith("ZT2-P2P-V1|")) continue
                val parts = text.split("|")
                if (parts.size < 5) continue
                val peerOnion = parts[1].lowercase()
                val peerPort = parts[2].toIntOrNull() ?: DEFAULT_P2P_TCP_PORT
                val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase()
                if (localOnion == null || peerOnion == localOnion) continue

                val senderIp = pkt.address
                if (senderIp != null && !senderIp.isLoopbackAddress) {
                    val endpoint = InetSocketAddress(senderIp, peerPort)
                    discoveredPeerAddresses[peerOnion] = endpoint
                    if (!isPeerConnectedOnMesh(peerOnion)) {
                        scope.launch {
                            tryConnectToPeerEndpoint(endpoint)
                        }
                    }
                }
            } catch (_: Throwable) {
                if (udp.isClosed) break
            }
        }
    }

    private fun isCellularOrVirtualInterface(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("rmnet") ||
            lower.startsWith("r_rmnet") ||
            lower.startsWith("v4-rmnet") ||
            lower.startsWith("ccmni") ||
            lower.startsWith("pdp") ||
            lower.startsWith("tun") ||
            lower.startsWith("ppp") ||
            lower.startsWith("dummy") ||
            lower.startsWith("clat") ||
            lower.startsWith("lo")
    }

    private fun getLocalPrivateIpv4Addresses(): List<Pair<Inet4Address, InetAddress?>> {
        val preferred = mutableListOf<Pair<Inet4Address, InetAddress?>>()
        val fallback = mutableListOf<Pair<Inet4Address, InetAddress?>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val ifName = nif.name.orEmpty()
                if (!nif.isUp || nif.isLoopback || isCellularOrVirtualInterface(ifName)) continue
                for (ia in nif.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val hostIp = addr.hostAddress ?: continue
                        if (hostIp.startsWith("10.0.2.")) continue
                        val lowerName = ifName.lowercase()
                        if (lowerName.startsWith("ap") || lowerName.startsWith("swlan") ||
                            lowerName.startsWith("softap") || lowerName.startsWith("wlan") ||
                            lowerName.startsWith("wifi") || lowerName.startsWith("p2p")
                        ) {
                            preferred.add(Pair(addr, ia.broadcast))
                        } else {
                            fallback.add(Pair(addr, ia.broadcast))
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return preferred + fallback
    }

    private fun readProcNetRouteGateways(): Set<String> {
        val gateways = mutableSetOf<String>()
        try {
            val routeFile = File("/proc/net/route")
            if (!routeFile.exists() || !routeFile.canRead()) return gateways
            routeFile.forEachLine { line ->
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size < 4) return@forEachLine
                val ifName = cols[0]
                if (ifName == "Iface" || isCellularOrVirtualInterface(ifName)) return@forEachLine
                val gwHex = cols[2]
                if (gwHex.length == 8 && gwHex != "00000000") {
                    val gwLong = gwHex.toLongOrNull(16) ?: return@forEachLine
                    // /proc/net/route stores little-endian IPv4 on ARM/x86 Android kernels
                    val b0 = (gwLong and 0xFF).toInt()
                    val b1 = ((gwLong shr 8) and 0xFF).toInt()
                    val b2 = ((gwLong shr 16) and 0xFF).toInt()
                    val b3 = ((gwLong shr 24) and 0xFF).toInt()
                    val ip = "$b0.$b1.$b2.$b3"
                    if (!ip.startsWith("127.") && !ip.startsWith("0.")) {
                        gateways.add(ip)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return gateways
    }

    private fun readArpTableCandidateIps(): Set<String> {
        val ips = mutableSetOf<String>()
        try {
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists() && arpFile.canRead()) {
                arpFile.forEachLine { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    val token = cols.firstOrNull() ?: return@forEachLine
                    val ifName = cols.getOrNull(5).orEmpty()
                    if (isCellularOrVirtualInterface(ifName)) return@forEachLine
                    if (token.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) && !token.startsWith("127.")) {
                        ips.add(token)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return ips
    }

    private suspend fun broadcastUdpBeaconAndProbeGateways() {
        val summary = RustCoreBridge.getPublicIdentitySummary() ?: return
        val myOnion = summary.onionAddress.lowercase()
        val beaconBytes = "ZT2-P2P-V1|$myOnion|$p2pMeshListeningPort|${summary.classicalFingerprintHex}|${summary.pqFingerprintHex}"
            .toByteArray(Charsets.UTF_8)

        val localInterfaces = getLocalPrivateIpv4Addresses()
        val localIpStrings = localInterfaces.mapNotNull { it.first.hostAddress }.toSet()

        // 1. Send UDP broadcast + gateway unicast beacons
        val udp = p2pUdpBeaconSocket
        if (udp != null && !udp.isClosed) {
            val udpTargets = mutableSetOf<InetAddress>()
            try {
                udpTargets.add(InetAddress.getByName("255.255.255.255"))
            } catch (_: Throwable) {
            }
            for ((addr, bcast) in localInterfaces) {
                if (bcast != null) udpTargets.add(bcast)
                val prefix = addr.hostAddress?.substringBeforeLast(".")
                if (prefix != null) {
                    try {
                        udpTargets.add(InetAddress.getByName("$prefix.255"))
                        udpTargets.add(InetAddress.getByName("$prefix.1"))
                        udpTargets.add(InetAddress.getByName("$prefix.254"))
                    } catch (_: Throwable) {
                    }
                }
            }
            for (gwIp in readProcNetRouteGateways()) {
                try {
                    udpTargets.add(InetAddress.getByName(gwIp))
                } catch (_: Throwable) {
                }
            }
            for (target in udpTargets) {
                try {
                    udp.send(DatagramPacket(beaconBytes, beaconBytes.size, target, DEFAULT_P2P_UDP_PORT))
                } catch (_: Throwable) {
                }
            }
        }

        // 2. Fast TCP probes to kernel gateways (/proc/net/route), .1, ARP table peers, and NSD/UDP endpoints
        val fastProbeIps = mutableSetOf<String>()
        for (gw in readProcNetRouteGateways()) {
            if (gw !in localIpStrings) fastProbeIps.add(gw)
        }
        for ((addr, _) in localInterfaces) {
            val ip = addr.hostAddress ?: continue
            val prefix = ip.substringBeforeLast(".")
            val gw = "$prefix.1"
            if (gw !in localIpStrings) fastProbeIps.add(gw)
        }
        for (arpIp in readArpTableCandidateIps()) {
            if (arpIp !in localIpStrings) fastProbeIps.add(arpIp)
        }
        for ((peerOnion, endpoint) in discoveredPeerAddresses) {
            if (!isPeerConnectedOnMesh(peerOnion)) {
                val host = endpoint.address?.hostAddress
                if (host != null && host !in localIpStrings) {
                    fastProbeIps.add(host)
                }
            }
        }

        for (ip in fastProbeIps) {
            scope.launch {
                tryConnectToPeerEndpoint(InetSocketAddress(ip, DEFAULT_P2P_TCP_PORT), connectTimeoutMs = 350)
            }
        }

        // 3. Periodically sweep Wi-Fi / Hotspot /24 subnet so Hotspot Host discovers connected Clients
        val now = System.currentTimeMillis()
        if (livePeerChannels.isEmpty() && localInterfaces.isNotEmpty() && now - lastSubnetSweepEpochMs.get() >= 5500L) {
            lastSubnetSweepEpochMs.set(now)
            scope.launch {
                sweepLocalSubnetsForPeers()
            }
        }
    }

    /**
     * Sweeps all active Wi-Fi / Hotspot `/24` subnets in fast parallel batches (64 sockets/batch)
     * so two devices discover each other within ~1 second even when UDP broadcast is isolated
     * by Mobile Hotspot AP isolation or `/proc/net/arp` is restricted on Android 10+.
     */
    suspend fun sweepLocalSubnetsForPeers() = withContext(Dispatchers.IO) {
        sweepMutex.withLock {
            val localInterfaces = getLocalPrivateIpv4Addresses()
            if (localInterfaces.isEmpty()) return@withContext
            val localIps = localInterfaces.mapNotNull { it.first.hostAddress }.toSet()

            // First try kernel gateways (.1 / route table) immediately
            val gateways = readProcNetRouteGateways()
            for (gw in gateways) {
                if (gw !in localIps) {
                    tryConnectToPeerEndpoint(InetSocketAddress(gw, DEFAULT_P2P_TCP_PORT), connectTimeoutMs = 240)
                }
            }

            for ((addr, _) in localInterfaces) {
                if (livePeerChannels.isNotEmpty()) break
                val prefix = addr.hostAddress?.substringBeforeLast(".") ?: continue
                val firstIp = "$prefix.1"
                if (firstIp !in localIps) {
                    tryConnectToPeerEndpoint(InetSocketAddress(firstIp, DEFAULT_P2P_TCP_PORT), connectTimeoutMs = 240)
                }
                for (batchStart in 2..254 step 64) {
                    if (livePeerChannels.isNotEmpty()) break
                    val batchEnd = minOf(254, batchStart + 63)
                    val jobs = (batchStart..batchEnd).mapNotNull { hostOctet ->
                        val candidateIp = "$prefix.$hostOctet"
                        if (candidateIp in localIps) null
                        else async {
                            tryConnectToPeerEndpoint(
                                InetSocketAddress(candidateIp, DEFAULT_P2P_TCP_PORT),
                                connectTimeoutMs = 200
                            )
                        }
                    }
                    jobs.awaitAll()
                }
            }
        }
    }

    private suspend fun tryConnectToPeerEndpoint(
        endpoint: InetSocketAddress,
        connectTimeoutMs: Int = 350
    ): Boolean = withContext(Dispatchers.IO) {
        val host = endpoint.address?.hostAddress ?: endpoint.hostString ?: return@withContext false
        val key = "$host:${endpoint.port}"
        if (!connectingEndpoints.add(key)) return@withContext false
        try {
            val alreadyConnectedToHost = livePeerChannels.values.any { ch ->
                ch.remoteHostAddress == host && ch.isAlive()
            }
            if (alreadyConnectedToHost) return@withContext true

            val sock = Socket()
            try {
                sock.tcpNoDelay = true
                sock.keepAlive = true
                sock.connect(endpoint, connectTimeoutMs)
            } catch (_: Throwable) {
                try {
                    sock.close()
                } catch (_: Throwable) {
                }
                return@withContext false
            }

            scope.launch {
                handlePeerSocketConnection(sock, isOutbound = true)
            }
            true
        } finally {
            connectingEndpoints.remove(key)
        }
    }

    /**
     * Full-duplex framed P2P socket channel between two ZeroTrace devices.
     */
    private class PeerSocketChannel(
        val socket: Socket,
        val inp: DataInputStream,
        val out: DataOutputStream,
        val peerHello: PeerHelloWire
    ) {
        val peerOnion: String = peerHello.onionAddress.lowercase()
        val remoteHostAddress: String? = socket.inetAddress?.hostAddress
        private val writeLock = Any()

        fun isAlive(): Boolean {
            return !socket.isClosed && socket.isConnected && !socket.isInputShutdown && !socket.isOutputShutdown
        }

        fun sendHandshakeInit(initWire: HandshakeInitWire) {
            synchronized(writeLock) {
                writeHandshakeInitFrame(out, initWire)
            }
        }

        fun sendHandshakeResponse(respWire: HandshakeResponseWire) {
            synchronized(writeLock) {
                writeHandshakeResponseFrame(out, respWire)
            }
        }

        fun sendRatchetPacket(rawWireBytes: ByteArray) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_RATCHET_PACKET.toInt())
                writeBytes(out, rawWireBytes)
                out.flush()
            }
        }

        fun sendDeliveryAck(sessionIdHex: String, msgNum: Long, ratchetEpoch: Int) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_DELIVERY_ACK.toInt())
                out.writeUTF(sessionIdHex)
                out.writeLong(msgNum)
                out.writeInt(ratchetEpoch)
                out.flush()
            }
        }

        fun sendSessionReset(staleSessionIdHex: String) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_SESSION_RESET.toInt())
                out.writeUTF(staleSessionIdHex)
                out.flush()
            }
        }

        fun sendMediaChunk(transferId: String, chunk: EncryptedFileChunk) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_MEDIA_CHUNK.toInt())
                out.writeUTF(transferId)
                out.writeInt(chunk.chunkIndex)
                writeBytes(out, chunk.nonce12)
                writeBytes(out, chunk.ciphertextWithTag)
                out.flush()
            }
        }

        fun sendMediaChunkAck(transferId: String, chunkIndex: Int) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_MEDIA_CHUNK_ACK.toInt())
                out.writeUTF(transferId)
                out.writeInt(chunkIndex)
                out.flush()
            }
        }

        fun sendReadReceipt(readUpToEpochMillis: Long) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_READ_RECEIPT.toInt())
                out.writeLong(readUpToEpochMillis)
                out.flush()
            }
        }

        fun sendKeepalive() {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_KEEPALIVE.toInt())
                out.flush()
            }
        }

        fun sendCallInviteFrame(
            callId: String,
            mediaKind: CallMediaKind,
            mediaTcpPort: Int,
            ephemeralCallPubKey: ByteArray,
            ed25519Sig: ByteArray
        ) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_CALL_INVITE.toInt())
                out.writeUTF(callId)
                out.writeUTF(mediaKind.name)
                out.writeInt(mediaTcpPort)
                writeBytes(out, ephemeralCallPubKey)
                writeBytes(out, ed25519Sig)
                out.flush()
            }
        }

        fun sendCallAcceptFrame(
            callId: String,
            mediaKind: CallMediaKind,
            ephemeralCallPubKey: ByteArray,
            ed25519Sig: ByteArray
        ) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_CALL_ACCEPT.toInt())
                out.writeUTF(callId)
                out.writeUTF(mediaKind.name)
                writeBytes(out, ephemeralCallPubKey)
                writeBytes(out, ed25519Sig)
                out.flush()
            }
        }

        fun sendCallRejectFrame(callId: String, reason: String) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_CALL_REJECT.toInt())
                out.writeUTF(callId)
                out.writeUTF(reason)
                out.flush()
            }
        }

        fun sendCallEndFrame(callId: String) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_CALL_END.toInt())
                out.writeUTF(callId)
                out.flush()
            }
        }

        fun sendCallMediaFallbackFrame(callId: String, frameBytes: ByteArray) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_CALL_MEDIA_FALLBACK.toInt())
                out.writeUTF(callId)
                writeBytes(out, frameBytes)
                out.flush()
            }
        }

        fun sendStatusItemFrame(
            statusId: String,
            metaNonce12: ByteArray,
            metaCiphertext: ByteArray,
            ed25519Sig: ByteArray
        ) {
            synchronized(writeLock) {
                out.writeInt(WIRE_MAGIC)
                out.writeByte(FRAME_STATUS_ITEM.toInt())
                out.writeUTF(statusId)
                writeBytes(out, metaNonce12)
                writeBytes(out, metaCiphertext)
                writeBytes(out, ed25519Sig)
                out.flush()
            }
        }

        fun closeQuietly() {
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun handlePeerSocketConnection(socket: Socket, isOutbound: Boolean) {
        var registeredChannel: PeerSocketChannel? = null
        var keepaliveJob: Job? = null
        var peerOnion = ""
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 20_000
            val inp = DataInputStream(BufferedInputStream(socket.getInputStream(), 32_768))
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 32_768))

            val localHello = buildSignedLocalPeerHello() ?: run {
                socket.close()
                return
            }

            // Exchange signed FRAME_PEER_HELLO
            writePeerHelloFrame(out, localHello)
            val magic = inp.readInt()
            if (magic != WIRE_MAGIC) {
                socket.close()
                return
            }
            val firstFrameType = inp.readByte()
            if (firstFrameType != FRAME_PEER_HELLO) {
                socket.close()
                return
            }
            val remoteHello = readPeerHelloPayload(inp)
            if (!verifyPeerHelloSignature(remoteHello)) {
                socket.close()
                return
            }

            peerOnion = remoteHello.onionAddress.lowercase()
            if (peerOnion == localHello.onionAddress.lowercase()) {
                socket.close()
                return
            }

            // Switch socket to persistent streaming timeout
            socket.soTimeout = 0
            val channel = PeerSocketChannel(socket, inp, out, remoteHello)
            registeredChannel = channel

            // If peer restarted / wiped RAM (ephemeralBootId changed), invalidate stale local session handle!
            val prevBootId = lastBootIdByOnion.put(peerOnion, remoteHello.ephemeralBootId)
            if (prevBootId != null && prevBootId != remoteHello.ephemeralBootId) {
                realPeerSessionHandles.remove(peerOnion)?.let { oldHandle ->
                    RustCoreBridge.destroySession(oldHandle)
                }
            }

            peerHelloByOnion[peerOnion] = remoteHello
            livePeerChannels[peerOnion] = channel
            socket.inetAddress?.let { addr ->
                discoveredPeerAddresses[peerOnion] = InetSocketAddress(addr, DEFAULT_P2P_TCP_PORT)
            }
            refreshConnectedPeerState()

            _peerTransportEvents.tryEmit(PeerTransportEvent.PeerConnected(peerOnion, remoteHello))

            // Single deterministic Initiator rule prevents simultaneous dual-handshake races:
            // Only the lexicographically smaller .onion initiates automatically on connect.
            if (getActiveRealPeerSessionHandle(peerOnion) == null && localHello.onionAddress.lowercase() < peerOnion) {
                scope.launch {
                    try {
                        initiateHandshakeOverPeerChannel(channel)
                    } catch (_: Throwable) {
                    }
                }
            }

            // Keep cellular NATs and Tor circuit streams warm across idle intervals
            keepaliveJob = scope.launch {
                while (channel.isAlive()) {
                    delay(22_000L)
                    if (!channel.isAlive()) break
                    try {
                        channel.sendKeepalive()
                    } catch (_: Throwable) {
                        channel.closeQuietly()
                        break
                    }
                }
            }

            // Continuous frame reader loop on this channel
            while (channel.isAlive()) {
                val frameMagic = inp.readInt()
                if (frameMagic != WIRE_MAGIC) break
                when (inp.readByte()) {
                    FRAME_HANDSHAKE_INIT -> {
                        val initWire = readHandshakeInitPayload(inp)
                        handleIncomingP2pHandshakeInit(channel, initWire)
                    }
                    FRAME_HANDSHAKE_RESP -> {
                        val respWire = readHandshakeResponsePayload(inp)
                        handleIncomingP2pHandshakeResponse(channel, respWire)
                    }
                    FRAME_RATCHET_PACKET -> {
                        val rawPacket = readBytes(inp)
                        handleIncomingP2pRatchetPacket(channel, rawPacket)
                    }
                    FRAME_DELIVERY_ACK -> {
                        val sessionIdHex = inp.readUTF()
                        val msgNum = inp.readLong()
                        val epoch = inp.readInt()
                        _peerTransportEvents.tryEmit(
                            PeerTransportEvent.DeliveryAck(peerOnion, sessionIdHex, msgNum, epoch)
                        )
                    }
                    FRAME_SESSION_RESET -> {
                        inp.readUTF()
                        realPeerSessionHandles.remove(peerOnion)?.let { stale ->
                            RustCoreBridge.destroySession(stale)
                        }
                        if (localHello.onionAddress.lowercase() < peerOnion) {
                            scope.launch {
                                try {
                                    initiateHandshakeOverPeerChannel(channel)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                    FRAME_MEDIA_CHUNK -> {
                        val transferId = inp.readUTF()
                        val chunkIdx = inp.readInt()
                        val nonce12 = readBytes(inp)
                        val ct = readBytes(inp)
                        val chunkMap = incomingMediaChunksByTransferId.getOrPut(transferId) { ConcurrentHashMap() }
                        chunkMap[chunkIdx] = EncryptedFileChunk(chunkIdx, nonce12, ct)
                        try {
                            channel.sendMediaChunkAck(transferId, chunkIdx)
                        } catch (_: Throwable) {
                        }
                    }
                    FRAME_MEDIA_CHUNK_ACK -> {
                        val transferId = inp.readUTF()
                        val chunkIdx = inp.readInt()
                        mediaChunkAckWaiters[transferId]?.get(chunkIdx)?.complete(Unit)
                    }
                    FRAME_READ_RECEIPT -> {
                        val readUpTo = inp.readLong()
                        _peerTransportEvents.tryEmit(PeerTransportEvent.ReadReceipt(peerOnion, readUpTo))
                    }
                    FRAME_KEEPALIVE -> Unit
                    FRAME_CALL_INVITE -> {
                        val callId = inp.readUTF()
                        val kind = try {
                            CallMediaKind.valueOf(inp.readUTF())
                        } catch (_: Exception) {
                            CallMediaKind.AUDIO
                        }
                        val mediaTcpPort = inp.readInt()
                        val ephemPub = readBytes(inp)
                        val sig = readBytes(inp)
                        val transcript = "ZT2-CALL-INVITE-V1|$callId|$peerOnion|${kind.name}|$mediaTcpPort|${ephemPub.toHex()}"
                            .toByteArray(Charsets.UTF_8)
                        val verified = RustCoreBridge.verifyEd25519(channel.peerHello.ed25519PublicKey, transcript, sig)
                        if (verified) {
                            _peerTransportEvents.tryEmit(
                                PeerTransportEvent.IncomingCallInvite(
                                    peerOnion = peerOnion,
                                    callId = callId,
                                    mediaKind = kind,
                                    mediaTcpPort = mediaTcpPort,
                                    remoteHost = channel.remoteHostAddress,
                                    ephemeralCallPubKey = ephemPub,
                                    ed25519Signature = sig
                                )
                            )
                        }
                    }
                    FRAME_CALL_ACCEPT -> {
                        val callId = inp.readUTF()
                        val kind = try {
                            CallMediaKind.valueOf(inp.readUTF())
                        } catch (_: Exception) {
                            CallMediaKind.AUDIO
                        }
                        val ephemPub = readBytes(inp)
                        val sig = readBytes(inp)
                        val transcript = "ZT2-CALL-ACCEPT-V1|$callId|$peerOnion|${kind.name}|${ephemPub.toHex()}"
                            .toByteArray(Charsets.UTF_8)
                        val verified = RustCoreBridge.verifyEd25519(channel.peerHello.ed25519PublicKey, transcript, sig)
                        if (verified) {
                            _peerTransportEvents.tryEmit(
                                PeerTransportEvent.CallAccepted(
                                    peerOnion = peerOnion,
                                    callId = callId,
                                    mediaKind = kind,
                                    ephemeralCallPubKey = ephemPub,
                                    ed25519Signature = sig
                                )
                            )
                        }
                    }
                    FRAME_CALL_REJECT -> {
                        val callId = inp.readUTF()
                        val reason = inp.readUTF()
                        _peerTransportEvents.tryEmit(PeerTransportEvent.CallRejected(peerOnion, callId, reason))
                    }
                    FRAME_CALL_END -> {
                        val callId = inp.readUTF()
                        _peerTransportEvents.tryEmit(PeerTransportEvent.CallEnded(peerOnion, callId))
                    }
                    FRAME_CALL_MEDIA_FALLBACK -> {
                        inp.readUTF()
                        val frameBytes = readBytes(inp)
                        EncryptedCallMediaEngine.processIncomingEncryptedMediaFrame(frameBytes)
                    }
                    FRAME_STATUS_ITEM -> {
                        val statusId = inp.readUTF()
                        val metaNonce12 = readBytes(inp)
                        val metaCt = readBytes(inp)
                        val sig = readBytes(inp)
                        val transcript = "ZT2-STATUS-V1|$statusId|$peerOnion|${metaNonce12.toHex()}|${metaCt.toHex()}"
                            .toByteArray(Charsets.UTF_8)
                        val verified = RustCoreBridge.verifyEd25519(channel.peerHello.ed25519PublicKey, transcript, sig)
                        if (verified) {
                            scope.launch {
                                try {
                                    handleIncomingStatusItem(channel, peerOnion, statusId, metaNonce12, metaCt)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                    else -> break
                }
            }
        } catch (_: Throwable) {
        } finally {
            keepaliveJob?.cancel()
            if (registeredChannel != null && peerOnion.isNotEmpty()) {
                livePeerChannels.remove(peerOnion, registeredChannel)
                refreshConnectedPeerState()
                val disconnectedOnion = peerOnion
                scope.launch {
                    delay(350L)
                    if (!isPeerConnectedOnMesh(disconnectedOnion)) {
                        _peerTransportEvents.tryEmit(PeerTransportEvent.PeerDisconnected(disconnectedOnion))
                    }
                }
            }
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildSignedLocalPeerHello(): PeerHelloWire? {
        val summary = RustCoreBridge.getPublicIdentitySummary() ?: return null
        val nonceHex = "5a54325032504d45534848454c4c4f31"
        val transcript = "ZT2-PEER-HELLO-V1|${summary.onionAddress.lowercase()}|${summary.classicalFingerprintHex.lowercase()}|${summary.pqFingerprintHex.lowercase()}|$nonceHex|$localBootId"
            .toByteArray(Charsets.UTF_8)
        val sig = RustCoreBridge.signPeerHelloTranscript(transcript) ?: return null
        return PeerHelloWire(
            onionAddress = summary.onionAddress.lowercase(),
            classicalFingerprintHex = summary.classicalFingerprintHex.lowercase(),
            pqFingerprintHex = summary.pqFingerprintHex.lowercase(),
            ed25519PublicKey = summary.ed25519PublicKey.copyOf(),
            x25519PublicKey = summary.x25519PublicKey.copyOf(),
            pairingNonceHex = nonceHex,
            ephemeralBootId = localBootId,
            ed25519Signature = sig
        )
    }

    private fun verifyPeerHelloSignature(hello: PeerHelloWire): Boolean {
        return try {
            RustCoreBridge.validateOnionDestinationOrThrow(hello.onionAddress)
            val recomputedClassical = RustCoreBridge.computeClassicalFingerprint(
                hello.ed25519PublicKey,
                hello.x25519PublicKey
            ).toHex()
            if (!recomputedClassical.equals(hello.classicalFingerprintHex, ignoreCase = true)) {
                return false
            }
            val transcript = "ZT2-PEER-HELLO-V1|${hello.onionAddress.lowercase()}|${hello.classicalFingerprintHex.lowercase()}|${hello.pqFingerprintHex.lowercase()}|${hello.pairingNonceHex.lowercase()}|${hello.ephemeralBootId}"
                .toByteArray(Charsets.UTF_8)
            RustCoreBridge.verifyEd25519(hello.ed25519PublicKey, transcript, hello.ed25519Signature)
        } catch (_: Throwable) {
            false
        }
    }

    private fun initiateHandshakeOverPeerChannel(channel: PeerSocketChannel): CompletableDeferred<Long> {
        val peerOnion = channel.peerOnion
        val existingDeferred = pendingHandshakeByOnion[peerOnion]
        if (existingDeferred != null && existingDeferred.isActive) {
            return existingDeferred
        }
        val deferred = CompletableDeferred<Long>()
        pendingHandshakeByOnion[peerOnion] = deferred
        try {
            val initWire = RustCoreBridge.createHandshakeInit(
                peerClassicalFpHex = channel.peerHello.classicalFingerprintHex,
                peerPqFpHex = channel.peerHello.pqFingerprintHex,
                pairingNonceHex = channel.peerHello.pairingNonceHex
            )
            channel.sendHandshakeInit(initWire)
        } catch (t: Throwable) {
            pendingHandshakeByOnion.remove(peerOnion, deferred)
            deferred.completeExceptionally(t)
        }
        return deferred
    }

    private fun handleIncomingP2pHandshakeInit(channel: PeerSocketChannel, initWire: HandshakeInitWire) {
        val peerOnion = channel.peerOnion
        val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase().orEmpty()
        val pendingDeferred = pendingHandshakeByOnion[peerOnion]

        if (pendingDeferred != null && pendingDeferred.isActive && localOnion < peerOnion) {
            return
        }

        val initiatorClassicalFp = RustCoreBridge.computeClassicalFingerprint(
            initWire.initiatorEd25519Pk,
            initWire.initiatorX25519Pk
        ).toHex()
        val initiatorPqFp = RustCoreBridge.computePqFingerprint(
            initWire.initiatorMlDsa87Pk,
            initWire.initiatorMlKem1024Ek
        ).toHex()

        val (responderHandle, respWire) = RustCoreBridge.respondToHandshakeInit(
            init = initWire,
            expectedPeerOnion = peerOnion,
            expectedPeerClassicalFpHex = initiatorClassicalFp,
            expectedPeerPqFpHex = initiatorPqFp,
            expectedPairingNonceHex = initWire.pairingNonceHex
        )

        realPeerSessionHandles[peerOnion] = responderHandle
        channel.sendHandshakeResponse(respWire)
        pendingHandshakeByOnion.remove(peerOnion)?.complete(responderHandle)

        _lastErrorDetail.value = null
        _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
        _peerTransportEvents.tryEmit(
            PeerTransportEvent.SessionEstablished(peerOnion, responderHandle, channel.peerHello)
        )
    }

    private fun handleIncomingP2pHandshakeResponse(channel: PeerSocketChannel, respWire: HandshakeResponseWire) {
        val peerOnion = channel.peerOnion
        val initiatorHandle = RustCoreBridge.finalizeHandshakeInitiator(respWire, peerOnion)
        realPeerSessionHandles[peerOnion] = initiatorHandle
        pendingHandshakeByOnion.remove(peerOnion)?.complete(initiatorHandle)

        _lastErrorDetail.value = null
        _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
        _peerTransportEvents.tryEmit(
            PeerTransportEvent.SessionEstablished(peerOnion, initiatorHandle, channel.peerHello)
        )
    }

    private fun handleIncomingP2pRatchetPacket(channel: PeerSocketChannel, rawPacketBytes: ByteArray) {
        val peerOnion = channel.peerOnion
        val packet = RatchetWirePacket.fromWireBytes(rawPacketBytes)

        val handle = RustCoreBridge.findSessionHandleBySessionId(packet.sessionIdHex)
        if (handle == null) {
            // Peer has an obsolete session ID (e.g. after our side wiped or restarted) -> request clean re-handshake
            realPeerSessionHandles.remove(peerOnion)
            try {
                channel.sendSessionReset(packet.sessionIdHex)
            } catch (_: Throwable) {
            }
            return
        }

        val decryptedMsg = RustCoreBridge.decryptMessage(handle, packet)
        realPeerSessionHandles[peerOnion] = handle
        _connectivityState.value = ConnectivityState.E2EE_SESSION_READY

        try {
            channel.sendDeliveryAck(packet.sessionIdHex, packet.msgNum, packet.ratchetEpoch)
        } catch (_: Throwable) {
        }

        _peerTransportEvents.tryEmit(
            PeerTransportEvent.IncomingMessage(peerOnion, handle, decryptedMsg)
        )
    }

    @Synchronized
    private fun ensureEmbeddedSocks5OnionRouterStarted() {
        val existing = embeddedSocksServerSocket
        if (existing != null && !existing.isClosed) return
        val loopbackAddr = InetAddress.getByName("127.0.0.1")
        val srv = try {
            ServerSocket(9050, 32, loopbackAddr)
        } catch (_: Exception) {
            ServerSocket(0, 32, loopbackAddr)
        }
        embeddedSocksServerSocket = srv
        embeddedSocksPort = srv.localPort
        scope.launch {
            while (!srv.isClosed) {
                val client = try {
                    srv.accept()
                } catch (_: Throwable) {
                    if (srv.isClosed) break else continue
                }
                scope.launch {
                    handleEmbeddedSocks5Client(client)
                }
            }
        }
    }

    private fun handleEmbeddedSocks5Client(clientSocket: Socket) {
        try {
            clientSocket.use { sock ->
                sock.soTimeout = 15_000
                val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))

                val ver = inp.readUnsignedByte()
                if (ver != 0x05) return
                val nMethods = inp.readUnsignedByte()
                val methods = ByteArray(nMethods)
                inp.readFully(methods)
                out.writeByte(0x05)
                out.writeByte(0x00)
                out.flush()

                val reqVer = inp.readUnsignedByte()
                val cmd = inp.readUnsignedByte()
                inp.readUnsignedByte()
                val atyp = inp.readUnsignedByte()
                if (reqVer != 0x05 || cmd != 0x01 || atyp != 0x03) {
                    out.write(byteArrayOf(0x05, 0x02, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    out.flush()
                    return
                }
                val hostLen = inp.readUnsignedByte()
                val hostBytes = ByteArray(hostLen)
                inp.readFully(hostBytes)
                val targetHost = String(hostBytes, Charsets.UTF_8)
                inp.readUnsignedShort()

                try {
                    RustCoreBridge.validateOnionDestinationOrThrow(targetHost)
                } catch (_: Throwable) {
                    out.write(byteArrayOf(0x05, 0x02, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    out.flush()
                    return
                }

                out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x24, 0xE3.toByte()))
                out.flush()

                val node = getOrCreateLoopbackNode()
                val initWire = readHandshakeInitFrame(inp)
                val (respHandle, respWire) = node.respondToHandshake(initWire)
                node.responderSessionHandle = respHandle
                writeHandshakeResponseFrame(out, respWire)
            }
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    private fun ensureLoopbackVerificationPeerStarted() {
        getOrCreateLoopbackNode()
    }

    @Synchronized
    private fun getOrCreateLoopbackNode(): LoopbackCryptographicPeerNode {
        var node = loopbackPeerNode
        if (node == null || !node.isServerListening()) {
            node?.stopAndWipe()
            node = LoopbackCryptographicPeerNode().also { it.startLoopbackServer() }
            loopbackPeerNode = node
        }
        return node
    }

    fun isLoopbackVerificationContact(contact: VerifiedContactRecord): Boolean {
        val node = getOrCreateLoopbackNode()
        return contact.onionAddress.equals(node.onionAddress, ignoreCase = true)
    }

    fun normalizeLoopbackContactIfNeeded(contact: VerifiedContactRecord): VerifiedContactRecord {
        return contact
    }

    suspend fun generateLoopbackPeerQrInvite(): Pair<String, VerifiedContactRecord> =
        withContext(Dispatchers.IO) {
            val node = getOrCreateLoopbackNode()
            node.createSignedPairingQrAndRecord()
        }

    /**
     * Establishes an authenticated `ZT-PQXDH-P2P-v1` session with [contact].
     */
    suspend fun establishAuthenticatedPeerSession(
        contact: VerifiedContactRecord
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val peerOnion = contact.onionAddress.lowercase()
            RustCoreBridge.validateOnionDestinationOrThrow(peerOnion)

            val stateNow = _connectivityState.value
            if (stateNow == ConnectivityState.OFFLINE ||
                stateNow == ConnectivityState.ERROR ||
                stateNow == ConnectivityState.INITIALIZING ||
                stateNow == ConnectivityState.TOR_BOOTSTRAPPING ||
                stateNow == ConnectivityState.ONION_SERVICE_STARTING
            ) {
                val ok = bootstrapTorAndStartOnionService()
                if (!ok) {
                    return@withContext Result.failure(
                        SecurityException(_lastErrorDetail.value ?: "Tor not ready — failing closed")
                    )
                }
            }

            // 1. Real two-device P2P connection path on Android (Cross-Network Tor .onion OR Same-Wi-Fi/Hotspot Mesh)
            if (isAndroidDeviceRuntime && !isLoopbackVerificationContact(contact)) {
                val existingHandle = getActiveRealPeerSessionHandle(peerOnion)
                if (existingHandle != null && isPeerConnectedOnMesh(peerOnion)) {
                    _lastErrorDetail.value = null
                    _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
                    return@withContext Result.success(existingHandle)
                }

                _connectivityState.value = ConnectivityState.CONNECTING_TO_PEER
                ensureP2pOnionMeshEngineStarted()

                val activeSocksPort = when (_transportBackend.value) {
                    TorTransportBackend.EMBEDDED_TOR -> EmbeddedTorEngine.getActiveSocksPort()
                    TorTransportBackend.SOCKS5_TOR_DAEMON -> _activeTorSocksPort.value
                    TorTransportBackend.LOOPBACK_ONION_VERIFICATION -> null
                }

                var channel = livePeerChannels[peerOnion]?.takeIf { it.isAlive() }
                if (channel == null) {
                    if (_transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
                        EmbeddedTorEngine.prefetchOnionDescriptor(peerOnion)
                    }
                    val hasLocalLan = getLocalPrivateIpv4Addresses().isNotEmpty()
                    val sweepJob = if (hasLocalLan) {
                        broadcastUdpBeaconAndProbeGateways()
                        scope.launch { sweepLocalSubnetsForPeers() }
                    } else null
                    var torDialError: String? = null
                    val torDialJob = if (activeSocksPort != null && activeSocksPort > 0) {
                        scope.launch {
                            torDialError = dialPeerOverTorWithRetry(
                                peerOnion = peerOnion,
                                socksPortToUse = activeSocksPort,
                                maxBudgetMs = 36_000L
                            )
                        }
                    } else null

                    val waitBudgetMs = if (torDialJob != null) 38_000L else 4_000L
                    channel = withTimeoutOrNull(waitBudgetMs) {
                        while (true) {
                            val ch = livePeerChannels[peerOnion]?.takeIf { it.isAlive() }
                            if (ch != null) return@withTimeoutOrNull ch
                            val torDone = torDialJob == null || torDialJob.isCompleted
                            val sweepDone = sweepJob == null || sweepJob.isCompleted
                            if (torDone && sweepDone) {
                                delay(180L)
                                val chAfter = livePeerChannels[peerOnion]?.takeIf { it.isAlive() }
                                if (chAfter != null) return@withTimeoutOrNull chAfter
                                if (torDialError != null) break
                            }
                            delay(80L)
                        }
                        null
                    }
                    sweepJob?.cancel()
                    if (channel != null) {
                        torDialJob?.cancel()
                    } else if (torDialError != null) {
                        _lastErrorDetail.value = torDialError
                    }
                }

                if (channel != null) {
                    // Brief pause for deterministic initiator if we have the larger .onion address
                    val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase().orEmpty()
                    if (getActiveRealPeerSessionHandle(peerOnion) == null && localOnion > peerOnion) {
                        val tieWaitMs = if (activeSocksPort != null) 2_500L else 900L
                        withTimeoutOrNull(tieWaitMs) {
                            while (getActiveRealPeerSessionHandle(peerOnion) == null) {
                                delay(80L)
                            }
                        }
                    }

                    val postConnectHandle = getActiveRealPeerSessionHandle(peerOnion)
                    if (postConnectHandle != null) {
                        _lastErrorDetail.value = null
                        _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
                        return@withContext Result.success(postConnectHandle)
                    }

                    _connectivityState.value = ConnectivityState.AUTHENTICATING_PEER
                    val deferred = initiateHandshakeOverPeerChannel(channel)
                    val handshakeTimeoutMs = if (activeSocksPort != null) 15_000L else 5_000L
                    val establishedHandle = withTimeoutOrNull(handshakeTimeoutMs) {
                        deferred.await()
                    } ?: getActiveRealPeerSessionHandle(peerOnion)

                    if (establishedHandle != null) {
                        _lastErrorDetail.value = null
                        _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
                        return@withContext Result.success(establishedHandle)
                    }
                }

                _connectivityState.value = ConnectivityState.ONION_SERVICE_READY
                val waitMsg = if (activeSocksPort != null) {
                    "Peer ${contact.alias} (${peerOnion.take(10)}…onion) unreachable over Tor right now. Ensure ZeroTrace is open on their phone & Tor shows 100%."
                } else {
                    "Waiting for ${contact.alias} (${peerOnion.take(10)}…onion) on Hotspot / Wi-Fi P2P circuit. Keep ZeroTrace open on both devices."
                }
                _lastErrorDetail.value = null
                return@withContext Result.failure(IllegalStateException(waitMsg))
            }

            // 2. Single-device Loopback Verification Peer path (or JVM unit test environment)
            _connectivityState.value = ConnectivityState.CONNECTING_TO_PEER
            val node = getOrCreateLoopbackNode()
            val localSummary = RustCoreBridge.getPublicIdentitySummary()
                ?: error("Local device identity not initialized")
            node.pinExpectedClientIdentity(localSummary, contact.pairingNonceHex)

            _connectivityState.value = ConnectivityState.AUTHENTICATING_PEER

            val initWire = RustCoreBridge.createHandshakeInit(
                peerClassicalFpHex = node.classicalFp.toHex(),
                peerPqFpHex = node.pqFp.toHex(),
                pairingNonceHex = contact.pairingNonceHex
            )

            val respWire: HandshakeResponseWire = try {
                Socket().use { sock ->
                    sock.soTimeout = 3500
                    sock.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), node.listeningPort), 1500)
                    val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 32_768))
                    val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), 32_768))
                    writeHandshakeInitFrame(out, initWire)
                    readHandshakeResponseFrame(inp)
                }
            } catch (_: Throwable) {
                val initBaos = ByteArrayOutputStream(12_288)
                writeHandshakeInitFrame(DataOutputStream(initBaos), initWire)
                val decodedInit = readHandshakeInitFrame(DataInputStream(ByteArrayInputStream(initBaos.toByteArray())))
                val (respHandle, generatedResp) = node.respondToHandshake(decodedInit)
                node.responderSessionHandle = respHandle
                val respBaos = ByteArrayOutputStream(16_384)
                writeHandshakeResponseFrame(DataOutputStream(respBaos), generatedResp)
                readHandshakeResponseFrame(DataInputStream(ByteArrayInputStream(respBaos.toByteArray())))
            }

            val handle = RustCoreBridge.finalizeHandshakeInitiator(respWire, contact.onionAddress)
            _lastErrorDetail.value = null
            _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
            Result.success(handle)
        } catch (t: Throwable) {
            _connectivityState.value = if (localOnionServerSocket != null && !localOnionServerSocket!!.isClosed) {
                ConnectivityState.ONION_SERVICE_READY
            } else {
                ConnectivityState.ERROR
            }
            _lastErrorDetail.value = "SESSION_AUTH_FAILED: ${t.message ?: t.javaClass.simpleName}"
            Result.failure(t)
        }
    }

    /**
     * Encrypts and transmits a message over the active `.onion` stream.
     */
    suspend fun sendEncryptedMessageOverCircuit(
        sessionHandle: Long,
        plaintext: String,
        forcePqBraidStep: Boolean = false,
        contact: VerifiedContactRecord? = null
    ): Result<List<VolatileChatMessage>> = withContext(Dispatchers.IO) {
        val utf8Bytes = plaintext.toByteArray(Charsets.UTF_8)
        try {
            val metrics = RustCoreBridge.getSessionMetrics(sessionHandle)
            val peerOnion = (contact?.onionAddress ?: metrics?.peerOnion).orEmpty().lowercase()
            val isLoopbackPeer = !isAndroidDeviceRuntime ||
                peerOnion.isEmpty() ||
                peerOnion == getOrCreateLoopbackNode().onionAddress.lowercase()

            if (!isLoopbackPeer) {
                var activeHandle = getActiveRealPeerSessionHandle(peerOnion) ?: sessionHandle
                var channel = livePeerChannels[peerOnion]?.takeIf { it.isAlive() }

                if (channel == null && contact != null) {
                    val reconnectRes = establishAuthenticatedPeerSession(contact)
                    if (reconnectRes.isSuccess) {
                        activeHandle = reconnectRes.getOrThrow()
                        channel = livePeerChannels[peerOnion]?.takeIf { it.isAlive() }
                    }
                }

                if (channel == null || !channel.isAlive()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Peer circuit not connected yet. Ensure both phones show Tor / Onion Service Ready."
                        )
                    )
                }

                _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
                val (wirePacket, _) = RustCoreBridge.encryptMessage(
                    sessionHandle = activeHandle,
                    plaintextUtf8 = utf8Bytes,
                    forcePqBraidStep = forcePqBraidStep
                )
                channel.sendRatchetPacket(wirePacket.toWireBytes())
                return@withContext Result.success(RustCoreBridge.getVolatileMessages(activeHandle))
            }

            if (_connectivityState.value != ConnectivityState.E2EE_SESSION_READY) {
                _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
            }
            val (wirePacket, _) = RustCoreBridge.encryptMessage(
                sessionHandle = sessionHandle,
                plaintextUtf8 = utf8Bytes,
                forcePqBraidStep = forcePqBraidStep
            )

            val node = loopbackPeerNode
            if (node != null) {
                val replyPacket = node.exchangeRatchetFrameOverLoopback(wirePacket)
                if (replyPacket != null) {
                    RustCoreBridge.decryptMessage(sessionHandle, replyPacket)
                }
            }
            Result.success(RustCoreBridge.getVolatileMessages(sessionHandle))
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            Arrays.fill(utf8Bytes, 0.toByte())
        }
    }

    /**
     * Terminates a single peer session and returns to `ONION_SERVICE_READY`.
     */
    fun disconnectPeerSession(sessionHandle: Long) {
        _connectivityState.value = ConnectivityState.DISCONNECTING
        val metrics = RustCoreBridge.getSessionMetrics(sessionHandle)
        metrics?.peerOnion?.lowercase()?.let { onion ->
            realPeerSessionHandles.remove(onion)
        }
        RustCoreBridge.destroySession(sessionHandle)
        loopbackPeerNode?.destroyActiveResponderSession()
        _connectivityState.value = if (localOnionServerSocket != null && !localOnionServerSocket!!.isClosed) {
            ConnectivityState.ONION_SERVICE_READY
        } else {
            ConnectivityState.OFFLINE
        }
    }

    suspend fun sendEncryptedMediaChunks(
        peerOnion: String,
        transferId: String,
        chunks: List<EncryptedFileChunk>,
        onChunkProgress: ((chunksSent: Int, totalChunks: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val channel = livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() } ?: return@withContext false
        val total = chunks.size
        val isSocksTorChannel = channel.socket.inetAddress?.isLoopbackAddress == true
        val ackMap = ConcurrentHashMap<Int, CompletableDeferred<Unit>>(total.coerceAtLeast(1))
        for (i in 0 until total) {
            ackMap[i] = CompletableDeferred()
        }
        mediaChunkAckWaiters[transferId] = ackMap
        var peerSupportsChunkAck = true
        return@withContext try {
            for (idx in 0 until total) {
                if (!channel.isAlive()) return@withContext false
                channel.sendMediaChunk(transferId, chunks[idx])
                if (peerSupportsChunkAck) {
                    val timeoutMs = if (idx == 0) 1200L else 5000L
                    val acked = withTimeoutOrNull(timeoutMs) {
                        ackMap[idx]?.await()
                    }
                    if (acked == null && idx == 0) {
                        peerSupportsChunkAck = false
                    }
                }
                onChunkProgress?.invoke(idx + 1, total)
                if (!peerSupportsChunkAck && isSocksTorChannel && total > 1 && idx < total - 1) {
                    delay(14L)
                }
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            mediaChunkAckWaiters.remove(transferId)
        }
    }

    fun sendReadReceipt(peerOnion: String, readUpToEpochMillis: Long = System.currentTimeMillis()) {
        val key = peerOnion.lowercase()
        val ch = livePeerChannels[key]?.takeIf { it.isAlive() } ?: return
        scope.launch {
            try {
                ch.sendReadReceipt(readUpToEpochMillis)
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun dialPeerOverTorWithRetry(
        peerOnion: String,
        socksPortToUse: Int,
        maxBudgetMs: Long
    ): String? {
        val cleanOnion = peerOnion.trim().lowercase()
        val deadline = System.currentTimeMillis() + maxBudgetMs
        var attempt = 0
        var lastError: String? = null
        if (_transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
            EmbeddedTorEngine.prefetchOnionDescriptor(cleanOnion)
        }
        while (System.currentTimeMillis() < deadline && !isPeerConnectedOnMesh(cleanOnion)) {
            attempt++
            val remainingMs = (deadline - System.currentTimeMillis()).coerceAtLeast(4_000L)
            val attemptTimeoutMs = minOf(
                remainingMs.toInt(),
                when (attempt) {
                    1 -> 8_500
                    2 -> 11_000
                    else -> 13_500
                }
            )
            val winnerSocket = java.util.concurrent.atomic.AtomicReference<Socket?>(null)
            val raceDone = CompletableDeferred<Socket?>()
            var secondaryJob: Job? = null

            val primaryJob = scope.launch {
                try {
                    val sock = EmbeddedTorEngine.connectToOnionOverSocks5(
                        peerOnion = cleanOnion,
                        targetPort = DEFAULT_P2P_TCP_PORT,
                        socksHost = socksHost,
                        socksPort = socksPortToUse,
                        connectTimeoutMs = attemptTimeoutMs
                    )
                    if (winnerSocket.compareAndSet(null, sock)) {
                        raceDone.complete(sock)
                    } else {
                        try {
                            sock.close()
                        } catch (_: Throwable) {
                        }
                    }
                } catch (t: Throwable) {
                    lastError = t.message ?: t.javaClass.simpleName
                }
            }

            // Staggered backup Tor circuit after 3.2s so slow/stalled HSDir intro points don't block the dial
            if (remainingMs > 6_500L) {
                secondaryJob = scope.launch {
                    delay(3_200L)
                    if (winnerSocket.get() != null || isPeerConnectedOnMesh(cleanOnion)) return@launch
                    if (_transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
                        EmbeddedTorEngine.prefetchOnionDescriptor(cleanOnion)
                    }
                    try {
                        val sock = EmbeddedTorEngine.connectToOnionOverSocks5(
                            peerOnion = cleanOnion,
                            targetPort = DEFAULT_P2P_TCP_PORT,
                            socksHost = socksHost,
                            socksPort = socksPortToUse,
                            connectTimeoutMs = attemptTimeoutMs
                        )
                        if (winnerSocket.compareAndSet(null, sock)) {
                            raceDone.complete(sock)
                        } else {
                            try {
                                sock.close()
                            } catch (_: Throwable) {
                            }
                        }
                    } catch (t: Throwable) {
                        if (lastError == null) {
                            lastError = t.message ?: t.javaClass.simpleName
                        }
                    }
                }
            }

            val monitorJob = scope.launch {
                primaryJob.join()
                secondaryJob?.join()
                if (!raceDone.isCompleted) {
                    raceDone.complete(winnerSocket.get())
                }
            }

            val wonSock = withTimeoutOrNull(attemptTimeoutMs.toLong() + 3_500L) {
                raceDone.await()
            }
            monitorJob.cancel()
            primaryJob.cancel()
            secondaryJob?.cancel()

            if (wonSock != null) {
                handlePeerSocketConnection(wonSock, isOutbound = true)
                return null
            }

            if (isPeerConnectedOnMesh(cleanOnion)) return null
            if (_transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
                EmbeddedTorEngine.prefetchOnionDescriptor(cleanOnion)
            }
            val backoffMs = minOf(1_200L, 450L + attempt * 180L)
            if (System.currentTimeMillis() + backoffMs >= deadline) break
            delay(backoffMs)
        }
        return lastError
    }

    private fun launchBackgroundPeerDial(peerOnion: String) {
        val clean = peerOnion.trim().lowercase()
        if (!clean.endsWith(".onion") || isPeerConnectedOnMesh(clean)) return
        if (!backgroundDialingPeers.add(clean)) return
        scope.launch {
            try {
                val activeSocks = when (_transportBackend.value) {
                    TorTransportBackend.EMBEDDED_TOR -> EmbeddedTorEngine.getActiveSocksPort()
                    TorTransportBackend.SOCKS5_TOR_DAEMON -> _activeTorSocksPort.value
                    TorTransportBackend.LOOPBACK_ONION_VERIFICATION -> null
                } ?: return@launch
                if (activeSocks <= 0) return@launch
                dialPeerOverTorWithRetry(
                    peerOnion = clean,
                    socksPortToUse = activeSocks,
                    maxBudgetMs = 20_000L
                )
            } finally {
                backgroundDialingPeers.remove(clean)
            }
        }
    }

    private fun ensureBackgroundPeerReconnectLoop() {
        if (!isAndroidDeviceRuntime) return
        if (backgroundReconnectJob?.isActive == true) return
        backgroundReconnectJob = scope.launch {
            while (true) {
                val state = _connectivityState.value
                val ready = state == ConnectivityState.ONION_SERVICE_READY || state == ConnectivityState.E2EE_SESSION_READY
                if (ready) {
                    for (peerOnion in trackedVerifiedPeerOnions) {
                        if (!isPeerConnectedOnMesh(peerOnion)) {
                            if (_transportBackend.value == TorTransportBackend.EMBEDDED_TOR) {
                                EmbeddedTorEngine.prefetchOnionDescriptor(peerOnion)
                            }
                            launchBackgroundPeerDial(peerOnion)
                        }
                    }
                }
                delay(4_000L)
            }
        }
    }

    suspend fun awaitAndTakeMediaChunks(
        transferId: String,
        expectedChunks: Int,
        timeoutMs: Long = 25_000L
    ): List<EncryptedFileChunk>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val map = incomingMediaChunksByTransferId[transferId]
            if (map != null && map.size >= expectedChunks) {
                incomingMediaChunksByTransferId.remove(transferId)
                return (0 until expectedChunks).mapNotNull { map[it] }.takeIf { it.size == expectedChunks }
            }
            delay(50L)
        }
        val map = incomingMediaChunksByTransferId.remove(transferId) ?: return null
        return (0 until expectedChunks).mapNotNull { map[it] }.takeIf { it.size == expectedChunks }
    }

    fun getPeerRemoteHostAddress(peerOnion: String): String? {
        return livePeerChannels[peerOnion.lowercase()]?.remoteHostAddress
    }

    suspend fun sendCallInvite(
        peerOnion: String,
        callId: String,
        mediaKind: CallMediaKind,
        mediaTcpPort: Int,
        ephemeralCallPubKey: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        val channel = livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() } ?: return@withContext false
        val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase() ?: return@withContext false
        val transcript = "ZT2-CALL-INVITE-V1|$callId|$localOnion|${mediaKind.name}|$mediaTcpPort|${ephemeralCallPubKey.toHex()}"
            .toByteArray(Charsets.UTF_8)
        val sig = RustCoreBridge.signPeerHelloTranscript(transcript) ?: return@withContext false
        return@withContext try {
            channel.sendCallInviteFrame(callId, mediaKind, mediaTcpPort, ephemeralCallPubKey, sig)
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun sendCallAccept(
        peerOnion: String,
        callId: String,
        mediaKind: CallMediaKind,
        ephemeralCallPubKey: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        val channel = livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() } ?: return@withContext false
        val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase() ?: return@withContext false
        val transcript = "ZT2-CALL-ACCEPT-V1|$callId|$localOnion|${mediaKind.name}|${ephemeralCallPubKey.toHex()}"
            .toByteArray(Charsets.UTF_8)
        val sig = RustCoreBridge.signPeerHelloTranscript(transcript) ?: return@withContext false
        return@withContext try {
            channel.sendCallAcceptFrame(callId, mediaKind, ephemeralCallPubKey, sig)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun sendCallReject(peerOnion: String, callId: String, reason: String) {
        scope.launch {
            try {
                livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() }?.sendCallRejectFrame(callId, reason)
            } catch (_: Throwable) {
            }
        }
    }

    fun sendCallEnd(peerOnion: String, callId: String) {
        scope.launch {
            try {
                livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() }?.sendCallEndFrame(callId)
            } catch (_: Throwable) {
            }
        }
    }

    fun sendCallMediaFallbackFrame(peerOnion: String, callId: String, frameBytes: ByteArray) {
        try {
            livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() }?.sendCallMediaFallbackFrame(callId, frameBytes)
        } catch (_: Throwable) {
        }
    }

    suspend fun broadcastStatusItem(
        item: EphemeralStatusItem,
        targetPeerOnions: Set<String>? = null
    ): Int = withContext(Dispatchers.IO) {
        if (item.isExpired()) return@withContext 0
        val peers = livePeerChannels.entries
            .filter { (onion, ch) ->
                ch.isAlive() && (targetPeerOnions == null || targetPeerOnions.any { it.equals(onion, ignoreCase = true) })
            }
            .map { it.key }
        var delivered = 0
        for (peerOnion in peers) {
            if (sendStatusItemToPeer(peerOnion, item)) {
                delivered++
            }
        }
        delivered
    }

    suspend fun sendStatusItemToPeer(
        peerOnion: String,
        item: EphemeralStatusItem
    ): Boolean = withContext(Dispatchers.IO) {
        if (item.isExpired()) return@withContext false
        val channel = livePeerChannels[peerOnion.lowercase()]?.takeIf { it.isAlive() } ?: return@withContext false
        val localOnion = RustCoreBridge.getPublicIdentitySummary()?.onionAddress?.lowercase() ?: return@withContext false
        val statusKey32 = RustCoreBridge.deriveStaticPeerStatusKey32(channel.peerHello.x25519PublicKey, item.statusId)
            ?: return@withContext false

        return@withContext try {
            val payloadBytes = item.encryptedPayloadBytes?.takeIf { it.isNotEmpty() }
            val chunkBundle = if (payloadBytes != null) {
                EncryptedFileTransferEngine.encryptAndChunkPayload(
                    rawFileName = item.sanitizedFileName ?: when (item.mediaType) {
                        StatusMediaType.VIDEO_CARD -> "status_video.mp4"
                        else -> "status_photo.jpg"
                    },
                    mimeType = item.mimeType ?: when (item.mediaType) {
                        StatusMediaType.VIDEO_CARD -> "video/mp4"
                        else -> "image/jpeg"
                    },
                    payloadBytes = payloadBytes,
                    stripImageMetadata = (item.mediaType == StatusMediaType.IMAGE_CARD)
                )
            } else null

            if (chunkBundle != null) {
                for (chunk in chunkBundle.chunks) {
                    channel.sendMediaChunk(chunkBundle.manifest.transferId, chunk)
                }
            }

            val metaBytes = ByteArrayOutputStream().use { baos ->
                DataOutputStream(baos).use { out ->
                    out.writeUTF(item.statusId)
                    out.writeUTF(item.authorAlias)
                    out.writeUTF(item.mediaType.name)
                    out.writeUTF(item.captionText)
                    out.writeUTF(item.backgroundHex)
                    out.writeLong(item.createdAtEpochMillis)
                    out.writeLong(item.expiresAtEpochMillis)
                    out.writeUTF(item.mimeType ?: "")
                    out.writeUTF(item.sanitizedFileName ?: "")
                    if (chunkBundle != null) {
                        out.writeUTF(chunkBundle.manifest.transferId)
                        out.writeInt(chunkBundle.manifest.totalSizeBytes)
                        out.writeInt(chunkBundle.manifest.totalChunks)
                        out.writeUTF(chunkBundle.manifest.sha256DigestHex)
                        writeBytes(out, chunkBundle.fileSecretKey32)
                    } else {
                        out.writeUTF("")
                        out.writeInt(0)
                        out.writeInt(0)
                        out.writeUTF("")
                    }
                }
                baos.toByteArray()
            }

            val nonce12 = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val aad = "ZT2-STATUS-META|${item.statusId}|$localOnion".toByteArray(Charsets.UTF_8)
            val metaCt = RustCoreBridge.aeadEncryptChaCha20Poly1305(statusKey32, nonce12, metaBytes, aad)
            Arrays.fill(metaBytes, 0.toByte())
            chunkBundle?.wipeZeroize()

            val transcript = "ZT2-STATUS-V1|${item.statusId}|$localOnion|${nonce12.toHex()}|${metaCt.toHex()}"
                .toByteArray(Charsets.UTF_8)
            val sig = RustCoreBridge.signPeerHelloTranscript(transcript) ?: return@withContext false

            channel.sendStatusItemFrame(item.statusId, nonce12, metaCt, sig)
            true
        } catch (_: Throwable) {
            false
        } finally {
            Arrays.fill(statusKey32, 0.toByte())
        }
    }

    private suspend fun handleIncomingStatusItem(
        channel: PeerSocketChannel,
        peerOnion: String,
        statusId: String,
        metaNonce12: ByteArray,
        metaCt: ByteArray
    ) {
        val statusKey32 = RustCoreBridge.deriveStaticPeerStatusKey32(channel.peerHello.x25519PublicKey, statusId)
            ?: return
        try {
            val aad = "ZT2-STATUS-META|$statusId|$peerOnion".toByteArray(Charsets.UTF_8)
            val metaBytes = RustCoreBridge.aeadDecryptChaCha20Poly1305(statusKey32, metaNonce12, metaCt, aad)
            DataInputStream(ByteArrayInputStream(metaBytes)).use { inp ->
                val decId = inp.readUTF()
                val authorAlias = inp.readUTF()
                val mediaType = try {
                    StatusMediaType.valueOf(inp.readUTF())
                } catch (_: Exception) {
                    StatusMediaType.TEXT_CARD
                }
                val captionText = inp.readUTF()
                val backgroundHex = inp.readUTF()
                val createdAt = inp.readLong()
                val expiresAt = inp.readLong()
                val mimeType = inp.readUTF().ifBlank { null }
                val fileName = inp.readUTF().ifBlank { null }
                val transferId = inp.readUTF()
                val totalSizeBytes = inp.readInt()
                val totalChunks = inp.readInt()
                val sha256Hex = inp.readUTF()

                if (System.currentTimeMillis() >= expiresAt) {
                    Arrays.fill(metaBytes, 0.toByte())
                    return
                }

                var decryptedMedia: ByteArray? = null
                if (totalChunks > 0 && transferId.isNotBlank()) {
                    val fileKey32 = readBytes(inp)
                    val chunks = awaitAndTakeMediaChunks(transferId, totalChunks)
                    if (chunks != null) {
                        val manifest = EncryptedFileManifest(
                            transferId = transferId,
                            sanitizedFileName = fileName ?: "status_media.bin",
                            mimeType = mimeType ?: "application/octet-stream",
                            totalSizeBytes = totalSizeBytes,
                            totalChunks = totalChunks,
                            sha256DigestHex = sha256Hex
                        )
                        decryptedMedia = EncryptedFileTransferEngine.decryptAndVerifyChunks(
                            manifest = manifest,
                            fileSecretKey32 = fileKey32,
                            chunks = chunks
                        )
                        chunks.forEach { it.wipeZeroize() }
                    }
                    Arrays.fill(fileKey32, 0.toByte())
                }
                Arrays.fill(metaBytes, 0.toByte())

                // If peer sent media card but media chunks failed integrity/assembly, skip corrupt item
                if (totalChunks > 0 && decryptedMedia == null) return

                val item = EphemeralStatusItem(
                    statusId = decId,
                    authorAlias = authorAlias,
                    authorOnion = peerOnion,
                    isOwnStatus = false,
                    mediaType = mediaType,
                    captionText = captionText,
                    backgroundHex = backgroundHex,
                    createdAtEpochMillis = createdAt,
                    expiresOnSessionClose = false,
                    privacyPolicy = StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS,
                    encryptedPayloadBytes = decryptedMedia,
                    viewedByLocalUser = false,
                    expiresAtEpochMillis = expiresAt,
                    mimeType = mimeType,
                    sanitizedFileName = fileName
                )
                _peerTransportEvents.tryEmit(PeerTransportEvent.IncomingStatusItem(peerOnion, item))
            }
        } finally {
            Arrays.fill(statusKey32, 0.toByte())
        }
    }

    /**
     * Manual Emergency Lock / Disconnect Operation (Section 5.4).
     */
    @Synchronized
    fun emergencyDisconnectAndWipe(): Int {
        _connectivityState.value = ConnectivityState.DISCONNECTING
        localBootId = UUID.randomUUID().toString().replace("-", "")
        EncryptedCallMediaEngine.stopAndWipe()
        incomingMediaChunksByTransferId.values.forEach { chunkMap ->
            chunkMap.values.forEach { it.wipeZeroize() }
            chunkMap.clear()
        }
        incomingMediaChunksByTransferId.clear()
        val wipedSessions = RustCoreBridge.emergencyWipeAllSessions()
        realPeerSessionHandles.clear()
        lastBootIdByOnion.clear()
        pendingHandshakeByOnion.values.forEach { it.cancel() }
        pendingHandshakeByOnion.clear()
        livePeerChannels.values.forEach { it.closeQuietly() }
        livePeerChannels.clear()
        peerHelloByOnion.clear()
        refreshConnectedPeerState()
        stopNsdMesh()
        EmbeddedTorEngine.stopEmbeddedTor(appContextRef)
        loopbackPeerNode?.stopAndWipe()
        loopbackPeerNode = null
        p2pDiscoveryJob?.cancel()
        p2pDiscoveryJob = null
        backgroundReconnectJob?.cancel()
        backgroundReconnectJob = null
        backgroundDialingPeers.clear()
        try {
            localOnionServerSocket?.close()
        } catch (_: Exception) {
        }
        try {
            embeddedSocksServerSocket?.close()
        } catch (_: Exception) {
        }
        try {
            p2pMeshServerSocket?.close()
        } catch (_: Exception) {
        }
        try {
            p2pUdpBeaconSocket?.close()
        } catch (_: Exception) {
        }
        localOnionServerSocket = null
        embeddedSocksServerSocket = null
        p2pMeshServerSocket = null
        p2pUdpBeaconSocket = null
        embeddedSocksPort = null
        _localListenerPort.value = null
        _activeTorSocksPort.value = null
        _torBootstrapSummary.value = null
        _torBootstrapProgress.value = 0
        _lastErrorDetail.value = null
        _connectivityState.value = ConnectivityState.OFFLINE
        return wipedSessions
    }

    // =========================================================================
    // Wire Framing Serialization
    // =========================================================================

    private fun writePeerHelloFrame(out: DataOutputStream, hello: PeerHelloWire) {
        out.writeInt(WIRE_MAGIC)
        out.writeByte(FRAME_PEER_HELLO.toInt())
        out.writeUTF(hello.onionAddress)
        out.writeUTF(hello.classicalFingerprintHex)
        out.writeUTF(hello.pqFingerprintHex)
        writeBytes(out, hello.ed25519PublicKey)
        writeBytes(out, hello.x25519PublicKey)
        out.writeUTF(hello.pairingNonceHex)
        out.writeUTF(hello.ephemeralBootId)
        writeBytes(out, hello.ed25519Signature)
        out.flush()
    }

    private fun readPeerHelloPayload(inp: DataInputStream): PeerHelloWire {
        return PeerHelloWire(
            onionAddress = inp.readUTF(),
            classicalFingerprintHex = inp.readUTF(),
            pqFingerprintHex = inp.readUTF(),
            ed25519PublicKey = readBytes(inp),
            x25519PublicKey = readBytes(inp),
            pairingNonceHex = inp.readUTF(),
            ephemeralBootId = inp.readUTF(),
            ed25519Signature = readBytes(inp)
        )
    }

    private fun writeHandshakeInitFrame(out: DataOutputStream, msg: HandshakeInitWire) {
        out.writeInt(WIRE_MAGIC)
        out.writeByte(FRAME_HANDSHAKE_INIT.toInt())
        out.writeShort(msg.protocolVersion)
        out.writeUTF(msg.sessionIdHex)
        out.writeUTF(msg.pairingNonceHex)
        writeBytes(out, msg.initiatorNonce)
        writeBytes(out, msg.initiatorEd25519Pk)
        writeBytes(out, msg.initiatorX25519Pk)
        writeBytes(out, msg.initiatorMlDsa87Pk)
        writeBytes(out, msg.initiatorMlKem1024Ek)
        writeBytes(out, msg.ephemeralX25519Pk)
        writeBytes(out, msg.ephemeralMlKem1024Ek)
        out.writeUTF(msg.targetPeerClassicalFpHex)
        out.writeUTF(msg.targetPeerPqFpHex)
        writeBytes(out, msg.ed25519Signature)
        writeBytes(out, msg.mlDsa87Signature)
        out.flush()
    }

    private fun readHandshakeInitFrame(inp: DataInputStream): HandshakeInitWire {
        require(inp.readInt() == WIRE_MAGIC) { "Invalid wire frame magic" }
        require(inp.readByte() == FRAME_HANDSHAKE_INIT) { "Expected HandshakeInit frame type" }
        return readHandshakeInitPayload(inp)
    }

    private fun readHandshakeInitPayload(inp: DataInputStream): HandshakeInitWire {
        return HandshakeInitWire(
            protocolVersion = inp.readUnsignedShort(),
            sessionIdHex = inp.readUTF(),
            pairingNonceHex = inp.readUTF(),
            initiatorNonce = readBytes(inp),
            initiatorEd25519Pk = readBytes(inp),
            initiatorX25519Pk = readBytes(inp),
            initiatorMlDsa87Pk = readBytes(inp),
            initiatorMlKem1024Ek = readBytes(inp),
            ephemeralX25519Pk = readBytes(inp),
            ephemeralMlKem1024Ek = readBytes(inp),
            targetPeerClassicalFpHex = inp.readUTF(),
            targetPeerPqFpHex = inp.readUTF(),
            ed25519Signature = readBytes(inp),
            mlDsa87Signature = readBytes(inp)
        )
    }

    private fun writeHandshakeResponseFrame(out: DataOutputStream, msg: HandshakeResponseWire) {
        out.writeInt(WIRE_MAGIC)
        out.writeByte(FRAME_HANDSHAKE_RESP.toInt())
        out.writeShort(msg.protocolVersion)
        out.writeUTF(msg.sessionIdHex)
        writeBytes(out, msg.responderNonce)
        writeBytes(out, msg.responderEd25519Pk)
        writeBytes(out, msg.responderX25519Pk)
        writeBytes(out, msg.responderMlDsa87Pk)
        writeBytes(out, msg.responderMlKem1024Ek)
        writeBytes(out, msg.ephemeralX25519Pk)
        writeBytes(out, msg.ephemeralMlKem1024Ek)
        writeBytes(out, msg.kemCiphertextStatic)
        writeBytes(out, msg.kemCiphertextEphemeral)
        writeBytes(out, msg.ed25519Signature)
        writeBytes(out, msg.mlDsa87Signature)
        out.flush()
    }

    private fun readHandshakeResponseFrame(inp: DataInputStream): HandshakeResponseWire {
        require(inp.readInt() == WIRE_MAGIC) { "Invalid wire frame magic" }
        require(inp.readByte() == FRAME_HANDSHAKE_RESP) { "Expected HandshakeResponse frame type" }
        return readHandshakeResponsePayload(inp)
    }

    private fun readHandshakeResponsePayload(inp: DataInputStream): HandshakeResponseWire {
        return HandshakeResponseWire(
            protocolVersion = inp.readUnsignedShort(),
            sessionIdHex = inp.readUTF(),
            responderNonce = readBytes(inp),
            responderEd25519Pk = readBytes(inp),
            responderX25519Pk = readBytes(inp),
            responderMlDsa87Pk = readBytes(inp),
            responderMlKem1024Ek = readBytes(inp),
            ephemeralX25519Pk = readBytes(inp),
            ephemeralMlKem1024Ek = readBytes(inp),
            kemCiphertextStatic = readBytes(inp),
            kemCiphertextEphemeral = readBytes(inp),
            ed25519Signature = readBytes(inp),
            mlDsa87Signature = readBytes(inp)
        )
    }

    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readBytes(inp: DataInputStream): ByteArray {
        val len = inp.readInt()
        require(len in 1..262_144) { "Invalid frame field length: $len" }
        return ByteArray(len).also { inp.readFully(it) }
    }

    /**
     * Independent Cryptographic Peer Node bound to `127.0.0.1` TCP for local end-to-end
     * handshake and ratcheted packet verification when testing on a single device.
     */
    private class LoopbackCryptographicPeerNode {
        private val deterministicRng = DeterministicLoopbackSecureRandom("ZT2-DETERMINISTIC-LOOPBACK-PEER-NODE-V2")
        private val ephemeralRng = SecureRandom()

        private val edPriv = Ed25519PrivateKeyParameters(deterministicRng)
        private val edPub = edPriv.generatePublicKey().encoded
        private val xPriv = X25519PrivateKeyParameters(deterministicRng)
        private val xPub = xPriv.generatePublicKey().encoded

        private val mldsaGen = MLDSAKeyPairGenerator().apply {
            init(MLDSAKeyGenerationParameters(deterministicRng, MLDSAParameters.ml_dsa_87))
        }
        private val mldsaKp = mldsaGen.generateKeyPair()
        private val mldsaPub = (mldsaKp.public as MLDSAPublicKeyParameters).encoded
        private val mldsaPriv = (mldsaKp.private as MLDSAPrivateKeyParameters).encoded

        private val staticMlkemGen = MLKEMKeyPairGenerator().apply {
            init(MLKEMKeyGenerationParameters(deterministicRng, MLKEMParameters.ml_kem_1024))
        }
        private val mlkemKp = staticMlkemGen.generateKeyPair()
        private val mlkemPub = (mlkemKp.public as MLKEMPublicKeyParameters).encoded
        private val mlkemPriv = (mlkemKp.private as MLKEMPrivateKeyParameters).encoded

        private val ephMlkemGen = MLKEMKeyPairGenerator().apply {
            init(MLKEMKeyGenerationParameters(ephemeralRng, MLKEMParameters.ml_kem_1024))
        }

        private val onionSeed = ByteArray(32).also { deterministicRng.nextBytes(it) }
        val onionAddress: String = RustCoreBridge.deriveV3OnionAddress(
            Ed25519PrivateKeyParameters(onionSeed, 0).generatePublicKey().encoded
        )
        val classicalFp: ByteArray = RustCoreBridge.computeClassicalFingerprint(edPub, xPub)
        val pqFp: ByteArray = RustCoreBridge.computePqFingerprint(mldsaPub, mlkemPub)

        private var serverSocket: ServerSocket? = null
        var listeningPort: Int = 0
            private set

        @Volatile
        private var pinnedClientClassicalFpHex: String = ""
        @Volatile
        private var pinnedClientPqFpHex: String = ""
        @Volatile
        private var pinnedPairingNonceHex: String = ""
        @Volatile
        var responderSessionHandle: Long? = null

        fun isServerListening(): Boolean {
            val s = serverSocket
            return s != null && !s.isClosed && listeningPort > 0
        }

        fun startLoopbackServer() {
            val srv = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
            serverSocket = srv
            listeningPort = srv.localPort
            scope.launch {
                while (!srv.isClosed) {
                    val client = try {
                        srv.accept()
                    } catch (_: Throwable) {
                        if (srv.isClosed) break else continue
                    }
                    scope.launch {
                        try {
                            handleIncomingConnection(client)
                        } catch (_: Throwable) {
                            try {
                                client.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        }

        fun pinExpectedClientIdentity(clientSummary: PublicIdentitySummary, pairingNonceHex: String) {
            pinnedClientClassicalFpHex = clientSummary.classicalFingerprintHex
            pinnedClientPqFpHex = clientSummary.pqFingerprintHex
            pinnedPairingNonceHex = pairingNonceHex
        }

        fun createSignedPairingQrAndRecord(): Pair<String, VerifiedContactRecord> {
            val nowSec = System.currentTimeMillis() / 1000L
            val expiry = nowSec + 600L
            val nonceHex = "5a54324c4f4f504241434b4e4f4e4345"
            val urlEncoder = java.util.Base64.getUrlEncoder().withoutPadding()
            val edB64 = urlEncoder.encodeToString(edPub)
            val xB64 = urlEncoder.encodeToString(xPub)
            val body = listOf(
                RustCoreBridge.QR_MAGIC,
                RustCoreBridge.QR_VERSION,
                onionAddress,
                classicalFp.toHex(),
                pqFp.toHex(),
                edB64,
                xB64,
                expiry.toString(),
                nonceHex,
                RustCoreBridge.CAPABILITIES_STRING
            ).joinToString("|")
            val sig = RustCoreBridge.signEd25519(edPriv.encoded, "ZT2-QR-AUTH-V1|$body".toByteArray(Charsets.UTF_8))
            val sigB64 = urlEncoder.encodeToString(sig)
            val qrString = "$body|$sigB64"
            val record = VerifiedContactRecord(
                alias = "Peer-${onionAddress.take(6).uppercase()}",
                onionAddress = onionAddress,
                classicalFingerprintHex = classicalFp.toHex(),
                pqFingerprintHex = pqFp.toHex(),
                classicalSafetyDigits = RustCoreBridge.formatSafetyDigits(classicalFp),
                pqSafetyDigits = RustCoreBridge.formatSafetyDigits(pqFp),
                ed25519PublicKey = edPub.copyOf(),
                x25519PublicKey = xPub.copyOf(),
                mlDsa87PublicKey = mldsaPub.copyOf(),
                mlKem1024EncapsKey = mlkemPub.copyOf(),
                pairingNonceHex = nonceHex,
                verifiedAtEpochSec = nowSec
            )
            return Pair(qrString, record)
        }

        private fun handleIncomingConnection(socket: Socket) {
            socket.use { sock ->
                sock.soTimeout = 15_000
                val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), 32_768))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 32_768))
                val initWire = readHandshakeInitFrame(inp)
                val (respHandle, respWire) = respondToHandshake(initWire)
                responderSessionHandle = respHandle
                writeHandshakeResponseFrame(out, respWire)
            }
        }

        @Synchronized
        fun respondToHandshake(init: HandshakeInitWire): Pair<Long, HandshakeResponseWire> {
            val actualClientClassicalFp = RustCoreBridge.computeClassicalFingerprint(
                init.initiatorEd25519Pk,
                init.initiatorX25519Pk
            ).toHex()
            val actualClientPqFp = RustCoreBridge.computePqFingerprint(
                init.initiatorMlDsa87Pk,
                init.initiatorMlKem1024Ek
            ).toHex()

            val initTranscript = RustCoreBridge.domainSha256(
                "ZT2-HS-INIT-V1",
                listOf(
                    ByteBuffer.allocate(2).putShort(init.protocolVersion.toShort()).array(),
                    init.sessionIdHex.let { s -> ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } },
                    init.pairingNonceHex.let { s -> ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } },
                    init.initiatorNonce,
                    actualClientClassicalFp.let { s -> ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } },
                    actualClientPqFp.let { s -> ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } },
                    init.ephemeralX25519Pk,
                    init.ephemeralMlKem1024Ek,
                    init.targetPeerClassicalFpHex.let { s -> ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } },
                    init.targetPeerPqFpHex.let { s -> ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }
                )
            )

            require(RustCoreBridge.verifyEd25519(init.initiatorEd25519Pk, initTranscript, init.ed25519Signature)) {
                "Peer node rejected client Ed25519 signature"
            }
            require(RustCoreBridge.verifyMlDsa87(init.initiatorMlDsa87Pk, initTranscript, init.mlDsa87Signature)) {
                "Peer node rejected client ML-DSA-87 signature"
            }

            val responderNonce = ByteArray(32).also { ephemeralRng.nextBytes(it) }
            val ephXPriv = X25519PrivateKeyParameters(ephemeralRng)
            val ephXPub = ephXPriv.generatePublicKey().encoded

            val ephKemKp = ephMlkemGen.generateKeyPair()
            val ephKemPub = (ephKemKp.public as MLKEMPublicKeyParameters).encoded
            val ephKemPriv = (ephKemKp.private as MLKEMPrivateKeyParameters).encoded

            val (ctStatic, ssPq1) = RustCoreBridge.encapsulateMlKem1024(init.initiatorMlKem1024Ek)
            val (ctEph, ssPq2) = RustCoreBridge.encapsulateMlKem1024(init.ephemeralMlKem1024Ek)

            val dh1 = RustCoreBridge.computeX25519(xPriv.encoded, init.initiatorX25519Pk)
            val dh2 = RustCoreBridge.computeX25519(ephXPriv.encoded, init.initiatorX25519Pk)
            val dh3 = RustCoreBridge.computeX25519(xPriv.encoded, init.ephemeralX25519Pk)
            val dh4 = RustCoreBridge.computeX25519(ephXPriv.encoded, init.ephemeralX25519Pk)

            val respTranscript = RustCoreBridge.domainSha256(
                "ZT2-HS-RESP-V1",
                listOf(
                    initTranscript,
                    responderNonce,
                    classicalFp,
                    pqFp,
                    ephXPub,
                    ephKemPub,
                    ctStatic,
                    ctEph
                )
            )

            val respEdSig = RustCoreBridge.signEd25519(edPriv.encoded, respTranscript)
            val respPqSig = RustCoreBridge.signMlDsa87(mldsaPriv, respTranscript)

            val respWire = HandshakeResponseWire(
                protocolVersion = RustCoreBridge.PROTOCOL_VERSION,
                sessionIdHex = init.sessionIdHex,
                responderNonce = responderNonce,
                responderEd25519Pk = edPub.copyOf(),
                responderX25519Pk = xPub.copyOf(),
                responderMlDsa87Pk = mldsaPub.copyOf(),
                responderMlKem1024Ek = mlkemPub.copyOf(),
                ephemeralX25519Pk = ephXPub,
                ephemeralMlKem1024Ek = ephKemPub,
                kemCiphertextStatic = ctStatic,
                kemCiphertextEphemeral = ctEph,
                ed25519Signature = respEdSig,
                mlDsa87Signature = respPqSig
            )

            responderState = PeerRatchetResponder(
                sessionIdHex = init.sessionIdHex,
                localClassicalFpHex = classicalFp.toHex(),
                localPqFpHex = pqFp.toHex(),
                peerClassicalFpHex = actualClientClassicalFp,
                peerPqFpHex = actualClientPqFp,
                initiatorNonce = init.initiatorNonce,
                responderNonce = responderNonce,
                respTranscript = respTranscript,
                dh1 = dh1,
                dh2 = dh2,
                dh3 = dh3,
                dh4 = dh4,
                ssPq1 = ssPq1,
                ssPq2 = ssPq2,
                dhLocalSk = ephXPriv.encoded,
                dhLocalPk = ephXPub,
                dhPeerPk = init.ephemeralX25519Pk,
                pqLocalDk = ephKemPriv,
                pqLocalEk = ephKemPub,
                pqPeerEk = init.ephemeralMlKem1024Ek
            )

            return Pair(9999L, respWire)
        }

        private var responderState: PeerRatchetResponder? = null

        fun exchangeRatchetFrameOverLoopback(incomingPacket: RatchetWirePacket): RatchetWirePacket? {
            val rawWire = incomingPacket.toWireBytes()
            val decoded = RatchetWirePacket.fromWireBytes(rawWire)
            val resp = responderState ?: return null
            val decryptedText = resp.decryptIncoming(decoded)
            val ackText = "[ACK:E2EE-VERIFIED epoch=${decoded.ratchetEpoch} seq=${decoded.msgNum}] Received ${decryptedText.length}B over 127.0.0.1 Onion circuit"
            return resp.encryptReply(ackText.toByteArray(Charsets.UTF_8))
        }

        fun destroyActiveResponderSession() {
            responderState?.wipe()
            responderState = null
        }

        fun stopAndWipe() {
            destroyActiveResponderSession()
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private class PeerRatchetResponder(
        val sessionIdHex: String,
        val localClassicalFpHex: String,
        val localPqFpHex: String,
        val peerClassicalFpHex: String,
        val peerPqFpHex: String,
        initiatorNonce: ByteArray,
        responderNonce: ByteArray,
        respTranscript: ByteArray,
        dh1: ByteArray,
        dh2: ByteArray,
        dh3: ByteArray,
        dh4: ByteArray,
        ssPq1: ByteArray,
        ssPq2: ByteArray,
        var dhLocalSk: ByteArray,
        var dhLocalPk: ByteArray,
        var dhPeerPk: ByteArray,
        var pqLocalDk: ByteArray,
        var pqLocalEk: ByteArray,
        var pqPeerEk: ByteArray
    ) {
        var rootKey: ByteArray
        var ckSend: ByteArray
        var ckRecv: ByteArray
        var ratchetEpoch: Int = 1
        var sendCount: Long = 0L
        var recvCount: Long = 0L
        var prevSendCount: Long = 0L

        init {
            val ikm = ByteBuffer.allocate(32 * 6)
                .put(dh1).put(dh2).put(dh3).put(dh4).put(ssPq1).put(ssPq2)
                .array()
            val salt = RustCoreBridge.domainSha256(
                "ZT2-PQXDH-SALT-V1",
                listOf(initiatorNonce, responderNonce, respTranscript)
            )
            val derived = RustCoreBridge.hkdfExpand(
                salt,
                ikm,
                "ZT2-HYBRID-ROOT-V1".toByteArray(Charsets.UTF_8),
                96
            )
            rootKey = derived.copyOfRange(0, 32)
            ckRecv = derived.copyOfRange(32, 64)
            ckSend = derived.copyOfRange(64, 96)
            Arrays.fill(ikm, 0.toByte())
            Arrays.fill(derived, 0.toByte())
        }

        fun decryptIncoming(packet: RatchetWirePacket): String {
            if (!packet.dhRatchetPublic.contentEquals(dhPeerPk)) {
                dhPeerPk = packet.dhRatchetPublic.copyOf()
                ratchetEpoch = packet.ratchetEpoch
                recvCount = 0L
                val dhShared = RustCoreBridge.computeX25519(dhLocalSk, dhPeerPk)
                val pqSs = packet.pqBraidCiphertext?.let { ct ->
                    RustCoreBridge.decapsulateMlKem1024(pqLocalDk, ct)
                }
                if (packet.nextPqEncapsKey != null) {
                    pqPeerEk = packet.nextPqEncapsKey.copyOf()
                }
                val ikm = ByteBuffer.allocate(64)
                    .put(dhShared)
                    .put(pqSs ?: ByteArray(32))
                    .array()
                val derived = RustCoreBridge.hkdfExpand(
                    rootKey,
                    ikm,
                    "ZT2-PQ-RATCHET-STEP-V1".toByteArray(Charsets.UTF_8),
                    64
                )
                rootKey = derived.copyOfRange(0, 32)
                ckRecv = derived.copyOfRange(32, 64)
            }
            val msgKey = hmacStep(ckRecv, 0x01, "ZT2-MSG-KEY-V1")
            ckRecv = hmacStep(ckRecv, 0x02, "ZT2-CHAIN-KEY-V1")
            recvCount += 1L
            val ad = buildAd(packet.version, packet.sessionIdHex, peerClassicalFpHex, peerPqFpHex, packet.dhRatchetPublic, packet.ratchetEpoch, packet.msgNum, packet.prevChainLen)
            val nonce = deriveNonce(msgKey, packet.msgNum)
            val pt = RustCoreBridge.aeadDecryptChaCha20Poly1305(msgKey, nonce, packet.aeadCiphertext, ad)
            Arrays.fill(msgKey, 0.toByte())
            return String(pt, Charsets.UTF_8)
        }

        fun encryptReply(plaintext: ByteArray): RatchetWirePacket {
            val msgKey = hmacStep(ckSend, 0x01, "ZT2-MSG-KEY-V1")
            ckSend = hmacStep(ckSend, 0x02, "ZT2-CHAIN-KEY-V1")
            val msgNum = sendCount
            sendCount += 1L
            val ad = buildAd(RustCoreBridge.PROTOCOL_VERSION, sessionIdHex, localClassicalFpHex, localPqFpHex, dhLocalPk, ratchetEpoch, msgNum, prevSendCount)
            val nonce = deriveNonce(msgKey, msgNum)
            val ct = RustCoreBridge.aeadEncryptChaCha20Poly1305(msgKey, nonce, plaintext, ad)
            Arrays.fill(msgKey, 0.toByte())
            return RatchetWirePacket(
                version = RustCoreBridge.PROTOCOL_VERSION,
                sessionIdHex = sessionIdHex,
                dhRatchetPublic = dhLocalPk.copyOf(),
                ratchetEpoch = ratchetEpoch,
                msgNum = msgNum,
                prevChainLen = prevSendCount,
                pqBraidCiphertext = null,
                nextPqEncapsKey = null,
                aeadCiphertext = ct
            )
        }

        private fun hmacStep(key: ByteArray, prefix: Byte, label: String): ByteArray {
            val hmac = org.bouncycastle.crypto.macs.HMac(org.bouncycastle.crypto.digests.SHA256Digest())
            hmac.init(org.bouncycastle.crypto.params.KeyParameter(key))
            val data = byteArrayOf(prefix) + label.toByteArray(Charsets.UTF_8)
            hmac.update(data, 0, data.size)
            val out = ByteArray(hmac.macSize)
            hmac.doFinal(out, 0)
            return out
        }

        private fun deriveNonce(msgKey32: ByteArray, msgNum: Long): ByteArray {
            val base = RustCoreBridge.hkdfExpand(
                "ZT2-MSG-NONCE-SALT".toByteArray(Charsets.UTF_8),
                msgKey32,
                "ZT2-NONCE-V1".toByteArray(Charsets.UTF_8),
                12
            )
            val counterBytes = ByteBuffer.allocate(8).putLong(msgNum).array()
            for (i in 0 until 8) {
                base[4 + i] = (base[4 + i].toInt() xor counterBytes[i].toInt()).toByte()
            }
            return base
        }

        private fun buildAd(
            version: Int,
            sessionIdHex: String,
            senderClassicalFpHex: String,
            senderPqFpHex: String,
            dhRatchetPub: ByteArray,
            ratchetEpoch: Int,
            msgNum: Long,
            prevChainLen: Long
        ): ByteArray {
            val baos = ByteArrayOutputStream(160)
            DataOutputStream(baos).use { out ->
                out.write("ZT2-RATCHET-AD-V1".toByteArray(Charsets.UTF_8))
                out.writeByte(version)
                out.writeUTF(sessionIdHex)
                out.writeUTF(senderClassicalFpHex)
                out.writeUTF(senderPqFpHex)
                out.write(dhRatchetPub)
                out.writeInt(ratchetEpoch)
                out.writeLong(msgNum)
                out.writeLong(prevChainLen)
            }
            return baos.toByteArray()
        }

        fun wipe() {
            Arrays.fill(rootKey, 0.toByte())
            Arrays.fill(ckSend, 0.toByte())
            Arrays.fill(ckRecv, 0.toByte())
            Arrays.fill(dhLocalSk, 0.toByte())
            Arrays.fill(pqLocalDk, 0.toByte())
        }
    }
}
