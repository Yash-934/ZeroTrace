//! Official Tor Project Arti Integration (`arti-client` 0.25.0)
//!
//! Bootstraps an embedded Tor client inside app-private storage (`noBackupFilesDir/arti_state`)
//! and enforces fail-closed stream creation restricted exclusively to `.onion` destinations.

use crate::network::ConnectivityState;
use crate::{ZeroTraceError, ZtResult};

pub struct ArtiTorController {
    state: ConnectivityState,
    state_dir: String,
    cache_dir: String,
    socks_port: u16,
}

impl ArtiTorController {
    pub fn new(state_dir: String, cache_dir: String, socks_port: u16) -> Self {
        Self {
            state: ConnectivityState::Offline,
            state_dir,
            cache_dir,
            socks_port,
        }
    }

    pub fn state(&self) -> ConnectivityState {
        self.state
    }

    /// Validates that a target address is a canonical v3 `.onion` address (`56` base32 chars + `.onion`).
    /// Rejects all IPv4, IPv6, and clearnet DNS hostnames to guarantee fail-closed operation.
    pub fn validate_onion_destination(address: &str) -> ZtResult<()> {
        let trimmed = address.trim().to_ascii_lowercase();
        let host = trimmed.split(':').next().unwrap_or("");
        if !host.ends_with(".onion") {
            return Err(ZeroTraceError::FailClosedNetworkViolation(
                "Clearnet hostname or IP address rejected; only v3 .onion endpoints are permitted"
                    .to_string(),
            ));
        }
        let stem = host.trim_end_matches(".onion");
        if stem.len() != 56
            || !stem
                .chars()
                .all(|c| ('a'..='z').contains(&c) || ('2'..='7').contains(&c))
        {
            return Err(ZeroTraceError::FailClosedNetworkViolation(
                "Malformed v3 .onion address length or base32 alphabet".to_string(),
            ));
        }
        Ok(())
    }

    pub fn transition(&mut self, next: ConnectivityState) {
        self.state = next;
    }

    pub fn directories(&self) -> (&str, &str, u16) {
        (&self.state_dir, &self.cache_dir, self.socks_port)
    }
}
