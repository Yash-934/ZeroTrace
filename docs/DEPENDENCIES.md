# ZeroTrace Messenger 2.0 — Dependency & Risk Matrix

## 1. Rust Security Core Dependencies (`rust-core/Cargo.toml`)

| Crate | Pinned Version | Purpose | License | Audit & Security Notes |
| :--- | :--- | :--- | :--- | :--- |
| `fips203` | `=0.4.3` | NIST FIPS 203 `ML-KEM-1024` key encapsulation | MIT / Apache-2.0 | Pure-Rust constant-time implementation aligned with final NIST FIPS 203 standard; passes NIST ACVP test vectors. Not yet formally audited by a third-party firm; documented in `LIMITATIONS.md`. |
| `fips204` | `=0.4.6` | NIST FIPS 204 `ML-DSA-87` digital signatures | MIT / Apache-2.0 | Pure-Rust constant-time implementation aligned with final NIST FIPS 204 standard; passes NIST ACVP test vectors. |
| `x25519-dalek` | `=2.0.1` | Classical `X25519` Diffie-Hellman | BSD-3-Clause | Audited (`dalek-cryptography`); constant-time scalar multiplication; integrates `zeroize`. |
| `ed25519-dalek` | `=2.1.1` | Classical `Ed25519` signatures | BSD-3-Clause | Audited (`dalek-cryptography`); strict signature verification enabled (`verify_strict`). |
| `chacha20poly1305` | `=0.10.1` | Authenticated Encryption (`AEAD`) | Apache-2.0 / MIT | RustCrypto AEAD crate audited by NCC Group. |
| `hkdf` | `=0.10.0` / `=0.12.4` | `HKDF-SHA256` key derivation | Apache-2.0 / MIT | RustCrypto standard RFC 5869 implementation. |
| `hmac` / `sha2` | `=0.12.1` / `=0.10.8` | `HMAC-SHA256` & `SHA-256` | Apache-2.0 / MIT | RustCrypto standard hash & MAC primitives. |
| `zeroize` | `=1.8.1` | Secure memory wiping (`ZeroizeOnDrop`) | Apache-2.0 / MIT | Uses volatile writes and compiler fences to prevent dead-store elimination. |
| `arti-client` | `=0.25.0` | Official Tor Project Rust client | MIT / Apache-2.0 | Official Tor Project implementation; active development; Onion Service v3 support via `tor-hsservice`. |
| `tor-hsservice` | `=0.25.0` | Tor v3 Onion Service hosting | MIT / Apache-2.0 | Official Tor Project crate for hosting hidden services in Rust. |
| `jni` | `=0.21.1` | Rust-to-Android JNI bindings | MIT / Apache-2.0 | Standard safe JNI wrapper for Android NDK targets. |

## 2. Android Application Dependencies (`gradle/libs.versions.toml`)

| Library | Version | Purpose | License | Risk Assessment |
| :--- | :--- | :--- | :--- | :--- |
| `org.bouncycastle:bcprov-jdk18on` | `1.79` | Standardized NIST FIPS 203 (`ML-KEM-1024`), FIPS 204 (`ML-DSA-87`), `X25519`, `Ed25519`, `ChaCha20-Poly1305` on JVM/Android | MIT | Actively maintained, industry-standard provider supporting final NIST FIPS 203/204 standards for JVM/Android execution and cross-verification. |
| `com.google.zxing:core` | `3.5.3` | Deterministic local QR generation & luminance frame decoding | Apache-2.0 | Zero network permissions; pure computational matrix barcode encoding/decoding. |
| `androidx.camera:camera-*` | `1.5.0` | CameraX lifecycle-bound QR scanning | Apache-2.0 | Official Android Jetpack library. |
| `androidx.compose.*` | BOM `2024.09.00` | Material 3 UI & state presentation | Apache-2.0 | Official Android Jetpack Compose toolkit. |
| `org.robolectric:robolectric` | `4.16.1` | Local JVM Android & security testing | MIT | Test-only dependency. |

## 3. Explicitly Excluded Dependencies
- **Firebase / Google Play Services Auth / Analytics**: Completely removed from `app/build.gradle.kts` to eliminate telemetry, clearnet background traffic, and proprietary cloud dependencies.
- **Retrofit / OkHttp / Cloud SDKs**: Removed from `app/build.gradle.kts` to enforce the strict no-clearnet-HTTP architecture.
- **Room SQLite Database**: Removed from `app/build.gradle.kts` to guarantee no persistent SQLite conversation database exists.
