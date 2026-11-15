//! ZeroTrace Messenger 2.0 — Rust Security Core (`libzerotrace_core.so`)
//!
//! Responsibilities:
//! - Hybrid Classical (`Ed25519` + `X25519`) and Post-Quantum (`ML-DSA-87` + `ML-KEM-1024`) identity generation.
//! - Authenticated Serverless Hybrid PQ Handshake (`ZT-PQXDH-P2P-v1`).
//! - Continuous Hybrid Double Ratchet + Periodic `ML-KEM-1024` Braid Ratchet.
//! - Volatile RAM-only session registry with `ZeroizeOnDrop` and best-effort `mlock`.
//! - Tor Onion Service v3 management via Arti with strict fail-closed network policy.
//! - Opaque handle FFI/JNI interface that never exposes private keys to UI layers.

#![forbid(unsafe_op_in_unsafe_fn)]
#![deny(unused_must_use)]

pub mod crypto;
pub mod ffi;
pub mod handshake;
pub mod identity;
pub mod memory;
pub mod network;
pub mod session;
pub mod storage;

use thiserror::Error;

/// Unified error type for all cryptographic, protocol, memory, and Tor operations.
#[derive(Debug, Error, PartialEq, Eq, Clone)]
pub enum ZeroTraceError {
    #[error("CSPRNG entropy failure")]
    CsprngFailure,
    #[error("Invalid key length or malformed public key encoding")]
    InvalidKeyMaterial,
    #[error("Classical Ed25519 signature verification failed")]
    ClassicalSignatureInvalid,
    #[error("Post-Quantum ML-DSA-87 signature verification failed")]
    PostQuantumSignatureInvalid,
    #[error("ML-KEM-1024 encapsulation or decapsulation failure")]
    KemOperationFailed,
    #[error("AEAD ChaCha20-Poly1305 authentication tag verification failed")]
    AeadDecryptionFailed,
    #[error("Protocol version unsupported or downgrade attempt detected")]
    ProtocolDowngradeOrVersionMismatch,
    #[error("Replay attack or duplicate message counter detected")]
    ReplayOrDuplicateMessage,
    #[error("Out-of-order message gap exceeds MAX_SKIP limit ({0})")]
    MaxSkipExceeded(u64),
    #[error("Peer identity fingerprint mismatch against pinned contact")]
    PeerIdentityMismatch,
    #[error("QR pairing payload expired or malformed: {0}")]
    InvalidQrPayload(String),
    #[error("Tor network not ready or clearnet address rejected (Fail-Closed Policy)")]
    FailClosedNetworkViolation(String),
    #[error("Session handle is invalid, expired, or destroyed")]
    InvalidSessionHandle,
    #[error("Storage envelope integrity check failed")]
    StorageEnvelopeCorrupted,
}

pub type ZtResult<T> = Result<T, ZeroTraceError>;
