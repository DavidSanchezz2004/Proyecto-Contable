package pe.ds.transferapp.view

import android.content.Context
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helpers suspend para confirmar acciones con SweetAlertDialog.
 * Usan los textos obligatorios del alcance.
 */
object Confirm {

    suspend fun confirmRegister(context: Context): Boolean = confirmWarning(
        context,
        title = "¿Registrar esta transferencia?",
        text = "Se guardará en tu base local."
    )

    suspend fun confirmUpdate(context: Context): Boolean = confirmWarning(
        context,
        title = "¿Guardar cambios?",
        text = "Se actualizará el registro en tu base local."
    )

    suspend fun confirmDelete(context: Context): Boolean = confirmWarning(
        context,
        title = "¿Eliminar este registro?",
        text = "Podrás restaurarlo desde la Papelera."
    )

    suspend fun confirmExport(context: Context, count: Int): Boolean = confirmWarning(
        context,
        title = "¿Exportar $count registro(s) a Excel?",
        text = "Se generará el archivo y se marcarán como exportados."
    )

    suspend fun confirmReplaceDuplicate(context: Context, strict: Boolean): Boolean {
        val title = if (strict) "Duplicado detectado" else "Posible duplicado"
        val text = if (strict) {
            "Ya existe con el mismo Banco + Nº de operación. ¿Reemplazar el existente?"
        } else {
            "Coincidencia por fecha/importe/últimos 4. ¿Reemplazar o crear nuevo?"
        }
        return confirmWarning(context, title, text, confirmText = "Reemplazar", cancelText = "Crear nuevo")
    }

    private suspend fun confirmWarning(
        context: Context,
        title: String,
        text: String,
        confirmText: String = "Confirmar",
        cancelText: String = "Cancelar"
    ): Boolean = suspendCancellableCoroutine { cont ->
        val dialog = SweetAlertDialog(context, SweetAlertDialog.WARNING_TYPE)
            .setTitleText(title)
            .setContentText(text)
            .setConfirmText(confirmText)
            .setCancelText(cancelText)
            .setConfirmClickListener {
                it.dismissWithAnimation()
                cont.resume(true)
            }
            .setCancelClickListener {
                it.dismissWithAnimation()
                cont.resume(false)
            }
        dialog.setOnDismissListener {
            if (cont.isActive) cont.resume(false)
        }
        dialog.show()
        cont.invokeOnCancellation { dialog.dismissWithAnimation() }
    }

    fun showSuccess(context: Context, title: String, text: String = "Operación completada.") {
        SweetAlertDialog(context, SweetAlertDialog.SUCCESS_TYPE)
            .setTitleText(title)
            .setContentText(text)
            .setConfirmText("OK")
            .show()
    }

    fun showError(context: Context, title: String, text: String) {
        SweetAlertDialog(context, SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(text)
            .setConfirmText("OK")
            .show()
    }
}
