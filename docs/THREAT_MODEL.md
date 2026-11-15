# ZeroTrace Messenger 2.0 — Threat Model

## 1. Security Objectives

ZeroTrace Messenger is engineered to protect one-to-one ephemeral text conversations against:
- **Passive Global Network Adversaries (Harvest-Now, Decrypt-Later)**: Attackers recording encrypted traffic today in hopes of decrypting it later with a cryptographically relevant quantum computer (CRQC).
- **Active Network / Tor Exit / Rendezvous Adversaries**: Attackers attempting to impersonate peers, inject packets, downgrade protocol versions, or replay captured frames.
- **Server-Side Compromise / Subpoena**: Eliminated by design because no central messaging server, account registry, or cloud backup exists.
- **Opportunistic Post-Session Physical Inspection**: Best-effort protection against casual inspection after session termination via RAM-only message storage, explicit buffer zeroization, `FLAG_SECURE`, and strict Android backup exclusions.

## 2. Adversary Capabilities Considered

| Adversary Class | Capabilities | Mitigation in ZeroTrace 2.0 |
| :--- | :--- | :--- |
| **Quantum Network Eavesdropper** | Captures ciphertext and handshake transcripts; possesses CRQC capable of breaking X25519 and Ed25519. | Hybrid key encapsulation (`X25519` + `ML-KEM-1024`) and dual signatures (`Ed25519` + `ML-DSA-87`) combined via domain-separated `HKDF-SHA256`. |
| **Malicious Tor HSDir / Rendezvous Node** | Observes `.onion` descriptor lookups or attempts MITM on circuit establishment. | Out-of-band authenticated QR pairing (`ZT2` binary/Base64URL envelope) binds both classical and PQ identity fingerprints plus a random pairing nonce. |
| **Active Packet Replay / Reordering Attacker** | Re-sends, duplicates, drops, or reorders encrypted frames on an active circuit. | Per-message monotonic sequence numbers, bounded skipped-key cache (`MAX_SKIP = 64`), immediate deletion of consumed message keys, and AEAD associated-data header binding. |
| **Identity Substitution Attacker** | Attempts to replace an existing contact's key material using the same display alias or `.onion` address. | Explicit identity pinning (`ContactVerificationState`). Any key mismatch triggers a blocking Security Alert and prohibits silent overwrite. |
| **Local Clearnet Leak / Traffic Fallback** | Blocks Tor bootstrap to force the app to connect over clearnet TCP/UDP/HTTPS. | Fail-closed transport state machine. Socket creation is restricted to loopback (`127.0.0.1`) Tor proxy/Arti circuit streams. Direct public IP connections are rejected. |

## 3. Explicit Non-Goals & Out-of-Scope Threats

ZeroTrace Messenger **never** makes unsupported claims of absolute invulnerability:

1. **Active Endpoint / Kernel Compromise (e.g., Pegasus-class mercenary spyware)**:
   - If the Android OS kernel, SurfaceFlinger, or input method (IME keyboard) is compromised while a user is typing or reading a message, the attacker can capture plaintext before encryption or after decryption. No application-layer cryptography can prevent this.
2. **Advanced Physical Forensics on Powered-On Unlocked Devices (e.g., Cellebrite UFED live RAM extraction)**:
   - While ZeroTrace never writes chat history to SQLite/flash storage and zeroizes buffers on disconnect, live RAM acquisition while a session is open can recover active buffers or JVM/ART heap copies before garbage collection.
3. **Global Traffic Correlation / ISP Timing Analysis**:
   - Tor Onion Services conceal IP addresses from peers, but Tor is a low-latency anonymity network and does not defeat a global passive adversary monitoring both endpoints' ISP connections simultaneously.
4. **Guaranteed NAND Flash Erasure**:
   - When deleting local wrapped identity or contact files, Android flash translation layers (FTL) and wear-leveling prevent guaranteed physical block erasure without full-disk encryption key destruction.
