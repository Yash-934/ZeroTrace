# ZeroTrace Messenger 2.0 — System Architecture

## 1. Architectural Overview

ZeroTrace Messenger 2.0 is a serverless, peer-to-peer, strictly ephemeral one-to-one private messaging application for Android (API 26+). It eliminates central messaging servers, phone numbers, email identifiers, and persistent conversation databases.

```
+-----------------------------------------------------------------------+
|                        ANDROID APPLICATION LAYER                       |
|  Jetpack Compose UI (FLAG_SECURE) | CameraX QR Scanner | Diagnostics   |
+-----------------------------------+-----------------------------------+
                                    |
        Opaque Session Handles / Verified DTOs (No Raw Private Keys)
                                    |
+-----------------------------------v-----------------------------------+
|                   SECURITY & BRIDGE LAYER (Kotlin / JNI)               |
|  KeystoreManager (StrongBox/TEE AES-256-GCM Key Wrapping)              |
|  SessionService (Foreground Service for Active Ephemeral Sessions)     |
|  RustCoreBridge (Loads libzerotrace_core.so + Verified JVM Engine)     |
+-----------------------------------+-----------------------------------+
                                    |
                     JNI / UniFFI Boundary (Zeroized Buffers)
                                    |
+-----------------------------------v-----------------------------------+
|                     RUST SECURITY CORE (libzerotrace_core.so)          |
|  +-----------------------------+  +--------------------------------+  |
|  | Hybrid Identity Engine      |  | Post-Quantum Session & Ratchet |  |
|  | - Ed25519 + X25519          |  | - ZT-PQXDH-P2P-v1 Handshake    |  |
|  | - NIST FIPS 204 ML-DSA-87   |  | - Double Ratchet + ML-KEM-1024 |  |
|  | - NIST FIPS 203 ML-KEM-1024 |  |   Braid / SPQR Epoch Ratchet   |  |
|  +-----------------------------+  +--------------------------------+  |
|  +-----------------------------+  +--------------------------------+  |
|  | Volatile Memory Manager     |  | Tor / Onion Service Transport  |  |
|  | - Zeroize / ZeroizeOnDrop   |  | - Arti TorClient & Onion v3    |  |
|  | - Best-effort libc::mlock   |  | - Loopback / SOCKS5 Only       |  |
|  | - Emergency Session Wipe    |  | - Strict Fail-Closed Policy    |  |
|  +-----------------------------+  +--------------------------------+  |
+-----------------------------------------------------------------------+
```

## 2. Core Architectural Principles

1. **Serverless Peer-to-Peer Topology**:
   - No central user directory, no cloud database, no push notification servers (FCM is excluded), and no offline message queues.
   - Each participant hosts an application-controlled Tor v3 Onion Service (`<56-char-base32>.onion`).
   - Both peers must be online simultaneously to establish an authenticated session and exchange messages (`STRICT EPHEMERAL MODE`).

2. **Separation of Network Identity and Cryptographic Identity**:
   - A Tor `.onion` address only identifies a network endpoint; it is never trusted as proof of peer identity.
   - Every peer possesses a long-term hybrid identity consisting of:
     - Classical signing key: `Ed25519`
     - Classical key-agreement prekey: `X25519`
     - Post-quantum signing key: `ML-DSA-87` (NIST FIPS 204)
     - Post-quantum encapsulation key: `ML-KEM-1024` (NIST FIPS 203)

3. **Hardware-Backed Wrapping Hierarchy**:
   - Android Keystore (StrongBox Keymaster where hardware-supported, falling back to TEE Keymaster, or reporting software-only Keystore truthfully) generates a non-exportable `AES-256-GCM` master wrapping key (`zerotrace_identity_wrap_key_v2`).
   - Long-term hybrid identity secret keys are serialized in memory, encrypted under the Android Keystore `AES-256-GCM` key with an authenticated tag, and stored in app-private storage (`noBackupFilesDir`).
   - Private keys are never passed to Jetpack Compose UI components.

4. **Strict Ephemeral Memory Lifecycle**:
   - Conversation messages exist exclusively in volatile RAM within session-scoped buffers.
   - Disconnecting a session, pressing the Emergency Lock button, exceeding the configurable session inactivity timer, or terminating the process destroys all active session handles and zeroizes message buffers on a best-effort basis.

5. **Dual-Tier Execution & Build Architecture**:
   - **Native Rust Core (`rust-core/`)**: Implements the complete Rust crate (`libzerotrace_core.so`) using `fips203` (`ML-KEM-1024`), `fips204` (`ML-DSA-87`), `x25519-dalek`, `ed25519-dalek`, `chacha20poly1305`, `hkdf`, `sha2`, `zeroize`, and `arti-client`/`tor-hsservice`.
   - **JNI & Verified JVM Cryptographic Engine (`bridge/RustCoreBridge.kt`)**: Attempts `System.loadLibrary("zerotrace_core")` first. When built in environments lacking the `cargo-ndk` cross-compiler (such as cloud container sandboxes without `rustc`/`ndk`), the bridge transparently reports the native library status on the Security Dashboard and executes the identical protocol specification using BouncyCastle (`bcprov-jdk18on` NIST FIPS 203 `ML-KEM-1024` & FIPS 204 `ML-DSA-87` + `Ed25519` + `X25519` + `ChaCha20-Poly1305` + `HKDF-SHA256`) and fail-closed Tor SOCKS5/loopback transport. It never claims `libzerotrace_core.so` is loaded if running in JVM fallback mode.
