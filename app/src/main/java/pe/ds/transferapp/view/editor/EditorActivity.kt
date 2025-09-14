package pe.ds.transferapp.view.editor

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.ds.transferapp.controller.TransferenciaController
import pe.ds.transferapp.controller.TransferenciaController.CreateOrUpdateResult
import pe.ds.transferapp.controller.TransferenciaController.DuplicateType
import pe.ds.transferapp.databinding.ActivityEditorBinding
import pe.ds.transferapp.model.AppDatabase
import pe.ds.transferapp.model.FormatUtils
import pe.ds.transferapp.view.Confirm
import pe.ds.transferapp.security.AuthGatePatternOrPin

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var controller: TransferenciaController

    private var editingId: String? = null  // null => creando

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        controller = TransferenciaController(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editor de transferencia"

        editingId = intent.getStringExtra(ARG_ID)

        lifecycleScope.launch {
            if (editingId != null) {
                loadForEdit(editingId!!)
            } else {
                fillDefaultsForCreate()
                applyPrefillIfAny()
            }
        }

        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                val ok = Confirm.confirmRegister(this@EditorActivity)
                if (ok) saveOrUpdateFlow()
            }
        }

        binding.btnDelete.setOnClickListener {
            val id = editingId
            if (id == null) {
                SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText("ERROR")
                    .setContentText("Aún no hay registro para eliminar.")
                    .setConfirmText("OK")
                    .show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                // 🔐 Patrón/PIN del dispositivo → fallback PIN de la app
                val authOk = AuthGatePatternOrPin.requireAuth(
                    this@EditorActivity,
                    title = "Confirmar eliminación",
                    subtitle = "Patrón/PIN del dispositivo o PIN de la app"
                )
                if (!authOk) return@launch

                val ok = Confirm.confirmDelete(this@EditorActivity)
                if (ok) {
                    val deleted = controller.deleteById(id)
                    if (deleted) {
                        Confirm.showSuccess(this@EditorActivity, "Eliminado", "Se eliminó correctamente.")
                        finish()
                    } else {
                        Confirm.showError(this@EditorActivity, "ERROR", "No se encontró el registro.")
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun loadForEdit(id: String) {
        val dao = AppDatabase.getInstance(this).transferenciaDao()
        val entity = withContext(Dispatchers.IO) { dao.getById(id) }
        if (entity == null) {
            Confirm.showError(this, "ERROR", "No se encontró el registro.")
            finish()
            return
        }
        binding.tilFecha.editText?.setText(entity.fecha)
        binding.tilHora.editText?.setText(entity.hora)
        binding.tilBanco.editText?.setText(entity.banco)
        binding.tilNro.editText?.setText(entity.nro_operacion ?: "")
        binding.tilBeneficiario.editText?.setText(entity.beneficiario)
        binding.tilUlt4.editText?.setText(entity.cta_dest_ult4)
        binding.tilImporte.editText?.setText(entity.importe)
        binding.tilExtras.editText?.setText(entity.extras ?: "")

        // Cargar datos de extras si existen
        entity.extras?.let { extrasJson ->
            try {
                val extrasObj = JSONObject(extrasJson)
                binding.tilTitularOrigen.editText?.setText(extrasObj.optString("titular_origen", ""))
                binding.tilUlt4Origen.editText?.setText(extrasObj.optString("cta_origen_ult4", ""))
            } catch (_: Exception) {
                // Ignorar errores de JSON
            }
        }

        binding.btnDelete.isEnabled = true
    }

    private fun fillDefaultsForCreate() {
        binding.tilFecha.editText?.setText(FormatUtils.nowDate())
        binding.tilHora.editText?.setText(FormatUtils.nowTime())
        binding.btnDelete.isEnabled = false
    }

    private fun applyPrefillIfAny() {
        val hasPrefill = intent.getBooleanExtra(EXTRA_PREFILL, false)
        if (!hasPrefill) return

        fun putIfNotNull(til: com.google.android.material.textfield.TextInputLayout, v: String?) {
            if (!v.isNullOrBlank()) til.editText?.setText(v)
        }

        putIfNotNull(binding.tilFecha, intent.getStringExtra(EXTRA_FECHA))
        putIfNotNull(binding.tilHora, intent.getStringExtra(EXTRA_HORA))
        putIfNotNull(binding.tilBanco, intent.getStringExtra(EXTRA_BANCO))
        putIfNotNull(binding.tilNro, intent.getStringExtra(EXTRA_NRO))
        putIfNotNull(binding.tilBeneficiario, intent.getStringExtra(EXTRA_BENEF))
        putIfNotNull(binding.tilUlt4, intent.getStringExtra(EXTRA_ULT4))
        putIfNotNull(binding.tilImporte, intent.getStringExtra(EXTRA_IMPORTE))
        putIfNotNull(binding.tilExtras, intent.getStringExtra(EXTRA_EXTRAS))
        putIfNotNull(binding.tilTitularOrigen, intent.getStringExtra(EXTRA_TITULAR_ORIG))
        putIfNotNull(binding.tilUlt4Origen, intent.getStringExtra(EXTRA_ULT4_ORIG))
    }

    private suspend fun saveOrUpdateFlow() {
        val titularOrigen = binding.tilTitularOrigen.editText?.text?.toString()?.trim()
        val ult4Origen = binding.tilUlt4Origen.editText?.text?.toString()?.trim()
        val extrasInput = binding.tilExtras.editText?.text?.toString()?.trim()?.ifEmpty { null }
        val extrasMerged = mergeExtras(extrasInput, titularOrigen, ult4Origen)

        val form = TransferenciaController.TransferenciaForm(
            id = editingId,
            fecha = binding.tilFecha.editText?.text?.toString()?.trim().orEmpty(),
            hora = binding.tilHora.editText?.text?.toString()?.trim().orEmpty(),
            banco = binding.tilBanco.editText?.text?.toString()?.trim().orEmpty(),
            nro_operacion = binding.tilNro.editText?.text?.toString()?.trim()?.ifEmpty { null },
            beneficiario = binding.tilBeneficiario.editText?.text?.toString()?.trim().orEmpty(),
            cta_dest_ult4 = binding.tilUlt4.editText?.text?.toString()?.trim().orEmpty(),
            importe = binding.tilImporte.editText?.text?.toString()?.trim().orEmpty(),
            extras = extrasMerged
        )

        when (val res = controller.createOrUpdateFromForm(form, replaceIfDuplicate = false)) {
            is CreateOrUpdateResult.ValidationError -> {
                Confirm.showError(this, "ERROR", res.message)
            }
            is CreateOrUpdateResult.DuplicateFound -> {
                val strict = res.type is DuplicateType.PRIMARY
                val ok = Confirm.confirmReplaceDuplicate(this, strict)
                if (ok) {
                    // 🔐 Patrón/PIN del dispositivo → fallback PIN de la app
                    val authOk = AuthGatePatternOrPin.requireAuth(
                        this@EditorActivity,
                        title = "Reemplazar duplicado",
                        subtitle = "Patrón/PIN del dispositivo o PIN de la app"
                    )
                    if (!authOk) return
                    val formReplace = form.copy(id = res.existing.id)
                    when (val res2 = controller.createOrUpdateFromForm(formReplace, replaceIfDuplicate = true)) {
                        is CreateOrUpdateResult.Success -> {
                            editingId = res2.id
                            Confirm.showSuccess(this, "Éxito", "Se reemplazó el duplicado.")
                            finish()
                        }
                        is CreateOrUpdateResult.ValidationError -> Confirm.showError(this, "ERROR", res2.message)
                        is CreateOrUpdateResult.DuplicateFound -> {
                            Confirm.showError(this, "ERROR", "Conflicto de duplicado. Intenta nuevamente.")
                        }
                    }
                } else {
                    val formNew = form.copy(id = null)
                    when (val res2 = controller.createOrUpdateFromForm(formNew, replaceIfDuplicate = false)) {
                        is CreateOrUpdateResult.Success -> {
                            editingId = res2.id
                            Confirm.showSuccess(this, "Éxito", "Se creó un nuevo registro.")
                            finish()
                        }
                        is CreateOrUpdateResult.ValidationError -> Confirm.showError(this, "ERROR", res2.message)
                        is CreateOrUpdateResult.DuplicateFound -> {
                            Confirm.showError(this, "ERROR", "Sigue detectándose duplicado. Revisa los datos.")
                        }
                    }
                }
            }
            is CreateOrUpdateResult.Success -> {
                editingId = res.id
                Confirm.showSuccess(this, "Éxito", if (res.replaced) "Se reemplazó el duplicado." else "Guardado correctamente.")
                finish()
            }
        }
    }

    private fun mergeExtras(existing: String?, titularOrigen: String?, ult4Origen: String?): String? {
        val obj = try {
            if (existing.isNullOrBlank()) JSONObject()
            else JSONObject(existing)
        } catch (_: Exception) {
            JSONObject()
        }

        if (!titularOrigen.isNullOrBlank()) {
            obj.put("titular_origen", titularOrigen)
        }

        if (!ult4Origen.isNullOrBlank()) {
            obj.put("cta_origen_ult4", ult4Origen)
        }

        return if (obj.length() == 0) null else obj.toString()
    }

    companion object {
        const val ARG_ID = "arg_id"

        const val EXTRA_PREFILL = "extra_prefill"
        const val EXTRA_FECHA = "extra_fecha"
        const val EXTRA_HORA = "extra_hora"
        const val EXTRA_BANCO = "extra_banco"
        const val EXTRA_NRO = "extra_nro"
        const val EXTRA_BENEF = "extra_benef"
        const val EXTRA_ULT4 = "extra_ult4"
        const val EXTRA_IMPORTE = "extra_importe"
        const val EXTRA_EXTRAS = "extra_extras"
        const val EXTRA_TITULAR_ORIG = "extra_titular_origen"
        const val EXTRA_ULT4_ORIG = "extra_ult4_origen"
    }
}
