//! Protocol Test Suite: ZT-PQXDH-P2P-v1 Handshake, MITM, Downgrade, and Identity Substitution

use zerotrace_core::handshake::{respond_to_handshake, InitiatorHandshakeState};
use zerotrace_core::identity::DeviceIdentity;
use zerotrace_core::ZeroTraceError;

#[test]
fn test_hybrid_pq_handshake_and_mitm_rejection() {
    let alice_id = DeviceIdentity::generate().unwrap();
    let bob_id = DeviceIdentity::generate().unwrap();
    let mallory_id = DeviceIdentity::generate().unwrap();

    let alice_pub = alice_id.public_bundle();
    let bob_pub = bob_id.public_bundle();
    let pairing_nonce = [0x42u8; 16];

    let (alice_state, mut init_msg) = InitiatorHandshakeState::initiate(
        &alice_id,
        bob_pub.classical_fingerprint,
        bob_pub.pq_fingerprint,
        pairing_nonce,
    )
    .unwrap();

    // 1. Verify MITM identity substitution is rejected
    let mallory_attempt = respond_to_handshake(
        &bob_id,
        mallory_id.public_bundle().classical_fingerprint,
        mallory_id.public_bundle().pq_fingerprint,
        pairing_nonce,
    &init_msg,
    );
    assert!(matches!(mallory_attempt, Err(ZeroTraceError::PeerIdentityMismatch)));

    // 2. Verify protocol downgrade attack is rejected
    init_msg.protocol_version = 1;
    let downgrade_attempt = respond_to_handshake(
        &bob_id,
        alice_pub.classical_fingerprint,
        alice_pub.pq_fingerprint,
        pairing_nonce,
        &init_msg,
    );
    assert!(matches!(
        downgrade_attempt,
        Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch)
    ));

    // Restore valid version and complete handshake
    init_msg.protocol_version = 2;
    let (mut bob_session, resp_msg) = respond_to_handshake(
        &bob_id,
        alice_pub.classical_fingerprint,
        alice_pub.pq_fingerprint,
        pairing_nonce,
        &init_msg,
    )
    .unwrap();

    let mut alice_session = alice_state.finalize(&alice_id, &resp_msg).unwrap();
    let pkt = alice_session.encrypt(b"Hello over PQ Hybrid Ratchet").unwrap();
    let decrypted = bob_session.decrypt(&pkt).unwrap();
    assert_eq!(decrypted.as_slice(), b"Hello over PQ Hybrid Ratchet");
}
