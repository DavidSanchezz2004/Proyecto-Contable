package pe.ds.transferapp.view.export

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlinx.coroutines.launch
import pe.ds.transferapp.controller.ExportController
import pe.ds.transferapp.databinding.ActivityExportBinding
import pe.ds.transferapp.model.FormatUtils
import pe.ds.transferapp.view.Confirm
import pe.ds.transferapp.security.AuthGate


class ExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportBinding
    private lateinit var exportController: ExportController
    private var pendingBytes: ByteArray? = null
    private var selectedRows: List<pe.ds.transferapp.model.Transferencia> = emptyList()

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = res.data?.data
            if (uri != null) {
                lifecycleScope.launch { writeAndFinish(uri) }
            } else {
                Confirm.showError(this, "ERROR", "No se seleccionó ubicación.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exportController = ExportController(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Exportar a Excel"

        // ✅ Prefill: desde hace 1 mes hasta hoy (zona local)
        val today = FormatUtils.nowDate()
        val monthAgo = FormatUtils.dateMonthsAgo(1)
        binding.tilDesde.editText?.setText(monthAgo)
        binding.tilHasta.editText?.setText(today)

        // Por defecto: solo pendientes
        binding.cbSoloPendientes.isChecked = true

        binding.btnExport.setOnClickListener {
            lifecycleScope.launch { startExportFlow() }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun startExportFlow() {
        val sel = ExportController.ExportSelection(
            soloPendientes = binding.cbSoloPendientes.isChecked,
            fechaDesde = binding.tilDesde.editText?.text?.toString()?.trim()?.ifEmpty { null },
            fechaHasta = binding.tilHasta.editText?.text?.toString()?.trim()?.ifEmpty { null }
        )

        selectedRows = exportController.selectForExport(sel)
        val count = selectedRows.size
        if (count == 0) {
            Confirm.showError(this, "ERROR", "No hay registros para exportar con ese criterio.")
            return
        }

        // 🔐 Paso de seguridad agregado en PASO 8
        val authOk = AuthGate.requireStrongAuth(this, "Autorizar exportación", "Biometría o PIN")
        if (!authOk) return

        val ok = Confirm.confirmExport(this, count)
        if (!ok) return

        pendingBytes = exportController.buildWorkbookBytes(selectedRows)

        val suggested = "Transferencias_${todayFileNamePart()}.xlsx"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_TITLE, suggested)
        }
        createFileLauncher.launch(intent)
    }


    private fun todayFileNamePart(): String {
        val d = FormatUtils.nowDate()
        val t = pe.ds.transferapp.model.FormatUtils.nowTime().replace(":", "")
        return "${d}_$t"
    }

    private suspend fun writeAndFinish(uri: Uri) {
        val bytes = pendingBytes ?: run {
            Confirm.showError(this, "ERROR", "No hay contenido para exportar.")
            return
        }
        contentResolver.openOutputStream(uri)?.use { os ->
            os.write(bytes); os.flush()
        } ?: run {
            Confirm.showError(this, "ERROR", "No se pudo abrir destino.")
            return
        }

        exportController.markExported(selectedRows)

        SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
            .setTitleText("Exportación completa")
            .setContentText("Se generó el archivo y se marcaron como exportados.")
            .setConfirmText("OK")
            .show()

        finish()
    }
}
