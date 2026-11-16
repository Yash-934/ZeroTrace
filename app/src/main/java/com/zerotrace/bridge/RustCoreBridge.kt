package com.zerotrace.bridge

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA3Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters

/**
 * JNI Native Loader for `libzerotrace_core.so`.
 * Never fakes native library availability: exposes [isNativeLibraryLoaded] honestly to diagnostics.
 */
object NativeRustBridge {
    val isNativeLibraryLoaded: Boolean
    val loadErrorReason: String?

    init {
        var loaded = false
        var err: String? = null
        try {
            System.loadLibrary("zerotrace_core")
            loaded = true
        } catch (t: UnsatisfiedLinkError) {
            err = "libzerotrace_core.so not bundled in APK jniLibs (${t.message}); using verified FIPS 203/204 JVM engine"
        } catch (t: Throwable) {
            err = t.message
        }
        isNativeLibraryLoaded = loaded
        loadErrorReason = err
    }

    @JvmStatic
    external fun nativeVersion(): String

    @JvmStatic
    external fun nativeValidatePairingQr(qrPayload: String, nowUnixSec: Long): Boolean

    @JvmStatic
    external fun nativeEmergencyWipeAllSessions(): Int
}

/**
 * Public identity summary safe to expose to UI components (contains zero private keys).
 */
data class PublicIdentitySummary(
    val onionAddress: String,
    val classicalFingerprintHex: String,
    val pqFingerprintHex: String,
    val classicalSafetyDigits: String,
    val pqSafetyDigits: String,
    val ed25519PublicKey: ByteArray,
    val x25519PublicKey: ByteArray,
    val mlDsa87PublicKey: ByteArray,
    val mlKem1024EncapsKey: ByteArray
)

data class LocalUserProfile(
    val displayName: String = "ZeroTrace User",
    val bio: String = "Encrypted via Post-Quantum Tor v3",
    val avatarJpegBytes: ByteArray? = null,
    val updatedAtEpochSec: Long = System.currentTimeMillis() / 1000L
)

/**
 * Verified or pending contact record (never stores private keys).
 */
data class VerifiedContactRecord(
    val alias: String,
    val onionAddress: String,
    val classicalFingerprintHex: String,
    val pqFingerprintHex: String,
    val classicalSafetyDigits: String,
    val pqSafetyDigits: String,
    val ed25519PublicKey: ByteArray,
    val x25519PublicKey: ByteArray,
    val mlDsa87PublicKey: ByteArray?,
    val mlKem1024EncapsKey: ByteArray?,
    val pairingNonceHex: String,
    val verifiedAtEpochSec: Long,
    val identityChangedWarning: Boolean = false,
    val bio: String = "",
    val avatarBytes: ByteArray? = null
)

/**
 * Parsed QR pairing payload (`ZT2|v1|...`).
 */
data class ParsedQrInvite(
    val onionAddress: String,
    val classicalFingerprintHex: String,
    val pqFingerprintHex: String,
    val classicalSafetyDigits: String,
    val pqSafetyDigits: String,
    val ed25519PublicKey: ByteArray,
    val x25519PublicKey: ByteArray,
    val expiryUnixSec: Long,
    val pairingNonceHex: String,
    val capabilities: String
)

/**
 * Ratchet wire packet (`ZT-PQXDH-P2P-v1` + Hybrid Double Ratchet + Periodic ML-KEM-1024 Braid).
 */
data class RatchetWirePacket(
    val version: Int,
    val sessionIdHex: String,
    val dhRatchetPublic: ByteArray,
    val ratchetEpoch: Int,
    val msgNum: Long,
    val prevChainLen: Long,
    val pqBraidCiphertext: ByteArray?,
    val nextPqEncapsKey: ByteArray?,
    val aeadCiphertext: ByteArray
) {
    fun toWireBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(0x5A543252) // "ZT2R"
            out.writeByte(version)
            out.writeUTF(sessionIdHex)
            out.writeInt(dhRatchetPublic.size)
            out.write(dhRatchetPublic)
            out.writeInt(ratchetEpoch)
            out.writeLong(msgNum)
            out.writeLong(prevChainLen)
            if (pqBraidCiphertext != null) {
                out.writeInt(pqBraidCiphertext.size)
                out.write(pqBraidCiphertext)
            } else {
                out.writeInt(0)
            }
            if (nextPqEncapsKey != null) {
                out.writeInt(nextPqEncapsKey.size)
                out.write(nextPqEncapsKey)
            } else {
                out.writeInt(0)
            }
            out.writeInt(aeadCiphertext.size)
            out.write(aeadCiphertext)
        }
        return baos.toByteArray()
    }

    companion object {
        private const val MAX_PACKET_BYTES = 65_536

        fun fromWireBytes(bytes: ByteArray): RatchetWirePacket {
            require(bytes.size in 32..MAX_PACKET_BYTES) { "Invalid wire packet size: ${bytes.size}" }
            DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
                val magic = inp.readInt()
                require(magic == 0x5A543252) { "Invalid ratchet wire magic" }
                val version = inp.readUnsignedByte()
                val sessionIdHex = inp.readUTF()
                val dhLen = inp.readInt()
                require(dhLen == 32) { "Invalid X25519 ratchet key length" }
                val dhPub = ByteArray(32).also { inp.readFully(it) }
                val epoch = inp.readInt()
                val msgNum = inp.readLong()
                val prevChainLen = inp.readLong()
                val pqCtLen = inp.readInt()
                require(pqCtLen in 0..4096) { "Invalid PQ ciphertext length" }
                val pqCt = if (pqCtLen > 0) ByteArray(pqCtLen).also { inp.readFully(it) } else null
                val nextEkLen = inp.readInt()
                require(nextEkLen in 0..4096) { "Invalid PQ EK length" }
                val nextEk = if (nextEkLen > 0) ByteArray(nextEkLen).also { inp.readFully(it) } else null
                val ctLen = inp.readInt()
                require(ctLen in 16..MAX_PACKET_BYTES) { "Invalid AEAD ciphertext length" }
                val ct = ByteArray(ctLen).also { inp.readFully(it) }
                return RatchetWirePacket(
                    version = version,
                    sessionIdHex = sessionIdHex,
                    dhRatchetPublic = dhPub,
                    ratchetEpoch = epoch,
                    msgNum = msgNum,
                    prevChainLen = prevChainLen,
                    pqBraidCiphertext = pqCt,
                    nextPqEncapsKey = nextEk,
                    aeadCiphertext = ct
                )
            }
        }
    }
}

/**
 * Handshake Init & Response messages for `ZT-PQXDH-P2P-v1`.
 */
data class HandshakeInitWire(
    val protocolVersion: Int,
    val sessionIdHex: String,
    val pairingNonceHex: String,
    val initiatorNonce: ByteArray,
    val initiatorEd25519Pk: ByteArray,
    val initiatorX25519Pk: ByteArray,
    val initiatorMlDsa87Pk: ByteArray,
    val initiatorMlKem1024Ek: ByteArray,
    val ephemeralX25519Pk: ByteArray,
    val ephemeralMlKem1024Ek: ByteArray,
    val targetPeerClassicalFpHex: String,
    val targetPeerPqFpHex: String,
    val ed25519Signature: ByteArray,
    val mlDsa87Signature: ByteArray
)

data class HandshakeResponseWire(
    val protocolVersion: Int,
    val sessionIdHex: String,
    val responderNonce: ByteArray,
    val responderEd25519Pk: ByteArray,
    val responderX25519Pk: ByteArray,
    val responderMlDsa87Pk: ByteArray,
    val responderMlKem1024Ek: ByteArray,
    val ephemeralX25519Pk: ByteArray,
    val ephemeralMlKem1024Ek: ByteArray,
    val kemCiphertextStatic: ByteArray,
    val kemCiphertextEphemeral: ByteArray,
    val ed25519Signature: ByteArray,
    val mlDsa87Signature: ByteArray
)

/**
 * Volatile chat message stored only in RAM (`plaintextUtf8` is wiped with `0x00` upon session destruction).
 */
class VolatileChatMessage(
    val messageId: Long,
    val isOutgoing: Boolean,
    val timestampEpochSec: Long,
    val ratchetEpoch: Int,
    val msgCounter: Long,
    val pqBraidStepTriggered: Boolean,
    private val plaintextUtf8: ByteArray,
    var deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT_OVER_TOR
) {
    @Volatile
    private var wiped: Boolean = false

    fun readPlaintext(): String {
        if (wiped) return "[WIPED]"
        return String(plaintextUtf8, Charsets.UTF_8)
    }

    fun wipeZeroize() {
        wiped = true
        Arrays.fill(plaintextUtf8, 0.toByte())
    }
}

enum class MessageDeliveryStatus {
    ENCRYPTING_PQ_RATCHET,
    SENT_OVER_TOR,
    DELIVERED_AUTHENTICATED,
    FAILED_CLOSED
}

/**
 * SecurityCoreBridge (`RustCoreBridge`)
 *
 * Keeps all private keys encapsulated inside internal zeroizing holders (`PrivateIdentityHolder`
 * and `InternalRatchetState`) and exposes only opaque `Long` session handles and public summaries
 * to the Android UI.
 */
object RustCoreBridge {

    const val PROTOCOL_VERSION = 2
    const val QR_MAGIC = "ZT2"
    const val QR_VERSION = "v1"
    const val MAX_SKIP = 64L
    const val PQ_BRAID_INTERVAL = 8L
    const val CAPABILITIES_STRING = "X25519+MLKEM1024+ED25519+MLDSA87+CHACHA20POLY1305"

    private val csprng = SecureRandom()
    private val nextSessionHandle = AtomicLong(1001L)
    private val nextMessageId = AtomicLong(1L)

    // Private identity held exclusively inside the bridge; never exposed to UI
    @Volatile
    private var activeIdentity: PrivateIdentityHolder? = null

    // Active volatile sessions indexed by opaque handle
    private val activeSessions = ConcurrentHashMap<Long, InternalRatchetState>()

    // Pending handshake initiator states indexed by sessionIdHex
    private val pendingInitiatorStates = ConcurrentHashMap<String, PendingInitiatorHandshake>()

    private class PrivateIdentityHolder(
        val ed25519Sk: ByteArray,
        val ed25519Pk: ByteArray,
        val x25519Sk: ByteArray,
        val x25519Pk: ByteArray,
        val mlDsa87SkEncoded: ByteArray,
        val mlDsa87PkEncoded: ByteArray,
        val mlKem1024DkEncoded: ByteArray,
        val mlKem1024EkEncoded: ByteArray,
        var onionServiceSeed: ByteArray,
        var onionServicePub: ByteArray
    ) {
        fun zeroize() {
            Arrays.fill(ed25519Sk, 0.toByte())
            Arrays.fill(x25519Sk, 0.toByte())
            Arrays.fill(mlDsa87SkEncoded, 0.toByte())
            Arrays.fill(mlKem1024DkEncoded, 0.toByte())
            Arrays.fill(onionServiceSeed, 0.toByte())
        }
    }

    private class PendingInitiatorHandshake(
        val sessionIdHex: String,
        val pairingNonceHex: String,
        val initiatorNonce: ByteArray,
        val ephX25519Sk: ByteArray,
        val ephX25519Pk: ByteArray,
        val ephMlKemDk: ByteArray,
        val ephMlKemEk: ByteArray,
        val expectedPeerClassicalFpHex: String,
        val expectedPeerPqFpHex: String,
        val initTranscriptHash: ByteArray
    ) {
        fun zeroize() {
            Arrays.fill(initiatorNonce, 0.toByte())
            Arrays.fill(ephX25519Sk, 0.toByte())
            Arrays.fill(ephMlKemDk, 0.toByte())
        }
    }

    private class InternalRatchetState(
        val handle: Long,
        val sessionIdHex: String,
        val peerOnion: String,
        val localClassicalFpHex: String,
        val localPqFpHex: String,
        val peerClassicalFpHex: String,
        val peerPqFpHex: String,
        var rootKey: ByteArray,
        var ckSend: ByteArray,
        var ckRecv: ByteArray,
        var dhLocalSk: ByteArray,
        var dhLocalPk: ByteArray,
        var dhPeerPk: ByteArray,
        var pqLocalDk: ByteArray,
        var pqLocalEk: ByteArray,
        var pqPeerEk: ByteArray,
        var ratchetEpoch: Int = 1,
        var sendCount: Long = 0L,
        var recvCount: Long = 0L,
        var prevSendCount: Long = 0L,
        var pqBraidStepsCompleted: Int = 0,
        var lastActivityEpochSec: Long = System.currentTimeMillis() / 1000L
    ) {
        val skippedMessageKeys = LinkedHashMap<String, ByteArray>()
        val consumedMessageKeys = HashSet<String>()
        val messages = mutableListOf<VolatileChatMessage>()

        @Synchronized
        fun wipeAndDestroy() {
            Arrays.fill(rootKey, 0.toByte())
            Arrays.fill(ckSend, 0.toByte())
            Arrays.fill(ckRecv, 0.toByte())
            Arrays.fill(dhLocalSk, 0.toByte())
            Arrays.fill(pqLocalDk, 0.toByte())
            skippedMessageKeys.values.forEach { Arrays.fill(it, 0.toByte()) }
            skippedMessageKeys.clear()
            consumedMessageKeys.clear()
            messages.forEach { it.wipeZeroize() }
            messages.clear()
        }
    }

    fun isIdentityLoaded(): Boolean = activeIdentity != null

    /**
     * Generates a fresh hybrid Classical (`Ed25519` + `X25519`) and Post-Quantum
     * (`NIST FIPS 204 ML-DSA-87` + `NIST FIPS 203 ML-KEM-1024`) identity using the OS CSPRNG.
     * Returns the serialized secret bundle strictly for immediate wrapping by [com.zerotrace.security.KeystoreManager].
     */
    @Synchronized
    fun generateNewDeviceIdentity(): Pair<PublicIdentitySummary, ByteArray> {
        activeIdentity?.zeroize()

        // 1. Classical Ed25519 signing keypair
        val edPriv = Ed25519PrivateKeyParameters(csprng)
        val edPub = edPriv.generatePublicKey()

        // 2. Classical X25519 static key agreement keypair
        val xPriv = X25519PrivateKeyParameters(csprng)
        val xPub = xPriv.generatePublicKey()

        // 3. Post-Quantum NIST FIPS 204 ML-DSA-87 signing keypair
        val mldsaGen = MLDSAKeyPairGenerator()
        mldsaGen.init(MLDSAKeyGenerationParameters(csprng, MLDSAParameters.ml_dsa_87))
        val mldsaKp: AsymmetricCipherKeyPair = mldsaGen.generateKeyPair()
        val mldsaPub = mldsaKp.public as MLDSAPublicKeyParameters
        val mldsaPriv = mldsaKp.private as MLDSAPrivateKeyParameters

        // 4. Post-Quantum NIST FIPS 203 ML-KEM-1024 key encapsulation keypair
        val mlkemGen = MLKEMKeyPairGenerator()
        mlkemGen.init(MLKEMKeyGenerationParameters(csprng, MLKEMParameters.ml_kem_1024))
        val mlkemKp: AsymmetricCipherKeyPair = mlkemGen.generateKeyPair()
        val mlkemPub = mlkemKp.public as MLKEMPublicKeyParameters
        val mlkemPriv = mlkemKp.private as MLKEMPrivateKeyParameters

        // 5. Separate Tor v3 Onion Service Ed25519 seed (kept distinct from application identity)
        val onionSeed = ByteArray(32).also { csprng.nextBytes(it) }
        val onionPub = Ed25519PrivateKeyParameters(onionSeed, 0).generatePublicKey().encoded

        val holder = PrivateIdentityHolder(
            ed25519Sk = edPriv.encoded,
            ed25519Pk = edPub.encoded,
            x25519Sk = xPriv.encoded,
            x25519Pk = xPub.encoded,
            mlDsa87SkEncoded = mldsaPriv.encoded,
            mlDsa87PkEncoded = mldsaPub.encoded,
            mlKem1024DkEncoded = mlkemPriv.encoded,
            mlKem1024EkEncoded = mlkemPub.encoded,
            onionServiceSeed = onionSeed,
            onionServicePub = onionPub
        )
        activeIdentity = holder
        val serialized = serializeIdentityHolder(holder)
        return Pair(buildPublicSummary(holder), serialized)
    }

    @Synchronized
    fun restoreWrappedIdentity(unwrappedBytes: ByteArray): PublicIdentitySummary {
        try {
            activeIdentity?.zeroize()
            val holder = deserializeIdentityHolder(unwrappedBytes)
            activeIdentity = holder
            return buildPublicSummary(holder)
        } finally {
            Arrays.fill(unwrappedBytes, 0.toByte())
        }
    }

    /**
     * Migrates legacy pre-Tor `ZTID` identities (which only stored the public onion key) to `ZTIE`
     * by generating a real 32-byte Ed25519 seed so the embedded Tor daemon can publish the exact
     * matching `.onion` hidden service. Returns updated summary + serialized bundle if migrated.
     */
    @Synchronized
    fun migrateLegacyOnionSeedIfNeeded(): Pair<PublicIdentitySummary, ByteArray>? {
        val holder = activeIdentity ?: return null
        if (holder.onionServiceSeed.size == 32) return null
        val newSeed = ByteArray(32).also { csprng.nextBytes(it) }
        val newPub = Ed25519PrivateKeyParameters(newSeed, 0).generatePublicKey().encoded
        Arrays.fill(holder.onionServiceSeed, 0.toByte())
        holder.onionServiceSeed = newSeed
        holder.onionServicePub = newPub
        return Pair(buildPublicSummary(holder), serializeIdentityHolder(holder))
    }

    /**
     * Expands a 32-byte Ed25519 seed per RFC 8032 + Tor Control Spec (`ADD_ONION`)
     * into the 64-byte clamped `(a || RH)` Base64 blob `"ED25519-V3:<base64>"`.
     */
    fun buildTorAddOnionPrivateKeyParam(seed32: ByteArray): String {
        require(seed32.size == 32) { "Tor v3 onion seed must be 32 bytes" }
        val expanded = MessageDigest.getInstance("SHA-512").digest(seed32)
        expanded[0] = (expanded[0].toInt() and 248).toByte()
        expanded[31] = ((expanded[31].toInt() and 63) or 64).toByte()
        val b64 = java.util.Base64.getEncoder().encodeToString(expanded)
        Arrays.fill(expanded, 0.toByte())
        return "ED25519-V3:$b64"
    }

    @Synchronized
    fun exportTorAddOnionPrivateKeyBlob(): String? {
        val holder = activeIdentity ?: return null
        if (holder.onionServiceSeed.size != 32) return null
        return buildTorAddOnionPrivateKeyParam(holder.onionServiceSeed)
    }

    @Synchronized
    fun getPublicIdentitySummary(): PublicIdentitySummary? {
        val holder = activeIdentity ?: return null
        return buildPublicSummary(holder)
    }

    private fun buildPublicSummary(holder: PrivateIdentityHolder): PublicIdentitySummary {
        val classicalFp = computeClassicalFingerprint(holder.ed25519Pk, holder.x25519Pk)
        val pqFp = computePqFingerprint(holder.mlDsa87PkEncoded, holder.mlKem1024EkEncoded)
        val onionAddress = deriveV3OnionAddress(holder.onionServicePub)
        return PublicIdentitySummary(
            onionAddress = onionAddress,
            classicalFingerprintHex = classicalFp.toHex(),
            pqFingerprintHex = pqFp.toHex(),
            classicalSafetyDigits = formatSafetyDigits(classicalFp),
            pqSafetyDigits = formatSafetyDigits(pqFp),
            ed25519PublicKey = holder.ed25519Pk.copyOf(),
            x25519PublicKey = holder.x25519Pk.copyOf(),
            mlDsa87PublicKey = holder.mlDsa87PkEncoded.copyOf(),
            mlKem1024EncapsKey = holder.mlKem1024EkEncoded.copyOf()
        )
    }

    /**
     * Domain-separated Classical Fingerprint: `SHA-256("ZT2-CLASSICAL-ID-V1" || ed25519_pk || x25519_pk)`
     */
    fun computeClassicalFingerprint(ed25519Pk: ByteArray, x25519Pk: ByteArray): ByteArray {
        return domainSha256("ZT2-CLASSICAL-ID-V1", listOf(ed25519Pk, x25519Pk))
    }

    /**
     * Domain-separated Post-Quantum Fingerprint: `SHA-256("ZT2-PQ-ID-V1" || ml_dsa_87_pk || ml_kem_1024_ek)`
     */
    fun computePqFingerprint(mlDsa87Pk: ByteArray, mlKem1024Ek: ByteArray): ByteArray {
        return domainSha256("ZT2-PQ-ID-V1", listOf(mlDsa87Pk, mlKem1024Ek))
    }

    fun formatSafetyDigits(fingerprint32: ByteArray): String {
        require(fingerprint32.size == 32)
        val groups = ArrayList<String>(8)
        for (i in 0 until 8) {
            val offset = i * 4
            val v = ByteBuffer.wrap(fingerprint32, offset, 4).int.toLong() and 0xFFFFFFFFL
            groups.add(String.format("%05d", v % 100_000L))
        }
        return groups.joinToString(" ")
    }

    /**
     * Derives a canonical 56-character base32 v3 `.onion` address from a 32-byte Ed25519 public key
     * per Tor `rend-spec-v3.txt` (`SHA3-256(".onion checksum" || pubkey || 0x03)`).
     */
    fun deriveV3OnionAddress(onionEd25519PubKey32: ByteArray): String {
        require(onionEd25519PubKey32.size == 32)
        val prefix = ".onion checksum".toByteArray(Charsets.UTF_8)
        val sha3 = SHA3Digest(256)
        sha3.update(prefix, 0, prefix.size)
        sha3.update(onionEd25519PubKey32, 0, 32)
        sha3.update(0x03.toByte())
        val checksum = ByteArray(32)
        sha3.doFinal(checksum, 0)

        val raw = ByteArray(35)
        System.arraycopy(onionEd25519PubKey32, 0, raw, 0, 32)
        raw[32] = checksum[0]
        raw[33] = checksum[1]
        raw[34] = 0x03
        return encodeBase32Lower(raw) + ".onion"
    }

    /**
     * Enforces Fail-Closed Destination Validation (Section 4.2):
     * Rejects all clearnet IPv4, IPv6, and regular DNS domains. Only canonical 56-char v3 `.onion` is allowed.
     */
    fun validateOnionDestinationOrThrow(address: String) {
        val cleaned = address.trim().lowercase().substringBefore(":")
        if (!cleaned.endsWith(".onion")) {
            throw SecurityException("FAIL_CLOSED_VIOLATION: Clearnet address '$address' rejected; only v3 .onion allowed")
        }
        val stem = cleaned.removeSuffix(".onion")
        if (stem.length != 56 || !stem.all { it in 'a'..'z' || it in '2'..'7' }) {
            throw SecurityException("FAIL_CLOSED_VIOLATION: Malformed v3 .onion address '$address'")
        }
    }

    /**
     * Generates a temporary, signed, versioned QR Pairing payload (`ZT2|v1|...`) that expires in [ttlSeconds].
     */
    fun generateSignedPairingQr(
        nowUnixSec: Long = System.currentTimeMillis() / 1000L,
        ttlSeconds: Long = 300L
    ): Pair<String, ParsedQrInvite> {
        val holder = activeIdentity ?: error("Device identity not initialized")
        require(ttlSeconds in 30L..900L) { "QR TTL must be between 30 and 900 seconds" }
        val summary = buildPublicSummary(holder)
        val expiry = nowUnixSec + ttlSeconds
        val nonce = ByteArray(16).also { csprng.nextBytes(it) }
        val nonceHex = nonce.toHex()

        val urlEncoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val edPkB64 = urlEncoder.encodeToString(holder.ed25519Pk)
        val xPkB64 = urlEncoder.encodeToString(holder.x25519Pk)

        val body = listOf(
            QR_MAGIC,
            QR_VERSION,
            summary.onionAddress,
            summary.classicalFingerprintHex,
            summary.pqFingerprintHex,
            edPkB64,
            xPkB64,
            expiry.toString(),
            nonceHex,
            CAPABILITIES_STRING
        ).joinToString("|")

        val authBytes = "ZT2-QR-AUTH-V1|$body".toByteArray(Charsets.UTF_8)
        val sig = signEd25519(holder.ed25519Sk, authBytes)
        val sigB64 = urlEncoder.encodeToString(sig)
        val fullQr = "$body|$sigB64"

        val parsed = parseAndVerifyPairingQr(fullQr, nowUnixSec)
        return Pair(fullQr, parsed)
    }

    /**
     * Deterministically parses and verifies a scanned QR pairing string.
     * Rejects expired, tampered, wrong-version, clearnet, or invalid-signature payloads.
     */
    fun parseAndVerifyPairingQr(
        rawQr: String,
        nowUnixSec: Long = System.currentTimeMillis() / 1000L
    ): ParsedQrInvite {
        require(rawQr.length in 64..2048) { "QR payload length out of bounds" }
        val parts = rawQr.trim().split("|")
        require(parts.size == 11) { "Expected 11 fields in ZT2 QR format, found ${parts.size}" }
        require(parts[0] == QR_MAGIC && parts[1] == QR_VERSION) {
            "Unsupported QR protocol or version: ${parts[0]}|${parts[1]}"
        }

        val onion = parts[2].lowercase()
        validateOnionDestinationOrThrow(onion)

        val classicalFp = parts[3].hexToBytes()
        val pqFp = parts[4].hexToBytes()
        require(classicalFp.size == 32 && pqFp.size == 32) { "Fingerprint must be 32 bytes" }

        val urlDecoder = java.util.Base64.getUrlDecoder()
        val edPk = urlDecoder.decode(parts[5])
        val xPk = urlDecoder.decode(parts[6])
        require(edPk.size == 32 && xPk.size == 32) { "Classical public keys must be 32 bytes" }

        val recomputedClassicalFp = computeClassicalFingerprint(edPk, xPk)
        if (!recomputedClassicalFp.contentEquals(classicalFp)) {
            throw SecurityException("Classical fingerprint does not match Ed25519+X25519 public keys")
        }

        val expirySec = parts[7].toLongOrNull() ?: throw IllegalArgumentException("Invalid expiry timestamp")
        if (expirySec <= nowUnixSec - 3600L || expirySec > nowUnixSec + 7200L) {
            throw SecurityException("QR pairing artifact has expired or has an out-of-range expiry")
        }

        val nonceBytes = parts[8].hexToBytes()
        require(nonceBytes.size == 16) { "Pairing nonce must be 16 bytes (32 hex chars)" }

        val capabilities = parts[9]
        if (!capabilities.contains("MLKEM1024") || !capabilities.contains("MLDSA87")) {
            throw SecurityException("Peer capabilities missing mandatory ML-KEM-1024 or ML-DSA-87")
        }

        val sig = urlDecoder.decode(parts[10])
        require(sig.size == 64) { "Invalid Ed25519 signature length" }
        val body = parts.subList(0, 10).joinToString("|")
        val authBytes = "ZT2-QR-AUTH-V1|$body".toByteArray(Charsets.UTF_8)
        if (!verifyEd25519(edPk, authBytes, sig)) {
            throw SecurityException("QR Ed25519 signature verification failed")
        }

        return ParsedQrInvite(
            onionAddress = onion,
            classicalFingerprintHex = classicalFp.toHex(),
            pqFingerprintHex = pqFp.toHex(),
            classicalSafetyDigits = formatSafetyDigits(classicalFp),
            pqSafetyDigits = formatSafetyDigits(pqFp),
            ed25519PublicKey = edPk,
            x25519PublicKey = xPk,
            expiryUnixSec = expirySec,
            pairingNonceHex = parts[8].lowercase(),
            capabilities = capabilities
        )
    }

    /**
     * Initiates a Serverless Hybrid Post-Quantum Handshake (`ZT-PQXDH-P2P-v1`) with a verified peer.
     */
    @Synchronized
    fun createHandshakeInit(
        peerClassicalFpHex: String,
        peerPqFpHex: String,
        pairingNonceHex: String
    ): HandshakeInitWire {
        val holder = activeIdentity ?: error("Device identity not initialized")
        val localSummary = buildPublicSummary(holder)

        val sessionId = ByteArray(16).also { csprng.nextBytes(it) }.toHex()
        val initiatorNonce = ByteArray(32).also { csprng.nextBytes(it) }

        val ephXPriv = X25519PrivateKeyParameters(csprng)
        val ephXPub = ephXPriv.generatePublicKey().encoded

        val mlkemGen = MLKEMKeyPairGenerator()
        mlkemGen.init(MLKEMKeyGenerationParameters(csprng, MLKEMParameters.ml_kem_1024))
        val ephKemKp = mlkemGen.generateKeyPair()
        val ephKemPub = (ephKemKp.public as MLKEMPublicKeyParameters).encoded
        val ephKemPriv = (ephKemKp.private as MLKEMPrivateKeyParameters).encoded

        val transcript = domainSha256(
            "ZT2-HS-INIT-V1",
            listOf(
                ByteBuffer.allocate(2).putShort(PROTOCOL_VERSION.toShort()).array(),
                sessionId.hexToBytes(),
                pairingNonceHex.hexToBytes(),
                initiatorNonce,
                localSummary.classicalFingerprintHex.hexToBytes(),
                localSummary.pqFingerprintHex.hexToBytes(),
                ephXPub,
                ephKemPub,
                peerClassicalFpHex.hexToBytes(),
                peerPqFpHex.hexToBytes()
            )
        )

        val edSig = signEd25519(holder.ed25519Sk, transcript)
        val pqSig = signMlDsa87(holder.mlDsa87SkEncoded, transcript)

        pendingInitiatorStates[sessionId] = PendingInitiatorHandshake(
            sessionIdHex = sessionId,
            pairingNonceHex = pairingNonceHex.lowercase(),
            initiatorNonce = initiatorNonce,
            ephX25519Sk = ephXPriv.encoded,
            ephX25519Pk = ephXPub,
            ephMlKemDk = ephKemPriv,
            ephMlKemEk = ephKemPub,
            expectedPeerClassicalFpHex = peerClassicalFpHex.lowercase(),
            expectedPeerPqFpHex = peerPqFpHex.lowercase(),
            initTranscriptHash = transcript
        )

        return HandshakeInitWire(
            protocolVersion = PROTOCOL_VERSION,
            sessionIdHex = sessionId,
            pairingNonceHex = pairingNonceHex.lowercase(),
            initiatorNonce = initiatorNonce,
            initiatorEd25519Pk = holder.ed25519Pk.copyOf(),
            initiatorX25519Pk = holder.x25519Pk.copyOf(),
            initiatorMlDsa87Pk = holder.mlDsa87PkEncoded.copyOf(),
            initiatorMlKem1024Ek = holder.mlKem1024EkEncoded.copyOf(),
            ephemeralX25519Pk = ephXPub,
            ephemeralMlKem1024Ek = ephKemPub,
            targetPeerClassicalFpHex = peerClassicalFpHex.lowercase(),
            targetPeerPqFpHex = peerPqFpHex.lowercase(),
            ed25519Signature = edSig,
            mlDsa87Signature = pqSig
        )
    }

    /**
     * Processes an incoming `HandshakeInitWire` as Responder, verifies dual classical + PQ identity
     * and signatures, performs 4-way X25519 + 2-way ML-KEM-1024 encapsulation, registers an opaque
     * session handle, and returns `(sessionHandle, HandshakeResponseWire)`.
     */
    @Synchronized
    fun respondToHandshakeInit(
        init: HandshakeInitWire,
        expectedPeerOnion: String,
        expectedPeerClassicalFpHex: String,
        expectedPeerPqFpHex: String,
        expectedPairingNonceHex: String
    ): Pair<Long, HandshakeResponseWire> {
        val holder = activeIdentity ?: error("Device identity not initialized")
        val localSummary = buildPublicSummary(holder)

        if (init.protocolVersion != PROTOCOL_VERSION) {
            throw SecurityException("PROTOCOL_DOWNGRADE_REJECTED: Unsupported version ${init.protocolVersion}")
        }
        if (!init.pairingNonceHex.equals(expectedPairingNonceHex, ignoreCase = true)) {
            throw SecurityException("PAIRING_NONCE_MISMATCH: Handshake pairing nonce does not match verified contact")
        }

        // Verify initiator classical & PQ fingerprints match pinned contact
        val actualPeerClassicalFp = computeClassicalFingerprint(init.initiatorEd25519Pk, init.initiatorX25519Pk).toHex()
        val actualPeerPqFp = computePqFingerprint(init.initiatorMlDsa87Pk, init.initiatorMlKem1024Ek).toHex()

        if (!actualPeerClassicalFp.equals(expectedPeerClassicalFpHex, ignoreCase = true) ||
            !actualPeerPqFp.equals(expectedPeerPqFpHex, ignoreCase = true)
        ) {
            throw SecurityException("PEER_IDENTITY_MISMATCH: Peer classical or ML-DSA-87/ML-KEM-1024 fingerprint does not match pinned contact")
        }

        if (!init.targetPeerClassicalFpHex.equals(localSummary.classicalFingerprintHex, ignoreCase = true) ||
            !init.targetPeerPqFpHex.equals(localSummary.pqFingerprintHex, ignoreCase = true)
        ) {
            throw SecurityException("TARGET_IDENTITY_MISMATCH: Handshake was not addressed to this device identity")
        }

        val initTranscript = domainSha256(
            "ZT2-HS-INIT-V1",
            listOf(
                ByteBuffer.allocate(2).putShort(init.protocolVersion.toShort()).array(),
                init.sessionIdHex.hexToBytes(),
                init.pairingNonceHex.hexToBytes(),
                init.initiatorNonce,
                actualPeerClassicalFp.hexToBytes(),
                actualPeerPqFp.hexToBytes(),
                init.ephemeralX25519Pk,
                init.ephemeralMlKem1024Ek,
                init.targetPeerClassicalFpHex.hexToBytes(),
                init.targetPeerPqFpHex.hexToBytes()
            )
        )

        if (!verifyEd25519(init.initiatorEd25519Pk, initTranscript, init.ed25519Signature)) {
            throw SecurityException("CLASSICAL_SIGNATURE_INVALID: Initiator Ed25519 signature verification failed")
        }
        if (!verifyMlDsa87(init.initiatorMlDsa87Pk, initTranscript, init.mlDsa87Signature)) {
            throw SecurityException("PQ_SIGNATURE_INVALID: Initiator NIST FIPS 204 ML-DSA-87 signature verification failed")
        }

        // Generate Responder ephemeral X25519 + ML-KEM-1024 keys and encapsulate to Initiator's static + ephemeral ML-KEM-1024 keys
        val responderNonce = ByteArray(32).also { csprng.nextBytes(it) }
        val ephXPriv = X25519PrivateKeyParameters(csprng)
        val ephXPub = ephXPriv.generatePublicKey().encoded

        val mlkemGen = MLKEMKeyPairGenerator()
        mlkemGen.init(MLKEMKeyGenerationParameters(csprng, MLKEMParameters.ml_kem_1024))
        val ephKemKp = mlkemGen.generateKeyPair()
        val ephKemPub = (ephKemKp.public as MLKEMPublicKeyParameters).encoded
        val ephKemPriv = (ephKemKp.private as MLKEMPrivateKeyParameters).encoded

        val (ctStatic, ssPq1) = encapsulateMlKem1024(init.initiatorMlKem1024Ek)
        val (ctEph, ssPq2) = encapsulateMlKem1024(init.ephemeralMlKem1024Ek)

        val dh1 = computeX25519(holder.x25519Sk, init.initiatorX25519Pk)
        val dh2 = computeX25519(ephXPriv.encoded, init.initiatorX25519Pk)
        val dh3 = computeX25519(holder.x25519Sk, init.ephemeralX25519Pk)
        val dh4 = computeX25519(ephXPriv.encoded, init.ephemeralX25519Pk)

        val respTranscript = domainSha256(
            "ZT2-HS-RESP-V1",
            listOf(
                initTranscript,
                responderNonce,
                localSummary.classicalFingerprintHex.hexToBytes(),
                localSummary.pqFingerprintHex.hexToBytes(),
                ephXPub,
                ephKemPub,
                ctStatic,
                ctEph
            )
        )

        val respEdSig = signEd25519(holder.ed25519Sk, respTranscript)
        val respPqSig = signMlDsa87(holder.mlDsa87SkEncoded, respTranscript)

        val ikm = ByteBuffer.allocate(32 * 6)
            .put(dh1).put(dh2).put(dh3).put(dh4).put(ssPq1).put(ssPq2)
            .array()
        val salt = domainSha256("ZT2-PQXDH-SALT-V1", listOf(init.initiatorNonce, responderNonce, respTranscript))
        val derived = hkdfExpand(salt, ikm, "ZT2-HYBRID-ROOT-V1".toByteArray(Charsets.UTF_8), 96)

        Arrays.fill(dh1, 0.toByte())
        Arrays.fill(dh2, 0.toByte())
        Arrays.fill(dh3, 0.toByte())
        Arrays.fill(dh4, 0.toByte())
        Arrays.fill(ssPq1, 0.toByte())
        Arrays.fill(ssPq2, 0.toByte())
        Arrays.fill(ikm, 0.toByte())

        val rk = derived.copyOfRange(0, 32)
        val ckAToB = derived.copyOfRange(32, 64)
        val ckBToA = derived.copyOfRange(64, 96)
        Arrays.fill(derived, 0.toByte())

        val handle = nextSessionHandle.getAndIncrement()
        // Responder sends on ckBToA and receives on ckAToB
        activeSessions[handle] = InternalRatchetState(
            handle = handle,
            sessionIdHex = init.sessionIdHex,
            peerOnion = expectedPeerOnion,
            localClassicalFpHex = localSummary.classicalFingerprintHex,
            localPqFpHex = localSummary.pqFingerprintHex,
            peerClassicalFpHex = actualPeerClassicalFp,
            peerPqFpHex = actualPeerPqFp,
            rootKey = rk,
            ckSend = ckBToA,
            ckRecv = ckAToB,
            dhLocalSk = ephXPriv.encoded,
            dhLocalPk = ephXPub,
            dhPeerPk = init.ephemeralX25519Pk.copyOf(),
            pqLocalDk = ephKemPriv,
            pqLocalEk = ephKemPub,
            pqPeerEk = init.ephemeralMlKem1024Ek.copyOf()
        )

        val respWire = HandshakeResponseWire(
            protocolVersion = PROTOCOL_VERSION,
            sessionIdHex = init.sessionIdHex,
            responderNonce = responderNonce,
            responderEd25519Pk = holder.ed25519Pk.copyOf(),
            responderX25519Pk = holder.x25519Pk.copyOf(),
            responderMlDsa87Pk = holder.mlDsa87PkEncoded.copyOf(),
            responderMlKem1024Ek = holder.mlKem1024EkEncoded.copyOf(),
            ephemeralX25519Pk = ephXPub,
            ephemeralMlKem1024Ek = ephKemPub,
            kemCiphertextStatic = ctStatic,
            kemCiphertextEphemeral = ctEph,
            ed25519Signature = respEdSig,
            mlDsa87Signature = respPqSig
        )
        return Pair(handle, respWire)
    }

    /**
     * Finalizes the Initiator side of the `ZT-PQXDH-P2P-v1` handshake upon receiving [HandshakeResponseWire],
     * verifies both `Ed25519` and `ML-DSA-87` responder signatures, decapsulates both `ML-KEM-1024` ciphertexts,
     * and returns the opaque session handle (`Long`).
     */
    @Synchronized
    fun finalizeHandshakeInitiator(
        resp: HandshakeResponseWire,
        peerOnion: String
    ): Long {
        val holder = activeIdentity ?: error("Device identity not initialized")
        val localSummary = buildPublicSummary(holder)
        val pending = pendingInitiatorStates.remove(resp.sessionIdHex)
            ?: throw SecurityException("HANDSHAKE_STATE_MISSING: Unknown or expired session ID ${resp.sessionIdHex}")

        try {
            if (resp.protocolVersion != PROTOCOL_VERSION) {
                throw SecurityException("PROTOCOL_DOWNGRADE_REJECTED: Unsupported version ${resp.protocolVersion}")
            }

            val actualPeerClassicalFp = computeClassicalFingerprint(resp.responderEd25519Pk, resp.responderX25519Pk).toHex()
            val actualPeerPqFp = computePqFingerprint(resp.responderMlDsa87Pk, resp.responderMlKem1024Ek).toHex()

            if (!actualPeerClassicalFp.equals(pending.expectedPeerClassicalFpHex, ignoreCase = true) ||
                !actualPeerPqFp.equals(pending.expectedPeerPqFpHex, ignoreCase = true)
            ) {
                throw SecurityException("PEER_IDENTITY_MISMATCH: Responder classical or PQ fingerprint mismatch")
            }

            val respTranscript = domainSha256(
                "ZT2-HS-RESP-V1",
                listOf(
                    pending.initTranscriptHash,
                    resp.responderNonce,
                    actualPeerClassicalFp.hexToBytes(),
                    actualPeerPqFp.hexToBytes(),
                    resp.ephemeralX25519Pk,
                    resp.ephemeralMlKem1024Ek,
                    resp.kemCiphertextStatic,
                    resp.kemCiphertextEphemeral
                )
            )

            if (!verifyEd25519(resp.responderEd25519Pk, respTranscript, resp.ed25519Signature)) {
                throw SecurityException("CLASSICAL_SIGNATURE_INVALID: Responder Ed25519 signature invalid")
            }
            if (!verifyMlDsa87(resp.responderMlDsa87Pk, respTranscript, resp.mlDsa87Signature)) {
                throw SecurityException("PQ_SIGNATURE_INVALID: Responder NIST FIPS 204 ML-DSA-87 signature invalid")
            }

            val dh1 = computeX25519(holder.x25519Sk, resp.responderX25519Pk)
            val dh2 = computeX25519(holder.x25519Sk, resp.ephemeralX25519Pk)
            val dh3 = computeX25519(pending.ephX25519Sk, resp.responderX25519Pk)
            val dh4 = computeX25519(pending.ephX25519Sk, resp.ephemeralX25519Pk)

            val ssPq1 = decapsulateMlKem1024(holder.mlKem1024DkEncoded, resp.kemCiphertextStatic)
            val ssPq2 = decapsulateMlKem1024(pending.ephMlKemDk, resp.kemCiphertextEphemeral)

            val ikm = ByteBuffer.allocate(32 * 6)
                .put(dh1).put(dh2).put(dh3).put(dh4).put(ssPq1).put(ssPq2)
                .array()
            val salt = domainSha256("ZT2-PQXDH-SALT-V1", listOf(pending.initiatorNonce, resp.responderNonce, respTranscript))
            val derived = hkdfExpand(salt, ikm, "ZT2-HYBRID-ROOT-V1".toByteArray(Charsets.UTF_8), 96)

            Arrays.fill(dh1, 0.toByte())
            Arrays.fill(dh2, 0.toByte())
            Arrays.fill(dh3, 0.toByte())
            Arrays.fill(dh4, 0.toByte())
            Arrays.fill(ssPq1, 0.toByte())
            Arrays.fill(ssPq2, 0.toByte())
            Arrays.fill(ikm, 0.toByte())

            val rk = derived.copyOfRange(0, 32)
            val ckAToB = derived.copyOfRange(32, 64)
            val ckBToA = derived.copyOfRange(64, 96)
            Arrays.fill(derived, 0.toByte())

            val handle = nextSessionHandle.getAndIncrement()
            activeSessions[handle] = InternalRatchetState(
                handle = handle,
                sessionIdHex = resp.sessionIdHex,
                peerOnion = peerOnion,
                localClassicalFpHex = localSummary.classicalFingerprintHex,
                localPqFpHex = localSummary.pqFingerprintHex,
                peerClassicalFpHex = actualPeerClassicalFp,
                peerPqFpHex = actualPeerPqFp,
                rootKey = rk,
                ckSend = ckAToB,
                ckRecv = ckBToA,
                dhLocalSk = pending.ephX25519Sk.copyOf(),
                dhLocalPk = pending.ephX25519Pk.copyOf(),
                dhPeerPk = resp.ephemeralX25519Pk.copyOf(),
                pqLocalDk = pending.ephMlKemDk.copyOf(),
                pqLocalEk = pending.ephMlKemEk.copyOf(),
                pqPeerEk = resp.ephemeralMlKem1024Ek.copyOf()
            )
            return handle
        } finally {
            pending.zeroize()
        }
    }

    /**
     * Encrypts an outgoing message inside the ratcheted session [sessionHandle], advancing the
     * symmetric chain and periodically performing an `ML-KEM-1024` Braid ratchet step.
     */
    fun encryptMessage(
        sessionHandle: Long,
        plaintextUtf8: ByteArray,
        forcePqBraidStep: Boolean = false
    ): Pair<RatchetWirePacket, VolatileChatMessage> {
        val state = activeSessions[sessionHandle]
            ?: throw IllegalStateException("Session handle $sessionHandle is invalid or destroyed")

        synchronized(state) {
            state.lastActivityEpochSec = System.currentTimeMillis() / 1000L
            var pqCt: ByteArray? = null
            var nextEk: ByteArray? = null
            var braidTriggered = false

            if (forcePqBraidStep || (state.sendCount > 0 && state.sendCount % PQ_BRAID_INTERVAL == 0L)) {
                state.prevSendCount = state.sendCount
                state.sendCount = 0L
                state.ratchetEpoch += 1
                state.pqBraidStepsCompleted += 1
                braidTriggered = true

                val newXPriv = X25519PrivateKeyParameters(csprng)
                state.dhLocalSk = newXPriv.encoded
                state.dhLocalPk = newXPriv.generatePublicKey().encoded
                val dhOut = computeX25519(state.dhLocalSk, state.dhPeerPk)

                val (ct, pqSs) = encapsulateMlKem1024(state.pqPeerEk)
                pqCt = ct

                val mlkemGen = MLKEMKeyPairGenerator()
                mlkemGen.init(MLKEMKeyGenerationParameters(csprng, MLKEMParameters.ml_kem_1024))
                val newKemKp = mlkemGen.generateKeyPair()
                state.pqLocalEk = (newKemKp.public as MLKEMPublicKeyParameters).encoded
                state.pqLocalDk = (newKemKp.private as MLKEMPrivateKeyParameters).encoded
                nextEk = state.pqLocalEk.copyOf()

                val (nextRk, nextCk) = kdfRootStep(state.rootKey, dhOut, pqSs)
                Arrays.fill(state.rootKey, 0.toByte())
                Arrays.fill(state.ckSend, 0.toByte())
                Arrays.fill(dhOut, 0.toByte())
                Arrays.fill(pqSs, 0.toByte())
                state.rootKey = nextRk
                state.ckSend = nextCk
            }

            val (nextCk, msgKey) = kdfChainStep(state.ckSend)
            Arrays.fill(state.ckSend, 0.toByte())
            state.ckSend = nextCk

            val msgNum = state.sendCount
            state.sendCount += 1L

            val ad = buildRatchetAssociatedData(
                version = PROTOCOL_VERSION,
                sessionIdHex = state.sessionIdHex,
                senderClassicalFpHex = state.localClassicalFpHex,
                senderPqFpHex = state.localPqFpHex,
                dhRatchetPub = state.dhLocalPk,
                ratchetEpoch = state.ratchetEpoch,
                msgNum = msgNum,
                prevChainLen = state.prevSendCount
            )
            val nonce = deriveMessageNonce(msgKey, msgNum)
            val ciphertext = try {
                aeadEncryptChaCha20Poly1305(msgKey, nonce, plaintextUtf8, ad)
            } finally {
                Arrays.fill(msgKey, 0.toByte())
            }

            val packet = RatchetWirePacket(
                version = PROTOCOL_VERSION,
                sessionIdHex = state.sessionIdHex,
                dhRatchetPublic = state.dhLocalPk.copyOf(),
                ratchetEpoch = state.ratchetEpoch,
                msgNum = msgNum,
                prevChainLen = state.prevSendCount,
                pqBraidCiphertext = pqCt,
                nextPqEncapsKey = nextEk,
                aeadCiphertext = ciphertext
            )

            val volatileMsg = VolatileChatMessage(
                messageId = nextMessageId.getAndIncrement(),
                isOutgoing = true,
                timestampEpochSec = state.lastActivityEpochSec,
                ratchetEpoch = state.ratchetEpoch,
                msgCounter = msgNum,
                pqBraidStepTriggered = braidTriggered,
                plaintextUtf8 = plaintextUtf8.copyOf(),
                deliveryStatus = MessageDeliveryStatus.DELIVERED_AUTHENTICATED
            )
            state.messages.add(volatileMsg)
            return Pair(packet, volatileMsg)
        }
    }

    /**
     * Decrypts and authenticates an incoming [RatchetWirePacket], enforcing:
     * - Protocol version & session ID binding
     * - Replay & duplicate counter rejection
     * - Out-of-order message key caching bounded to `MAX_SKIP = 64`
     * - Periodic `ML-KEM-1024` Braid decapsulation
     */
    fun decryptMessage(
        sessionHandle: Long,
        packet: RatchetWirePacket
    ): VolatileChatMessage {
        val state = activeSessions[sessionHandle]
            ?: throw IllegalStateException("Session handle $sessionHandle is invalid or destroyed")

        synchronized(state) {
            state.lastActivityEpochSec = System.currentTimeMillis() / 1000L
            if (packet.version != PROTOCOL_VERSION || packet.sessionIdHex != state.sessionIdHex) {
                throw SecurityException("RATCHET_VERSION_OR_SESSION_MISMATCH")
            }

            val msgKeyId = "${packet.dhRatchetPublic.toHex()}:${packet.msgNum}"
            if (state.consumedMessageKeys.contains(msgKeyId)) {
                throw SecurityException("REPLAY_OR_DUPLICATE_MESSAGE: Message $msgKeyId already consumed")
            }

            val ad = buildRatchetAssociatedData(
                version = packet.version,
                sessionIdHex = packet.sessionIdHex,
                senderClassicalFpHex = state.peerClassicalFpHex,
                senderPqFpHex = state.peerPqFpHex,
                dhRatchetPub = packet.dhRatchetPublic,
                ratchetEpoch = packet.ratchetEpoch,
                msgNum = packet.msgNum,
                prevChainLen = packet.prevChainLen
            )

            // 1. Check skipped message key cache for out-of-order arrival
            val skippedKey = state.skippedMessageKeys.remove(msgKeyId)
            if (skippedKey != null) {
                try {
                    val nonce = deriveMessageNonce(skippedKey, packet.msgNum)
                    val pt = aeadDecryptChaCha20Poly1305(skippedKey, nonce, packet.aeadCiphertext, ad)
                    state.consumedMessageKeys.add(msgKeyId)
                    val msg = VolatileChatMessage(
                        messageId = nextMessageId.getAndIncrement(),
                        isOutgoing = false,
                        timestampEpochSec = state.lastActivityEpochSec,
                        ratchetEpoch = packet.ratchetEpoch,
                        msgCounter = packet.msgNum,
                        pqBraidStepTriggered = packet.pqBraidCiphertext != null,
                        plaintextUtf8 = pt
                    )
                    state.messages.add(msg)
                    return msg
                } finally {
                    Arrays.fill(skippedKey, 0.toByte())
                }
            }

            // 2. If peer rotated their DH ratchet public key, advance receiving root & chain keys
            var braidStepReceived = false
            if (!packet.dhRatchetPublic.contentEquals(state.dhPeerPk)) {
                skipReceivingMessageKeys(state, packet.prevChainLen)
                state.dhPeerPk = packet.dhRatchetPublic.copyOf()
                state.ratchetEpoch = packet.ratchetEpoch
                state.recvCount = 0L

                val dhShared = computeX25519(state.dhLocalSk, state.dhPeerPk)
                val pqSs = if (packet.pqBraidCiphertext != null) {
                    braidStepReceived = true
                    state.pqBraidStepsCompleted += 1
                    val ss = decapsulateMlKem1024(state.pqLocalDk, packet.pqBraidCiphertext)
                    if (packet.nextPqEncapsKey != null) {
                        state.pqPeerEk = packet.nextPqEncapsKey.copyOf()
                    }
                    ss
                } else {
                    null
                }

                val (nextRk, nextCk) = kdfRootStep(state.rootKey, dhShared, pqSs)
                Arrays.fill(state.rootKey, 0.toByte())
                Arrays.fill(state.ckRecv, 0.toByte())
                Arrays.fill(dhShared, 0.toByte())
                pqSs?.let { Arrays.fill(it, 0.toByte()) }
                state.rootKey = nextRk
                state.ckRecv = nextCk
            }

            if (packet.msgNum < state.recvCount) {
                throw SecurityException("REPLAY_OR_PAST_COUNTER: msgNum=${packet.msgNum} < recvCount=${state.recvCount}")
            }

            // 3. Cache any skipped intermediate message keys up to packet.msgNum
            skipReceivingMessageKeys(state, packet.msgNum)

            // 4. Advance receiving chain key for the current message
            val (nextCk, msgKey) = kdfChainStep(state.ckRecv)
            Arrays.fill(state.ckRecv, 0.toByte())
            state.ckRecv = nextCk
            state.recvCount += 1L

            val plaintext = try {
                val nonce = deriveMessageNonce(msgKey, packet.msgNum)
                aeadDecryptChaCha20Poly1305(msgKey, nonce, packet.aeadCiphertext, ad)
            } finally {
                Arrays.fill(msgKey, 0.toByte())
            }

            state.consumedMessageKeys.add(msgKeyId)
            val msg = VolatileChatMessage(
                messageId = nextMessageId.getAndIncrement(),
                isOutgoing = false,
                timestampEpochSec = state.lastActivityEpochSec,
                ratchetEpoch = packet.ratchetEpoch,
                msgCounter = packet.msgNum,
                pqBraidStepTriggered = braidStepReceived,
                plaintextUtf8 = plaintext
            )
            state.messages.add(msg)
            return msg
        }
    }

    private fun skipReceivingMessageKeys(state: InternalRatchetState, untilMsgNum: Long) {
        if (untilMsgNum <= state.recvCount) return
        val gap = untilMsgNum - state.recvCount
        if (gap > MAX_SKIP || (state.skippedMessageKeys.size + gap) > MAX_SKIP) {
            throw SecurityException("MAX_SKIP_EXCEEDED: Out-of-order message gap $gap exceeds limit $MAX_SKIP")
        }
        val peerDhHex = state.dhPeerPk.toHex()
        while (state.recvCount < untilMsgNum) {
            val (nextCk, mk) = kdfChainStep(state.ckRecv)
            Arrays.fill(state.ckRecv, 0.toByte())
            state.ckRecv = nextCk
            state.skippedMessageKeys["$peerDhHex:${state.recvCount}"] = mk
            state.recvCount += 1L
        }
    }

    fun getVolatileMessages(sessionHandle: Long): List<VolatileChatMessage> {
        val state = activeSessions[sessionHandle] ?: return emptyList()
        synchronized(state) {
            return state.messages.toList()
        }
    }

    fun getSessionMetrics(sessionHandle: Long): SessionRatchetMetrics? {
        val state = activeSessions[sessionHandle] ?: return null
        synchronized(state) {
            return SessionRatchetMetrics(
                handle = state.handle,
                sessionIdHex = state.sessionIdHex,
                peerOnion = state.peerOnion,
                ratchetEpoch = state.ratchetEpoch,
                sendCount = state.sendCount,
                recvCount = state.recvCount,
                pqBraidStepsCompleted = state.pqBraidStepsCompleted,
                skippedKeysCached = state.skippedMessageKeys.size,
                volatileMessagesInRam = state.messages.size
            )
        }
    }

    fun findSessionHandleBySessionId(sessionIdHex: String): Long? {
        return activeSessions.values.firstOrNull {
            it.sessionIdHex.equals(sessionIdHex, ignoreCase = true)
        }?.handle
    }

    fun findSessionHandleByPeerOnion(peerOnion: String): Long? {
        return activeSessions.values
            .filter { it.peerOnion.equals(peerOnion, ignoreCase = true) }
            .maxByOrNull { it.handle }
            ?.handle
    }

    @Synchronized
    fun signPeerHelloTranscript(transcript: ByteArray): ByteArray? {
        val holder = activeIdentity ?: return null
        return signEd25519(holder.ed25519Sk, transcript)
    }

    /**
     * Destroys a single session handle and zeroizes all associated ratchet keys and chat messages.
     */
    fun destroySession(sessionHandle: Long) {
        activeSessions.remove(sessionHandle)?.wipeAndDestroy()
    }

    /**
     * Emergency Lock / Disconnect Operation (Section 5.4):
     * Immediately destroys all active sessions, pending handshakes, and volatile chat buffers.
     */
    @Synchronized
    fun emergencyWipeAllSessions(): Int {
        val count = activeSessions.size
        activeSessions.values.forEach { it.wipeAndDestroy() }
        activeSessions.clear()
        pendingInitiatorStates.values.forEach { it.zeroize() }
        pendingInitiatorStates.clear()
        if (NativeRustBridge.isNativeLibraryLoaded) {
            try {
                NativeRustBridge.nativeEmergencyWipeAllSessions()
            } catch (_: Throwable) {
            }
        }
        return count
    }

    @Synchronized
    fun purgeExpiredSessions(timeoutSeconds: Long): Int {
        val now = System.currentTimeMillis() / 1000L
        var purged = 0
        val iterator = activeSessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastActivityEpochSec >= timeoutSeconds) {
                entry.value.wipeAndDestroy()
                iterator.remove()
                purged++
            }
        }
        return purged
    }

    @Synchronized
    fun wipeLoadedIdentityFromRam() {
        emergencyWipeAllSessions()
        activeIdentity?.zeroize()
        activeIdentity = null
    }

    // =========================================================================
    // Cryptographic Helper Primitives (NIST FIPS 203, FIPS 204, RFC 7748, RFC 8032, RFC 8439, RFC 5869)
    // =========================================================================

    fun signEd25519(privateKey32: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey32, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verifyEd25519(publicKey32: ByteArray, message: ByteArray, signature64: ByteArray): Boolean {
        if (publicKey32.size != 32 || signature64.size != 64) return false
        return try {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey32, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature64)
        } catch (_: Exception) {
            false
        }
    }

    fun signMlDsa87(privateKeyEncoded: ByteArray, message: ByteArray): ByteArray {
        val privParams = MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_87, privateKeyEncoded, null)
        val signer = MLDSASigner()
        signer.init(true, privParams)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verifyMlDsa87(publicKeyEncoded: ByteArray, message: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val pubParams = MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_87, publicKeyEncoded)
            val verifier = MLDSASigner()
            verifier.init(false, pubParams)
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    fun encapsulateMlKem1024(peerEncapsKeyEncoded: ByteArray): Pair<ByteArray, ByteArray> {
        val pubParams = MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_1024, peerEncapsKeyEncoded)
        val gen = MLKEMGenerator(csprng)
        val secretWithEnc = gen.generateEncapsulated(pubParams)
        val ct = secretWithEnc.encapsulation.copyOf()
        val ss = secretWithEnc.secret.copyOf()
        secretWithEnc.destroy()
        return Pair(ct, ss)
    }

    fun decapsulateMlKem1024(localDecapsKeyEncoded: ByteArray, ciphertext: ByteArray): ByteArray {
        val privParams = MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_1024, localDecapsKeyEncoded)
        val extractor = MLKEMExtractor(privParams)
        return extractor.extractSecret(ciphertext)
    }

    fun computeX25519(localPrivateKey32: ByteArray, peerPublicKey32: ByteArray): ByteArray {
        val priv = X25519PrivateKeyParameters(localPrivateKey32, 0)
        val pub = X25519PublicKeyParameters(peerPublicKey32, 0)
        val agreement = X25519Agreement()
        agreement.init(priv)
        val shared = ByteArray(32)
        agreement.calculateAgreement(pub, shared, 0)
        if (shared.all { it == 0.toByte() }) {
            throw SecurityException("Invalid low-order X25519 point rejected")
        }
        return shared
    }

    fun deriveStaticPeerStatusKey32(peerX25519Pk: ByteArray, statusId: String): ByteArray? {
        val holder = activeIdentity ?: return null
        if (peerX25519Pk.size != 32) return null
        val dh = computeX25519(holder.x25519Sk, peerX25519Pk)
        return try {
            hkdfExpand(
                salt = statusId.toByteArray(Charsets.UTF_8),
                ikm = dh,
                info = "ZT2-STATUS-KEY-V1".toByteArray(Charsets.UTF_8),
                outLen = 32
            )
        } finally {
            Arrays.fill(dh, 0.toByte())
        }
    }

    fun hkdfExpand(salt: ByteArray, ikm: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        val out = ByteArray(outLen)
        hkdf.generateBytes(out, 0, outLen)
        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(key))
        hmac.update(data, 0, data.size)
        val out = ByteArray(hmac.macSize)
        hmac.doFinal(out, 0)
        return out
    }

    private fun kdfChainStep(chainKey32: ByteArray): Pair<ByteArray, ByteArray> {
        val msgKey = hmacSha256(chainKey32, byteArrayOf(0x01) + "ZT2-MSG-KEY-V1".toByteArray(Charsets.UTF_8))
        val nextCk = hmacSha256(chainKey32, byteArrayOf(0x02) + "ZT2-CHAIN-KEY-V1".toByteArray(Charsets.UTF_8))
        return Pair(nextCk, msgKey)
    }

    private fun kdfRootStep(
        rootKey32: ByteArray,
        dhSecret32: ByteArray,
        pqBraidSecret32: ByteArray?
    ): Pair<ByteArray, ByteArray> {
        val ikm = ByteBuffer.allocate(64)
            .put(dhSecret32)
            .put(pqBraidSecret32 ?: ByteArray(32))
            .array()
        val derived = hkdfExpand(rootKey32, ikm, "ZT2-PQ-RATCHET-STEP-V1".toByteArray(Charsets.UTF_8), 64)
        Arrays.fill(ikm, 0.toByte())
        val nextRk = derived.copyOfRange(0, 32)
        val nextCk = derived.copyOfRange(32, 64)
        Arrays.fill(derived, 0.toByte())
        return Pair(nextRk, nextCk)
    }

    private fun deriveMessageNonce(msgKey32: ByteArray, msgNum: Long): ByteArray {
        val base = hkdfExpand(
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

    fun aeadEncryptChaCha20Poly1305(
        key32: ByteArray,
        nonce12: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key32), 128, nonce12, associatedData))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    fun aeadDecryptChaCha20Poly1305(
        key32: ByteArray,
        nonce12: ByteArray,
        ciphertextWithTag: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, AEADParameters(KeyParameter(key32), 128, nonce12, associatedData))
            val out = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
            val len = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, out, 0)
            cipher.doFinal(out, len)
            out
        } catch (e: Exception) {
            throw SecurityException("AEAD_TAG_VERIFICATION_FAILED: Tampered ciphertext or wrong key", e)
        }
    }

    private fun buildRatchetAssociatedData(
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

    fun domainSha256(domain: String, parts: List<ByteArray>): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(domain.toByteArray(Charsets.UTF_8))
        for (part in parts) {
            md.update(ByteBuffer.allocate(4).putInt(part.size).array())
            md.update(part)
        }
        return md.digest()
    }

    private fun serializeIdentityHolder(h: PrivateIdentityHolder): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(0x5A544945) // "ZTIE" v2 with private Tor seed + pubkey
            writeByteArray(out, h.ed25519Sk)
            writeByteArray(out, h.ed25519Pk)
            writeByteArray(out, h.x25519Sk)
            writeByteArray(out, h.x25519Pk)
            writeByteArray(out, h.mlDsa87SkEncoded)
            writeByteArray(out, h.mlDsa87PkEncoded)
            writeByteArray(out, h.mlKem1024DkEncoded)
            writeByteArray(out, h.mlKem1024EkEncoded)
            writeByteArray(out, h.onionServiceSeed)
            writeByteArray(out, h.onionServicePub)
        }
        return baos.toByteArray()
    }

    private fun deserializeIdentityHolder(bytes: ByteArray): PrivateIdentityHolder {
        DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
            val magic = inp.readInt()
            require(magic == 0x5A544944 || magic == 0x5A544945) { "Invalid wrapped identity header" }
            val edSk = readByteArray(inp)
            val edPk = readByteArray(inp)
            val xSk = readByteArray(inp)
            val xPk = readByteArray(inp)
            val dsaSk = readByteArray(inp)
            val dsaPk = readByteArray(inp)
            val kemDk = readByteArray(inp)
            val kemEk = readByteArray(inp)
            return if (magic == 0x5A544945) {
                val onionSeed = readByteArray(inp)
                val onionPub = readByteArray(inp)
                PrivateIdentityHolder(
                    ed25519Sk = edSk,
                    ed25519Pk = edPk,
                    x25519Sk = xSk,
                    x25519Pk = xPk,
                    mlDsa87SkEncoded = dsaSk,
                    mlDsa87PkEncoded = dsaPk,
                    mlKem1024DkEncoded = kemDk,
                    mlKem1024EkEncoded = kemEk,
                    onionServiceSeed = onionSeed,
                    onionServicePub = onionPub
                )
            } else {
                // Legacy ZTID stored only the public key in the onionServiceSeed slot
                val legacyPub = readByteArray(inp)
                PrivateIdentityHolder(
                    ed25519Sk = edSk,
                    ed25519Pk = edPk,
                    x25519Sk = xSk,
                    x25519Pk = xPk,
                    mlDsa87SkEncoded = dsaSk,
                    mlDsa87PkEncoded = dsaPk,
                    mlKem1024DkEncoded = kemDk,
                    mlKem1024EkEncoded = kemEk,
                    onionServiceSeed = ByteArray(0),
                    onionServicePub = legacyPub
                )
            }
        }
    }

    private fun writeByteArray(out: DataOutputStream, arr: ByteArray) {
        out.writeInt(arr.size)
        out.write(arr)
    }

    private fun readByteArray(inp: DataInputStream): ByteArray {
        val len = inp.readInt()
        require(len in 1..32_768) { "Invalid key component length: $len" }
        return ByteArray(len).also { inp.readFully(it) }
    }

    private fun encodeBase32Lower(data: ByteArray): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(alphabet[(buffer shr bitsLeft) and 0x1F])
            }
        }
        return sb.toString()
    }
}

data class SessionRatchetMetrics(
    val handle: Long,
    val sessionIdHex: String,
    val peerOnion: String,
    val ratchetEpoch: Int,
    val sendCount: Long,
    val recvCount: Long,
    val pqBraidStepsCompleted: Int,
    val skippedKeysCached: Int,
    val volatileMessagesInRam: Int
)

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    val clean = trim()
    require(clean.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
