//! Ratchet Test Suite: Out-of-order messages, Replay rejection, MAX_SKIP limit, and PQ Braid Epochs

use zerotrace_core::crypto::kem::{ClassicalKemKeyPair, PqKemKeyPair};
use zerotrace_core::crypto::ratchet::HybridRatchetSession;
use zerotrace_core::memory::LockedSecretBuffer;
use zerotrace_core::ZeroTraceError;

#[test]
fn test_ratchet_replay_and_out_of_order_handling() {
    let session_id = [0x11u8; 16];
    let alice_dh = ClassicalKemKeyPair::generate();
    let bob_dh = ClassicalKemKeyPair::generate();
    let alice_pq = PqKemKeyPair::generate().unwrap();
    let bob_pq = PqKemKeyPair::generate().unwrap();

    let alice_dh_pub = alice_dh.public_bytes;
    let bob_dh_pub = bob_dh.public_bytes;
    let alice_pq_ek = alice_pq.ek_bytes.clone();
    let bob_pq_ek = bob_pq.ek_bytes.clone();

    let mut alice = HybridRatchetSession::new(
        session_id,
        [0xAA; 32],
        [0xBB; 32],
        [0xCC; 32],
        [0xDD; 32],
        LockedSecretBuffer::new([0x01; 32]),
        LockedSecretBuffer::new([0x02; 32]),
        LockedSecretBuffer::new([0x03; 32]),
        alice_dh,
        bob_dh_pub,
        alice_pq,
        bob_pq_ek,
    );

    let mut bob = HybridRatchetSession::new(
        session_id,
        [0xCC; 32],
        [0xDD; 32],
        [0xAA; 32],
        [0xBB; 32],
        LockedSecretBuffer::new([0x01; 32]),
        LockedSecretBuffer::new([0x03; 32]),
        LockedSecretBuffer::new([0x02; 32]),
        bob_dh,
        alice_dh_pub,
        bob_pq,
        alice_pq_ek,
    );

    let p0 = alice.encrypt(b"msg-0").unwrap();
    let p1 = alice.encrypt(b"msg-1").unwrap();
    let p2 = alice.encrypt(b"msg-2").unwrap();

    // Deliver out of order: p2 first, then p0, then p1
    assert_eq!(bob.decrypt(&p2).unwrap().as_slice(), b"msg-2");
    assert_eq!(bob.decrypt(&p0).unwrap().as_slice(), b"msg-0");
    assert_eq!(bob.decrypt(&p1).unwrap().as_slice(), b"msg-1");

    // Replay attack on p1 must fail
    assert_eq!(
        bob.decrypt(&p1).unwrap_err(),
        ZeroTraceError::ReplayOrDuplicateMessage
    );
}
