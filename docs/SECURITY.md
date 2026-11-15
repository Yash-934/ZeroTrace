# ZeroTrace Messenger 2.0 — Security Controls & Hardening

## 1. Android Keystore & Hardware Wrapping (`KeystoreManager.kt` / `StrongBoxManager.kt`)

- **Key Generation**:
  - Generates an `AES-256-GCM` key in `"AndroidKeyStore"` with `KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT`.
  - Sets `setRandomizedEncryptionRequired(true)`, `setBlockModes(KeyProperties.BLOCK_MODE_GCM)`, and `setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)`.
- **StrongBox Detection & Fallback**:
  - On API 28+, checks `PackageManager.FEATURE_STRONGBOX_KEYSTORE` and attempts generation with `setIsStrongBoxBacked(true)`.
  - If `StrongBoxUnavailableException` or provider failure occurs, falls back to standard TEE-backed Android Keystore (`setIsStrongBoxBacked(false)`).
  - Queries `KeyFactory` / `SecretKeyFactory` -> `KeyInfo.securityLevel` (API 31+) or `KeyInfo.isInsideSecureHardware` to report the **true** hardware backing state (`STRONGBOX`, `TEE_HARDWARE`, or `SOFTWARE_ONLY`) on the Security Dashboard.
- **Why Hybrid PQ Keys Are Wrapped Rather Than Generated Inside Keystore**:
  - Android Keystore and StrongBox hardware security modules do not natively support NIST FIPS 203 `ML-KEM-1024` or FIPS 204 `ML-DSA-87` key generation across Android devices. Therefore, PQ keys are generated via the OS CSPRNG (`getrandom` / `SecureRandom`) and immediately wrapped at rest under the Keystore `AES-256-GCM` key.

## 2. Volatile Memory & Zeroization

- **Rust Layer**:
  - Secret keys, shared secrets, root keys, chain keys, and message keys implement `zeroize::Zeroize` and `ZeroizeOnDrop`.
  - Best-effort `libc::mlock` is attempted on sensitive session key pages within `RLIMIT_MEMLOCK` constraints.
- **Kotlin / JVM Boundary**:
  - Sensitive byte arrays (`ByteArray`) and char buffers (`CharArray`) are explicitly overwritten with `0x00` (`Arrays.fill(buf, 0.toByte())`) in `finally` blocks immediately after cryptographic operations.
  - Ephemeral chat messages are held inside `VolatileSessionMemory`, which overwrites UTF-8 payload byte arrays upon session termination or manual Lock.

## 3. Window, Screenshot, Clipboard & Backup Hardening

- **`FLAG_SECURE`**: Enabled unconditionally on the main `Window` via `SecureWindowManager.enableSecureWindow(window)` to block screenshots, screen recording, and recent-apps task switcher thumbnails.
- **Backup Exclusions**:
  - `android:allowBackup="false"` in `AndroidManifest.xml`.
  - `backup_rules.xml` and `data_extraction_rules.xml` explicitly exclude all domains (`root`, `file`, `database`, `sharedpref`, `external`) from cloud backup and device-to-device transfer.
- **Clipboard Hygiene**:
  - Copying `.onion` addresses or safety fingerprints marks the `ClipData` with `ClipDescription.EXTRA_IS_SENSITIVE = true` (API 33+) and schedules an automatic clipboard wipe after 60 seconds.
- **Notification Privacy**:
  - The foreground service notification (`NotificationCompat.VISIBILITY_SECRET`) displays only generic connectivity status (`"ZeroTrace Ephemeral Circuit Active"`) and never contains peer names or message previews.
