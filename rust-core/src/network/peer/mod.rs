//! Length-Prefixed Binary Wire Framing for Peer-to-Peer Onion Streams
//!
//! Frame Format:
//! - Magic (`4` bytes): `b"ZT2F"`
//! - Frame Type (`1` byte): `0x01` HandshakeInit, `0x02` HandshakeResp, `0x03` RatchetMsg, `0x04` Close
//! - Payload Length (`4` bytes big-endian): Bounded to `MAX_FRAME_LEN = 65_536` bytes
//! - Payload (`N` bytes)

use crate::{ZeroTraceError, ZtResult};

pub const FRAME_MAGIC: &[u8; 4] = b"ZT2F";
pub const MAX_FRAME_LEN: usize = 65_536;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum WireFrameType {
    HandshakeInit = 0x01,
    HandshakeResponse = 0x02,
    RatchetCiphertext = 0x03,
    SessionClose = 0x04,
}

impl WireFrameType {
    pub fn from_byte(b: u8) -> ZtResult<Self> {
        match b {
            0x01 => Ok(Self::HandshakeInit),
            0x02 => Ok(Self::HandshakeResponse),
            0x03 => Ok(Self::RatchetCiphertext),
            0x04 => Ok(Self::SessionClose),
            _ => Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch),
        }
    }
}

pub fn encode_wire_frame(frame_type: WireFrameType, payload: &[u8]) -> ZtResult<Vec<u8>> {
    if payload.len() > MAX_FRAME_LEN {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }
    let mut out = Vec::with_capacity(9 + payload.len());
    out.extend_from_slice(FRAME_MAGIC);
    out.push(frame_type as u8);
    out.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    out.extend_from_slice(payload);
    Ok(out)
}

pub fn decode_wire_frame(raw: &[u8]) -> ZtResult<(WireFrameType, &[u8])> {
    if raw.len() < 9 {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }
    if &raw[0..4] != FRAME_MAGIC {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }
    let frame_type = WireFrameType::from_byte(raw[4])?;
    let declared_len = u32::from_be_bytes([raw[5], raw[6], raw[7], raw[8]]) as usize;
    if declared_len > MAX_FRAME_LEN || raw.len() != 9 + declared_len {
        return Err(ZeroTraceError::ProtocolDowngradeOrVersionMismatch);
    }
    Ok((frame_type, &raw[9..9 + declared_len]))
}
