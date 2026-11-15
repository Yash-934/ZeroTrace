# ZeroTrace Messenger 2.0

> **Private Communication. No Central Server. No Persistent Conversations.**

ZeroTrace Messenger 2.0 is a native Android one-to-one ephemeral messenger engineered around **hybrid post-quantum cryptography**, **Tor Onion Services**, **Android Keystore hardware-backed key wrapping**, and **volatile RAM-only session storage**.

---

## 1. Key Security Architecture

| Layer | Implementation Details |
| :--- | :--- |
| **Classical Cryptography** | `X25519` (RFC 7748) key agreement & `Ed25519` (RFC 8032) identity signatures |
| **Post-Quantum Cryptography** | Standardized **NIST FIPS 203 `ML-KEM-1024`** (1568B EK / 1568B CT) & **NIST FIPS 204 `ML-DSA-87`** (2592B PK / 4627B Sig) |
| **Initial Handshake** | `ZT-PQXDH-P2P-v1`: 4-way `X25519` + 2-way `ML-KEM-1024` hybrid key agreement authenticated with dual `Ed25519` + `ML-DSA-87` signatures and bound to the out-of-band QR pairing nonce |
| **Continuous Ratchet** | Hybrid Double Ratchet (`HMAC-SHA256` chains + `ChaCha20-Poly1305` AEAD) + periodic `ML-KEM-1024` Braid encapsulation every 8 messages (`MAX_SKIP = 64` out-of-order cache, strict replay rejection) |
| **Network Transport** | Fail-closed Tor v3 `.onion` routing (Arti in `rust-core/` + SOCKS5 `127.0.0.1:9050` / Local `127.0.0.1` Loopback Onion Verification Circuit). Zero clearnet IP/HTTP fallback |
| **Device Hardening** | Android Keystore `AES-256-GCM` wrapping (StrongBox/TEE detection), `FLAG_SECURE`, `android:allowBackup="false"`, `VISIBILITY_SECRET` foreground notification, clipboard auto-scrubbing, and one-tap **Emergency Lock / Wipe** |

---

## 2. Project Structure

```
.
├── app/src/main/java/
│   ├── com/example/MainActivity.kt
│   └── com/zerotrace/
│       ├── bridge/RustCoreBridge.kt          # JNI loader + FIPS 203/204 + Ratchet engine
│       ├── network/SessionService.kt         # Foreground Service & Fail-Closed Tor State Machine
│       ├── security/
│       │   ├── KeystoreManager.kt            # AES-256-GCM wrapping in noBackupFilesDir
│       │   ├── StrongBoxManager.kt           # Honest StrongBox / TEE / Software detection
│       │   ├── SecureWindowManager.kt        # FLAG_SECURE, clipboard scrub & integrity checks
│       │   └── BackupPolicy.kt               # Runtime allowBackup=false audit
│       └── ui/
│           ├── onboarding/                   # Splash & Mandatory Limitations Acceptance
│           ├── identity/                     # Identity Creation & My Identity / Signed QR
│           ├── pairing/                      # CameraX ZXing QR Scanner & Fingerprint Verification
│           ├── contacts/                     # Home Console & Verified Contacts
│           ├── chat/                         # Volatile RAM-only E2EE Chat Screen
│           ├── dashboard/                    # Real-Time Security Diagnostics Dashboard
│           └── settings/                     # Bilingual (EN/HI), Timeouts, Transport & Key Wipe
├── rust-core/                                # Native Rust Security Core (libzerotrace_core.so)
│   ├── Cargo.toml
│   ├── Cargo.lock
│   ├── src/{identity,handshake,crypto,network,session,memory,storage,ffi,lib.rs}
│   └── fuzz/                                 # cargo-fuzz targets for QR & wire framing
├── docs/                                     # Threat Model, Protocol Spec, Security & Limitations
└── tests/                                    # Standalone Rust & Android JVM/Robolectric tests
```

---

## 3. Building & Testing Instructions

### 3.1 Build and Test Android Application (Gradle)

```bash
# Run full cryptographic, handshake, ratchet, fail-closed network, and Robolectric tests:
gradle :app:testDebugUnitTest

# Assemble the Debug APK:
gradle :app:assembleDebug
```

### 3.2 Compile Native Rust Core (`libzerotrace_core.so`) on a Workstation with Rust & Android NDK

When building on a workstation with `rustup`, `cargo-ndk`, and the Android NDK installed:

```bash
# 1. Add Android targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# 2. Run Rust unit tests
cargo test --manifest-path rust-core/Cargo.toml

# 3. Cross-compile libzerotrace_core.so into app/src/main/jniLibs/
cargo ndk \
  -t arm64-v8a \
  -t armeabi-v7a \
  -t x86_64 \
  -o app/src/main/jniLibs \
  build --release --manifest-path rust-core/Cargo.toml

# 4. Run cargo-fuzz on QR and wire framing parsers (requires nightly Rust)
cargo +nightly fuzz run --manifest-path rust-core/fuzz/Cargo.toml fuzz_qr_and_framing -- -max_total_time=60
```

---

## 4. End-to-End Testing Between Two Physical Android Devices

1. Install the debug APK on **Device A** and **Device B**.
2. On both devices, complete **Onboarding** (accepting security limitations) and tap **Generate Protected Identity**.
3. For physical two-device Tor routing:
   - Ensure an active Tor SOCKS5/Hidden Service daemon (or embedded Arti `libzerotrace_core.so` build) is active on `127.0.0.1:9050`, and select **External Tor Daemon SOCKS5 (127.0.0.1:9050)** in **Settings**.
   - For single-device or emulator cryptographic verification without an external Tor daemon, keep **Local Loopback Onion Circuit (127.0.0.1 Peer Verification)** enabled in **Settings**.
4. On **Device A**, open **Add Contact -> Display My QR** (or **My QR Code** on Home).
5. On **Device B**, open **Add Contact -> Scan Peer QR** and scan Device A's QR code using the CameraX scanner (or tap **Scan Loopback Peer QR (ZT2|v1)** for single-device loopback testing).
6. Compare both the **Classical (`Ed25519` + `X25519`)** and **Post-Quantum (`ML-DSA-87` + `ML-KEM-1024`)** 40-digit safety numbers side-by-side, check the verification box, and tap **Confirm & Save Verified Contact**.
7. Tap **Start E2EE Session** on the verified contact card to execute the `ZT-PQXDH-P2P-v1` handshake and enter the volatile RAM-only chat screen.
8. Send messages, trigger a manual **PQ Step** (`ML-KEM-1024` Braid epoch re-key), and tap **Wipe** or **Secure Lock** to verify that all volatile messages are immediately erased from RAM.
