//! Tor Onion Service & Fail-Closed Peer Transport Module
//!
//! Enforces strict fail-closed network rules (Section 4.2):
//! - No direct clearnet IP connections.
//! - No unencrypted WebSocket or HTTPS fallback.
//! - Explicit state machine (`ConnectivityState`).

pub mod arti;
pub mod onion;
pub mod peer;

use serde::{Deserialize, Serialize};

/// Explicit Connectivity States mandated by Section 4.3.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ConnectivityState {
    Offline,
    Initializing,
    TorBootstrapping,
    TorReady,
    OnionServiceStarting,
    OnionServiceReady,
    ConnectingToPeer,
    AuthenticatingPeer,
    E2eeSessionReady,
    Disconnecting,
    Error,
}
