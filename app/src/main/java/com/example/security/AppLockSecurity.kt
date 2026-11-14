package com.example.security

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

enum class BiometricCapability(val label: String, val isAvailable: Boolean) {
    AVAILABLE("Fingerprint / Biometric ready on this device", true),
    NONE_ENROLLED("No fingerprint or biometric enrolled in device Settings", false),
    UNAVAILABLE("Biometric hardware unavailable on this device", false)
}

object AppLockSecurity {

    private val random = SecureRandom()

    fun createSaltedHash(password: String): Pair<String, String> {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = hashWithSalt(password, salt)
        return saltB64 to hashB64
    }

    fun verifyPassword(
        password: String,
        saltBase64: String,
        expectedHashBase64: String
    ): Boolean {
        if (password.isEmpty() || saltBase64.isBlank() || expectedHashBase64.isBlank()) {
            return false
        }
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val actualHash = hashBytes(password, salt)
            val expectedHash = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun hashWithSalt(password: String, salt: ByteArray): String =
        Base64.encodeToString(hashBytes(password, salt), Base64.NO_WRAP)

    private fun hashBytes(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(StandardCharsets.UTF_8))
    }

    fun biometricStatus(context: Context): BiometricCapability {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NONE_ENROLLED
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    fun launchBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Unlock Typerter",
        subtitle: String = "Verify with your fingerprint or biometric credential",
        negativeButtonText: String = "Use Password",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Fingerprint not recognized. Try again or use your password.")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
