//! Classical (`Ed25519`) and Post-Quantum (`NIST FIPS 204 ML-DSA-87`) Digital Signatures

use crate::{ZeroTraceError, ZtResult};
use ed25519_dalek::{Signature as EdSignature, Signer, SigningKey, VerifyingKey};
use fips204::ml_dsa_87;
use fips204::traits::{KeyGen, SerDes, Signer as PqSigner, Verifier as PqVerifier};
use rand_core::OsRng;
use zeroize::{Zeroize, ZeroizeOnDrop};

pub const ED25519_PK_LEN: usize = 32;
pub const ED25519_SK_LEN: usize = 32;
pub const ED25519_SIG_LEN: usize = 64;

pub const ML_DSA_87_PK_LEN: usize = ml_dsa_87::PK_LEN; // 2592 bytes
pub const ML_DSA_87_SK_LEN: usize = ml_dsa_87::SK_LEN; // 4896 bytes
pub const ML_DSA_87_SIG_LEN: usize = ml_dsa_87::SIG_LEN; // 4627 bytes

pub const SIGNATURE_DOMAIN_CONTEXT: &[u8] = b"ZT2-DUAL-SIG-CTX-V1";

/// Classical Ed25519 Signing Keypair.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct ClassicalSigningKeyPair {
    secret_bytes: [u8; ED25519_SK_LEN],
    #[zeroize(skip)]
    pub public_bytes: [u8; ED25519_PK_LEN],
}

impl ClassicalSigningKeyPair {
    pub fn generate() -> Self {
        let signing_key = SigningKey::generate(&mut OsRng);
        let verifying_key = signing_key.verifying_key();
        Self {
            secret_bytes: signing_key.to_bytes(),
            public_bytes: verifying_key.to_bytes(),
        }
    }

    pub fn from_secret_bytes(secret_bytes: [u8; ED25519_SK_LEN]) -> Self {
        let signing_key = SigningKey::from_bytes(&secret_bytes);
        let verifying_key = signing_key.verifying_key();
        Self {
            secret_bytes,
            public_bytes: verifying_key.to_bytes(),
        }
    }

    pub fn sign(&self, message: &[u8]) -> [u8; ED25519_SIG_LEN] {
        let signing_key = SigningKey::from_bytes(&self.secret_bytes);
        signing_key.sign(message).to_bytes()
    }

    pub fn secret_bytes(&self) -> &[u8; ED25519_SK_LEN] {
        &self.secret_bytes
    }
}

pub fn verify_ed25519(
    public_bytes: &[u8; ED25519_PK_LEN],
    message: &[u8],
    signature_bytes: &[u8; ED25519_SIG_LEN],
) -> ZtResult<()> {
    let vk = VerifyingKey::from_bytes(public_bytes)
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
    let sig = EdSignature::from_bytes(signature_bytes);
    vk.verify_strict(message, &sig)
        .map_err(|_| ZeroTraceError::ClassicalSignatureInvalid)
}

/// NIST FIPS 204 ML-DSA-87 Signing Keypair.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct PqSigningKeyPair {
    sk_bytes: Vec<u8>,
    #[zeroize(skip)]
    pub pk_bytes: Vec<u8>,
}

impl PqSigningKeyPair {
    pub fn generate() -> ZtResult<Self> {
        let (pk, sk) = ml_dsa_87::try_keygen_with_rng(&mut OsRng)
            .map_err(|_| ZeroTraceError::CsprngFailure)?;
        Ok(Self {
            sk_bytes: sk.into_bytes().to_vec(),
            pk_bytes: pk.into_bytes().to_vec(),
        })
    }

    pub fn from_bytes(pk_bytes: Vec<u8>, sk_bytes: Vec<u8>) -> ZtResult<Self> {
        if pk_bytes.len() != ML_DSA_87_PK_LEN || sk_bytes.len() != ML_DSA_87_SK_LEN {
            return Err(ZeroTraceError::InvalidKeyMaterial);
        }
        Ok(Self { sk_bytes, pk_bytes })
    }

    pub fn sign(&self, message: &[u8]) -> ZtResult<Vec<u8>> {
        let sk_arr: [u8; ML_DSA_87_SK_LEN] = self
            .sk_bytes
            .as_slice()
            .try_into()
            .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
        let sk = ml_dsa_87::PrivateKey::try_from_bytes(sk_arr)
            .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
        let sig = sk
            .try_sign_with_rng(&mut OsRng, message, SIGNATURE_DOMAIN_CONTEXT)
            .map_err(|_| ZeroTraceError::PostQuantumSignatureInvalid)?;
        Ok(sig.to_vec())
    }

    pub fn sk_bytes(&self) -> &[u8] {
        &self.sk_bytes
    }
}

pub fn verify_ml_dsa_87(
    public_bytes: &[u8],
    message: &[u8],
    signature_bytes: &[u8],
) -> ZtResult<()> {
    let pk_arr: [u8; ML_DSA_87_PK_LEN] = public_bytes
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
    let sig_arr: [u8; ML_DSA_87_SIG_LEN] = signature_bytes
        .try_into()
        .map_err(|_| ZeroTraceError::PostQuantumSignatureInvalid)?;
    let pk = ml_dsa_87::PublicKey::try_from_bytes(pk_arr)
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
    if pk.verify(message, &sig_arr, SIGNATURE_DOMAIN_CONTEXT) {
        Ok(())
    } else {
        Err(ZeroTraceError::PostQuantumSignatureInvalid)
    }
}
