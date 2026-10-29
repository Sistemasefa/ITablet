package mx.efa.itablet.integration

import mx.efa.itablet.data.InventoryHeader
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Genera XLSX estándar sin dependencias adicionales. No altera los datos locales. */
object InventoryExcel {
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
    private fun date(time: Long?): String = time?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: ""
    fun generate(inv: InventoryHeader, rows: List<JSONObject>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Inventario" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""")
            val data = mutableListOf<List<Any?>>()
            data.add(listOf("Inventario ITablet", inv.id))
            data.add(listOf("Responsable", inv.responsible))
            data.add(listOf("Almacén", inv.warehouse, inv.warehouseName))
            data.add(listOf("Inicio", date(inv.startedAt)))
            data.add(listOf("Consulta SAP inicio", date(inv.queryStartedAt)))
            data.add(listOf("Consulta SAP fin", date(inv.queryFinishedAt)))
            data.add(listOf("Finalización", date(inv.finishedAt)))
            data.add(emptyList())
            data.add(listOf("Artículo", "Descripción", "Almacén", "Cantidad SAP (kg)", "Cantidad contada (kg)", "Piezas", "Diferencia (kg)", "Estado"))
            rows.forEach { row ->
                val counted = row.has("CountedQty")
                val sap = row.optDouble("InStock")
                val qty = if (counted) row.getDouble("CountedQty") else null
                data.add(listOf(row.optString("ItemCode"), row.optString("ItemName"), inv.warehouse,
                    sap, qty, if (counted && row.has("Pieces")) row.getDouble("Pieces") else null,
                    if (qty != null) qty - sap else null, if (counted) "Contado" else "Sin contar"))
            }
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                data.forEachIndexed { index, cells ->
                    append("<row r=\"").append(index + 1).append("\">")
                    cells.forEachIndexed { col, cell ->
                        if (cell != null) {
                            val address = "${('A'.code + col).toChar()}${index + 1}"
                            if (cell is Number) append("<c r=\"$address\"><v>${cell}</v></c>")
                            else append("<c r=\"$address\" t=\"inlineStr\"><is><t>").append(xml(cell.toString())).append("</t></is></c>")
                        }
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            entry("xl/worksheets/sheet1.xml", sheet)
        }
        return output.toByteArray()
    }
}
