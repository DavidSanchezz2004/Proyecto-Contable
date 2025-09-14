package pe.ds.transferapp.security

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AuthGate {

    /**
     * Pide biometría; si el usuario cancela o no está disponible, usa PIN.
     * Si no hay PIN configurado, abre flujo de configuración y luego verificación.
     */
    suspend fun requireStrongAuth(activity: AppCompatActivity, title: String, subtitle: String?): Boolean {
        // 1) Intentar biometría si está disponible
        if (BiometricAuth.isAvailable(activity)) {
            val okBio = BiometricAuth.authenticate(activity, title, subtitle)
            if (okBio) return true
            // si falla/cancela, sigue a PIN
        }

        // 2) PIN (setup si no existe)
        val needSetup = !SecurityManager.hasPin(activity)
        if (needSetup) {
            val setupOk = launchForResult(activity, Intent(activity, PinActivity::class.java).putExtra(PinActivity.EXTRA_SETUP, true))
            if (!setupOk) return false
        }
        val verifyOk = launchForResult(activity, Intent(activity, PinActivity::class.java))
        return verifyOk
    }

    private suspend fun launchForResult(activity: AppCompatActivity, intent: Intent): Boolean =
        suspendCancellableCoroutine { cont ->
            val launcher = activity.activityResultRegistry.register(
                "auth_${System.currentTimeMillis()}",
                ActivityResultContracts.StartActivityForResult()
            ) { res ->
                cont.resume(res.resultCode == Activity.RESULT_OK)
            }
            activity.lifecycle.addObserver(LauncherCleaner(launcher))
            launcher.launch(intent)
        }
}
