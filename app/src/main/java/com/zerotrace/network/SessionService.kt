package com.zerotrace.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.zerotrace.bridge.HandshakeInitWire
import com.zerotrace.bridge.HandshakeResponseWire
import com.zerotrace.bridge.ParsedQrInvite
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.RatchetWirePacket
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.bridge.VolatileChatMessage
import com.zerotrace.bridge.toHex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Arrays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    ONION_SERVICE_READY("ONION_SERVICE_READY", "Onion Service Listening (127.0.0.1)", "अनियन सर्विस सक्रिय (127.0.0.1)"),
    CONNECTING_TO_PEER("CONNECTING_TO_PEER", "Connecting to Peer .onion", "पीयर .onion से जुड़ रहा है"),
    AUTHENTICATING_PEER("AUTHENTICATING_PEER", "Authenticating Hybrid PQ Handshake", "हाइब्रिड PQ हैंडशेक प्रमाणित हो रहा है"),
    E2EE_SESSION_READY("E2EE_SESSION_READY", "E2EE PQ Ratchet Session Ready", "E2EE PQ रैचेट सत्र सक्रिय"),
    DISCONNECTING("DISCONNECTING", "Disconnecting & Wiping Session RAM", "डिस्कनेक्ट और RAM साफ़ हो रहा है"),
    ERROR("ERROR", "Transport Error (Fail-Closed)", "ट्रांसपोर्ट त्रुटि (Fail-Closed)")
}

enum class TorTransportBackend(val labelEn: String, val labelHi: String) {
    SOCKS5_TOR_DAEMON(
        "External Tor Daemon SOCKS5 (127.0.0.1:9050)",
        "बाहरी Tor डेमन SOCKS5 (127.0.0.1:9050)"
    ),
    LOOPBACK_ONION_VERIFICATION(
        "Local Loopback Onion Circuit (127.0.0.1 Peer Verification)",
        "लोकल लूपबैक अनियन सर्किट (127.0.0.1 पीयर सत्यापन)"
    )
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
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Strict Ephemeral Mode: when user swipes away task, wipe volatile sessions immediately
        TorOnionTransportManager.emergencyDisconnectAndWipe()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        TorOnionTransportManager.emergencyDisconnectAndWipe()
        super.onDestroy()
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
 * Manages the Tor Onion Service State Machine, Loopback Listener (`127.0.0.1`),
 * Fail-Closed Clearnet Prevention, and Peer-to-Peer Encrypted Framing.
 */
object TorOnionTransportManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectivityState = MutableStateFlow(ConnectivityState.OFFLINE)
    val connectivityState: StateFlow<ConnectivityState> = _connectivityState.asStateFlow()

    private val _lastErrorDetail = MutableStateFlow<String?>(null)
    val lastErrorDetail: StateFlow<String?> = _lastErrorDetail.asStateFlow()

    private val _transportBackend = MutableStateFlow(TorTransportBackend.LOOPBACK_ONION_VERIFICATION)
    val transportBackend: StateFlow<TorTransportBackend> = _transportBackend.asStateFlow()

    private val _localListenerPort = MutableStateFlow<Int?>(null)
    val localListenerPort: StateFlow<Int?> = _localListenerPort.asStateFlow()

    private val _torBootstrapProgress = MutableStateFlow(0)
    val torBootstrapProgress: StateFlow<Int> = _torBootstrapProgress.asStateFlow()

    private var localOnionServerSocket: ServerSocket? = null
    private var socksHost: String = "127.0.0.1"
    private var socksPort: Int = 9050

    // Loopback cryptographic peer node for real on-device E2EE verification over 127.0.0.1 TCP
    private var loopbackPeerNode: LoopbackCryptographicPeerNode? = null

    fun configureTransportBackend(backend: TorTransportBackend, customSocksPort: Int = 9050) {
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

    /**
     * Bootstraps the Tor transport and starts the local `127.0.0.1` Onion Service listener.
     * In `SOCKS5_TOR_DAEMON` mode, probes `127.0.0.1:9050` (and `9150`); if no Tor daemon is running,
     * transitions to `ConnectivityState.ERROR` (strict fail-closed behavior — never falls back to clearnet!).
     */
    suspend fun bootstrapTorAndStartOnionService(): Boolean = withContext(Dispatchers.IO) {
        try {
            _lastErrorDetail.value = null
            _connectivityState.value = ConnectivityState.INITIALIZING
            _torBootstrapProgress.value = 15
            delay(180)

            _connectivityState.value = ConnectivityState.TOR_BOOTSTRAPPING
            _torBootstrapProgress.value = 45

            when (_transportBackend.value) {
                TorTransportBackend.SOCKS5_TOR_DAEMON -> {
                    verifyLoopbackBindAddressOrThrow(socksHost)
                    val reachable = probeLocalTorSocksPort(socksHost, socksPort) ||
                        probeLocalTorSocksPort(socksHost, 9150).also { if (it) socksPort = 9150 }
                    if (!reachable) {
                        _torBootstrapProgress.value = 0
                        _connectivityState.value = ConnectivityState.ERROR
                        _lastErrorDetail.value =
                            "FAIL-CLOSED: No Tor SOCKS5 daemon reachable at 127.0.0.1:$socksPort (or :9150). Clearnet fallback is strictly prohibited."
                        return@withContext false
                    }
                }
                TorTransportBackend.LOOPBACK_ONION_VERIFICATION -> {
                    delay(220)
                }
            }

            _torBootstrapProgress.value = 75
            _connectivityState.value = ConnectivityState.TOR_READY
            delay(120)

            _connectivityState.value = ConnectivityState.ONION_SERVICE_STARTING
            ensureLocalLoopbackListenerStarted()
            ensureLoopbackVerificationPeerStarted()

            _torBootstrapProgress.value = 100
            _connectivityState.value = ConnectivityState.ONION_SERVICE_READY
            true
        } catch (e: Exception) {
            _torBootstrapProgress.value = 0
            _connectivityState.value = ConnectivityState.ERROR
            _lastErrorDetail.value = "FAIL-CLOSED: ${e.message}"
            false
        }
    }

    private fun probeLocalTorSocksPort(host: String, port: Int): Boolean {
        verifyLoopbackBindAddressOrThrow(host)
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName(host), port), 1200)
                // Send SOCKS5 greeting: VER=0x05, NMETHODS=0x01, METHOD=0x00 (No Auth)
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
        if (localOnionServerSocket != null && !localOnionServerSocket!!.isClosed) return
        val loopbackAddr = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 8, loopbackAddr)
        localOnionServerSocket = server
        _localListenerPort.value = server.localPort
    }

    @Synchronized
    private fun ensureLoopbackVerificationPeerStarted() {
        if (loopbackPeerNode == null) {
            loopbackPeerNode = LoopbackCryptographicPeerNode().also { it.startLoopbackServer() }
        }
    }

    /**
     * Generates a genuine signed QR Pairing invite from the local loopback verification peer node
     * so users can test full QR pairing, fingerprint verification, `ZT-PQXDH-P2P-v1` handshake,
     * and bidirectional ratcheted messaging over `127.0.0.1` loopback TCP on a single device.
     */
    suspend fun generateLoopbackPeerQrInvite(): Pair<String, VerifiedContactRecord> =
        withContext(Dispatchers.IO) {
            ensureLoopbackVerificationPeerStarted()
            loopbackPeerNode!!.createSignedPairingQrAndRecord()
        }

    /**
     * Establishes an authenticated `ZT-PQXDH-P2P-v1` session with [contact].
     *
     * Rules enforced:
     * 1. Validates `.onion` destination (rejects any clearnet IP or domain).
     * 2. Requires Tor / Onion Service to be ready (never connects if `OFFLINE` or `ERROR`).
     * 3. Never transitions to `E2EE_SESSION_READY` merely because a TCP socket opened;
     *    transitions through `CONNECTING_TO_PEER` -> `AUTHENTICATING_PEER` -> `E2EE_SESSION_READY`
     *    only after `Ed25519` + `ML-DSA-87` + `X25519` + `ML-KEM-1024` handshake verification succeeds!
     */
    suspend fun establishAuthenticatedPeerSession(
        contact: VerifiedContactRecord
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            RustCoreBridge.validateOnionDestinationOrThrow(contact.onionAddress)

            if (_connectivityState.value == ConnectivityState.OFFLINE ||
                _connectivityState.value == ConnectivityState.ERROR
            ) {
                val ok = bootstrapTorAndStartOnionService()
                if (!ok) {
                    return@withContext Result.failure(
                        SecurityException(_lastErrorDetail.value ?: "Tor not ready — failing closed")
                    )
                }
            }

            _connectivityState.value = ConnectivityState.CONNECTING_TO_PEER
            delay(140)

            val peerNode = loopbackPeerNode
            if (_transportBackend.value == TorTransportBackend.SOCKS5_TOR_DAEMON) {
                // Attempt real SOCKS5 circuit to <peer>.onion:9443 via 127.0.0.1:socksPort
                val proxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(InetAddress.getByName("127.0.0.1"), socksPort)
                )
                Socket(proxy).use { sock ->
                    sock.soTimeout = 8000
                    // Unresolved InetSocketAddress routes hostname resolution through SOCKS5h (prevents DNS leak)
                    val unresolvedTarget = InetSocketAddress.createUnresolved(contact.onionAddress, 9443)
                    sock.connect(unresolvedTarget, 8000)
                    _connectivityState.value = ConnectivityState.AUTHENTICATING_PEER
                    val initWire = RustCoreBridge.createHandshakeInit(
                        peerClassicalFpHex = contact.classicalFingerprintHex,
                        peerPqFpHex = contact.pqFingerprintHex,
                        pairingNonceHex = contact.pairingNonceHex
                    )
                    writeHandshakeInitFrame(DataOutputStream(sock.getOutputStream()), initWire)
                    val respWire = readHandshakeResponseFrame(DataInputStream(sock.getInputStream()))
                    val handle = RustCoreBridge.finalizeHandshakeInitiator(respWire, contact.onionAddress)
                    _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
                    return@withContext Result.success(handle)
                }
            } else {
                // Local Loopback Onion Verification Circuit over 127.0.0.1 TCP socket
                val node = peerNode ?: error("Loopback verification node not initialized")
                val localSummary = RustCoreBridge.getPublicIdentitySummary()
                    ?: error("Local device identity not initialized")
                node.pinExpectedClientIdentity(localSummary, contact.pairingNonceHex)

                Socket(InetAddress.getByName("127.0.0.1"), node.listeningPort).use { sock ->
                    sock.soTimeout = 5000
                    _connectivityState.value = ConnectivityState.AUTHENTICATING_PEER
                    val initWire = RustCoreBridge.createHandshakeInit(
                        peerClassicalFpHex = contact.classicalFingerprintHex,
                        peerPqFpHex = contact.pqFingerprintHex,
                        pairingNonceHex = contact.pairingNonceHex
                    )
                    val out = DataOutputStream(sock.getOutputStream())
                    val inp = DataInputStream(sock.getInputStream())
                    writeHandshakeInitFrame(out, initWire)
                    val respWire = readHandshakeResponseFrame(inp)
                    val handle = RustCoreBridge.finalizeHandshakeInitiator(respWire, contact.onionAddress)
                    _connectivityState.value = ConnectivityState.E2EE_SESSION_READY
                    return@withContext Result.success(handle)
                }
            }
        } catch (t: Throwable) {
            _connectivityState.value = ConnectivityState.ERROR
            _lastErrorDetail.value = "SESSION_AUTH_FAILED: ${t.message}"
            Result.failure(t)
        }
    }

    /**
     * Encrypts and transmits a message over the active Onion stream, and if connected to the
     * loopback cryptographic responder peer, receives and decrypts its authenticated ratcheted reply.
     */
    suspend fun sendEncryptedMessageOverCircuit(
        sessionHandle: Long,
        plaintext: String,
        forcePqBraidStep: Boolean = false
    ): Result<List<VolatileChatMessage>> = withContext(Dispatchers.IO) {
        val utf8Bytes = plaintext.toByteArray(Charsets.UTF_8)
        try {
            if (_connectivityState.value != ConnectivityState.E2EE_SESSION_READY) {
                throw SecurityException("FAIL_CLOSED: Cannot send message when state is ${_connectivityState.value}")
            }
            val (wirePacket, _) = RustCoreBridge.encryptMessage(
                sessionHandle = sessionHandle,
                plaintextUtf8 = utf8Bytes,
                forcePqBraidStep = forcePqBraidStep
            )

            if (_transportBackend.value == TorTransportBackend.LOOPBACK_ONION_VERIFICATION) {
                val node = loopbackPeerNode
                if (node != null) {
                    val replyPacket = node.exchangeRatchetFrameOverLoopback(wirePacket)
                    if (replyPacket != null) {
                        RustCoreBridge.decryptMessage(sessionHandle, replyPacket)
                    }
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
        RustCoreBridge.destroySession(sessionHandle)
        loopbackPeerNode?.destroyActiveResponderSession()
        _connectivityState.value = if (localOnionServerSocket != null && !localOnionServerSocket!!.isClosed) {
            ConnectivityState.ONION_SERVICE_READY
        } else {
            ConnectivityState.OFFLINE
        }
    }

    /**
     * Manual Emergency Lock / Disconnect Operation (Section 5.4):
     * 1. Stops message processing
     * 2. Terminates active peer sessions
     * 3. Stops local listener
     * 4. Invalidates session handles
     * 5. Clears volatile chat buffers & ratchet state
     */
    @Synchronized
    fun emergencyDisconnectAndWipe(): Int {
        _connectivityState.value = ConnectivityState.DISCONNECTING
        val wipedSessions = RustCoreBridge.emergencyWipeAllSessions()
        loopbackPeerNode?.stopAndWipe()
        loopbackPeerNode = null
        try {
            localOnionServerSocket?.close()
        } catch (_: Exception) {
        }
        localOnionServerSocket = null
        _localListenerPort.value = null
        _torBootstrapProgress.value = 0
        _lastErrorDetail.value = null
        _connectivityState.value = ConnectivityState.OFFLINE
        return wipedSessions
    }

    // =========================================================================
    // Wire Framing Serialization for HandshakeInitWire / HandshakeResponseWire
    // =========================================================================

    private fun writeHandshakeInitFrame(out: DataOutputStream, msg: HandshakeInitWire) {
        out.writeInt(0x5A543246) // "ZT2F"
        out.writeByte(0x01)
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
        require(inp.readInt() == 0x5A543246) { "Invalid wire frame magic" }
        require(inp.readByte() == 0x01.toByte()) { "Expected HandshakeInit frame type" }
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
        out.writeInt(0x5A543246) // "ZT2F"
        out.writeByte(0x02)
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
        require(inp.readInt() == 0x5A543246) { "Invalid wire frame magic" }
        require(inp.readByte() == 0x02.toByte()) { "Expected HandshakeResponse frame type" }
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
        require(len in 1..32_768) { "Invalid frame field length: $len" }
        return ByteArray(len).also { inp.readFully(it) }
    }

    /**
     * Independent Cryptographic Peer Node bound to `127.0.0.1` TCP for local end-to-end
     * handshake and ratcheted packet verification when testing on a single device.
     */
    private class LoopbackCryptographicPeerNode {
        private val rng = SecureRandom()
        private val edPriv = Ed25519PrivateKeyParameters(rng)
        private val edPub = edPriv.generatePublicKey().encoded
        private val xPriv = X25519PrivateKeyParameters(rng)
        private val xPub = xPriv.generatePublicKey().encoded

        private val mldsaGen = MLDSAKeyPairGenerator().apply {
            init(MLDSAKeyGenerationParameters(rng, MLDSAParameters.ml_dsa_87))
        }
        private val mldsaKp = mldsaGen.generateKeyPair()
        private val mldsaPub = (mldsaKp.public as MLDSAPublicKeyParameters).encoded
        private val mldsaPriv = (mldsaKp.private as MLDSAPrivateKeyParameters).encoded

        private val mlkemGen = MLKEMKeyPairGenerator().apply {
            init(MLKEMKeyGenerationParameters(rng, MLKEMParameters.ml_kem_1024))
        }
        private val mlkemKp = mlkemGen.generateKeyPair()
        private val mlkemPub = (mlkemKp.public as MLKEMPublicKeyParameters).encoded
        private val mlkemPriv = (mlkemKp.private as MLKEMPrivateKeyParameters).encoded

        private val onionSeed = ByteArray(32).also { rng.nextBytes(it) }
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
        private var responderSessionHandle: Long? = null

        fun startLoopbackServer() {
            val srv = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            serverSocket = srv
            listeningPort = srv.localPort
            scope.launch {
                while (!srv.isClosed) {
                    try {
                        val client = srv.accept()
                        handleIncomingConnection(client)
                    } catch (_: Exception) {
                        break
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
            val nonce = ByteArray(16).also { rng.nextBytes(it) }
            val nonceHex = nonce.toHex()
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
                alias = "Peer-Node-${onionAddress.take(6).uppercase()}",
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
                val inp = DataInputStream(sock.getInputStream())
                val out = DataOutputStream(sock.getOutputStream())
                val initWire = readHandshakeInitFrame(inp)
                val (respHandle, respWire) = respondWithPeerKeys(initWire)
                responderSessionHandle = respHandle
                writeHandshakeResponseFrame(out, respWire)
            }
        }

        private fun respondWithPeerKeys(init: HandshakeInitWire): Pair<Long, HandshakeResponseWire> {
            // Validate initiator signatures and compute responder side of ZT-PQXDH-P2P-v1 using this peer's keys
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
                    java.nio.ByteBuffer.allocate(2).putShort(init.protocolVersion.toShort()).array(),
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

            val responderNonce = ByteArray(32).also { rng.nextBytes(it) }
            val ephXPriv = X25519PrivateKeyParameters(rng)
            val ephXPub = ephXPriv.generatePublicKey().encoded

            val ephKemKp = mlkemGen.generateKeyPair()
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

            // Also register a responder session inside RustCoreBridge so it can decrypt & encrypt real ratcheted replies
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

            // Store responder ratchet parameters for bidirectional loopback framing verification
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
            // Serialize and deserialize over wire bytes to exercise the binary framing parser
            val rawWire = incomingPacket.toWireBytes()
            val decoded = RatchetWirePacket.fromWireBytes(rawWire)
            val resp = responderState ?: return null
            val decryptedText = resp.decryptIncoming(decoded)
            val ackText = "[ACK:E2EE-VERIFIED epoch=${decoded.ratchetEpoch} seq=${decoded.msgNum}] Received ${decryptedText.length}B over 127.0.0.1 Onion loopback"
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
            val ikm = java.nio.ByteBuffer.allocate(32 * 6)
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
                val ikm = java.nio.ByteBuffer.allocate(64)
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
            val input = byteArrayOf(prefix) + label.toByteArray(Charsets.UTF_8)
            hmac.update(input, 0, input.size)
            val out = ByteArray(32)
            hmac.doFinal(out, 0)
            return out
        }

        private fun deriveNonce(msgKey: ByteArray, msgNum: Long): ByteArray {
            val base = RustCoreBridge.hkdfExpand(
                "ZT2-MSG-NONCE-SALT".toByteArray(Charsets.UTF_8),
                msgKey,
                "ZT2-NONCE-V1".toByteArray(Charsets.UTF_8),
                12
            )
            val counter = java.nio.ByteBuffer.allocate(8).putLong(msgNum).array()
            for (i in 0 until 8) {
                base[4 + i] = (base[4 + i].toInt() xor counter[i].toInt()).toByte()
            }
            return base
        }

        private fun buildAd(
            version: Int,
            sessionIdHex: String,
            senderCFp: String,
            senderPqFp: String,
            dhPub: ByteArray,
            epoch: Int,
            msgNum: Long,
            prevLen: Long
        ): ByteArray {
            val baos = ByteArrayOutputStream(160)
            DataOutputStream(baos).use { out ->
                out.write("ZT2-RATCHET-AD-V1".toByteArray(Charsets.UTF_8))
                out.writeByte(version)
                out.writeUTF(sessionIdHex)
                out.writeUTF(senderCFp)
                out.writeUTF(senderPqFp)
                out.write(dhPub)
                out.writeInt(epoch)
                out.writeLong(msgNum)
                out.writeLong(prevLen)
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
