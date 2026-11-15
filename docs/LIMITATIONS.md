# ZeroTrace Messenger 2.0 — Known Security & Engineering Limitations

In accordance with ZeroTrace's strict engineering transparency policy, all architectural, cryptographic, and platform limitations are documented below.

## 1. Cryptographic & Protocol Limitations

1. **No Independent Third-Party Cryptographic Audit**:
   - While ZeroTrace uses standardized algorithms (NIST FIPS 203 `ML-KEM-1024`, NIST FIPS 204 `ML-DSA-87`, `X25519`, `Ed25519`, `ChaCha20-Poly1305`, `HKDF-SHA256`), the serverless peer-to-peer composition (`ZT-PQXDH-P2P-v1`) and periodic `ML-KEM-1024` Braid ratchet integration have **not** been independently audited by an external cryptographic security firm.
2. **SPQR / Triple Ratchet Standardization Status**:
   - Signal's Sparse Post-Quantum Ratchet (SPQR) / ML-KEM Braid does not yet have a single standalone, universally standardized IETF RFC or drop-in no-std Rust crate for serverless P2P streams. ZeroTrace implements a well-defined Hybrid Double Ratchet augmented with periodic `ML-KEM-1024` encapsulation epochs (`PQ_BRAID_STEP`) and explicitly marks this construction as **Experimental / Unaudited** in the Security Dashboard and protocol docs.

## 2. Memory & Android Runtime Limitations

1. **JVM / ART Garbage Collection Copies**:
   - When data crosses the JNI boundary into Kotlin (`String` for Compose UI rendering or `ByteArray` in JVM memory), the Android Runtime (ART) copying garbage collector may relocate objects in RAM without zeroizing the old physical memory pages.
   - ZeroTrace minimizes this by keeping private keys wrapped or inside session handles, using `ByteArray`/`CharArray` with explicit `fill(0)` cleanup wherever possible, and destroying references immediately on disconnect, but **cannot guarantee zero residual plaintext fragments in unallocated ART heap pages** against physical live-RAM forensics.
2. **`mlock` Constraints on Android**:
   - Unprivileged Android app processes have a strict `RLIMIT_MEMLOCK` quota (typically 64 KB) and cannot prevent kernel-level zRAM compression or full-memory hibernation snapshots if the OS kernel is modified or rooted.
3. **Android Keystore Algorithm Support**:
   - Hardware StrongBox and TEE Keymaster modules do not natively generate `ML-KEM-1024` or `ML-DSA-87` keys. ZeroTrace wraps the serialized PQ + classical identity key bundle using a hardware-backed `AES-256-GCM` Keystore key. During active identity signing or handshake decapsulation, the unwrapped key material briefly resides in volatile application RAM before zeroization.

## 3. Networking & Tor Limitations

1. **Simultaneous Online Requirement**:
   - Because ZeroTrace operates in `STRICT EPHEMERAL MODE` with zero central servers or store-and-forward relays, both peers must be online with active Tor circuits simultaneously to pair or exchange messages.
2. **Tor Onion Service Latency & Background Execution**:
   - Establishing a 6-hop Tor v3 Onion Service rendezvous circuit typically takes 10–45 seconds.
   - Android OEM battery killers may terminate background foreground services despite `START_STICKY` and persistent notifications. When the process is killed, the `.onion` endpoint immediately goes offline and all volatile chat state is lost.
3. **Container Build Environment Constraints**:
   - In build environments where `cargo-ndk` or an external Tor daemon (`127.0.0.1:9050`) is not installed at build/test time, the app compiles cleanly, runs its full NIST PQ + Double Ratchet + Loopback Onion transport verification suite on the JVM (`bcprov-jdk18on`), and truthfully displays `[JVM_VERIFIED_FALLBACK]` / `[LOOPBACK_OR_SOCKS5]` on the Security Dashboard rather than faking a native `.so` load or live Tor network circuit.
