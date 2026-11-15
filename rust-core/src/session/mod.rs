//! Volatile Session Handle Registry & Emergency Destruction
//!
//! Exposes opaque 64-bit session handles (`u64`) to the FFI/JNI layer so private ratchet keys
//! remain inside the Rust security core.

use crate::crypto::ratchet::HybridRatchetSession;
use crate::memory::EphemeralBytes;
use crate::{ZeroTraceError, ZtResult};
use std::collections::HashMap;
use std::sync::Mutex;

pub type OpaqueSessionHandle = u64;

pub struct EphemeralMessageEntry {
    pub message_id: u64,
    pub is_outgoing: bool,
    pub timestamp_epoch_sec: u64,
    pub plaintext_utf8: EphemeralBytes,
}

pub struct ManagedEphemeralSession {
    pub handle: OpaqueSessionHandle,
    pub peer_onion: String,
    pub ratchet: HybridRatchetSession,
    pub messages: Vec<EphemeralMessageEntry>,
    pub last_activity_sec: u64,
}

impl ManagedEphemeralSession {
    pub fn wipe_all(&mut self) {
        self.ratchet.destroy();
        for msg in self.messages.iter_mut() {
            msg.plaintext_utf8.wipe_now();
        }
        self.messages.clear();
    }
}

pub struct VolatileSessionRegistry {
    next_handle: u64,
    sessions: HashMap<OpaqueSessionHandle, ManagedEphemeralSession>,
}

impl VolatileSessionRegistry {
    pub fn new() -> Self {
        Self {
            next_handle: 1,
            sessions: HashMap::new(),
        }
    }

    pub fn register_session(
        &mut self,
        peer_onion: String,
        ratchet: HybridRatchetSession,
        now_sec: u64,
    ) -> OpaqueSessionHandle {
        let handle = self.next_handle;
        self.next_handle = self.next_handle.wrapping_add(1).max(1);
        self.sessions.insert(
            handle,
            ManagedEphemeralSession {
                handle,
                peer_onion,
                ratchet,
                messages: Vec::new(),
                last_activity_sec: now_sec,
            },
        );
        handle
    }

    pub fn destroy_session(&mut self, handle: OpaqueSessionHandle) -> ZtResult<()> {
        if let Some(mut session) = self.sessions.remove(&handle) {
            session.wipe_all();
            Ok(())
        } else {
            Err(ZeroTraceError::InvalidSessionHandle)
        }
    }

    /// Emergency Lock / Disconnect operation (Section 5.4):
    /// Destroys all active session handles and zeroizes volatile chat buffers.
    pub fn emergency_wipe_all(&mut self) -> usize {
        let count = self.sessions.len();
        for (_, mut session) in self.sessions.drain() {
            session.wipe_all();
        }
        count
    }

    /// Purges sessions that have exceeded `timeout_sec` of inactivity.
    pub fn purge_expired_sessions(&mut self, now_sec: u64, timeout_sec: u64) -> usize {
        let expired_handles: Vec<OpaqueSessionHandle> = self
            .sessions
            .iter()
            .filter(|(_, s)| now_sec.saturating_sub(s.last_activity_sec) >= timeout_sec)
            .map(|(h, _)| *h)
            .collect();
        let count = expired_handles.len();
        for h in expired_handles {
            if let Some(mut s) = self.sessions.remove(&h) {
                s.wipe_all();
            }
        }
        count
    }
}

pub static GLOBAL_SESSION_REGISTRY: Mutex<Option<VolatileSessionRegistry>> = Mutex::new(None);
