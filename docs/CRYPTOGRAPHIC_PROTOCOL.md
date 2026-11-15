# ZeroTrace Messenger 2.0 — Cryptographic Protocol Specification (`ZT-PQXDH-P2P-v1`)

## 1. Normative References

1. **NIST FIPS 203**: Module-Lattice-Based Key-Encapsulation Mechanism Standard (`ML-KEM-1024`).
2. **NIST FIPS 204**: Module-Lattice-Based Digital Signature Standard (`ML-DSA-87`).
3. **RFC 7748**: Elliptic Curves for Security (`X25519`).
4. **RFC 8032**: Edwards-Curve Digital Signature Algorithm (`Ed25519`).
5. **RFC 8439**: ChaCha20 and Poly1305 for IETF Protocols (`ChaCha20-Poly1305`).
6. **RFC 5869**: HMAC-based Extract-and-Expand Key Derivation Function (`HKDF-SHA256`).
7. **Signal Specifications**: PQXDH, Double Ratchet, and SPQR / ML-KEM Braid concepts adapted for serverless P2P connection establishment.

> **Important Security Notice**: Because standard Signal PQXDH assumes an asynchronous prekey server that stores one-time prekey bundles while a recipient is offline, ZeroTrace 2.0 defines an explicit serverless synchronous variant (`ZT-PQXDH-P2P-v1`) where both online peers exchange freshly signed ephemeral classical + ML-KEM-1024 prekey bundles directly over their Tor Onion Service circuit, authenticated against the out-of-band QR pairing artifact. This serverless adaptation has not undergone an external third-party cryptographic audit and is isolated in `handshake` and `ratchet` modules.

---

## 2. Cryptographic Primitives

| Function | Algorithm | Key / Output Size | Standard |
| :--- | :--- | :--- | :--- |
| Classical KEM / DH | `X25519` | 32-byte public, 32-byte shared secret | RFC 7748 |
| Post-Quantum KEM | `ML-KEM-1024` | 1568-byte ek, 1568-byte ct, 32-byte ss | NIST FIPS 203 |
| Classical Signature | `Ed25519` | 32-byte pk, 64-byte sig | RFC 8032 |
| Post-Quantum Signature | `ML-DSA-87` | 2592-byte pk, 4627-byte sig | NIST FIPS 204 |
| Key Derivation | `HKDF-SHA256` | 32-byte PRK / OKM | RFC 5869 |
| Message Authentication | `HMAC-SHA256` | 32-byte tag | RFC 2104 |
| Symmetric AEAD | `ChaCha20-Poly1305` | 32-byte key, 12-byte nonce, 16-byte tag | RFC 8439 |

---

## 3. Dual Identity and Fingerprint Computation

Each device generates an identity tuple:
- $IK_{ed} = (pk_{ed}, sk_{ed}) \leftarrow \text{Ed25519.KeyGen}()$
- $IK_{x} = (pk_{x}, sk_{x}) \leftarrow \text{X25519.KeyGen}()$
- $IK_{mldsa} = (pk_{mldsa}, sk_{mldsa}) \leftarrow \text{ML-DSA-87.KeyGen}()$
- $IK_{mlkem} = (pk_{mlkem}, sk_{mlkem}) \leftarrow \text{ML-KEM-1024.KeyGen}()$

### Domain-Separated Fingerprints
- **Classical Identity Fingerprint**:
  $$FP_{classical} = \text{SHA-256}(\texttt{"ZT2-CLASSICAL-ID-V1"} \parallel pk_{ed} \parallel pk_{x})$$
- **Post-Quantum Identity Fingerprint**:
  $$FP_{pq} = \text{SHA-256}(\texttt{"ZT2-PQ-ID-V1"} \parallel pk_{mldsa} \parallel pk_{mlkem})$$
- **Combined Safety Comparison Number**:
  Formatted in 8 groups of 5 decimal digits or hex blocks for side-by-side human verification during QR pairing.

---

## 4. Authenticated QR Pairing Format (`ZT2-QR-v1`)

To fit reliably within a high-density QR code that scans rapidly on mobile cameras while authenticating both classical and post-quantum identities, the QR payload uses a deterministic pipe-delimited canonical structure signed with `Ed25519` and binding the `ML-DSA-87` and `ML-KEM-1024` SHA-256 commitments:

```
ZT2|v1|<onion_address>|<classical_fp_hex>|<pq_fp_hex>|<ed25519_pk_b64>|<x25519_pk_b64>|<expiry_unix_sec>|<nonce_hex>|<capabilities>|<ed25519_sig_b64>
```

1. **Validation Rules**:
   - Protocol prefix must equal `ZT2` and version must equal `v1`.
   - `<onion_address>` must be a valid 56-character base32 v3 `.onion` address (`[a-z2-7]{56}\.onion`) or loopback test endpoint in diagnostic mode.
   - `<expiry_unix_sec>` must be strictly greater than current Unix time and no more than 15 minutes (`900` seconds) in the future.
   - `<nonce_hex>` must be 32 hex characters (128-bit CSPRNG nonce).
   - `<classical_fp_hex>` must match $\text{SHA-256}(\texttt{"ZT2-CLASSICAL-ID-V1"} \parallel pk_{ed} \parallel pk_{x})$.
   - `<ed25519_sig_b64>` must verify over the canonical byte prefix `ZT2-QR-AUTH-V1|<all_preceding_fields>`.
2. **Full PQ Key Exchange Upon Circuit Connection**:
   - Once the Tor circuit opens between the paired peers, the full `ML-DSA-87` public key (2592 bytes) and `ML-KEM-1024` public key (1568 bytes) are transmitted in the `ClientHello` / `ServerHello` frames and immediately verified against the QR-pinned `<pq_fp_hex>` commitment before signature verification proceeds. If $\text{SHA-256}(\texttt{"ZT2-PQ-ID-V1"} \parallel pk_{mldsa} \parallel pk_{mlkem}) \neq FP_{pq}$, the handshake aborts immediately with `ERR_PQ_IDENTITY_MISMATCH`.

---

## 5. Serverless Hybrid Post-Quantum Handshake (`ZT-PQXDH-P2P-v1`)

Let Alice ($A$) be the initiator and Bob ($B$) be the responder over an established Tor Onion Service stream:

1. **Prekey & Ephemeral Generation**:
   - Alice generates fresh ephemeral $EK_{A,x} \leftarrow \text{X25519.KeyGen}()$ and ephemeral $EK_{A,pq} \leftarrow \text{ML-KEM-1024.KeyGen}()$, plus 32-byte random nonce $N_A$.
   - Alice signs $(\texttt{"ZT2-HS-INIT-V1"} \parallel N_A \parallel pk_{EK_{A,x}} \parallel pk_{EK_{A,pq}} \parallel FP_{B})$ using both $sk_{A,ed}$ (`Ed25519`) and $sk_{A,mldsa}$ (`ML-DSA-87`).
2. **Responder Encapsulation**:
   - Bob verifies Alice's protocol version, pairing nonce, pinned $FP_{A,classical}$ and $FP_{A,pq}$, and both `Ed25519` and `ML-DSA-87` signatures.
   - Bob generates fresh ephemeral $EK_{B,x} \leftarrow \text{X25519.KeyGen}()$ and random nonce $N_B$.
   - Bob encapsulates against Alice's static PQ key and Alice's ephemeral PQ key:
     - $(ct_1, ss_{pq1}) \leftarrow \text{ML-KEM-1024.Encaps}(pk_{A,mlkem})$
     - $(ct_2, ss_{pq2}) \leftarrow \text{ML-KEM-1024.Encaps}(pk_{EK_{A,pq}})$
   - Bob computes classical X25519 agreements:
     - $dh_1 = \text{X25519}(sk_{B,x}, pk_{A,x})$
     - $dh_2 = \text{X25519}(sk_{EK_{B,x}}, pk_{A,x})$
     - $dh_3 = \text{X25519}(sk_{B,x}, pk_{EK_{A,x}})$
     - $dh_4 = \text{X25519}(sk_{EK_{B,x}}, pk_{EK_{A,x}})$
   - Bob signs the response transcript using both $sk_{B,ed}$ and $sk_{B,mldsa}$.
3. **Master Root Key Derivation**:
   $$IKM = dh_1 \parallel dh_2 \parallel dh_3 \parallel dh_4 \parallel ss_{pq1} \parallel ss_{pq2}$$
   $$Salt = \text{SHA-256}(\texttt{"ZT2-PQXDH-SALT-V1"} \parallel N_A \parallel N_B \parallel TH)$$
   $$(RK_0, CK_{send,0}, CK_{recv,0}) = \text{HKDF-SHA256}(Salt, IKM, \texttt{"ZT2-HYBRID-ROOT-V1"}, 96)$$

---

## 6. Hybrid Double Ratchet + Periodic ML-KEM-1024 Braid

1. **Symmetric Chain Step**:
   - For each message $n$, the sending or receiving chain key $CK_n$ advances via HMAC-SHA256:
     - $MK_n = \text{HMAC-SHA256}(CK_n, \texttt{0x01} \parallel \texttt{"ZT2-MSG-KEY"})$
     - $CK_{n+1} = \text{HMAC-SHA256}(CK_n, \texttt{0x02} \parallel \texttt{"ZT2-CHAIN-KEY"})$
   - $CK_n$ is immediately zeroized. $MK_n$ is zeroized immediately after AEAD encryption/decryption.
2. **Classical DH + Post-Quantum Braid Ratchet Step**:
   - Classical X25519 DH ratchet steps occur on direction turns.
   - Every $K = 8$ ratchet turns (or on explicit re-key request), an `ML-KEM-1024` encapsulation step (`PQ_BRAID_STEP`) mixes a fresh 32-byte post-quantum shared secret $ss_{braid}$ into the Root Key:
     $$RK_{next}, CK_{next} = \text{HKDF-SHA256}(RK_{curr}, dh_{ratchet} \parallel ss_{braid}, \texttt{"ZT2-PQ-RATCHET-STEP-V1"}, 64)$$
3. **Deterministic Nonce & Associated Data**:
   - A unique 12-byte nonce is derived per message key via $\text{HKDF-SHA256}(MK_n, \texttt{"ZT2-NONCE-V1"}, 12)$ XORed with the 64-bit big-endian message counter. Because $MK_n$ is never reused, `(MK_n, Nonce)` is strictly unique.
   - Associated Data ($AD$) binds: `protocol_version || session_id || sender_classical_fp || sender_pq_fp || ratchet_epoch || msg_counter || prev_chain_length`.
