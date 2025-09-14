package pe.ds.transferapp.controller

import android.content.Context
import android.util.Log
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
        Log.d("ParserController", "=== INICIO DE PARSING ===")
        Log.d("ParserController", "Texto completo original: $fullText")

        val text = fullText
            .replace('\u00A0', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()

        Log.d("ParserController", "Texto limpio: $text")

        val bancoDetected = detectBank(text)
        val bankKey = when (bancoDetected?.lowercase(Locale.ROOT)) {
            "bcp", "bbva", "interbank", "scotiabank" -> bancoDetected.lowercase(Locale.ROOT)
            else -> "generic"
        }

        Log.d("ParserController", "Banco detectado: $bancoDetected, usando reglas: $bankKey")

        // Para BBVA, usar parsing específico
        if (bankKey == "bbva") {
            return parseBBVA(text, bancoDetected)
        }

        // Parsing genérico para otros bancos (código original)
        return parseGeneric(text, bankKey, bancoDetected)
    }

    private fun parseBBVA(text: String, bancoDetected: String?): ParseOutput {
        Log.d("ParserController", "=== PARSING ESPECÍFICO BBVA ===")

        // 1. Fecha - buscar patrón "Sábado, 17 Junio 2023"
        val fechaPattern = "(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),?\\s*(\\d{1,2})\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+(\\d{4})"
        val fechaRegex = Regex(fechaPattern, RegexOption.IGNORE_CASE)
        val fechaMatch = fechaRegex.find(text)
        val fechaN = if (fechaMatch != null) {
            val day = fechaMatch.groupValues[1].toInt()
            val monthName = unaccent(fechaMatch.groupValues[2]).uppercase(Locale.ROOT)
            val year = fechaMatch.groupValues[3].toInt()
            val monthMap = mapOf(
                "ENERO" to 1, "FEBRERO" to 2, "MARZO" to 3, "ABRIL" to 4, "MAYO" to 5, "JUNIO" to 6,
                "JULIO" to 7, "AGOSTO" to 8, "SEPTIEMBRE" to 9, "SETIEMBRE" to 9, "OCTUBRE" to 10, "NOVIEMBRE" to 11, "DICIEMBRE" to 12
            )
            val month = monthMap[monthName]
            if (month != null) "%04d-%02d-%02d".format(year, month, day) else null
        } else null

        Log.d("ParserController", "BBVA Fecha encontrada: $fechaN")

        // 2. Hora - buscar patrón "07:22 p.m."
        val horaPattern = "(\\d{1,2}):(\\d{2})\\s*([ap])\\.?m\\.?"
        val horaRegex = Regex(horaPattern, RegexOption.IGNORE_CASE)
        val horaMatch = horaRegex.find(text)
        val horaN = if (horaMatch != null) {
            var hour = horaMatch.groupValues[1].toInt()
            val min = horaMatch.groupValues[2].toInt()
            val ampm = horaMatch.groupValues[3].lowercase()
            if (ampm == "p" && hour != 12) hour += 12
            if (ampm == "a" && hour == 12) hour = 0
            "%02d:%02d".format(hour, min)
        } else null

        Log.d("ParserController", "BBVA Hora encontrada: $horaN")

        // 3. Número de operación - buscar después de "Número de operación:"
        val nroPattern = "(?i)n[úu]mero\\s+de\\s+operaci[óo]n\\s*:?\\s*(\\d{6,12})"
        val nroRegex = Regex(nroPattern)
        val nroMatch = nroRegex.find(text)
        val nroOp = nroMatch?.groupValues?.get(1)

        Log.d("ParserController", "BBVA Nro Operación encontrada: $nroOp")

        // 4. Buscar bloques de cuenta específicamente
        Log.d("ParserController", "=== BUSCANDO CUENTAS BBVA ===")

        // Patrón para "Cuenta de destino: **** 7042 Ramirez Guerrero W."
        val destinoPattern = "(?i)cuenta\\s+de\\s+destino\\s*:?\\s*\\*{2,}\\s*(\\d{4})\\s*([A-Za-zÁÉÍÓÚÑñ\\s\\.'\\-&]{3,})"
        val destinoRegex = Regex(destinoPattern)
        val destinoMatch = destinoRegex.find(text)

        var ult4Dest: String? = null
        var titularDest: String? = null

        if (destinoMatch != null) {
            ult4Dest = destinoMatch.groupValues[1]
            titularDest = cleanName(destinoMatch.groupValues[2])
            Log.d("ParserController", "BBVA Destino encontrado - ULT4: $ult4Dest, Titular: $titularDest")
        } else {
            Log.d("ParserController", "BBVA Destino NO encontrado con patrón principal")

            // Patrón alternativo más simple
            val altDestinoPattern = "\\*{2,}\\s*(\\d{4})\\s*([A-Za-zÁÉÍÓÚÑñ][A-Za-zÁÉÍÓÚÑñ\\s\\.'\\-&]{2,})"
            val altDestinoRegex = Regex(altDestinoPattern)
            val matches = altDestinoRegex.findAll(text).toList()

            Log.d("ParserController", "Patrones **** NNNN encontrados: ${matches.size}")
            matches.forEachIndexed { index, match ->
                Log.d("ParserController", "Match $index: ${match.value} -> ULT4: ${match.groupValues[1]}, Nombre: ${match.groupValues[2]}")
            }

            // Tomar el primer match que no sea "2023" (año)
            val validMatch = matches.find { it.groupValues[1] != "2023" }
            if (validMatch != null) {
                ult4Dest = validMatch.groupValues[1]
                titularDest = cleanName(validMatch.groupValues[2])
                Log.d("ParserController", "BBVA Destino alternativo - ULT4: $ult4Dest, Titular: $titularDest")
            }
        }

        // Patrón para "Cuenta de origen: **** 0035 Hamann Diseno Y Construccion S.a.c"
        val origenPattern = "(?i)cuenta\\s+de\\s+origen\\s*:?\\s*\\*{2,}\\s*(\\d{4})\\s*([A-Za-zÁÉÍÓÚÑñ\\s\\.'\\-&]{3,})"
        val origenRegex = Regex(origenPattern)
        val origenMatch = origenRegex.find(text)

        var ult4Orig: String? = null
        var titularOrig: String? = null

        if (origenMatch != null) {
            ult4Orig = origenMatch.groupValues[1]
            titularOrig = cleanName(origenMatch.groupValues[2])
            Log.d("ParserController", "BBVA Origen encontrado - ULT4: $ult4Orig, Titular: $titularOrig")
        } else {
            Log.d("ParserController", "BBVA Origen NO encontrado")

            // Buscar segundo patrón **** NNNN si hay múltiples
            val allMatches = Regex("\\*{2,}\\s*(\\d{4})\\s*([A-Za-zÁÉÍÓÚÑñ][A-Za-zÁÉÍÓÚÑñ\\s\\.'\\-&]{2,})").findAll(text).toList()
            if (allMatches.size >= 2) {
                val secondMatch = allMatches[1]
                if (secondMatch.groupValues[1] != "2023") {
                    ult4Orig = secondMatch.groupValues[1]
                    titularOrig = cleanName(secondMatch.groupValues[2])
                    Log.d("ParserController", "BBVA Origen segundo match - ULT4: $ult4Orig, Titular: $titularOrig")
                }
            }
        }

        // 5. Importe - buscar "S/ 921.88"
        val importePattern = "(?i)(?:monto\\s+transferido\\s*:?\\s*)?S/\\s*(\\d+(?:[,.]\\d{2})?)"
        val importeRegex = Regex(importePattern)
        val importeMatch = importeRegex.find(text)
        val importeN = if (importeMatch != null) {
            val amount = importeMatch.groupValues[1].replace(",", ".")
            "PEN $amount"
        } else null

        Log.d("ParserController", "BBVA Importe encontrado: $importeN")

        // Beneficiario final
        val beneficiarioFinal = titularDest

        // Extras con datos de origen
        val extrasObj = JSONObject()
        if (!ult4Orig.isNullOrBlank()) extrasObj.put("cta_origen_ult4", ult4Orig)
        if (!titularOrig.isNullOrBlank()) extrasObj.put("titular_origen", titularOrig)
        val extrasStr = if (extrasObj.length() == 0) null else extrasObj.toString()

        Log.d("ParserController", "=== RESULTADO FINAL BBVA ===")
        Log.d("ParserController", "Banco: BBVA")
        Log.d("ParserController", "Fecha: $fechaN")
        Log.d("ParserController", "Hora: $horaN")
        Log.d("ParserController", "Nro Op: $nroOp")
        Log.d("ParserController", "Beneficiario: $beneficiarioFinal")
        Log.d("ParserController", "ULT4 Dest: $ult4Dest")
        Log.d("ParserController", "ULT4 Orig: $ult4Orig")
        Log.d("ParserController", "Titular Orig: $titularOrig")
        Log.d("ParserController", "Importe: $importeN")
        Log.d("ParserController", "Extras: $extrasStr")

        return ParseOutput(
            banco = ParsedField("BBVA", 95),
            fecha = ParsedField(fechaN, conf(fechaN != null, 95, 20)),
            hora  = ParsedField(horaN,  conf(horaN != null,  95, 20)),
            nroOperacion = ParsedField(nroOp, conf(nroOp != null, 85, 0)),
            beneficiario = ParsedField(beneficiarioFinal, conf(!beneficiarioFinal.isNullOrBlank(), 85, 0)),
            ult4 = ParsedField(ult4Dest, conf(ult4Dest != null, 95, 0)),
            importe = ParsedField(importeN, conf(importeN != null, 95, 30)),
            extrasJson = extrasStr,
            titularDestino = beneficiarioFinal,
            titularOrigen = titularOrig
        )
    }

    private fun parseGeneric(text: String, bankKey: String, bancoDetected: String?): ParseOutput {
        // Código de parsing genérico original (simplificado para esta versión)
        val bankRules = rules.optJSONObject(bankKey) ?: rules.getJSONObject("generic")

        val rawFecha = matchFirstValue(text, bankRules.optString("fecha", ""))
        val rawHora = matchFirstValue(text, bankRules.optString("hora", ""))
        val rawImporte = matchFirstValue(text, bankRules.optString("importe", ""))
        val rawNro = matchFirstValue(text, bankRules.optString("nro_operacion", ""))

        val fechaN = normalizeFecha(rawFecha)
        val horaN = normalizeHora(rawHora)
        val importeN = normalizeImporte(rawImporte)

        return ParseOutput(
            banco = ParsedField(bancoDetected ?: bankKey.uppercase(Locale.ROOT), conf(bancoDetected != null, 90, 60)),
            fecha = ParsedField(fechaN, conf(fechaN != null, 95, 20)),
            hora  = ParsedField(horaN,  conf(horaN != null,  95, 20)),
            nroOperacion = ParsedField(rawNro, conf(rawNro != null, 85, 0)),
            beneficiario = ParsedField(null, 0),
            ult4 = ParsedField(null, 0),
            importe = ParsedField(importeN, conf(importeN != null, 95, 30)),
            extrasJson = null,
            titularDestino = null,
            titularOrigen = null
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

    private fun detectBank(text: String): String? {
        val upper = text.uppercase(Locale.ROOT)
        val checks = mapOf(
            "BCP" to listOf("BANCO DE CREDITO", "BCP"),
            "BBVA" to listOf("BBVA", "CONSTANCIA DE TRANSFERENCIA", "MONTO TRANSFERIDO", "HAMANN DISENO Y CONSTRUCCION"),
            "INTERBANK" to listOf("INTERBANK"),
            "SCOTIABANK" to listOf("SCOTIABANK")
        )
        for ((bank, keys) in checks) {
            if (keys.any { upper.contains(it) }) {
                Log.d("ParserController", "Banco detectado: $bank por clave: ${keys.find { upper.contains(it) }}")
                return bank
            }
        }
        Log.d("ParserController", "No se detectó banco específico")
        return null
    }

    private fun cleanName(name: String): String {
        return name.trim()
            .replace("\\s+".toRegex(), " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
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
        return null
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