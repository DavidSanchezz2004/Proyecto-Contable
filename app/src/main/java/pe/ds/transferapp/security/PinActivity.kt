package pe.ds.transferapp.security

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import pe.ds.transferapp.databinding.ActivityPinBinding
import pe.ds.transferapp.security.SecurityManager

class PinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isSetup = intent.getBooleanExtra(EXTRA_SETUP, false)
        supportActionBar?.title = if (isSetup) "Configurar PIN" else "Ingresar PIN"

        binding.btnOk.setOnClickListener {
            val pin = binding.tilPin.editText?.text?.toString()?.trim().orEmpty()
            if (pin.length !in 4..8 || !pin.all { it.isDigit() }) {
                binding.tilPin.error = "PIN de 4 a 8 dígitos"
                return@setOnClickListener
            }
            if (isSetup) {
                SecurityManager.setPin(this, pin)
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                if (SecurityManager.verifyPin(this, pin)) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    binding.tilPin.error = "PIN incorrecto"
                }
            }
        }

        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        const val EXTRA_SETUP = "extra_setup"
    }
}
