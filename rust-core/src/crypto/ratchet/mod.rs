//! Hybrid Double Ratchet + Periodic ML-KEM-1024 Braid State Machine
//!
//! Security Properties:
//! - Separate sending and receiving chains (`ck_send`, `ck_recv`).
//! - Immediate zeroization of consumed message keys.
//! - Out-of-order message resilience with bounded skipped-key cache (`MAX_SKIP = 64`).
//! - Replay and duplicate message rejection.
//! - Unique per-message key and deterministic nonce derivation (`ChaCha20-Poly1305`).
//! - Periodic Post-Quantum encapsulation ratchet step (`PQ_BRAID_EPOCH_INTERVAL = 8`).
//!
//! Note: As documented in `LIMITATIONS.md`, this hybrid Double Ratchet + ML-KEM-1024 Braid
//! construction is marked `EXPERIMENTAL_UNAUDITED` until an independent third-party audit is completed.

use crate::crypto::kdf::{hkdf_sha256, kdf_chain_step, kdf_root_step};
use crate::crypto::kem::{
    ml_kem_1024_encapsulate, ClassicalKemKeyPair, PqKemKeyPair, X25519_KEY_LEN,
};
use crate::memory::{EphemeralBytes, LockedSecretBuffer};
use crate::{ZeroTraceError, ZtResult};
use chacha20poly1305::aead::{Aead, KeyInit, Payload};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use zeroize::Zeroize;

pub const MAX_SKIP: u64 = 64;
pub const PQ_BRAID_EPOCH_INTERVAL: u32 = 8;
pub const RATCHET_PROTOCOL_VERSION: u8 = 2;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RatchetHeader {
    pub version: u8,
    pub session_id: [u8; 16],
    pub dh_ratchet_pub: [u8; X25519_KEY_LEN],
    pub ratchet_epoch: u32,
    pub msg_num: u64,
    pub prev_chain_len: u64,
    /// Present when this message advances the periodic ML-KEM-1024 Braid epoch
    pub pq_braid_ciphertext: Option<Vec<u8>>,
    /// Sender's updated ML-KEM-1024 encapsulation key for the next braid epoch
    pub next_pq_ek: Option<Vec<u8>>,
}

impl RatchetHeader {
    pub fn to_associated_data(
        &self,
        sender_classical_fp: &[u8; 32],
        sender_pq_fp: &[u8; 32],
    ) -> Vec<u8> {
        let mut ad = Vec::with_capacity(140);
        ad.extend_from_slice(b"ZT2-RATCHET-AD-V1");
        ad.push(self.version);
        ad.extend_from_slice(&self.session_id);
        ad.extend_from_slice(sender_classical_fp);
        ad.extend_from_slice(sender_pq_fp);
        ad.extend_from_slice(&self.dh_ratchet_pub);
        ad.extend_from_slice(&self.ratchet_epoch.to_be_bytes());
        ad.extend_from_slice(&self.msg_num.to_be_bytes());
        ad.extend_from_slice(&self.prev_chain_len.to_be_bytes());
        ad
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct EncryptedRatchetPacket {
    pub header: RatchetHeader,
    pub ciphertext: Vec<u8>,
}

pub struct HybridRatchetSession {
    pub session_id: [u8; 16],
    pub local_classical_fp: [u8; 32],
    pub local_pq_fp: [u8; 32],
    pub peer_classical_fp: [u8; 32],
    pub peer_pq_fp: [u8; 32],
    root_key: LockedSecretBuffer<32>,
    ck_send: LockedSecretBuffer<32>,
    ck_recv: LockedSecretBuffer<32>,
    dh_local: ClassicalKemKeyPair,
    dh_peer_pub: [u8; X25519_KEY_LEN],
    pq_local: PqKemKeyPair,
    pq_peer_ek: Vec<u8>,
    pub ratchet_epoch: u32,
    pub send_count: u64,
    pub recv_count: u64,
    pub prev_send_count: u64,
    skipped_message_keys: HashMap<([u8; X25519_KEY_LEN], u64), LockedSecretBuffer<32>>,
    consumed_message_ids: HashMap<([u8; X25519_KEY_LEN], u64), bool>,
}

impl HybridRatchetSession {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        session_id: [u8; 16],
        local_classical_fp: [u8; 32],
        local_pq_fp: [u8; 32],
        peer_classical_fp: [u8; 32],
        peer_pq_fp: [u8; 32],
        root_key: LockedSecretBuffer<32>,
        ck_send: LockedSecretBuffer<32>,
        ck_recv: LockedSecretBuffer<32>,
        dh_local: ClassicalKemKeyPair,
        dh_peer_pub: [u8; X25519_KEY_LEN],
        pq_local: PqKemKeyPair,
        pq_peer_ek: Vec<u8>,
    ) -> Self {
        Self {
            session_id,
            local_classical_fp,
            local_pq_fp,
            peer_classical_fp,
            peer_pq_fp,
            root_key,
            ck_send,
            ck_recv,
            dh_local,
            dh_peer_pub,
            pq_local,
            pq_peer_ek,
            ratchet_epoch: 1,
            send_count: 0,
            recv_count: 0,
            prev_send_count: 0,
            skipped_message_keys: HashMap::new(),
            consumed_message_ids: HashMap::new(),
        }
    }

    /// Performs an explicit or periodic DH + ML-KEM-1024 Braid ratchet step before sending.
    pub fn advance_ratchet_epoch(&mut self, include_pq_braid: bool) -> ZtResult<(Option<Vec<u8>>, Option<Vec<u8>>)> {
        self.prev_send_count = self.send_count;
        self.send_count = 0;
        self.ratchet_epoch = self.ratchet_epoch.saturating_add(1);
        self.dh_local = ClassicalKemKeyPair::generate();
        let dh_out = self.dh_local.diffie_hellman(&self.dh_peer_pub)?;

        let mut pq_ct_opt = None;
        let mut next_ek_opt = None;
        let (next_rk, next_ck) = if include_pq_braid {
            let (ct, pq_ss) = ml_kem_1024_encapsulate(&self.pq_peer_ek)?;
            self.pq_local = PqKemKeyPair::generate()?;
            pq_ct_opt = Some(ct);
            next_ek_opt = Some(self.pq_local.ek_bytes.clone());
            kdf_root_step(&self.root_key, &dh_out, Some(&pq_ss))
        } else {
            kdf_root_step(&self.root_key, &dh_out, None)
        };
        self.root_key = next_rk;
        self.ck_send = next_ck;
        Ok((pq_ct_opt, next_ek_opt))
    }

    /// Encrypts a plaintext payload using the next sending message key and advances `ck_send`.
    pub fn encrypt(&mut self, plaintext: &[u8]) -> ZtResult<EncryptedRatchetPacket> {
        let mut pq_ct = None;
        let mut next_pq_ek = None;
        if self.send_count > 0 && (self.send_count % (PQ_BRAID_EPOCH_INTERVAL as u64) == 0) {
            let (ct, ek) = self.advance_ratchet_epoch(true)?;
            pq_ct = ct;
            next_pq_ek = ek;
        }

        let (next_ck, mut msg_key) = kdf_chain_step(&self.ck_send);
        self.ck_send = next_ck;
        let msg_num = self.send_count;
        self.send_count += 1;

        let header = RatchetHeader {
            version: RATCHET_PROTOCOL_VERSION,
            session_id: self.session_id,
            dh_ratchet_pub: self.dh_local.public_bytes,
            ratchet_epoch: self.ratchet_epoch,
            msg_num,
            prev_chain_len: self.prev_send_count,
            pq_braid_ciphertext: pq_ct,
            next_pq_ek,
        };

        let ad = header.to_associated_data(&self.local_classical_fp, &self.local_pq_fp);
        let nonce_bytes = derive_message_nonce(&msg_key, msg_num);
        let cipher = ChaCha20Poly1305::new(Key::from_slice(&*msg_key));
        let ciphertext = cipher
            .encrypt(
                Nonce::from_slice(&nonce_bytes),
                Payload {
                    msg: plaintext,
                    aad: &ad,
                },
            )
            .map_err(|_| ZeroTraceError::AeadDecryptionFailed)?;

        msg_key.zeroize();
        Ok(EncryptedRatchetPacket { header, ciphertext })
    }

    /// Decrypts and authenticates an incoming ratchet packet, handling out-of-order and replay checks.
    pub fn decrypt(&mut self, packet: &EncryptedRatchetPacket) -> ZtResult<EphemeralBytes> {
        let hdr = &packet.header;
        if hdr.version != RATCHET_PROTOCOL_VERSION || hdr.session_id != self.session_id {
            return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
        }

        let msg_id = (hdr.dh_ratchet_pub, hdr.msg_num);
        if self.consumed_message_ids.contains_key(&msg_id) {
            return Err(ZeroTraceError::ReplayOrDuplicateMessage);
        }

        // 1. Check if this belongs to a previously skipped message key
        if let Some(mut skipped_mk) = self.skipped_message_keys.remove(&msg_id) {
            let ad = hdr.to_associated_data(&self.peer_classical_fp, &self.peer_pq_fp);
            let nonce_bytes = derive_message_nonce(&skipped_mk, hdr.msg_num);
            let cipher = ChaCha20Poly1305::new(Key::from_slice(&*skipped_mk));
            let pt = cipher
                .decrypt(
                    Nonce::from_slice(&nonce_bytes),
                    Payload {
                        msg: &packet.ciphertext,
                        aad: &ad,
                    },
                )
                .map_err(|_| ZeroTraceError::AeadDecryptionFailed)?;
            skipped_mk.zeroize();
            self.consumed_message_ids.insert(msg_id, true);
            return Ok(EphemeralBytes::from_slice(&pt));
        }

        // 2. If peer rotated their DH ratchet public key, advance receiving ratchet epoch
        if hdr.dh_ratchet_pub != self.dh_peer_pub {
            self.skip_message_keys(hdr.prev_chain_len)?;
            self.dh_peer_pub = hdr.dh_ratchet_pub;
            self.ratchet_epoch = hdr.ratchet_epoch;
            self.recv_count = 0;

            let dh_shared = self.dh_local.diffie_hellman(&self.dh_peer_pub)?;
            let (next_rk, next_ck) = if let Some(ref pq_ct) = hdr.pq_braid_ciphertext {
                let pq_ss = self.pq_local.decapsulate(pq_ct)?;
                if let Some(ref next_ek) = hdr.next_pq_ek {
                    self.pq_peer_ek = next_ek.clone();
                }
                kdf_root_step(&self.root_key, &dh_shared, Some(&pq_ss))
            } else {
                kdf_root_step(&self.root_key, &dh_shared, None)
            };
            self.root_key = next_rk;
            self.ck_recv = next_ck;
        }

        // 3. Reject message numbers in the past that were not in `skipped_message_keys`
        if hdr.msg_num < self.recv_count {
            return Err(ZeroTraceError::ReplayOrDuplicateMessage);
        }

        // 4. Advance and cache any skipped intermediate messages in the current chain
        self.skip_message_keys(hdr.msg_num)?;

        // 5. Derive the target message key
        let (next_ck, mut msg_key) = kdf_chain_step(&self.ck_recv);
        self.ck_recv = next_ck;
        self.recv_count += 1;

        let ad = hdr.to_associated_data(&self.peer_classical_fp, &self.peer_pq_fp);
        let nonce_bytes = derive_message_nonce(&msg_key, hdr.msg_num);
        let cipher = ChaCha20Poly1305::new(Key::from_slice(&*msg_key));
        let pt = cipher
            .decrypt(
                Nonce::from_slice(&nonce_bytes),
                Payload {
                    msg: &packet.ciphertext,
                    aad: &ad,
                },
            )
            .map_err(|_| ZeroTraceError::AeadDecryptionFailed)?;

        msg_key.zeroize();
        self.consumed_message_ids.insert(msg_id, true);
        Ok(EphemeralBytes::from_slice(&pt))
    }

    fn skip_message_keys(&mut self, until_msg_num: u64) -> ZtResult<()> {
        if until_msg_num < self.recv_count {
            return Ok(());
        }
        let gap = until_msg_num - self.recv_count;
        if gap > MAX_SKIP || (self.skipped_message_keys.len() as u64 + gap) > MAX_SKIP {
            return Err(ZeroTraceError::MaxSkipExceeded(MAX_SKIP));
        }
        while self.recv_count < until_msg_num {
            let (next_ck, mk) = kdf_chain_step(&self.ck_recv);
            self.ck_recv = next_ck;
            self.skipped_message_keys
                .insert((self.dh_peer_pub, self.recv_count), mk);
            self.recv_count += 1;
        }
        Ok(())
    }

    pub fn destroy(&mut self) {
        self.root_key.zeroize();
        self.ck_send.zeroize();
        self.ck_recv.zeroize();
        for (_, mut mk) in self.skipped_message_keys.drain() {
            mk.zeroize();
        }
        self.consumed_message_ids.clear();
    }
}

fn derive_message_nonce(msg_key: &[u8; 32], msg_num: u64) -> [u8; 12] {
    let base = hkdf_sha256::<12>(b"ZT2-MSG-NONCE-SALT", msg_key, b"ZT2-NONCE-V1");
    let mut nonce = *base;
    let counter_bytes = msg_num.to_be_bytes();
    for i in 0..8 {
        nonce[4 + i] ^= counter_bytes[i];
    }
    nonce
}
