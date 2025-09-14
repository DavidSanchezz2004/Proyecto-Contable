package pe.ds.transferapp.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityManager {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_PIN = "app_pin"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun hasPin(context: Context): Boolean =
        prefs(context).contains(KEY_PIN)

    fun setPin(context: Context, pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN inválido" }
        prefs(context).edit().putString(KEY_PIN, pin).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean =
        prefs(context).getString(KEY_PIN, null) == pin
}
