//! Deterministic Authenticated QR Pairing Parser (`ZT2-QR-v1`)
//!
//! Canonical Format:
//! `ZT2|v1|<onion_address>|<classical_fp_hex>|<pq_fp_hex>|<ed25519_pk_b64>|<x25519_pk_b64>|<expiry_unix_sec>|<nonce_hex>|<capabilities>|<ed25519_sig_b64>`

use crate::crypto::signatures::{verify_ed25519, ClassicalSigningKeyPair, ED25519_PK_LEN, ED25519_SIG_LEN};
use crate::identity::PublicIdentityBundle;
use crate::network::arti::ArtiTorController;
use crate::{ZeroTraceError, ZtResult};
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;

pub const QR_MAGIC: &str = "ZT2";
pub const QR_VERSION: &str = "v1";
pub const MAX_QR_LIFETIME_SEC: u64 = 900; // 15 minutes max

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedPairingQr {
    pub onion_address: String,
    pub classical_fingerprint: [u8; 32],
    pub pq_fingerprint: [u8; 32],
    pub ed25519_pk: [u8; 32],
    pub x25519_pk: [u8; 32],
    pub expiry_unix_sec: u64,
    pub pairing_nonce: [u8; 16],
    pub capabilities: String,
}

pub fn serialize_pairing_qr(
    signing_key: &ClassicalSigningKeyPair,
    x25519_pk: &[u8; 32],
    pq_fingerprint: &[u8; 32],
    onion_address: &str,
    expiry_unix_sec: u64,
    pairing_nonce: &[u8; 16],
    capabilities: &str,
) -> ZtResult<String> {
    ArtiTorController::validate_onion_destination(onion_address)?;
    let classical_fp =
        PublicIdentityBundle::compute_classical_fingerprint(&signing_key.public_bytes, x25519_pk);
    let c_fp_hex = hex::encode(classical_fp);
    let pq_fp_hex = hex::encode(pq_fingerprint);
    let ed_pk_b64 = URL_SAFE_NO_PAD.encode(signing_key.public_bytes);
    let x_pk_b64 = URL_SAFE_NO_PAD.encode(x25519_pk);
    let nonce_hex = hex::encode(pairing_nonce);

    let body = format!(
        "{}|{}|{}|{}|{}|{}|{}|{}|{}|{}",
        QR_MAGIC,
        QR_VERSION,
        onion_address.to_ascii_lowercase(),
        c_fp_hex,
        pq_fp_hex,
        ed_pk_b64,
        x_pk_b64,
        expiry_unix_sec,
        nonce_hex,
        capabilities
    );

    let auth_msg = format!("ZT2-QR-AUTH-V1|{}", body);
    let sig = signing_key.sign(auth_msg.as_bytes());
    let sig_b64 = URL_SAFE_NO_PAD.encode(sig);

    Ok(format!("{}|{}", body, sig_b64))
}

pub fn parse_and_verify_pairing_qr(raw_qr: &str, now_unix_sec: u64) -> ZtResult<ParsedPairingQr> {
    if raw_qr.len() > 2048 {
        return Err(ZeroTraceError::InvalidQrPayload(
            "QR payload exceeds 2048 bytes".to_string(),
        ));
    }
    let parts: Vec<&str> = raw_qr.trim().split('|').collect();
    if parts.len() != 11 {
        return Err(ZeroTraceError::InvalidQrPayload(
            "Expected 11 pipe-delimited fields".to_string(),
        ));
    }
    if parts[0] != QR_MAGIC || parts[1] != QR_VERSION {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }

    let onion_address = parts[2].to_ascii_lowercase();
    ArtiTorController::validate_onion_destination(&onion_address)?;

    let classical_fp_vec = hex::decode(parts[3])
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Bad classical fingerprint hex".into()))?;
    let pq_fp_vec = hex::decode(parts[4])
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Bad PQ fingerprint hex".into()))?;
    let classical_fingerprint: [u8; 32] = classical_fp_vec
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Classical fingerprint length != 32".into()))?;
    let pq_fingerprint: [u8; 32] = pq_fp_vec
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidQrPayload("PQ fingerprint length != 32".into()))?;

    let ed_pk_vec = URL_SAFE_NO_PAD
        .decode(parts[5])
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Invalid Ed25519 pk base64".into()))?;
    let x_pk_vec = URL_SAFE_NO_PAD
        .decode(parts[6])
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Invalid X25519 pk base64".into()))?;
    let ed25519_pk: [u8; ED25519_PK_LEN] = ed_pk_vec
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;
    let x25519_pk: [u8; 32] = x_pk_vec
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidKeyMaterial)?;

    let computed_c_fp = PublicIdentityBundle::compute_classical_fingerprint(&ed25519_pk, &x25519_pk);
    if computed_c_fp != classical_fingerprint {
        return Err(ZeroTraceError::PeerIdentityMismatch);
    }

    let expiry_unix_sec: u64 = parts[7]
        .parse()
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Invalid expiry timestamp".into()))?;
    if expiry_unix_sec <= now_unix_sec || expiry_unix_sec > now_unix_sec + MAX_QR_LIFETIME_SEC + 60 {
        return Err(ZeroTraceError::InvalidQrPayload(
            "Pairing QR artifact has expired or timestamp is out of bounds".into(),
        ));
    }

    let nonce_vec = hex::decode(parts[8])
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Invalid pairing nonce hex".into()))?;
    let pairing_nonce: [u8; 16] = nonce_vec
        .try_into()
        .map_err(|_| ZeroTraceError::InvalidQrPayload("Pairing nonce length != 16".into()))?;

    let capabilities = parts[9].to_string();
    if !capabilities.contains("MLKEM1024") || !capabilities.contains("MLDSA87") {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }

    let sig_vec = URL_SAFE_NO_PAD
        .decode(parts[10])
        .map_err(|_| ZeroTraceError::ClassicalSignatureInvalid)?;
    let sig_arr: [u8; ED25519_SIG_LEN] = sig_vec
        .try_into()
        .map_err(|_| ZeroTraceError::ClassicalSignatureInvalid)?;

    let body = parts[0..10].join("|");
    let auth_msg = format!("ZT2-QR-AUTH-V1|{}", body);
    verify_ed25519(&ed25519_pk, auth_msg.as_bytes(), &sig_arr)?;

    Ok(ParsedPairingQr {
        onion_address,
        classical_fingerprint,
        pq_fingerprint,
        ed25519_pk,
        x25519_pk,
        expiry_unix_sec,
        pairing_nonce,
        capabilities,
    })
}
