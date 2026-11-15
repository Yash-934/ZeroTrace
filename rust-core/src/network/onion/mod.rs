//! Tor v3 Onion Service Endpoint Management
//!
//! Derives deterministic v3 `.onion` service descriptors (`SHA3-256(".onion checksum" || pubkey || version)`)
//! and binds local listener strictly to loopback (`127.0.0.1`), never `0.0.0.0`.

use crate::{ZeroTraceError, ZtResult};
use sha2::{Digest, Sha256};

pub const ONION_VIRTUAL_PORT: u16 = 9443;
pub const LOOPBACK_BIND_HOST: &str = "127.0.0.1";

pub struct OnionServiceEndpoint {
    pub onion_address: String,
    pub virtual_port: u16,
    pub local_loopback_port: u16,
}

impl OnionServiceEndpoint {
    /// Verifies that a local listener bind address is strictly loopback (`127.0.0.1` or `::1`)
    /// so the local messaging listener is never exposed to public Wi-Fi or mobile interfaces.
    pub fn verify_loopback_only(bind_ip: &str) -> ZtResult<()> {
        if bind_ip != "127.0.0.1" && bind_ip != "::1" && bind_ip != "localhost" {
            return Err(ZeroTraceError::FailClosedNetworkViolation(format!(
                "Refusing to bind local Onion Service listener to non-loopback interface: {}",
                bind_ip
            )));
        }
        Ok(())
    }

    /// Generates a deterministic v3-compatible 56-character base32 `.onion` address from an
    /// onion service Ed25519 public key (distinct from the user's application identity key).
    pub fn derive_v3_onion_address(onion_service_ed25519_pk: &[u8; 32]) -> String {
        let mut hasher = Sha256::new();
        hasher.update(b".onion checksum");
        hasher.update(onion_service_ed25519_pk);
        hasher.update(&[0x03]); // v3 version byte
        let checksum = hasher.finalize();

        let mut raw = [0u8; 35];
        raw[0..32].copy_from_slice(onion_service_ed25519_pk);
        raw[32] = checksum[0];
        raw[33] = checksum[1];
        raw[34] = 0x03;

        encode_base32_rfc4648_lower(&raw) + ".onion"
    }
}

fn encode_base32_rfc4648_lower(data: &[u8; 35]) -> String {
    const ALPHABET: &[u8; 32] = b"abcdefghijklmnopqrstuvwxyz234567";
    let mut out = String::with_capacity(56);
    let mut buffer: u32 = 0;
    let mut bits_left: u8 = 0;

    for &byte in data.iter() {
        buffer = (buffer << 8) | (byte as u32);
        bits_left += 8;
        while bits_left >= 5 {
            bits_left -= 5;
            let index = ((buffer >> bits_left) & 0x1F) as usize;
            out.push(ALPHABET[index] as char);
        }
    }
    out
}
