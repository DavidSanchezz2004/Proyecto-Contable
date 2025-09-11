package pe.ds.transferapp.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Tabla transferencia (8+1 campos opcional) + timestamps.
 * Nota: UUID se guarda como String para compatibilidad Room.
 */
@Entity(
    tableName = "transferencia",
    indices = [
        // UNIQUE(banco, nro_operacion). SQLite permite múltiples NULL en unique.
        Index(value = ["banco", "nro_operacion"], unique = true)
    ]
)
data class Transferencia(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val fecha: String,              // YYYY-MM-DD
    val hora: String,               // HH:mm
    val banco: String,              // Ej: "BCP", "BBVA", etc.
    val nro_operacion: String?,     // 6–12 si existe
    val beneficiario: String,
    val cta_dest_ult4: String,      // 4 dígitos
    val importe: String,            // Ej: "PEN 921.88" / "USD 100.00"
    val extras: String? = null,     // JSON opcional

    val created_at: String,         // ISO-8601 datetime
    val updated_at: String,
    val exported_at: String? = null // nullable
)
