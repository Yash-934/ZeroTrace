//! Serverless Authenticated Hybrid Post-Quantum Handshake (`ZT-PQXDH-P2P-v1`)
//!
//! Combines:
//! - Mutual Dual Identity Verification (`Ed25519` + `ML-DSA-87`)
//! - 4-way Classical Diffie-Hellman (`X25519` static/ephemeral)
//! - 2-way Post-Quantum Key Encapsulation (`ML-KEM-1024` static + ephemeral)
//! - QR Pairing Nonce Binding & Protocol Version Downgrade Defense

use crate::crypto::kdf::{domain_sha256, hkdf_sha256};
use crate::crypto::kem::{ml_kem_1024_encapsulate, ClassicalKemKeyPair, PqKemKeyPair};
use crate::crypto::ratchet::HybridRatchetSession;
use crate::crypto::signatures::ED25519_SIG_LEN;
use crate::identity::{verify_hybrid_signature, DeviceIdentity, PublicIdentityBundle};
use crate::memory::LockedSecretBuffer;
use crate::{ZeroTraceError, ZtResult};
use rand_core::{OsRng, RngCore};
use serde::{Deserialize, Serialize};

pub const HANDSHAKE_PROTOCOL_VERSION: u16 = 2;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HandshakeInitMessage {
    pub protocol_version: u16,
    pub session_id: [u8; 16],
    pub pairing_nonce: [u8; 16],
    pub initiator_nonce: [u8; 32],
    pub initiator_identity: PublicIdentityBundle,
    pub ephemeral_x25519_pk: [u8; 32],
    pub ephemeral_ml_kem_1024_ek: Vec<u8>,
    pub target_peer_classical_fp: [u8; 32],
    pub target_peer_pq_fp: [u8; 32],
    pub ed25519_signature: Vec<u8>,
    pub ml_dsa_87_signature: Vec<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HandshakeResponseMessage {
    pub protocol_version: u16,
    pub session_id: [u8; 16],
    pub responder_nonce: [u8; 32],
    pub responder_identity: PublicIdentityBundle,
    pub ephemeral_x25519_pk: [u8; 32],
    pub ephemeral_ml_kem_1024_ek: Vec<u8>,
    pub kem_ciphertext_static: Vec<u8>,
    pub kem_ciphertext_ephemeral: Vec<u8>,
    pub ed25519_signature: Vec<u8>,
    pub ml_dsa_87_signature: Vec<u8>,
}

pub struct InitiatorHandshakeState {
    pub session_id: [u8; 16],
    pub pairing_nonce: [u8; 16],
    pub initiator_nonce: [u8; 32],
    pub ephemeral_x25519: ClassicalKemKeyPair,
    pub ephemeral_pq_kem: PqKemKeyPair,
    pub expected_peer_classical_fp: [u8; 32],
    pub expected_peer_pq_fp: [u8; 32],
    pub init_transcript_hash: [u8; 32],
}

impl InitiatorHandshakeState {
    pub fn initiate(
        local_id: &DeviceIdentity,
        expected_peer_classical_fp: [u8; 32],
        expected_peer_pq_fp: [u8; 32],
        pairing_nonce: [u8; 16],
    ) -> ZtResult<(Self, HandshakeInitMessage)> {
        let mut session_id = [0u8; 16];
        let mut initiator_nonce = [0u8; 32];
        OsRng.fill_bytes(&mut session_id);
        OsRng.fill_bytes(&mut initiator_nonce);

        let ephemeral_x25519 = ClassicalKemKeyPair::generate();
        let ephemeral_pq_kem = PqKemKeyPair::generate()?;
        let pub_bundle = local_id.public_bundle();

        let transcript = domain_sha256(
            b"ZT2-HS-INIT-V1",
            &[
                &HANDSHAKE_PROTOCOL_VERSION.to_be_bytes(),
                &session_id,
                &pairing_nonce,
                &initiator_nonce,
                &pub_bundle.classical_fingerprint,
                &pub_bundle.pq_fingerprint,
                &ephemeral_x25519.public_bytes,
                &ephemeral_pq_kem.ek_bytes,
                &expected_peer_classical_fp,
                &expected_peer_pq_fp,
            ],
        );

        let (ed_sig, pq_sig) = local_id.sign_hybrid(&transcript)?;

        let msg = HandshakeInitMessage {
            protocol_version: HANDSHAKE_PROTOCOL_VERSION,
            session_id,
            pairing_nonce,
            initiator_nonce,
            initiator_identity: pub_bundle,
            ephemeral_x25519_pk: ephemeral_x25519.public_bytes,
            ephemeral_ml_kem_1024_ek: ephemeral_pq_kem.ek_bytes.clone(),
            target_peer_classical_fp: expected_peer_classical_fp,
            target_peer_pq_fp: expected_peer_pq_fp,
            ed25519_signature: ed_sig.to_vec(),
            ml_dsa_87_signature: pq_sig,
        };

        let state = Self {
            session_id,
            pairing_nonce,
            initiator_nonce,
            ephemeral_x25519,
            ephemeral_pq_kem,
            expected_peer_classical_fp,
            expected_peer_pq_fp,
            init_transcript_hash: transcript,
        };

        Ok((state, msg))
    }

    pub fn finalize(
        self,
        local_id: &DeviceIdentity,
        resp: &HandshakeResponseMessage,
    ) -> ZtResult<HybridRatchetSession> {
        if resp.protocol_version != HANDSHAKE_PROTOCOL_VERSION || resp.session_id != self.session_id
        {
            return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
        }

        resp.responder_identity.validate_integrity()?;
        if resp.responder_identity.classical_fingerprint != self.expected_peer_classical_fp
            || resp.responder_identity.pq_fingerprint != self.expected_peer_pq_fp
        {
            return Err(ZeroTraceError::PeerIdentityMismatch);
        }

        let resp_transcript = domain_sha256(
            b"ZT2-HS-RESP-V1",
            &[
                &self.init_transcript_hash,
                &resp.responder_nonce,
                &resp.responder_identity.classical_fingerprint,
                &resp.responder_identity.pq_fingerprint,
                &resp.ephemeral_x25519_pk,
                &resp.ephemeral_ml_kem_1024_ek,
                &resp.kem_ciphertext_static,
                &resp.kem_ciphertext_ephemeral,
            ],
        );

        let ed_sig_arr: [u8; ED25519_SIG_LEN] = resp
            .ed25519_signature
            .as_slice()
            .try_into()
            .map_err(|_| ZeroTraceError::ClassicalSignatureInvalid)?;
        verify_hybrid_signature(
            &resp.responder_identity,
            &resp_transcript,
            &ed_sig_arr,
            &resp.ml_dsa_87_signature,
        )?;

        // Compute 4-way X25519 + 2-way ML-KEM-1024 shared secrets
        let dh1 = local_id
            .classical_kem
            .diffie_hellman(&resp.responder_identity.x25519_pk)?;
        let dh2 = local_id
            .classical_kem
            .diffie_hellman(&resp.ephemeral_x25519_pk)?;
        let dh3 = self
            .ephemeral_x25519
            .diffie_hellman(&resp.responder_identity.x25519_pk)?;
        let dh4 = self
            .ephemeral_x25519
            .diffie_hellman(&resp.ephemeral_x25519_pk)?;

        let ss_pq1 = local_id.pq_kem.decapsulate(&resp.kem_ciphertext_static)?;
        let ss_pq2 = self
            .ephemeral_pq_kem
            .decapsulate(&resp.kem_ciphertext_ephemeral)?;

        let mut ikm = Vec::with_capacity(32 * 6);
        ikm.extend_from_slice(&*dh1);
        ikm.extend_from_slice(&*dh2);
        ikm.extend_from_slice(&*dh3);
        ikm.extend_from_slice(&*dh4);
        ikm.extend_from_slice(&*ss_pq1);
        ikm.extend_from_slice(&*ss_pq2);

        let salt = domain_sha256(
            b"ZT2-PQXDH-SALT-V1",
            &[&self.initiator_nonce, &resp.responder_nonce, &resp_transcript],
        );
        let derived = hkdf_sha256::<96>(&salt, &ikm, b"ZT2-HYBRID-ROOT-V1");

        let mut rk = [0u8; 32];
        let mut ck_a_to_b = [0u8; 32];
        let mut ck_b_to_a = [0u8; 32];
        rk.copy_from_slice(&derived[0..32]);
        ck_a_to_b.copy_from_slice(&derived[32..64]);
        ck_b_to_a.copy_from_slice(&derived[64..96]);

        let local_pub = local_id.public_bundle();
        Ok(HybridRatchetSession::new(
            self.session_id,
            local_pub.classical_fingerprint,
            local_pub.pq_fingerprint,
            resp.responder_identity.classical_fingerprint,
            resp.responder_identity.pq_fingerprint,
            LockedSecretBuffer::new(rk),
            LockedSecretBuffer::new(ck_a_to_b),
            LockedSecretBuffer::new(ck_b_to_a),
            self.ephemeral_x25519,
            resp.ephemeral_x25519_pk,
            self.ephemeral_pq_kem,
            resp.ephemeral_ml_kem_1024_ek.clone(),
        ))
    }
}

pub fn respond_to_handshake(
    local_id: &DeviceIdentity,
    expected_peer_classical_fp: [u8; 32],
    expected_peer_pq_fp: [u8; 32],
    expected_pairing_nonce: [u8; 16],
    init: &HandshakeInitMessage,
) -> ZtResult<(HybridRatchetSession, HandshakeResponseMessage)> {
    if init.protocol_version != HANDSHAKE_PROTOCOL_VERSION
        || init.pairing_nonce != expected_pairing_nonce
    {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }

    init.initiator_identity.validate_integrity()?;
    if init.initiator_identity.classical_fingerprint != expected_peer_classical_fp
        || init.initiator_identity.pq_fingerprint != expected_peer_pq_fp
    {
        return Err(ZeroTraceError::PeerIdentityMismatch);
    }

    let local_pub = local_id.public_bundle();
    if init.target_peer_classical_fp != local_pub.classical_fingerprint
        || init.target_peer_pq_fp != local_pub.pq_fingerprint
    {
        return Err(ZeroTraceError::PeerIdentityMismatch);
    }

    let init_transcript = domain_sha256(
        b"ZT2-HS-INIT-V1",
        &[
            &init.protocol_version.to_be_bytes(),
            &init.session_id,
            &init.pairing_nonce,
            &init.initiator_nonce,
            &init.initiator_identity.classical_fingerprint,
            &init.initiator_identity.pq_fingerprint,
            &init.ephemeral_x25519_pk,
            &init.ephemeral_ml_kem_1024_ek,
            &init.target_peer_classical_fp,
            &init.target_peer_pq_fp,
        ],
    );

    let ed_sig_arr: [u8; ED25519_SIG_LEN] = init
        .ed25519_signature
        .as_slice()
        .try_into()
        .map_err(|_| ZeroTraceError::ClassicalSignatureInvalid)?;
    verify_hybrid_signature(
        &init.initiator_identity,
        &init_transcript,
        &ed_sig_arr,
        &init.ml_dsa_87_signature,
    )?;

    let mut responder_nonce = [0u8; 32];
    OsRng.fill_bytes(&mut responder_nonce);
    let ephemeral_x25519 = ClassicalKemKeyPair::generate();
    let ephemeral_pq_kem = PqKemKeyPair::generate()?;

    let (ct_static, ss_pq1) = ml_kem_1024_encapsulate(&init.initiator_identity.ml_kem_1024_ek)?;
    let (ct_eph, ss_pq2) = ml_kem_1024_encapsulate(&init.ephemeral_ml_kem_1024_ek)?;

    let dh1 = local_id
        .classical_kem
        .diffie_hellman(&init.initiator_identity.x25519_pk)?;
    let dh2 = ephemeral_x25519.diffie_hellman(&init.initiator_identity.x25519_pk)?;
    let dh3 = local_id
        .classical_kem
        .diffie_hellman(&init.ephemeral_x25519_pk)?;
    let dh4 = ephemeral_x25519.diffie_hellman(&init.ephemeral_x25519_pk)?;

    let resp_transcript = domain_sha256(
        b"ZT2-HS-RESP-V1",
        &[
            &init_transcript,
            &responder_nonce,
            &local_pub.classical_fingerprint,
            &local_pub.pq_fingerprint,
            &ephemeral_x25519.public_bytes,
            &ephemeral_pq_kem.ek_bytes,
            &ct_static,
            &ct_eph,
        ],
    );

    let (resp_ed_sig, resp_pq_sig) = local_id.sign_hybrid(&resp_transcript)?;

    let mut ikm = Vec::with_capacity(32 * 6);
    ikm.extend_from_slice(&*dh1);
    ikm.extend_from_slice(&*dh2);
    ikm.extend_from_slice(&*dh3);
    ikm.extend_from_slice(&*dh4);
    ikm.extend_from_slice(&*ss_pq1);
    ikm.extend_from_slice(&*ss_pq2);

    let salt = domain_sha256(
        b"ZT2-PQXDH-SALT-V1",
        &[&init.initiator_nonce, &responder_nonce, &resp_transcript],
    );
    let derived = hkdf_sha256::<96>(&salt, &ikm, b"ZT2-HYBRID-ROOT-V1");

    let mut rk = [0u8; 32];
    let mut ck_a_to_b = [0u8; 32];
    let mut ck_b_to_a = [0u8; 32];
    rk.copy_from_slice(&derived[0..32]);
    ck_a_to_b.copy_from_slice(&derived[32..64]);
    ck_b_to_a.copy_from_slice(&derived[64..96]);

    // Responder's send chain is ck_b_to_a, receive chain is ck_a_to_b
    let session = HybridRatchetSession::new(
        init.session_id,
        local_pub.classical_fingerprint,
        local_pub.pq_fingerprint,
        init.initiator_identity.classical_fingerprint,
        init.initiator_identity.pq_fingerprint,
        LockedSecretBuffer::new(rk),
        LockedSecretBuffer::new(ck_b_to_a),
        LockedSecretBuffer::new(ck_a_to_b),
        ephemeral_x25519,
        init.ephemeral_x25519_pk,
        ephemeral_pq_kem,
        init.ephemeral_ml_kem_1024_ek.clone(),
    );

    let response = HandshakeResponseMessage {
        protocol_version: HANDSHAKE_PROTOCOL_VERSION,
        session_id: init.session_id,
        responder_nonce,
        responder_identity: local_pub,
        ephemeral_x25519_pk: session_dh_pub(&session),
        ephemeral_ml_kem_1024_ek: session_pq_ek(&session),
        kem_ciphertext_static: ct_static,
        kem_ciphertext_ephemeral: ct_eph,
        ed25519_signature: resp_ed_sig.to_vec(),
        ml_dsa_87_signature: resp_pq_sig,
    };

    Ok((session, response))
}

fn session_dh_pub(_s: &HybridRatchetSession) -> [u8; 32] {
    [0u8; 32]
}

fn session_pq_ek(_s: &HybridRatchetSession) -> Vec<u8> {
    Vec::new()
}
