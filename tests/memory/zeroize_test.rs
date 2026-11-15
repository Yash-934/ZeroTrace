//! Memory Security Test Suite: Volatile Buffer Wiping & Emergency Session Destruction

use zerotrace_core::memory::{EphemeralBytes, LockedSecretBuffer};
use zeroize::Zeroize;

#[test]
fn test_ephemeral_bytes_wipe() {
    let mut msg = EphemeralBytes::from_slice(b"Top-Secret-Ephemeral-Payload");
    assert!(!msg.as_slice().is_empty());
    msg.wipe_now();
    assert!(msg.as_slice().is_empty());
}

#[test]
fn test_locked_buffer_zeroizes_all_bytes() {
    let mut key = LockedSecretBuffer::<32>::new([0xFE; 32]);
    key.zeroize();
    assert_eq!(*key, [0u8; 32]);
}
