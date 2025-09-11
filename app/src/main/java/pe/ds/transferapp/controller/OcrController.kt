package pe.ds.transferapp.controller

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.tasks.await
import java.io.InputStream

class OcrController(private val context: Context) {

    data class OcrResult(
        val fullText: String,
        val blocks: List<String>
    )

    suspend fun recognizeFromBitmap(bitmap: Bitmap): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val result: Text = recognizer.process(image).await()
        return OcrResult(
            fullText = result.text,
            blocks = result.textBlocks.map { it.text }
        )
    }

    suspend fun recognizeFromUri(uri: Uri): OcrResult {
        val bitmap = decodeBitmapFromUri(context.contentResolver, uri)
        return recognizeFromBitmap(bitmap)
    }

    private fun decodeBitmapFromUri(cr: ContentResolver, uri: Uri): Bitmap {
        val stream: InputStream = cr.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el stream de la URI")
        return android.graphics.BitmapFactory.decodeStream(stream)
            ?: throw IllegalStateException("No se pudo decodificar el bitmap")
    }
}
