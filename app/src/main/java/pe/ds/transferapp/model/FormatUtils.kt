package pe.ds.transferapp.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

object FormatUtils {

    private val REGEX_IMPORTE = Pattern.compile("^[A-Z]{3}\\s\\d+(\\.\\d{2})$")
    private val REGEX_ULT4 = Pattern.compile("^\\d{4}$")
    private val REGEX_HHMM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val REGEX_NRO = Pattern.compile("^.{6,12}$") // flexible por banco

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    // === AHORA (usando zona local del dispositivo) ===
    fun nowDate(): String = LocalDate.now(ZoneId.systemDefault()).format(DATE_FMT)

    fun nowTime(): String = LocalTime.now(ZoneId.systemDefault())
        .withSecond(0).withNano(0)
        .format(TIME_FMT)

    fun nowDateTime(): String = LocalDateTime.now(ZoneId.systemDefault())
        .withSecond(0).withNano(0)
        .format(DT_FMT)

    // === Fechas relativas para filtros ===
    fun dateDaysAgo(days: Long): String =
        LocalDate.now(ZoneId.systemDefault()).minusDays(days).format(DATE_FMT)

    fun dateMonthsAgo(months: Long): String =
        LocalDate.now(ZoneId.systemDefault()).minusMonths(months).format(DATE_FMT)

    // === Normalizadores / validadores ===
    fun normalizeBanco(input: String): String = input.trim().uppercase(Locale.ROOT)

    fun isValidImporte(importe: String): Boolean = REGEX_IMPORTE.matcher(importe.trim()).matches()
    fun isValidUlt4(ult4: String): Boolean = REGEX_ULT4.matcher(ult4.trim()).matches()
    fun isValidHora(hora: String): Boolean = REGEX_HHMM.matcher(hora.trim()).matches()
    fun isValidNro(nro: String?): Boolean = nro == null || REGEX_NRO.matcher(nro.trim()).matches()

    fun isValidFecha(fecha: String): Boolean {
        return try {
            val d = LocalDate.parse(fecha, DATE_FMT)
            val today = LocalDate.now(ZoneId.systemDefault())
            !d.isAfter(today)
        } catch (_: Exception) {
            false
        }
    }

    /** Deriva moneda y monto a partir de "PEN 921.88" */
    fun splitImporte(importe: String): Pair<String, String>? {
        if (!isValidImporte(importe)) return null
        val parts = importe.trim().split(" ")
        return if (parts.size == 2) parts[0] to parts[1] else null
    }
}
