# ZeroTrace Messenger 2.0 — Privacy Policy & Data Handling

## 1. Zero Personal Identifiers
ZeroTrace Messenger does not collect, request, transmit, or store:
- Phone numbers or SIM identifiers (`READ_PHONE_STATE` is never requested).
- Email addresses or Google accounts.
- Device Contacts (`READ_CONTACTS` is never requested).
- Fine or coarse GPS location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` are never requested).
- Advertising IDs, analytics events, crash telemetry, or Firebase tokens.

## 2. Ephemeral RAM-Only Conversations
- All chat messages exist strictly in volatile application memory (`RAM`) for the duration of the active peer-to-peer session.
- There is no SQLite message database, no Room message table, and no disk-backed message cache.
- When either peer disconnects, the session times out, the user taps **Emergency Lock**, or the Android OS reclaims the process, the conversation history is irretrievably cleared.

## 3. Local Encrypted Storage Scope
The only data written to app-private `noBackupFilesDir` is:
1. **Wrapped Device Identity (`identity_envelope_v2.bin`)**: Encrypted with Android Keystore `AES-256-GCM`. Can be permanently wiped at any time from Settings -> **Delete Local Identity**.
2. **Wrapped Verified Contacts (`contacts_envelope_v2.bin`)**: Contains only user-verified peer `.onion` addresses and pinned classical/PQ public keys encrypted under the Android Keystore `AES-256-GCM` key.
3. **Minimal User Preferences**: Language (`en`/`hi`), inactivity timeout duration, and Tor SOCKS/bridge configuration.

## 4. Permissions Justification
- `android.permission.INTERNET`: Required strictly for local Tor Onion Service circuit bootstrapping. All clearnet direct peer connections are blocked.
- `android.permission.CAMERA`: Requested at runtime solely when scanning a peer's QR pairing code.
- `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` / `POST_NOTIFICATIONS`: Used to keep the active ephemeral Tor session alive while the user is actively engaged in a live session.
