//! Dual Classical + Post-Quantum Identity Management
//!
//! Each device independently generates:
//! 1. Classical Identity: `Ed25519` (signing) + `X25519` (static key agreement)
//! 2. Post-Quantum Identity: `ML-DSA-87` (signing) + `ML-KEM-1024` (key encapsulation)

use crate::crypto::kdf::domain_sha256;
use crate::crypto::kem::{ClassicalKemKeyPair, PqKemKeyPair};
use crate::crypto::signatures::{
    verify_ed25519, verify_ml_dsa_87, ClassicalSigningKeyPair, PqSigningKeyPair, ED25519_PK_LEN,
    ED25519_SIG_LEN,
};
use crate::{ZeroTraceError, ZtResult};
use serde::{Deserialize, Serialize};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Public bundle representing a peer's or local user's full dual identity.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct PublicIdentityBundle {
    pub ed25519_pk: [u8; ED25519_PK_LEN],
    pub x25519_pk: [u8; 32],
    pub ml_dsa_87_pk: Vec<u8>,
    pub ml_kem_1024_ek: Vec<u8>,
    pub classical_fingerprint: [u8; 32],
    pub pq_fingerprint: [u8; 32],
}

impl PublicIdentityBundle {
    pub fn compute_classical_fingerprint(ed25519_pk: &[u8; 32], x25519_pk: &[u8; 32]) -> [u8; 32] {
        domain_sha256(b"ZT2-CLASSICAL-ID-V1", &[ed25519_pk, x25519_pk])
    }

    pub fn compute_pq_fingerprint(ml_dsa_87_pk: &[u8], ml_kem_1024_ek: &[u8]) -> [u8; 32] {
        domain_sha256(b"ZT2-PQ-ID-V1", &[ml_dsa_87_pk, ml_kem_1024_ek])
    }

    /// Verifies that the stored fingerprints match the actual public key bytes.
    pub fn validate_integrity(&self) -> ZtResult<()> {
        let expected_c = Self::compute_classical_fingerprint(&self.ed25519_pk, &self.x25519_pk);
        let expected_pq = Self::compute_pq_fingerprint(&self.ml_dsa_87_pk, &self.ml_kem_1024_ek);
        if expected_c != self.classical_fingerprint || expected_pq != self.pq_fingerprint {
            return Err(ZeroTraceError::PeerIdentityMismatch);
        }
        Ok(())
    }

    /// Formats a 32-byte fingerprint into 8 groups of 5 decimal digits for human comparison.
    pub fn format_safety_digits(fp: &[u8; 32]) -> String {
        let mut groups = Vec::with_capacity(8);
        for chunk in fp.chunks_exact(4) {
            let val = u32::from_be_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]) % 100_000;
            groups.push(format!("{:05}", val));
        }
        groups.join(" ")
    }
}

/// Complete device identity held exclusively inside the Rust security core.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct DeviceIdentity {
    pub classical_sign: ClassicalSigningKeyPair,
    pub classical_kem: ClassicalKemKeyPair,
    pub pq_sign: PqSigningKeyPair,
    pub pq_kem: PqKemKeyPair,
}

impl DeviceIdentity {
    pub fn generate() -> ZtResult<Self> {
        let classical_sign = ClassicalSigningKeyPair::generate();
        let classical_kem = ClassicalKemKeyPair::generate();
        let pq_sign = PqSigningKeyPair::generate()?;
        let pq_kem = PqKemKeyPair::generate()?;
        Ok(Self {
            classical_sign,
            classical_kem,
            pq_sign,
            pq_kem,
        })
    }

    pub fn public_bundle(&self) -> PublicIdentityBundle {
        let classical_fingerprint = PublicIdentityBundle::compute_classical_fingerprint(
            &self.classical_sign.public_bytes,
            &self.classical_kem.public_bytes,
        );
        let pq_fingerprint = PublicIdentityBundle::compute_pq_fingerprint(
            &self.pq_sign.pk_bytes,
            &self.pq_kem.ek_bytes,
        );
        PublicIdentityBundle {
            ed25519_pk: self.classical_sign.public_bytes,
            x25519_pk: self.classical_kem.public_bytes,
            ml_dsa_87_pk: self.pq_sign.pk_bytes.clone(),
            ml_kem_1024_ek: self.pq_kem.ek_bytes.clone(),
            classical_fingerprint,
            pq_fingerprint,
        }
    }

    /// Signs a transcript with BOTH `Ed25519` and `ML-DSA-87`.
    pub fn sign_hybrid(&self, transcript: &[u8]) -> ZtResult<([u8; ED25519_SIG_LEN], Vec<u8>)> {
        let ed_sig = self.classical_sign.sign(transcript);
        let pq_sig = self.pq_sign.sign(transcript)?;
        Ok((ed_sig, pq_sig))
    }
}

/// Verifies BOTH `Ed25519` and `ML-DSA-87` signatures against a `PublicIdentityBundle`.
pub fn verify_hybrid_signature(
    peer: &PublicIdentityBundle,
    transcript: &[u8],
    ed_sig: &[u8; ED25519_SIG_LEN],
    pq_sig: &[u8],
) -> ZtResult<()> {
    peer.validate_integrity()?;
    verify_ed25519(&peer.ed25519_pk, transcript, ed_sig)?;
    verify_ml_dsa_87(&peer.ml_dsa_87_pk, transcript, pq_sig)?;
    Ok(())
}
