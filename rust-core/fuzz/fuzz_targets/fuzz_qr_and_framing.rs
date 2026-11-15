#![no_main]

use libfuzzer_sys::fuzz_target;
use zerotrace_core::crypto::ratchet::EncryptedRatchetPacket;
use zerotrace_core::network::arti::ArtiTorController;
use zerotrace_core::network::peer::decode_wire_frame;
use zerotrace_core::storage::parse_and_verify_pairing_qr;

fuzz_target!(|data: &[u8]| {
    // 1. Fuzz Wire Framing Decoder (must never panic on arbitrary untrusted peer bytes)
    let _ = decode_wire_frame(data);

    // 2. Fuzz Serialized Ratchet Packet Deserialization
    let _ = bincode::deserialize::<EncryptedRatchetPacket>(data);

    // 3. Fuzz QR Pairing Parser & Onion Address Validator
    if let Ok(utf8_str) = std::str::from_utf8(data) {
        let _ = parse_and_verify_pairing_qr(utf8_str, 1_763_200_000);
        let _ = ArtiTorController::validate_onion_destination(utf8_str);
    }
});
