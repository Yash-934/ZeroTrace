//! Classical (`X25519`) and Post-Quantum (`NIST FIPS 203 ML-KEM-1024`) Key Encapsulation

use crate::memory::LockedSecretBuffer;
use crate::{ZeroTraceError, ZtResult};
use fips203::ml_kem_1024;
use fips203::traits::{Decaps, Encaps, KeyGen, SerDes};
use rand_core::OsRng;
use x25519_dalek::{PublicKey as X25519PublicKey, StaticSecret as X25519StaticSecret};
use zeroize::{Zeroize, ZeroizeOnDrop};

pub const X25519_KEY_LEN: usize = 32;
pub const ML_KEM_1024_EK_LEN: usize = ml_kem_1024::EK_LEN; // 1568 bytes
pub const ML_KEM_1024_DK_LEN: usize = ml_kem_1024::DK_LEN; // 3168 bytes
pub const ML_KEM_1024_CT_LEN: usize = ml_kem_1024::CT_LEN; // 1568 bytes
pub const SHARED_SECRET_LEN: usize = 32;

/// Classical X25519 Keypair with zeroization on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct ClassicalKemKeyPair {
    secret_bytes: [u8; X25519_KEY_LEN],
    #[zeroize(skip)]
    pub public_bytes: [u8; X25519_KEY_LEN],
}

impl ClassicalKemKeyPair {
    pub fn generate() -> Self {
        let secret = X25519StaticSecret::random_from_rng(OsRng);
        let public = X25519PublicKey::from(&secret);
        Self {
            secret_bytes: secret.to_bytes(),
            public_bytes: public.to_bytes(),
        }
    }

    pub fn from_secret_bytes(secret_bytes: [u8; X25519_KEY_LEN]) -> Self {
        let secret = X25519StaticSecret::from(secret_bytes);
        let public = X25519PublicKey::from(&secret);
        Self {
            secret_bytes,
            public_bytes: public.to_bytes(),
        }
    }

    pub fn secret_bytes(&self) -> &[u8; X25519_KEY_LEN] {
        &self.secret_bytes
    }

    pub fn diffie_hellman(
        &self,
        peer_public: &[u8; X25519_KEY_LEN],
    ) -> ZtResult<LockedSecretBuffer<SHARED_SECRET_LEN>> {
        let sk = X25519StaticSecret::from(self.secret_bytes);
        let pk = X25519PublicKey::from(*peer_public);
        let shared = sk.diffie_hellman(&pk);
        // Reject all-zero low-order curve point output
        if shared.as_bytes().iter().all(|&b| b == 0) {
            return Err(ZeroTraceError::InvalidKeyMaterial);
        }
        Ok(LockedSecretBuffer::new(shared.to_bytes()))
    }
}

/// NIST FIPS 203 ML-KEM-1024 Keypair.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct PqKemKeyPair {
    dk_bytes: Vec<u8>,
    #[zeroize(skip)]
    pub ek_bytes: Vec<u8>,
}

impl PqKemKeyPair {
    pub fn generate() -> ZtResult<Self> {
        let (ek, dk) = ml_kem_1024::KG::try_keygen_with_rng(&mut OsRng)
            .map_err(|_| ZeroTraceError::CsprngFailure)?;
        Ok(Self {
            dk_bytes: dk.into_bytes().to_vec(),
            ek_bytes: ek.into_bytes().to_vec(),
        })
    }

    pub fn from_bytes(ek_bytes: Vec<u8>, dk_bytes: Vec<u8>) -> ZtResult<Self> {
        if ek_bytes.len() != ML_KEM_1024_EK_LEN || dk_bytes.len() != ML_KEM_1024_DK_LEN {
            return Err(ZeroTraceError::InvalidKeyMaterial);
        }
        Ok(Self { dk_bytes, ek_bytes })
    }

    pub fn decapsulate(
        &self,
        ciphertext: &[u8],
    ) -> ZtResult<LockedSecretBuffer<SHARED_SECRET_LEN>> {
        let dk_arr: [u8; ML_KEM_1024_DK_LEN] = self
            .dk_bytes
            .as_slice()
            .try_into()
            .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
        let ct_arr: [u8; ML_KEM_1024_CT_LEN] = ciphertext
            .try_into()
            .map_err(|_| ZeroTraceError::KemOperationFailed)?;
        let dk = ml_kem_1024::DecapsKey::try_from_bytes(dk_arr)
            .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
        let ct = ml_kem_1024::CipherText::try_from_bytes(ct_arr)
            .map_err(|_| ZeroTraceError::KemOperationFailed)?;
        let ss = dk
            .try_decaps(&ct)
            .map_err(|_| ZeroTraceError::KemOperationFailed)?;
        Ok(LockedSecretBuffer::new(ss.into_bytes()))
    }

    pub fn dk_bytes(&self) -> &[u8] {
        &self.dk_bytes
    }
}

/// Encapsulates a fresh 32-byte shared secret to a peer's ML-KEM-1024 encapsulation key.
pub fn ml_kem_1024_encapsulate(
    peer_ek_bytes: &[u8],
) -> ZtResult<(Vec<u8>, LockedSecretBuffer<SHARED_SECRET_LEN>)> {
    let ek_arr: [u8; ML_KEM_1024_EK_LEN] = peer_ek_bytes
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
    let ek = ml_kem_1024::EncapsKey::try_from_bytes(ek_arr)
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
    let (ss, ct) = ek
        .try_encaps_with_rng(&mut OsRng)
        .map_err(|_| ZeroTraceError::KemOperationFailed)?;
    Ok((
        ct.into_bytes().to_vec(),
        LockedSecretBuffer::new(ss.into_bytes()),
    ))
}
