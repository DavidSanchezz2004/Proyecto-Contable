package pe.ds.transferapp.security

import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class LauncherCleaner(private val launcher: ActivityResultLauncher<*>) : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
        try { launcher.unregister() } catch (_: Exception) {}
    }
}
