package pe.ds.transferapp.controller

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

class ParserController(private val context: Context) {

    data class ParsedField<T>(val value: T?, val confidence: Int)

    data class ParseOutput(
        val banco: ParsedField<String>,
        val fecha: ParsedField<String>,
        val hora: ParsedField<String>,
        val nroOperacion: ParsedField<String?>,
        val beneficiario: ParsedField<String>,   // titular destino si se logra
        val ult4: ParsedField<String>,           // cuenta destino
        val importe: ParsedField<String>,        // "PEN 921.88"
        val extrasJson: String?,                 // incluirá cta_origen_ult4 y titular_origen si se detectan
        val titularDestino: String?,             // NUEVO: para prellenar Beneficiario
        val titularOrigen: String?               // NUEVO: para prellenar campo nuevo
    )

    private val rules: JSONObject by lazy { loadRules() }

    private fun loadRules(): JSONObject {
        context.assets.open("parser_rules.json").use { input ->
            val br = BufferedReader(InputStreamReader(input))
            val text = buildString {
                var line = br.readLine()
                while (line != null) { append(line).append('\n'); line = br.readLine() }
            }
            return JSONObject(text)
        }
    }

    fun parse(fullText: String): ParseOutput {
        val text = fullText
            .replace('\u00A0', ' ')
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        val bancoDetected = detectBank(text)
        val bankKey = when (bancoDetected?.lowercase(Locale.ROOT)) {
            "bcp", "bbva", "interbank", "scotiabank" -> bancoDetected.lowercase(Locale.ROOT)
            else -> "generic"
        }
        val bankRules = rules.optJSONObject(bankKey) ?: rules.getJSONObject("generic")

        // Básicos
        val rawFecha   = matchFirstValue(text, bankRules.optString("fecha", ""))
        val rawHora    = matchFirstValue(text, bankRules.optString("hora", ""))
        val rawImporte = matchFirstValue(text, bankRules.optString("importe", ""))
        val rawNro     = matchFirstValue(text, bankRules.optString("nro_operacion", ""))

        // Beneficiario simple (si no logramos bloquear)
        var rawBenef   = matchFirstValue(text, bankRules.optString("beneficiario", ""))

        // Bloques destino/origen (capturan ult4 + nombre)
        val destBlock  = matchGroups(text, bankRules.optString("destino_block", ""))
        val origBlock  = matchGroups(text, bankRules.optString("origen_block", ""))

        var rawUlt4Dest: String? = destBlock?.getOrNull(1)
        var titularDest:  String? = destBlock?.getOrNull(2)
        var rawUlt4Orig: String? = origBlock?.getOrNull(1)
        var titularOrig:  String? = origBlock?.getOrNull(2)

        // Si faltan ult4, intenta regex simples
        if (rawUlt4Dest.isNullOrBlank()) rawUlt4Dest = matchFirstValue(text, bankRules.optString("ult4", ""))
        if (rawUlt4Orig.isNullOrBlank()) rawUlt4Orig = matchFirstValue(text, bankRules.optString("ult4_origen", ""))

        // Heurística si aún faltan
        if (rawUlt4Dest.isNullOrBlank() || rawUlt4Orig.isNullOrBlank()) {
            val (fbDest, fbOrig) = extractUlt4Heuristic(text)
            if (rawUlt4Dest.isNullOrBlank()) rawUlt4Dest = fbDest
            if (rawUlt4Orig.isNullOrBlank()) rawUlt4Orig = fbOrig
        }

        // Normalizaciones
        val fechaN   = normalizeFecha(rawFecha)
        val horaN    = normalizeHora(rawHora)
        val importeN = normalizeImporte(rawImporte)

        // Beneficiario final
        val beneficiarioFinal = when {
            !titularDest.isNullOrBlank() -> titularDest
            !rawBenef.isNullOrBlank()    -> rawBenef
            else -> null
        }

        // Extras con datos de origen
        val extrasObj = JSONObject()
        if (!rawUlt4Orig.isNullOrBlank()) extrasObj.put("cta_origen_ult4", rawUlt4Orig)
        if (!titularOrig.isNullOrBlank()) extrasObj.put("titular_origen", titularOrig)
        val extrasStr = if (extrasObj.length() == 0) null else extrasObj.toString()

        return ParseOutput(
            banco = ParsedField(bancoDetected ?: bankKey.uppercase(Locale.ROOT), conf(bancoDetected != null, 90, 60)),
            fecha = ParsedField(fechaN, conf(fechaN != null, 95, 20)),
            hora  = ParsedField(horaN,  conf(horaN != null,  95, 20)),
            nroOperacion = ParsedField(rawNro, conf(rawNro != null, 85, 0)),
            beneficiario = ParsedField(beneficiarioFinal, conf(!beneficiarioFinal.isNullOrBlank(), 85, 0)),
            ult4 = ParsedField(rawUlt4Dest, conf(rawUlt4Dest != null, 95, 0)),
            importe = ParsedField(importeN, conf(importeN != null, 95, 30)),
            extrasJson = extrasStr,
            titularDestino = beneficiarioFinal,
            titularOrigen = titularOrig
        )
    }

    // -------- Helpers de matching --------
    private fun matchFirstValue(text: String, regex: String): String? {
        if (regex.isBlank()) return null
        val p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        val m = p.matcher(text)
        if (!m.find()) return null
        for (i in m.groupCount() downTo 1) {
            val g = m.group(i)
            if (!g.isNullOrBlank()) return g.trim()
        }
        return m.group(0)?.trim()
    }

    private fun matchGroups(text: String, regex: String): List<String>? {
        if (regex.isBlank()) return null
        val p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        val m = p.matcher(text)
        if (!m.find()) return null
        return (0..m.groupCount()).map { m.group(it)?.trim() ?: "" }
    }

    private fun detectBank(text: String): String? {
        val upper = text.uppercase(Locale.ROOT)
        val checks = mapOf(
            "BCP" to listOf("BANCO DE CREDITO", "BCP"),
            "BBVA" to listOf("BBVA", "CONSTANCIA DE TRANSFERENCIA", "MONTO TRANSFERIDO"),
            "INTERBANK" to listOf("INTERBANK"),
            "SCOTIABANK" to listOf("SCOTIABANK")
        )
        for ((bank, keys) in checks) if (keys.any { upper.contains(it) }) return bank
        return null
    }

    // -------- Heurística para ult4 destino/origen --------
    private fun extractUlt4Heuristic(text: String): Pair<String?, String?> {
        val maskRegex = Regex("(\\*{2,}|X{2,})\\s*(\\d{4})")
        val matches = maskRegex.findAll(text).map { it.range.first to it.groupValues[2] }.toList()
        if (matches.isEmpty()) return null to null

        val posDestino = findKeywordPos(text, listOf("destino", "cta destino", "cuenta de destino"))
        val posOrigen  = findKeywordPos(text, listOf("origen", "cta origen", "cuenta de origen"))

        fun closestTo(pos: Int?, takenIndex: Int? = null): Int? {
            if (pos == null) return null
            var bestIdx: Int? = null
            var bestDist = Int.MAX_VALUE
            matches.forEachIndexed { idx, (at, _) ->
                if (takenIndex != idx) {
                    val d = abs(at - pos)
                    if (d < bestDist) { bestDist = d; bestIdx = idx }
                }
            }
            return bestIdx
        }

        var idxDest = closestTo(posDestino)
        var idxOrig = closestTo(posOrigen, takenIndex = idxDest)

        if (idxDest == null && matches.isNotEmpty()) idxDest = 0
        if (idxOrig == null && matches.size >= 2) idxOrig = if (idxDest == 0) 1 else 0

        val dest = idxDest?.let { matches[it].second }
        val orig = idxOrig?.let { matches[it].second }
        return dest to orig
    }

    private fun findKeywordPos(text: String, keys: List<String>): Int? {
        val lower = text.lowercase(Locale.ROOT)
        for (k in keys) {
            val idx = lower.indexOf(k)
            if (idx >= 0) return idx
        }
        return null
    }

    // -------- Normalizaciones --------
    private fun normalizeFecha(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return s
        if (s.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
            val (d, m, y) = s.split("/")
            return "%s-%02d-%02d".format(y, m.toInt(), d.toInt())
        }
        val r = Regex("(\\d{1,2})\\s+([A-Za-zÁÉÍÓÚñ]+)\\s+(\\d{4})", RegexOption.IGNORE_CASE)
        val m = r.find(s) ?: return null
        val day = m.groupValues[1].toInt()
        val monthName = unaccent(m.groupValues[2]).uppercase(Locale.ROOT)
        val year = m.groupValues[3].toInt()
        val monthMap = mapOf(
            "ENERO" to 1, "FEBRERO" to 2, "MARZO" to 3, "ABRIL" to 4, "MAYO" to 5, "JUNIO" to 6,
            "JULIO" to 7, "AGOSTO" to 8, "SEPTIEMBRE" to 9, "SETIEMBRE" to 9, "OCTUBRE" to 10, "NOVIEMBRE" to 11, "DICIEMBRE" to 12
        )
        val month = monthMap[monthName] ?: return null
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun normalizeHora(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("a.m.", "am").replace("p.m.", "pm")
            .replace("a.m", "am").replace("p.m", "pm")
        val m = Regex("(\\d{1,2}):(\\d{2})(am|pm)?").find(s) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val ampm = m.groupValues.getOrNull(3)
        if (!ampm.isNullOrBlank()) {
            if (ampm == "pm" && h != 12) h += 12
            if (ampm == "am" && h == 12) h = 0
        }
        return "%02d:%02d".format(h, min)
    }

    private fun normalizeImporte(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim().replace('\u00A0', ' ')
        s = s.replace("S/", "PEN", ignoreCase = true).replace("S /", "PEN", ignoreCase = true)
        s = s.replace(Regex("(?<=\\d)[\\.\\s](?=\\d{3}(\\D|$))"), "")
        s = s.replace(",", ".")
        val m = Regex("(PEN|USD)\\s?(\\d+(?:\\.\\d{2})?)", RegexOption.IGNORE_CASE).find(s) ?: return null
        val currency = m.groupValues[1].uppercase(Locale.ROOT)
        val amount = fixDecimals(m.groupValues[2])
        return "$currency $amount"
    }

    private fun fixDecimals(n: String?): String {
        if (n.isNullOrBlank()) return "0.00"
        val parts = n.split(".")
        return when (parts.size) {
            1 -> parts[0] + ".00"
            2 -> parts[0] + "." + parts[1].padEnd(2, '0').take(2)
            else -> n
        }
    }

    private fun unaccent(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

    private fun conf(ok: Boolean, good: Int, bad: Int) = if (ok) good else bad
}
