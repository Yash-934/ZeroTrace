package com.zerotrace.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Hardware Security Level detected for the Android Keystore master wrapping key.
 * Never claims StrongBox or TEE hardware backing when running on a software-backed keystore.
 */
enum class HardwareSecurityLevel(val labelEn: String, val labelHi: String, val isHardwareBacked: Boolean) {
    STRONGBOX_HARDWARE(
        labelEn = "StrongBox Dedicated HSM",
        labelHi = "StrongBox समर्पित हार्डवेयर सुरक्षा मॉड्यूल",
        isHardwareBacked = true
    ),
    TEE_HARDWARE(
        labelEn = "TEE Hardware-Backed Keystore",
        labelHi = "TEE हार्डवेयर-समर्थित कीस्टोर",
        isHardwareBacked = true
    ),
    SOFTWARE_FALLBACK(
        labelEn = "Software-Backed Keystore Only",
        labelHi = "केवल सॉफ़्टवेयर-आधारित कीस्टोर",
        isHardwareBacked = false
    ),
    UNINITIALIZED(
        labelEn = "Keystore Wrapping Key Not Initialized",
        labelHi = "कीस्टोर रैपिंग कुंजी प्रारंभ नहीं हुई",
        isHardwareBacked = false
    )
}

object StrongBoxManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Checks whether the device reports hardware feature support for StrongBox Keymaster.
     */
    fun hasStrongBoxFeature(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }

    /**
     * Inspects an existing AndroidKeyStore [SecretKey] to determine its true hardware security backing.
     */
    fun inspectKeySecurityLevel(secretKey: SecretKey, requestedStrongBox: Boolean): HardwareSecurityLevel {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as? KeyInfo
                ?: return HardwareSecurityLevel.SOFTWARE_FALLBACK

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareSecurityLevel.STRONGBOX_HARDWARE
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareSecurityLevel.TEE_HARDWARE
                    else -> HardwareSecurityLevel.SOFTWARE_FALLBACK
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) {
                    if (requestedStrongBox) HardwareSecurityLevel.STRONGBOX_HARDWARE
                    else HardwareSecurityLevel.TEE_HARDWARE
                } else {
                    HardwareSecurityLevel.SOFTWARE_FALLBACK
                }
            }
        } catch (_: Exception) {
            // In JVM Robolectric test environments where AndroidKeyStore is simulated, report SOFTWARE_FALLBACK honestly
            HardwareSecurityLevel.SOFTWARE_FALLBACK
        }
    }
}
