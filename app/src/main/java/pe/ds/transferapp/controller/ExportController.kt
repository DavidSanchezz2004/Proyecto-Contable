package pe.ds.transferapp.controller

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.ds.transferapp.model.AppDatabase
import pe.ds.transferapp.model.FormatUtils
import pe.ds.transferapp.model.Transferencia
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportController(private val context: Context) {

    data class ExportSelection(
        val soloPendientes: Boolean,
        val fechaDesde: String?, // YYYY-MM-DD inclusive
        val fechaHasta: String?  // YYYY-MM-DD inclusive
    )

    suspend fun selectForExport(sel: ExportSelection): List<Transferencia> = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).transferenciaDao()
        val all = dao.listAll()
        all.filter { t ->
            val pendiente = t.exported_at == null
            val okPend = if (sel.soloPendientes) pendiente else true
            val okDesde = sel.fechaDesde?.let { t.fecha >= it } ?: true
            val okHasta = sel.fechaHasta?.let { t.fecha <= it } ?: true
            okPend && okDesde && okHasta
        }
    }

    /** Construye un .xlsx (SpreadsheetML mínimo) SIN Apache POI. */
    suspend fun buildWorkbookBytes(rows: List<Transferencia>): ByteArray = withContext(Dispatchers.Default) {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            // ---- archivos mínimos de un .xlsx ----
            putEntry(zip, "[Content_Types].xml", contentTypesXml())
            putEntry(zip, "_rels/.rels", relsRootXml())
            putEntry(zip, "docProps/app.xml", appXml())
            putEntry(zip, "docProps/core.xml", coreXml())
            putEntry(zip, "xl/workbook.xml", workbookXml())
            putEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            putEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(rows))
        }
        bos.toByteArray()
    }

    /** Marca exported_at para los registros exportados. */
    suspend fun markExported(rows: List<Transferencia>) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).transferenciaDao()
        val now = FormatUtils.nowDateTime()
        rows.forEach { t ->
            dao.upsert(t.copy(exported_at = now, updated_at = now))
        }
    }

    // ------------------ Helpers XLSX ------------------

    private fun putEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        val bytes = content.toByteArray(Charsets.UTF_8)
        zip.write(bytes, 0, bytes.size)
        zip.closeEntry()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /** Deriva ("PEN","921.88") desde "PEN 921.88"/"USD 100" etc. */
    private fun deriveCurrencyAmount(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank()) return "PEN" to "0.00"
        val s = raw.trim().uppercase(Locale.ROOT).replace("\u00A0", " ")
        val m = Regex("(PEN|USD)\\s?(\\d+(?:\\.\\d{1,2})?)").find(s)
        return if (m != null) {
            val cur = m.groupValues[1]
            val amt = fixDecimals(m.groupValues[2])
            cur to amt
        } else "PEN" to "0.00"
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

    private fun safeExtras(json: String?): org.json.JSONObject = try {
        if (json.isNullOrBlank()) org.json.JSONObject() else org.json.JSONObject(json)
    } catch (_: Exception) { org.json.JSONObject() }

    // ------------------ XML contents ------------------

    private fun contentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
          <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
        </Types>
    """.trimIndent()

    private fun relsRootXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
        </Relationships>
    """.trimIndent()

    private fun appXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
            xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
          <Application>TransferApp</Application>
          <DocSecurity>0</DocSecurity>
          <ScaleCrop>false</ScaleCrop>
          <HeadingPairs>
            <vt:vector size="2" baseType="variant">
              <vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>
              <vt:variant><vt:i4>1</vt:i4></vt:variant>
            </vt:vector>
          </HeadingPairs>
          <TitlesOfParts>
            <vt:vector size="1" baseType="lpstr"><vt:lpstr>Transferencias</vt:lpstr></vt:vector>
          </TitlesOfParts>
        </Properties>
    """.trimIndent()

    private fun coreXml(): String {
        val now = OffsetDateTime.now()
        val iso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val creator = "TransferApp"
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
               xmlns:dc="http://purl.org/dc/elements/1.1/"
               xmlns:dcterms="http://purl.org/dc/terms/"
               xmlns:dcmitype="http://purl.org/dc/dcmitype/"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <dc:creator>${escapeXml(creator)}</dc:creator>
              <cp:lastModifiedBy>${escapeXml(creator)}</cp:lastModifiedBy>
              <dcterms:created xsi:type="dcterms:W3CDTF">$iso</dcterms:created>
              <dcterms:modified xsi:type="dcterms:W3CDTF">$iso</dcterms:modified>
            </cp:coreProperties>
        """.trimIndent()
    }

    private fun workbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Transferencias" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        </Relationships>
    """.trimIndent()

    /** Genera la hoja con celdas tipo inlineStr (todo como texto, suficiente para contabilidad). */
    private fun sheetXml(rows: List<Transferencia>): String {
        val headers = listOf(
            "fecha","hora","banco","nro_operacion","beneficiario",
            "cta_dest_ult4","importe","moneda","monto",
            "canal","tipo_operacion","id","created_at","updated_at","exported_at"
        )

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Dimensiones aproximadas
        val totalRows = rows.size + 1
        val lastColRef = columnRef(headers.size)
        sb.append("<dimension ref=\"A1:$lastColRef$totalRows\"/>")

        sb.append("<sheetData>")

        // Header (fila 1)
        sb.append("<row r=\"1\">")
        headers.forEachIndexed { idx, h ->
            val ref = cellRef(1, idx + 1)
            sb.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(h)}</t></is></c>""")
        }
        sb.append("</row>")

        // Datos
        rows.forEachIndexed { rIdx, t ->
            val r = rIdx + 2 // data desde fila 2
            val (moneda, monto) = deriveCurrencyAmount(t.importe)
            val extras = safeExtras(t.extras)
            val canal = extras.optString("canal", "")
            val tipoOp = extras.optString("tipo_operacion", "")

            val values = listOf(
                t.fecha, t.hora, t.banco, t.nro_operacion ?: "", t.beneficiario,
                t.cta_dest_ult4, t.importe, moneda, monto,
                canal, tipoOp, t.id, t.created_at, t.updated_at, t.exported_at ?: ""
            )

            sb.append("""<row r="$r">""")
            values.forEachIndexed { cIdx, v ->
                val ref = cellRef(r, cIdx + 1)
                sb.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(v)}</t></is></c>""")
            }
            sb.append("</row>")
        }

        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    // ------------------ direccionamiento de celdas ------------------

    private fun cellRef(row: Int, col: Int): String = "${columnRef(col)}$row"

    private fun columnRef(col: Int): String {
        // 1 -> A, 2 -> B, ... 26 -> Z, 27 -> AA, ...
        var c = col
        val sb = StringBuilder()
        while (c > 0) {
            val rem = (c - 1) % 26
            sb.append(('A'.code + rem).toChar())
            c = (c - 1) / 26
        }
        return sb.reverse().toString()
    }
}
