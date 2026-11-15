//! Cryptographic Test Suite: NIST FIPS 203 (ML-KEM-1024) & FIPS 204 (ML-DSA-87) Vectors & Edge Cases

use zerotrace_core::crypto::kem::{ml_kem_1024_encapsulate, ClassicalKemKeyPair, PqKemKeyPair};
use zerotrace_core::crypto::signatures::{
    verify_ed25519, verify_ml_dsa_87, ClassicalSigningKeyPair, PqSigningKeyPair,
};
use zerotrace_core::ZeroTraceError;

#[test]
fn test_ml_kem_1024_encaps_decaps_and_tamper_resistance() {
    let keypair = PqKemKeyPair::generate().expect("ML-KEM-1024 keygen should succeed");
    assert_eq!(keypair.ek_bytes.len(), 1568);

    let (mut ct, ss_sender) =
        ml_kem_1024_encapsulate(&keypair.ek_bytes).expect("Encapsulation should succeed");
    assert_eq!(ct.len(), 1568);

    let ss_receiver = keypair.decapsulate(&ct).expect("Decapsulation should succeed");
    assert_eq!(*ss_sender, *ss_receiver);

    // Tamper with ciphertext (implicit rejection in FIPS 203 produces a pseudorandom key != ss_sender)
    ct[42] ^= 0xFF;
    let ss_tampered = keypair.decapsulate(&ct).expect("Implicit rejection returns key");
    assert_ne!(*ss_sender, *ss_tampered);
}

#[test]
fn test_ml_dsa_87_and_ed25519_dual_signature_verification() {
    let ed_kp = ClassicalSigningKeyPair::generate();
    let pq_kp = PqSigningKeyPair::generate().expect("ML-DSA-87 keygen should succeed");
    let msg = b"ZeroTrace-Transcript-Test-Vector-2026";

    let ed_sig = ed_kp.sign(msg);
    let mut pq_sig = pq_kp.sign(msg).expect("ML-DSA-87 sign should succeed");

    assert!(verify_ed25519(&ed_kp.public_bytes, msg, &ed_sig).is_ok());
    assert!(verify_ml_dsa_87(&pq_kp.pk_bytes, msg, &pq_sig).is_ok());

    // Tamper with ML-DSA-87 signature
    pq_sig[10] ^= 0x01;
    assert_eq!(
        verify_ml_dsa_87(&pq_kp.pk_bytes, msg, &pq_sig),
        Err(ZeroTraceError::PostQuantumSignatureInvalid)
    );
}

#[test]
fn test_x25519_key_agreement() {
    let alice = ClassicalKemKeyPair::generate();
    let bob = ClassicalKemKeyPair::generate();
    let s1 = alice.diffie_hellman(&bob.public_bytes).unwrap();
    let s2 = bob.diffie_hellman(&alice.public_bytes).unwrap();
    assert_eq!(*s1, *s2);
}
