//! Domain-Separated Key Derivation (`HKDF-SHA256` & `HMAC-SHA256`)

use crate::memory::LockedSecretBuffer;
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

/// Computes domain-separated SHA-256 digest: `SHA-256(domain || parts...)`
pub fn domain_sha256(domain: &[u8], parts: &[&[u8]]) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(domain);
    for part in parts {
        hasher.update(&(part.len() as u32).to_be_bytes());
        hasher.update(part);
    }
    hasher.finalize().into()
}

/// Derives `N` bytes using RFC 5869 `HKDF-SHA256(salt, ikm, info)`.
pub fn hkdf_sha256<const N: usize>(
    salt: &[u8],
    ikm: &[u8],
    info: &[u8],
) -> LockedSecretBuffer<N> {
    let hk = Hkdf::<Sha256>::new(Some(salt), ikm);
    let mut okm = [0u8; N];
    hk.expand(info, &mut okm)
        .expect("HKDF output length N <= 255 * 32 bytes");
    LockedSecretBuffer::new(okm)
}

/// Computes symmetric chain ratchet step: `(next_chain_key, message_key)` from `current_chain_key`.
pub fn kdf_chain_step(
    chain_key: &[u8; 32],
) -> (LockedSecretBuffer<32>, LockedSecretBuffer<32>) {
    let mut mac_msg = HmacSha256::new_from_slice(chain_key).expect("HMAC key size valid");
    mac_msg.update(&[0x01]);
    mac_msg.update(b"ZT2-MSG-KEY-V1");
    let msg_key: [u8; 32] = mac_msg.finalize().into_bytes().into();

    let mut mac_next = HmacSha256::new_from_slice(chain_key).expect("HMAC key size valid");
    mac_next.update(&[0x02]);
    mac_next.update(b"ZT2-CHAIN-KEY-V1");
    let next_chain_key: [u8; 32] = mac_next.finalize().into_bytes().into();

    (
        LockedSecretBuffer::new(next_chain_key),
        LockedSecretBuffer::new(msg_key),
    )
}

/// Mixes a DH shared secret and an optional ML-KEM-1024 Braid shared secret into the Root Key.
pub fn kdf_root_step(
    root_key: &[u8; 32],
    dh_secret: &[u8; 32],
    pq_braid_secret: Option<&[u8; 32]>,
) -> (LockedSecretBuffer<32>, LockedSecretBuffer<32>) {
    let mut ikm = Vec::with_capacity(64);
    ikm.extend_from_slice(dh_secret);
    if let Some(pq_ss) = pq_braid_secret {
        ikm.extend_from_slice(pq_ss);
    } else {
        ikm.extend_from_slice(&[0u8; 32]);
    }
    let derived = hkdf_sha256::<64>(root_key, &ikm, b"ZT2-PQ-RATCHET-STEP-V1");
    let mut next_rk = [0u8; 32];
    let mut next_ck = [0u8; 32];
    next_rk.copy_from_slice(&derived[0..32]);
    next_ck.copy_from_slice(&derived[32..64]);
    (
        LockedSecretBuffer::new(next_rk),
        LockedSecretBuffer::new(next_ck),
    )
}
