package pe.ds.transferapp.controller

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.ds.transferapp.model.AppDatabase
import pe.ds.transferapp.model.FormatUtils
import pe.ds.transferapp.model.Transferencia
import java.util.Locale

/**
 * Controller (reglas de negocio) para Transferencia.
 * - Valida y normaliza
 * - Dedupe principal: banco + nro_operacion (si nro no es null)
 * - Dedupe fallback: fecha + importe + cta_dest_ult4
 */
class TransferenciaController(context: Context) {

    private val dao = AppDatabase.getInstance(context).transferenciaDao()

    /** Formulario "plano" proveniente de la vista */
    data class TransferenciaForm(
        val id: String? = null,           // null -> create ; no-null -> update
        val fecha: String,
        val hora: String,
        val banco: String,
        val nro_operacion: String?,       // puede ser null
        val beneficiario: String,
        val cta_dest_ult4: String,
        val importe: String,              // "PEN 921.88"
        val extras: String? = null
    )

    sealed class DuplicateType { object PRIMARY: DuplicateType(); object FALLBACK: DuplicateType() }

    sealed class CreateOrUpdateResult {
        data class Success(val id: String, val replaced: Boolean) : CreateOrUpdateResult()
        data class ValidationError(val message: String) : CreateOrUpdateResult()
        data class DuplicateFound(
            val existing: Transferencia,
            val type: DuplicateType
        ) : CreateOrUpdateResult()
    }

    /**
     * Crea o actualiza desde un form. Si detecta duplicado:
     *  - Si replaceIfDuplicate == true -> reemplaza (upsert)
     *  - Si replaceIfDuplicate == false -> devuelve DuplicateFound para que la Vista confirme
     */
    suspend fun createOrUpdateFromForm(
        form: TransferenciaForm,
        replaceIfDuplicate: Boolean
    ): CreateOrUpdateResult = withContext(Dispatchers.IO) {
        // 1) Validaciones
        if (!FormatUtils.isValidFecha(form.fecha)) {
            return@withContext CreateOrUpdateResult.ValidationError("Fecha inválida o futura.")
        }
        if (!FormatUtils.isValidHora(form.hora)) {
            return@withContext CreateOrUpdateResult.ValidationError("Hora inválida (HH:mm).")
        }
        if (!FormatUtils.isValidUlt4(form.cta_dest_ult4)) {
            return@withContext CreateOrUpdateResult.ValidationError("Últimos 4 dígitos inválidos.")
        }
        if (!FormatUtils.isValidImporte(form.importe)) {
            return@withContext CreateOrUpdateResult.ValidationError("Importe inválido. Formato: MON 999.99")
        }
        if (!FormatUtils.isValidNro(form.nro_operacion)) {
            return@withContext CreateOrUpdateResult.ValidationError("Nº de operación inválido (6–12) si existe.")
        }

        // 2) Normalización
        val bancoNorm = FormatUtils.normalizeBanco(form.banco)
        val benefNorm = form.beneficiario.trim().replace("\\s+".toRegex(), " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

        // 3) Dedupe
        val existingPrimary = if (form.nro_operacion != null) {
            dao.findByBankAndNro(bancoNorm, form.nro_operacion)
        } else null

        if (existingPrimary != null && existingPrimary.id != form.id) {
            if (!replaceIfDuplicate) {
                return@withContext CreateOrUpdateResult.DuplicateFound(existingPrimary, DuplicateType.PRIMARY)
            }
            // reemplazo: mantén el id del existente
            val nowDT = FormatUtils.nowDateTime()
            val updated = Transferencia(
                id = existingPrimary.id,
                fecha = form.fecha,
                hora = form.hora,
                banco = bancoNorm,
                nro_operacion = form.nro_operacion,
                beneficiario = benefNorm,
                cta_dest_ult4 = form.cta_dest_ult4,
                importe = form.importe,
                extras = form.extras,
                created_at = existingPrimary.created_at,
                updated_at = nowDT,
                exported_at = existingPrimary.exported_at
            )
            dao.upsert(updated)
            return@withContext CreateOrUpdateResult.Success(id = updated.id, replaced = true)
        }

        // Fallback dedupe (si no hubo primary o nro es null)
        val possible = dao.findPossibleDuplicate(form.fecha, form.importe, form.cta_dest_ult4)
        if (possible != null && possible.id != form.id) {
            if (!replaceIfDuplicate) {
                return@withContext CreateOrUpdateResult.DuplicateFound(possible, DuplicateType.FALLBACK)
            }
            val nowDT = FormatUtils.nowDateTime()
            val updated = Transferencia(
                id = possible.id,
                fecha = form.fecha,
                hora = form.hora,
                banco = bancoNorm,
                nro_operacion = form.nro_operacion,
                beneficiario = benefNorm,
                cta_dest_ult4 = form.cta_dest_ult4,
                importe = form.importe,
                extras = form.extras,
                created_at = possible.created_at,
                updated_at = nowDT,
                exported_at = possible.exported_at
            )
            dao.upsert(updated)
            return@withContext CreateOrUpdateResult.Success(id = updated.id, replaced = true)
        }

        // 4) Create o Update normal
        val nowDT = FormatUtils.nowDateTime()
        val idFinal = form.id ?: java.util.UUID.randomUUID().toString()
        val createdAt = if (form.id == null) nowDT else (dao.getById(idFinal)?.created_at ?: nowDT)

        val entity = Transferencia(
            id = idFinal,
            fecha = form.fecha,
            hora = form.hora,
            banco = bancoNorm,
            nro_operacion = form.nro_operacion,
            beneficiario = benefNorm,
            cta_dest_ult4 = form.cta_dest_ult4,
            importe = form.importe,
            extras = form.extras,
            created_at = createdAt,
            updated_at = nowDT,
            exported_at = dao.getById(idFinal)?.exported_at
        )

        dao.upsert(entity)
        return@withContext CreateOrUpdateResult.Success(id = entity.id, replaced = false)
    }

    suspend fun deleteById(id: String): Boolean = withContext(Dispatchers.IO) {
        val found = dao.getById(id) ?: return@withContext false
        dao.delete(found)
        true
    }

    suspend fun search(query: String): List<Transferencia> = withContext(Dispatchers.IO) {
        val q = "%${query.trim()}%"
        dao.search(q)
    }
}
