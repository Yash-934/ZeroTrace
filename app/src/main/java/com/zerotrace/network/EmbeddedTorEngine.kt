package com.zerotrace.network

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.zerotrace.bridge.RustCoreBridge
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.freehaven.tor.control.TorControlCommands
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService

data class EmbeddedTorReadyResult(
    val socksPort: Int,
    val publishedOnionAddress: String
)

/**
 * Manages the in-process Guardian Project `tor-android` daemon (`TorService` + `libtor.so`),
 * real Tor circuit bootstrap monitoring via `TorControlConnection`, ephemeral `ADD_ONION` v3
 * hidden service publishing, and RFC 1928 SOCKS5 `ATYP=0x03` `.onion` circuit dialing.
 *
 * Strictly zero clearnet DNS resolution and zero central servers.
 */
object EmbeddedTorEngine {

    private val progressRegex = Regex("""PROGRESS=(\d+)""")
    private val summaryRegex = Regex("""SUMMARY="([^"]*)"""")

    @Volatile
    private var boundService: TorService? = null

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    @Volatile
    private var statusReceiver: BroadcastReceiver? = null

    @Volatile
    private var lastTorBroadcastStatus: String = TorService.STATUS_OFF

    @Volatile
    private var lastTorBroadcastError: String? = null

    @Volatile
    private var activeSocksPort: Int? = null

    @Volatile
    private var activePublishedOnionStem: String? = null

    @Volatile
    private var activePublishedLocalPort: Int? = null

    fun getActiveSocksPort(): Int? = activeSocksPort?.takeIf { it > 0 }

    fun getPublishedOnionAddress(): String? = activePublishedOnionStem?.let { "$it.onion" }

    /**
     * Starts & binds [TorService], waits for Tor network bootstrap (0..100%),
     * retrieves the active loopback SOCKS5 port, and registers our v3 `.onion` service
     * forwarding [virtualOnionPort] -> `127.0.0.1:[localListenPort]`.
     */
    suspend fun startAndPublishOnionService(
        context: Context,
        virtualOnionPort: Int,
        localListenPort: Int,
        torAddOnionPrivateKeyParam: String?,
        expectedOnionAddress: String?,
        onProgress: (progressPercent: Int, summary: String) -> Unit
    ): EmbeddedTorReadyResult = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        require(localListenPort in 1..65535) { "Invalid local listen port: $localListenPort" }

        registerStatusReceiverIfNeeded(appCtx)
        val service = ensureTorServiceBound(appCtx)

        onProgress(10, "Starting embedded Tor daemon…")

        // 1. Wait up to 25s for TorControlConnection to initialize
        val startDeadline = System.currentTimeMillis() + 25_000L
        var controlConn: TorControlConnection? = service.torControlConnection
        while (controlConn == null && System.currentTimeMillis() < startDeadline) {
            lastTorBroadcastError?.let { err ->
                throw IOException("Embedded Tor daemon error: $err")
            }
            delay(250L)
            controlConn = service.torControlConnection
        }
        val conn = controlConn
            ?: throw IOException("Embedded Tor control socket did not start within 25s")

        applyFastTorRuntimeConf(conn)
        try {
            conn.signal("ACTIVE")
        } catch (_: Throwable) {
        }

        onProgress(20, "Bootstrapping Tor directory consensus…")

        // 2. Monitor real Tor bootstrap progress (0% -> 100%, up to 95s on cellular/slow networks)
        val bootstrapDeadline = System.currentTimeMillis() + 95_000L
        var lastReportedPct = 20
        var resolvedSocksPort = 0

        while (System.currentTimeMillis() < bootstrapDeadline) {
            lastTorBroadcastError?.let { err ->
                throw IOException("Embedded Tor error: $err")
            }

            val phaseRaw = try {
                conn.getInfo("status/bootstrap-phase")
            } catch (_: Throwable) {
                null
            }
            val pct = phaseRaw?.let { progressRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                ?: if (lastTorBroadcastStatus == TorService.STATUS_ON) 100 else lastReportedPct
            val summary = phaseRaw?.let { summaryRegex.find(it)?.groupValues?.getOrNull(1) }
                ?.takeIf { it.isNotBlank() }
                ?: "Building Tor circuits ($pct%)…"

            val clampedPct = pct.coerceIn(lastReportedPct, 99)
            if (clampedPct > lastReportedPct) {
                lastReportedPct = clampedPct
            }
            onProgress(lastReportedPct, summary)

            resolvedSocksPort = querySocksPort(conn, service)
            if (pct >= 100 && resolvedSocksPort > 0) {
                break
            }
            delay(450L)
        }

        if (resolvedSocksPort <= 0) {
            resolvedSocksPort = querySocksPort(conn, service)
        }
        val finalBootstrap = try {
            conn.getInfo("status/bootstrap-phase")
        } catch (_: Throwable) {
            null
        }
        val finalPct = finalBootstrap?.let { progressRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: if (lastTorBroadcastStatus == TorService.STATUS_ON) 100 else 0

        if (finalPct < 100 || resolvedSocksPort <= 0) {
            throw IOException(
                "Embedded Tor timed out at $finalPct% bootstrap (check internet connection or switch to Local Wi-Fi P2P mode)"
            )
        }
        activeSocksPort = resolvedSocksPort
        onProgress(95, "Publishing v3 .onion descriptor on Tor DHT…")

        // 3. Publish or reuse ephemeral v3 Onion Service mapped to 127.0.0.1:localListenPort
        val currentStem = activePublishedOnionStem
        val currentLocalPort = activePublishedLocalPort
        if (currentStem != null && currentLocalPort == localListenPort) {
            onProgress(100, "Tor v3 Onion Service live ($resolvedSocksPort)")
            return@withContext EmbeddedTorReadyResult(
                socksPort = resolvedSocksPort,
                publishedOnionAddress = "$currentStem.onion"
            )
        }

        if (currentStem != null) {
            try {
                conn.delOnion(currentStem)
            } catch (_: Throwable) {
            }
            activePublishedOnionStem = null
            activePublishedLocalPort = null
        }

        val keySpec = torAddOnionPrivateKeyParam ?: "NEW:ED25519-V3"
        val ports = mapOf(virtualOnionPort to "127.0.0.1:$localListenPort")
        val addRes = conn.addOnion(keySpec, ports, true, false)
        val rawStem = addRes[TorControlCommands.HS_ADDRESS]?.trim()?.lowercase()
            ?.removeSuffix(".onion")
            ?: throw IOException("Tor ADD_ONION did not return a valid ServiceID")

        val fullOnion = "$rawStem.onion"
        RustCoreBridge.validateOnionDestinationOrThrow(fullOnion)
        if (!expectedOnionAddress.isNullOrBlank() &&
            torAddOnionPrivateKeyParam != null &&
            !fullOnion.equals(expectedOnionAddress.trim(), ignoreCase = true)
        ) {
            // Unexpected mismatch: clean up and fail closed rather than silently mismatching identity
            try {
                conn.delOnion(rawStem)
            } catch (_: Throwable) {
            }
            throw SecurityException(
                "Tor ADD_ONION ServiceID mismatch: expected $expectedOnionAddress but got $fullOnion"
            )
        }

        activePublishedOnionStem = rawStem
        activePublishedLocalPort = localListenPort
        try {
            conn.signal("ACTIVE")
        } catch (_: Throwable) {
        }
        onProgress(100, "Tor v3 Onion Service live (SOCKS5 :$resolvedSocksPort)")

        EmbeddedTorReadyResult(
            socksPort = resolvedSocksPort,
            publishedOnionAddress = fullOnion
        )
    }

    private fun applyFastTorRuntimeConf(conn: TorControlConnection) {
        // Tune Tor 0.4.8 for faster mobile cross-network & cellular/5G CGNAT rendezvous:
        // - Prefer IPv4 ORPorts (avoids 30s+ IPv6 CGNAT connect stalls on mobile data)
        // - Tight CircuitBuildTimeout + SocksTimeout so stalled intro/HSDir circuits fail-fast & retry immediately
        val fastSettings = listOf(
            "ClientUseIPv4 1",
            "ClientPreferIPv6ORPort 0",
            "LearnCircuitBuildTimeout 0",
            "CircuitBuildTimeout 10",
            "NewCircuitPeriod 15",
            "NumEntryGuards 2",
            "DormantOnFirstStartup 0",
            "DormantCanceledByStartup 1",
            "KeepalivePeriod 25",
            "SocksTimeout 20"
        )
        try {
            conn.setConf(fastSettings)
        } catch (_: Throwable) {
            for (entry in fastSettings) {
                val spaceIdx = entry.indexOf(' ')
                if (spaceIdx > 0) {
                    val k = entry.substring(0, spaceIdx)
                    val v = entry.substring(spaceIdx + 1)
                    try {
                        conn.setConf(k, v)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    /**
     * Triggers rapid Tor circuit & hidden-service recovery after Android switches networks
     * (e.g. Wi-Fi A -> Wi-Fi B or Wi-Fi -> 4G/5G Mobile Data) without tearing down identity.
     */
    suspend fun onNetworkChanged(
        virtualOnionPort: Int,
        localListenPort: Int,
        torAddOnionPrivateKeyParam: String?,
        expectedOnionAddress: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val service = boundService ?: return@withContext false
        val conn = service.torControlConnection ?: return@withContext false
        try {
            try {
                conn.setConf("DisableNetwork", "1")
                delay(120L)
                conn.setConf("DisableNetwork", "0")
            } catch (_: Throwable) {
            }
            applyFastTorRuntimeConf(conn)
            try {
                conn.dropTimeouts()
            } catch (_: Throwable) {
            }
            try {
                conn.signal("ACTIVE")
            } catch (_: Throwable) {
            }

            val resolvedPort = querySocksPort(conn, service)
            if (resolvedPort in 1..65535) {
                activeSocksPort = resolvedPort
            }

            // Re-verify our v3 .onion descriptor is still registered on Tor
            val expectedStem = expectedOnionAddress?.trim()?.lowercase()?.removeSuffix(".onion")
            val currentOnionsRaw = try {
                conn.getInfo("onions/current")
            } catch (_: Throwable) {
                null
            }
            val currentStem = activePublishedOnionStem
            val isMissing = currentStem.isNullOrBlank() ||
                (expectedStem != null && currentOnionsRaw != null && !currentOnionsRaw.contains(expectedStem, ignoreCase = true))

            if (isMissing && localListenPort in 1..65535) {
                val keySpec = torAddOnionPrivateKeyParam ?: "NEW:ED25519-V3"
                val ports = mapOf(virtualOnionPort to "127.0.0.1:$localListenPort")
                val addRes = conn.addOnion(keySpec, ports, true, false)
                val newStem = addRes[TorControlCommands.HS_ADDRESS]?.trim()?.lowercase()?.removeSuffix(".onion")
                if (!newStem.isNullOrBlank()) {
                    activePublishedOnionStem = newStem
                    activePublishedLocalPort = localListenPort
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Proactively asks the embedded Tor daemon to fetch a peer's v3 `.onion` descriptor
     * so subsequent SOCKS5 rendezvous circuits establish faster.
     */
    fun prefetchOnionDescriptor(peerOnion: String) {
        val conn = try {
            boundService?.torControlConnection
        } catch (_: Throwable) {
            null
        } ?: return
        val stem = peerOnion.trim().lowercase().removeSuffix(".onion")
        if (stem.length != 56) return
        try {
            conn.hsFetch(stem)
        } catch (_: Throwable) {
        }
    }

    private fun querySocksPort(conn: TorControlConnection, service: TorService): Int {
        try {
            val raw = conn.getInfo("net/listeners/socks")?.trim()?.removeSurrounding("\"")
            if (!raw.isNullOrBlank()) {
                val firstToken = raw.split(" ").firstOrNull()?.removeSurrounding("\"")
                val port = firstToken?.substringAfterLast(":")?.toIntOrNull()
                if (port != null && port in 1..65535) return port
            }
        } catch (_: Throwable) {
        }
        val svcPort = try {
            service.socksPort
        } catch (_: Throwable) {
            0
        }
        return if (svcPort in 1..65535) svcPort else 0
    }

    private suspend fun ensureTorServiceBound(appCtx: Context): TorService {
        boundService?.let { return it }

        val ready = CompletableDeferred<TorService>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = (binder as? TorService.LocalBinder)?.service
                if (svc != null) {
                    boundService = svc
                    ready.complete(svc)
                } else {
                    ready.completeExceptionally(IllegalStateException("Unexpected TorService binder type"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
                activeSocksPort = null
                activePublishedOnionStem = null
                activePublishedLocalPort = null
            }
        }

        serviceConnection = conn
        val intent = Intent(appCtx, TorService::class.java)
        try {
            appCtx.startService(intent)
        } catch (_: Throwable) {
        }
        val bindAccepted = appCtx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bindAccepted) {
            serviceConnection = null
            throw IOException("Android rejected bindService for embedded TorService")
        }

        return withTimeoutOrNull(15_000L) { ready.await() }
            ?: run {
                try {
                    appCtx.unbindService(conn)
                } catch (_: Throwable) {
                }
                serviceConnection = null
                throw IOException("Timed out binding to embedded TorService")
            }
    }

    private fun registerStatusReceiverIfNeeded(appCtx: Context) {
        if (statusReceiver != null) return
        lastTorBroadcastError = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    TorService.ACTION_STATUS -> {
                        lastTorBroadcastStatus =
                            intent.getStringExtra(TorService.EXTRA_STATUS) ?: lastTorBroadcastStatus
                    }
                    TorService.ACTION_ERROR -> {
                        val err = intent.getStringExtra(Intent.EXTRA_TEXT)
                            ?: intent.getStringExtra(TorService.EXTRA_STATUS)
                            ?: "Unknown embedded Tor error"
                        lastTorBroadcastError = err
                    }
                }
            }
        }
        statusReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(TorService.ACTION_STATUS)
            addAction(TorService.ACTION_ERROR)
        }
        ContextCompat.registerReceiver(
            appCtx,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Connects to a remote v3 `.onion` service strictly through local loopback Tor SOCKS5
     * using RFC 1928 `ATYP = 0x03` (domain name), guaranteeing zero clearnet DNS queries.
     */
    fun connectToOnionOverSocks5(
        peerOnion: String,
        targetPort: Int,
        socksHost: String = "127.0.0.1",
        socksPort: Int,
        connectTimeoutMs: Int = 45_000
    ): Socket {
        TorOnionTransportManager.verifyLoopbackBindAddressOrThrow(socksHost)
        RustCoreBridge.validateOnionDestinationOrThrow(peerOnion)
        require(targetPort in 1..65535) { "Invalid .onion target port: $targetPort" }
        require(socksPort in 1..65535) { "Invalid Tor SOCKS5 port: $socksPort" }

        val canonicalHost = peerOnion.trim().lowercase().substringBefore(":")
        val hostBytes = canonicalHost.toByteArray(Charsets.US_ASCII)
        require(hostBytes.size in 1..255) { "Onion hostname length invalid" }

        val sock = Socket()
        try {
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.soTimeout = connectTimeoutMs
            sock.connect(
                InetSocketAddress(InetAddress.getByName(socksHost), socksPort),
                minOf(connectTimeoutMs, 8_000)
            )

            val out = BufferedOutputStream(sock.getOutputStream(), 1024)
            val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), 1024))

            // 1. RFC 1928 Greeting: VER=5, NMETHODS=1, NO_AUTH=0x00
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()

            val greetingResp = ByteArray(2)
            inp.readFully(greetingResp)
            if (greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x00.toByte()) {
                throw IOException(
                    "SOCKS5 auth negotiation failed (ver=${greetingResp[0]}, method=${greetingResp[1]})"
                )
            }

            // 2. RFC 1928 CONNECT with ATYP=0x03 (domain name) so Tor resolves .onion inside the circuit
            val req = ByteArray(4 + 1 + hostBytes.size + 2)
            req[0] = 0x05 // VER
            req[1] = 0x01 // CMD = CONNECT
            req[2] = 0x00 // RSV
            req[3] = 0x03 // ATYP = DOMAINNAME (zero local DNS leak)
            req[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
            req[5 + hostBytes.size] = ((targetPort ushr 8) and 0xFF).toByte()
            req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
            out.write(req)
            out.flush()

            // 3. Read SOCKS5 CONNECT Reply: VER, REP, RSV, ATYP, BND.ADDR, BND.PORT
            val header = ByteArray(4)
            inp.readFully(header)
            if (header[0] != 0x05.toByte()) {
                throw IOException("Invalid SOCKS5 reply version: ${header[0]}")
            }
            val rep = header[1].toInt() and 0xFF
            if (rep != 0x00) {
                throw IOException("Tor SOCKS5 CONNECT failed (${describeSocksReplyCode(rep)})")
            }

            when (header[3].toInt() and 0xFF) {
                0x01 -> inp.readFully(ByteArray(4 + 2))
                0x03 -> {
                    val bndLen = inp.readUnsignedByte()
                    inp.readFully(ByteArray(bndLen + 2))
                }
                0x04 -> inp.readFully(ByteArray(16 + 2))
                else -> throw IOException("Unsupported SOCKS5 reply ATYP: ${header[3]}")
            }

            return sock
        } catch (t: Throwable) {
            try {
                sock.close()
            } catch (_: Throwable) {
            }
            throw t
        }
    }

    private fun describeSocksReplyCode(rep: Int): String = when (rep) {
        0x01 -> "0x01 General Tor SOCKS failure"
        0x02 -> "0x02 Connection not allowed by ruleset"
        0x03 -> "0x03 Network unreachable"
        0x04 -> "0x04 Peer .onion offline or descriptor unavailable"
        0x05 -> "0x05 Peer .onion refused port connection"
        0x06 -> "0x06 Tor circuit TTL expired"
        else -> "REP=0x${"%02x".format(rep)}"
    }

    @Synchronized
    fun stopEmbeddedTor(context: Context?) {
        val stem = activePublishedOnionStem
        val conn = try {
            boundService?.torControlConnection
        } catch (_: Throwable) {
            null
        }
        if (stem != null && conn != null) {
            try {
                conn.delOnion(stem)
            } catch (_: Throwable) {
            }
        }
        activePublishedOnionStem = null
        activePublishedLocalPort = null
        activeSocksPort = null
        lastTorBroadcastStatus = TorService.STATUS_OFF
        lastTorBroadcastError = null

        val appCtx = context?.applicationContext
        if (appCtx != null) {
            statusReceiver?.let { rcv ->
                try {
                    appCtx.unregisterReceiver(rcv)
                } catch (_: Throwable) {
                }
            }
            serviceConnection?.let { sc ->
                try {
                    appCtx.unbindService(sc)
                } catch (_: Throwable) {
                }
            }
            try {
                appCtx.stopService(Intent(appCtx, TorService::class.java))
            } catch (_: Throwable) {
            }
        }
        statusReceiver = null
        serviceConnection = null
        boundService = null
    }
}
