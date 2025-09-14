package pe.ds.transferapp.security

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Intenta primero acreditar con el Credential del dispositivo (patrón/PIN/password del sistema).
 * Si no está disponible o el usuario cancela/falla, usa el PIN de la app como respaldo.
 */
object AuthGatePatternOrPin {

    suspend fun requireAuth(activity: AppCompatActivity, title: String, subtitle: String? = null): Boolean {
        // 1) Intentar credential del dispositivo (sin biometría)
        if (CredentialAuth.isDeviceCredentialAvailable(activity)) {
            val ok = CredentialAuth.authenticate(activity, title, subtitle)
            if (ok) return true
            // si cancela/falla, pasamos al PIN de la app
        }

        // 2) PIN app (setup si aún no existe)
        if (!SecurityManager.hasPin(activity)) {
            val setupOk = launchForResult(
                activity,
                Intent(activity, PinActivity::class.java).putExtra(PinActivity.EXTRA_SETUP, true)
            )
            if (!setupOk) return false
        }
        return launchForResult(activity, Intent(activity, PinActivity::class.java))
    }

    // helper suspend para startActivityForResult
    private suspend fun launchForResult(activity: AppCompatActivity, intent: Intent): Boolean =
        suspendCancellableCoroutine { cont ->
            val key = "auth_${System.currentTimeMillis()}"
            val launcher = activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.StartActivityForResult()
            ) { res ->
                cont.resume(res.resultCode == Activity.RESULT_OK)
            }
            activity.lifecycle.addObserver(LauncherCleaner(launcher))
            launcher.launch(intent)
        }
}
