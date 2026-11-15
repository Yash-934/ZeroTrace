//! JNI & FFI Boundary (`libzerotrace_core.so`)
//!
//! Exposes safe, opaque-handle functions to Kotlin (`com.example.bridge.NativeRustBridge`).
//! Unavoidable JNI copies are documented in `docs/LIMITATIONS.md` and minimized by returning
//! opaque `jlong` session handles rather than private keys.

#[cfg(feature = "jni-bridge")]
use jni::objects::{JClass, JString};
#[cfg(feature = "jni-bridge")]
use jni::sys::{jboolean, jint, jlong, jstring, JNI_TRUE};
#[cfg(feature = "jni-bridge")]
use jni::JNIEnv;

use crate::session::{VolatileSessionRegistry, GLOBAL_SESSION_REGISTRY};
use crate::storage::parse_and_verify_pairing_qr;

#[cfg(feature = "jni-bridge")]
#[no_mangle]
pub extern "system" fn Java_com_example_bridge_NativeRustBridge_nativeVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    env.new_string("zerotrace_core 2.0.0 (NIST FIPS 203 ML-KEM-1024 / FIPS 204 ML-DSA-87 / Arti 0.25)")
        .expect("Failed to allocate JNI version string")
        .into_raw()
}

#[cfg(feature = "jni-bridge")]
#[no_mangle]
pub extern "system" fn Java_com_example_bridge_NativeRustBridge_nativeValidatePairingQr(
    mut env: JNIEnv,
    _class: JClass,
    qr_payload: JString,
    now_unix_sec: jlong,
) -> jboolean {
    let qr_str: String = match env.get_string(&qr_payload) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    match parse_and_verify_pairing_qr(&qr_str, now_unix_sec as u64) {
        Ok(_) => JNI_TRUE,
        Err(_) => 0,
    }
}

#[cfg(feature = "jni-bridge")]
#[no_mangle]
pub extern "system" fn Java_com_example_bridge_NativeRustBridge_nativeEmergencyWipeAllSessions(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    let mut guard = GLOBAL_SESSION_REGISTRY.lock().unwrap();
    if let Some(ref mut registry) = *guard {
        registry.emergency_wipe_all() as jint
    } else {
        *guard = Some(VolatileSessionRegistry::new());
        0
    }
}
