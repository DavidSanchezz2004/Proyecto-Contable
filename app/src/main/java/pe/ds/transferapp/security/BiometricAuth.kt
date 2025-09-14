package pe.ds.transferapp.security

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BiometricAuth {

    fun isAvailable(activity: AppCompatActivity): Boolean {
        val mgr = BiometricManager.from(activity)
        val res = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        return res == BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(activity: AppCompatActivity, title: String, subtitle: String?): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!cont.isCompleted) cont.resume(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!cont.isCompleted) cont.resume(false)
                }
                override fun onAuthenticationFailed() { /* sigue esperando */ }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle ?: "")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                .setNegativeButtonText("Usar PIN")
                .build()
            prompt.authenticate(info)
        }
}
