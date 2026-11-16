package com.zerotrace.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface BiometricCapability {
    data object ReadyBiometricOrCredential : BiometricCapability
    data object ReadyBiometricOnly : BiometricCapability
    data class Unavailable(val reason: String) : BiometricCapability
}

/**
 * Hardware Fingerprint / Biometric & Device Credential authenticator using AndroidX BiometricPrompt.
 * Handles API 30+ authenticators (BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
 * and API 26-29 safely without throwing IllegalArgumentException on PromptInfo combinations.
 */
object BiometricAuthHelper {

    fun checkCapability(context: Context): BiometricCapability {
        val mgr = BiometricManager.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val statusStrongOrCred = mgr.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            if (statusStrongOrCred == BiometricManager.BIOMETRIC_SUCCESS) {
                return BiometricCapability.ReadyBiometricOrCredential
            }
        }

        val statusBio = mgr.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (statusBio == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricCapability.ReadyBiometricOnly
        }

        @Suppress("DEPRECATION")
        val legacyCred = mgr.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (legacyCred == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricCapability.ReadyBiometricOrCredential
        }

        return when (statusBio) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.Unavailable(
                "No fingerprint or screen lock enrolled on this phone. Add a fingerprint/PIN in Android Settings first."
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.Unavailable(
                "No biometric hardware detected on this device."
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.Unavailable(
                "Biometric sensor is currently unavailable. Try again."
            )
            else -> BiometricCapability.Unavailable(
                "Biometric or device credential authentication is unavailable."
            )
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String = "Confirm your fingerprint to unlock your encrypted ZeroTrace vault.",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val capability = checkCapability(activity)
        if (capability is BiometricCapability.Unavailable) {
            onError(capability.reason)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Android keeps the prompt open until max attempts or cancel; no-op here
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setConfirmationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            capability is BiometricCapability.ReadyBiometricOrCredential
        ) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            builder.setNegativeButtonText("Cancel")
        }

        try {
            prompt.authenticate(builder.build())
        } catch (t: Throwable) {
            onError(t.message ?: "Unable to open biometric prompt")
        }
    }
}
