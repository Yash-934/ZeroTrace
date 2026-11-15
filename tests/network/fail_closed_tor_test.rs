//! Network Security Test Suite: Fail-Closed Clearnet Rejection & Loopback Enforcement

use zerotrace_core::network::arti::ArtiTorController;
use zerotrace_core::network::onion::OnionServiceEndpoint;
use zerotrace_core::ZeroTraceError;

#[test]
fn test_fail_closed_rejects_clearnet_ips_and_domains() {
    assert!(matches!(
        ArtiTorController::validate_onion_destination("192.168.1.10:9443"),
        Err(ZeroTraceError::FailClosedNetworkViolation(_))
    ));
    assert!(matches!(
        ArtiTorController::validate_onion_destination("8.8.8.8:443"),
        Err(ZeroTraceError::FailClosedNetworkViolation(_))
    ));
    assert!(matches!(
        ArtiTorController::validate_onion_destination("relay.example.com:443"),
        Err(ZeroTraceError::FailClosedNetworkViolation(_))
    ));

    let valid_onion = OnionServiceEndpoint::derive_v3_onion_address(&[0x55u8; 32]);
    assert!(ArtiTorController::validate_onion_destination(&valid_onion).is_ok());
}

#[test]
fn test_onion_listener_rejects_public_interface_bind() {
    assert!(OnionServiceEndpoint::verify_loopback_only("127.0.0.1").is_ok());
    assert!(OnionServiceEndpoint::verify_loopback_only("0.0.0.0").is_err());
    assert!(OnionServiceEndpoint::verify_loopback_only("10.0.2.15").is_err());
}
