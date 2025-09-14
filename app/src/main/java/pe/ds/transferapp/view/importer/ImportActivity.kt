package pe.ds.transferapp.view.importer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import pe.ds.transferapp.controller.OcrController
import pe.ds.transferapp.controller.ParserController
import pe.ds.transferapp.databinding.ActivityImportBinding
import pe.ds.transferapp.view.Confirm
import pe.ds.transferapp.view.editor.EditorActivity
import java.io.File

class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private var lastUri: Uri? = null
    private var lastBitmap: Bitmap? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lastUri = uri
            handleUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnChoose.setOnClickListener {
            filePicker.launch(arrayOf("application/pdf", "image/*"))
        }

        binding.btnOcr.setOnClickListener {
            val bitmap = lastBitmap
            val uri = lastUri
            if (bitmap == null && uri == null) {
                Confirm.showError(this, "ERROR", "Primero selecciona un archivo.")
                return@setOnClickListener
            }
            lifecycleScope.launch {
                runOcrAndOpenEditor(bitmap, uri)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun handleUri(uri: Uri) {
        val name = getFileName(uri)
        binding.tvFileName.text = name

        if (name.lowercase().endsWith(".pdf")) {
            renderPdf(uri)
        } else {
            binding.ivPreview.setImageURI(uri)
            // también guarda un bitmap para OCR
            lastBitmap = decodeBitmapFromUri(uri)
        }
    }

    private fun renderPdf(uri: Uri) {
        try {
            val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    binding.ivPreview.setImageBitmap(bitmap)
                    lastBitmap = bitmap
                    page.close()
                }
                renderer.close()
            }
        } catch (e: Exception) {
            Confirm.showError(this, "ERROR", "No se pudo renderizar PDF: ${e.message}")
            Log.e("IMPORT", "PDF render error", e)
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        val stream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el stream de la URI")
        return android.graphics.BitmapFactory.decodeStream(stream)
            ?: throw IllegalStateException("No se pudo decodificar el bitmap")
    }

    private suspend fun runOcrAndOpenEditor(bitmap: Bitmap?, uri: Uri?) {
        try {
            val ocr = OcrController(this)
            val ocrRes = if (bitmap != null) {
                ocr.recognizeFromBitmap(bitmap)
            } else {
                ocr.recognizeFromUri(uri!!)
            }

            Log.d("ImportActivity", "Texto OCR completo: ${ocrRes.fullText}")

            if (ocrRes.fullText.isBlank()) {
                Confirm.showError(this, "ERROR", "No se detectó texto en la imagen/PDF.")
                return
            }

            val parser = ParserController(this)
            val parsed = parser.parse(ocrRes.fullText)

            // Extraer ULT4 origen de extras
            var ult4Origen: String? = null
            parsed.extrasJson?.let { extrasJson ->
                try {
                    val extrasObj = JSONObject(extrasJson)
                    ult4Origen = extrasObj.optString("cta_origen_ult4", null)
                } catch (e: Exception) {
                    Log.e("ImportActivity", "Error parsing extras JSON", e)
                }
            }

            // Muestra resumen y abre editor
            val resumen = buildString {
                append("Banco: ").append(parsed.banco.value ?: "-").append('\n')
                append("Fecha: ").append(parsed.fecha.value ?: "-").append('\n')
                append("Hora: ").append(parsed.hora.value ?: "-").append('\n')
                append("Operación: ").append(parsed.nroOperacion.value ?: "-").append('\n')
                append("Beneficiario: ").append(parsed.beneficiario.value ?: "-").append('\n')
                append("ULT4 Destino: ").append(parsed.ult4.value ?: "-").append('\n')
                append("ULT4 Origen: ").append(ult4Origen ?: "-").append('\n')
                append("Titular Origen: ").append(parsed.titularOrigen ?: "-").append('\n')
                append("Importe: ").append(parsed.importe.value ?: "-")
            }

            Confirm.showSuccess(this, "OCR OK", resumen)

            // Ir al editor con autollenado
            startActivity(
                Intent(this, EditorActivity::class.java).apply {
                    putExtra(EditorActivity.EXTRA_PREFILL, true)
                    putExtra(EditorActivity.EXTRA_FECHA, parsed.fecha.value)
                    putExtra(EditorActivity.EXTRA_HORA, parsed.hora.value)
                    putExtra(EditorActivity.EXTRA_BANCO, parsed.banco.value)
                    putExtra(EditorActivity.EXTRA_NRO, parsed.nroOperacion.value)
                    putExtra(EditorActivity.EXTRA_BENEF, parsed.beneficiario.value)
                    putExtra(EditorActivity.EXTRA_ULT4, parsed.ult4.value)
                    putExtra(EditorActivity.EXTRA_IMPORTE, parsed.importe.value)
                    putExtra(EditorActivity.EXTRA_EXTRAS, parsed.extrasJson)
                    putExtra(EditorActivity.EXTRA_TITULAR_ORIG, parsed.titularOrigen)
                    putExtra(EditorActivity.EXTRA_ULT4_ORIG, ult4Origen)
                }
            )
        } catch (e: Exception) {
            Confirm.showError(this, "ERROR", "Fallo OCR/Parser: ${e.message}")
            Log.e("IMPORT", "OCR error", e)
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    result = cursor.getString(idx)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = File(uri.path ?: "").name
        }
        return result ?: "desconocido"
    }
}