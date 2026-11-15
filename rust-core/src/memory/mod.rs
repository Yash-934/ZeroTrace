//! Volatile Memory Management & Best-Effort Page Locking (`mlock`)
//!
//! Security Limitations (explicitly documented per Section 5.2):
//! - `mlock` on Android is subject to unprivileged process `RLIMIT_MEMLOCK` quotas (often 64 KiB).
//! - Even when `mlock` succeeds, it does not prevent physical cold-boot extraction, kernel-level
//!   compromise, or ART JVM heap copies once data crosses into Kotlin `String` objects.

use std::ops::{Deref, DerefMut};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Status of `libc::mlock` on a secret buffer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MemoryLockStatus {
    /// Pages locked via `libc::mlock`
    LockedInRam,
    /// `mlock` failed due to `RLIMIT_MEMLOCK` or platform restriction; relies on `ZeroizeOnDrop`
    ZeroizeOnlyFallback,
}

/// Fixed-size zeroizing buffer that attempts `mlock` on allocation and `munlock` + `zeroize` on drop.
#[derive(Clone, PartialEq, Eq)]
pub struct LockedSecretBuffer<const N: usize> {
    data: [u8; N],
    lock_status: MemoryLockStatus,
}

impl<const N: usize> LockedSecretBuffer<N> {
    pub fn new(initial: [u8; N]) -> Self {
        let mut buf = Self {
            data: initial,
            lock_status: MemoryLockStatus::ZeroizeOnlyFallback,
        };
        buf.try_mlock();
        buf
    }

    pub fn zeroed() -> Self {
        Self::new([0u8; N])
    }

    pub fn lock_status(&self) -> MemoryLockStatus {
        self.lock_status
    }

    fn try_mlock(&mut self) {
        #[cfg(unix)]
        {
            let ptr = self.data.as_ptr() as *const libc::c_void;
            // SAFETY: `self.data` is a valid aligned byte array of length `N`.
            let rc = unsafe { libc::mlock(ptr, N) };
            if rc == 0 {
                self.lock_status = MemoryLockStatus::LockedInRam;
            }
        }
    }

    fn try_munlock(&mut self) {
        #[cfg(unix)]
        if self.lock_status == MemoryLockStatus::LockedInRam {
            let ptr = self.data.as_ptr() as *const libc::c_void;
            // SAFETY: Matches the range previously locked by `mlock`.
            unsafe {
                let _ = libc::munlock(ptr, N);
            }
            self.lock_status = MemoryLockStatus::ZeroizeOnlyFallback;
        }
    }
}

impl<const N: usize> Deref for LockedSecretBuffer<N> {
    type Target = [u8; N];
    fn deref(&self) -> &Self::Target {
        &self.data
    }
}

impl<const N: usize> DerefMut for LockedSecretBuffer<N> {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.data
    }
}

impl<const N: usize> Zeroize for LockedSecretBuffer<N> {
    fn zeroize(&mut self) {
        self.data.zeroize();
    }
}

impl<const N: usize> Drop for LockedSecretBuffer<N> {
    fn drop(&mut self) {
        self.data.zeroize();
        self.try_munlock();
    }
}

impl<const N: usize> ZeroizeOnDrop for LockedSecretBuffer<N> {}

/// Dynamically-sized ephemeral plaintext buffer that wipes its contents on drop.
#[derive(Clone, Default, Zeroize, ZeroizeOnDrop)]
pub struct EphemeralBytes(Vec<u8>);

impl EphemeralBytes {
    pub fn from_slice(slice: &[u8]) -> Self {
        let mut v = Vec::with_capacity(slice.len());
        v.extend_from_slice(slice);
        Self(v)
    }

    pub fn as_slice(&self) -> &[u8] {
        &self.0
    }

    pub fn wipe_now(&mut self) {
        self.0.zeroize();
        self.0.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_locked_secret_buffer_zeroizes() {
        let mut secret = LockedSecretBuffer::<32>::new([0xAA; 32]);
        assert_eq!(secret[0], 0xAA);
        secret.zeroize();
        assert_eq!(*secret, [0u8; 32]);
    }
}
