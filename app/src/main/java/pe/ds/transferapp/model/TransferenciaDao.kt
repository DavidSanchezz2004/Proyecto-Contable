package pe.ds.transferapp.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferenciaDao {

    @Upsert
    suspend fun upsert(entity: Transferencia)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: Transferencia)

    @Update
    suspend fun update(entity: Transferencia)

    @Delete
    suspend fun delete(entity: Transferencia)

    @Query("SELECT * FROM transferencia WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Transferencia?

    // Dedupe principal
    @Query("SELECT * FROM transferencia WHERE banco = :banco AND nro_operacion = :nro LIMIT 1")
    suspend fun findByBankAndNro(banco: String, nro: String?): Transferencia?

    // Listado y Flow
    @Query("SELECT * FROM transferencia ORDER BY fecha DESC, hora DESC")
    suspend fun listAll(): List<Transferencia>

    @Query("SELECT * FROM transferencia ORDER BY fecha DESC, hora DESC")
    fun listAllFlow(): Flow<List<Transferencia>>

    // Fallback dedupe
    @Query("""
        SELECT * FROM transferencia
        WHERE fecha = :fecha AND importe = :importe AND cta_dest_ult4 = :ult4
        ORDER BY hora DESC
        LIMIT 1
    """)
    suspend fun findPossibleDuplicate(fecha: String, importe: String, ult4: String): Transferencia?

    // Búsqueda simple por beneficiario/banco/importe/nro_operacion
    @Query("""
        SELECT * FROM transferencia
        WHERE beneficiario LIKE :q OR banco LIKE :q OR importe LIKE :q OR IFNULL(nro_operacion,'') LIKE :q
        ORDER BY fecha DESC, hora DESC
    """)
    suspend fun search(q: String): List<Transferencia>
}
