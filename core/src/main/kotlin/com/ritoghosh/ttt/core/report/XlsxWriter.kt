/**
 * XlsxWriter — writes report tables as one .xlsx workbook, using only java.util.zip and string
 * building (SPEC 11.3). One sheet per table, in order.
 *
 * Depends on: ReportTable.kt, ReportWriter.kt, XlsxSheet.kt, XlsxText.kt.
 * Must not: add a library or write formulas; values only, so Google Sheets, LibreOffice and
 * Excel all show the same thing.
 *
 * Parts: [Content_Types].xml, _rels/.rels, xl/workbook.xml, xl/_rels/workbook.xml.rels,
 * xl/styles.xml, xl/worksheets/sheetN.xml. Strings are inline (no shared-strings part).
 */
package com.ritoghosh.ttt.core.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
internal const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
internal const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val SPREADSHEET_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml"

class XlsxWriter : ReportWriter {

    override fun write(tables: List<ReportTable>, output: OutputStream) {
        require(tables.isNotEmpty()) { "A workbook needs at least one sheet" }
        val names = tables.map { sheetName(it.title) }
        require(names.map { it.lowercase() }.distinct().size == names.size) { "Sheet names must differ: $names" }
        // Not closed on purpose: closing would also close the caller's stream.
        val zip = ZipOutputStream(output)
        zip.putPart("[Content_Types].xml", contentTypes(tables.size))
        zip.putPart("_rels/.rels", rootRelationships())
        zip.putPart("xl/workbook.xml", workbook(tables, names))
        zip.putPart("xl/_rels/workbook.xml.rels", workbookRelationships(tables.size))
        zip.putPart("xl/styles.xml", STYLES)
        tables.forEachIndexed { index, table ->
            zip.putPart("xl/worksheets/sheet${index + 1}.xml", sheetXml(table))
        }
        zip.finish()
    }

    private fun ZipOutputStream.putPart(path: String, xml: String) {
        putNextEntry(ZipEntry(path))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XML_DECLARATION).append("<Types xmlns=\"$CONTENT_TYPES_NS\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"$SPREADSHEET_TYPE.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"$SPREADSHEET_TYPE.styles+xml\"/>")
        for (number in 1..sheetCount) {
            append("<Override PartName=\"/xl/worksheets/sheet$number.xml\" ")
            append("ContentType=\"$SPREADSHEET_TYPE.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun rootRelationships(): String = XML_DECLARATION +
        "<Relationships xmlns=\"$PACKAGE_REL_NS\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    /** Lists the sheets; data sheets also get the hidden name spreadsheet apps use for autofilters. */
    private fun workbook(tables: List<ReportTable>, names: List<String>): String = buildString {
        append(XML_DECLARATION).append("<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\"><sheets>")
        names.forEachIndexed { index, name ->
            append("<sheet name=\"${escapeXml(name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets>")
        val filtered = tables.indices.filter { tables[it].isDataSheet }
        if (filtered.isNotEmpty()) {
            append("<definedNames>")
            filtered.forEach { index ->
                append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"$index\" hidden=\"1\">")
                append(escapeXml(filterReference(tables[index], names[index])))
                append("</definedName>")
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }

    private fun workbookRelationships(sheetCount: Int): String = buildString {
        append(XML_DECLARATION).append("<Relationships xmlns=\"$PACKAGE_REL_NS\">")
        for (number in 1..sheetCount) {
            append("<Relationship Id=\"rId$number\" Type=\"$REL_NS/worksheet\" ")
            append("Target=\"worksheets/sheet$number.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }
}

/** Fonts and number formats; the cellXfs order is the XlsxStyle numbering in XlsxSheet.kt. */
private val STYLES = XML_DECLARATION +
    "<styleSheet xmlns=\"$MAIN_NS\">" +
    "<numFmts count=\"4\">" +
    "<numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/>" +
    "<numFmt numFmtId=\"165\" formatCode=\"hh:mm\"/>" +
    "<numFmt numFmtId=\"166\" formatCode=\"yyyy-mm-dd hh:mm\"/>" +
    "<numFmt numFmtId=\"167\" formatCode=\"0.0\"/>" +
    "</numFmts>" +
    "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
    "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
    "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
    "<cellXfs count=\"7\">" +
    "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
    "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "</cellXfs>" +
    "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
    "</styleSheet>"
