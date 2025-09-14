package pe.ds.transferapp.security

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CredentialAuth {

    fun isDeviceCredentialAvailable(activity: AppCompatActivity): Boolean {
        val mgr = androidx.biometric.BiometricManager.from(activity)
        val res = mgr.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return res == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(
        activity: AppCompatActivity,
        title: String,
        subtitle: String?
    ): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                if (!cont.isCompleted) cont.resume(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!cont.isCompleted) cont.resume(false)
            }
            override fun onAuthenticationFailed() { }
        }

        val prompt = androidx.biometric.BiometricPrompt(activity, executor, callback)

        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle ?: "")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}
