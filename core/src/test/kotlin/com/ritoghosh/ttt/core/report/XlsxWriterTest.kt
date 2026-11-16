/**
 * XlsxWriterTest — writes a workbook, unzips it, and checks that every part is well-formed XML,
 * cell values and styles are right, text is escaped and cleaned, and data sheets have a frozen
 * header and an autofilter (SPEC 11.3, 13).
 *
 * Depends on: XlsxWriter, the JDK's zip and XML parser.
 * Must not: compare whole XML strings; it reads the parsed documents, so harmless formatting
 * changes do not break it.
 */
package com.ritoghosh.ttt.core.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {

    private val dataSheet = ReportTable(
        title = "Sessions",
        header = listOf("Date", "Start", "Worked min", "Hours", "Note", "When"),
        rows = listOf(
            listOf(d("1970-01-01"), tm("12:00"), n(25), h(1.5), t("<draft> & \"notes\""), dt("1970-01-02T06:00")),
            listOf(d("1900-03-01"), tm("00:00"), n(-40), h(0.0), Cell.Empty, Cell.Empty),
        ),
    )
    private val summary =
        ReportTable("Summary", listOf("Item", "Value"), listOf(listOf(t("Range"), t("All time"))), isDataSheet = false)

    private val parts: Map<String, ByteArray> = unzip(write(listOf(summary, dataSheet)))

    @Test
    fun `every part is there and is well-formed XML`() {
        val expected = setOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml",
        )
        assertEquals(expected, parts.keys)
        parts.keys.forEach { parse(it) } // throws if any part is not well-formed
    }

    @Test
    fun `workbook lists the sheets in order with an autofilter name for data sheets only`() {
        val workbook = parse("xl/workbook.xml")
        assertEquals(listOf("Summary", "Sessions"), elements(workbook, "sheet").map { it.getAttribute("name") })
        val names = elements(workbook, "definedName")
        assertEquals(1, names.size)
        assertEquals("1", names.single().getAttribute("localSheetId"))
        assertEquals("'Sessions'!\$A\$1:\$F\$3", names.single().textContent)
    }

    @Test
    fun `cells hold typed values with the right styles`() {
        val cells = cellsOf("xl/worksheets/sheet2.xml")
        assertEquals("Date" to "1", cells.getValue("A1").textAndStyle()) // bold header
        assertEquals("25569" to "2", cells.getValue("A2").textAndStyle()) // 1970-01-01, date style
        assertEquals("61" to "2", cells.getValue("A3").textAndStyle()) // 1900-03-01 after Excel's leap bug
        assertEquals("0.5" to "3", cells.getValue("B2").textAndStyle()) // noon
        assertEquals("0" to "3", cells.getValue("B3").textAndStyle())
        assertEquals("25" to "5", cells.getValue("C2").textAndStyle())
        assertEquals("-40" to "5", cells.getValue("C3").textAndStyle())
        assertEquals("1.5" to "6", cells.getValue("D2").textAndStyle())
        assertEquals("25570.25" to "4", cells.getValue("F2").textAndStyle())
        assertFalse("empty cells are left out", "E3" in cells)
    }

    @Test
    fun `text is escaped and comes back unchanged`() {
        val cell = cellsOf("xl/worksheets/sheet2.xml").getValue("E2")
        assertEquals("inlineStr", cell.getAttribute("t"))
        assertEquals("<draft> & \"notes\"", cell.textContent)
    }

    @Test
    fun `control characters are removed, emoji kept, long text cut`() {
        val messy = "a\u0000b\u0007c\td😀e\uD800"
        val long = "x".repeat(40_000)
        val table = ReportTable("Notes", listOf("Note"), listOf(listOf(t(messy)), listOf(t(long))))
        val cells = cellsOf(unzip(write(listOf(table))), "xl/worksheets/sheet1.xml")
        assertEquals("abc\td😀e", cells.getValue("A2").textContent)
        assertEquals(32_000, cells.getValue("A3").textContent.length)
    }

    @Test
    fun `data sheets have a frozen header and an autofilter, the summary does not`() {
        val data = parse("xl/worksheets/sheet2.xml")
        assertEquals("frozen", elements(data, "pane").single().getAttribute("state"))
        assertEquals("A1:F3", elements(data, "autoFilter").single().getAttribute("ref"))
        assertEquals(6, elements(data, "col").size)
        val summarySheet = parse("xl/worksheets/sheet1.xml")
        assertTrue(elements(summarySheet, "pane").isEmpty())
        assertTrue(elements(summarySheet, "autoFilter").isEmpty())
    }

    @Test
    fun `long sheet names are cut to 31 characters`() {
        val table = ReportTable("A very long sheet name that goes on: and on", listOf("x"), emptyList())
        val workbook = parse(unzip(write(listOf(table))), "xl/workbook.xml")
        val name = elements(workbook, "sheet").single().getAttribute("name")
        assertEquals(31, name.length)
        assertFalse(':' in name)
    }

    @Test
    fun `the whole fixture report writes and parses`() {
        val report = buildReport(FIXTURE, ReportRange.AllTime, fixtureClock())
        val fixtureParts = unzip(write(report))
        (1..6).forEach { parse(fixtureParts, "xl/worksheets/sheet$it.xml") }
    }

    @Test
    fun `column names and date serials`() {
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA"), listOf(0, 25, 26, 51, 52).map(::columnName))
        assertEquals(46_342L, excelSerial(LocalDate.parse("2026-11-16")))
    }

    private fun write(tables: List<ReportTable>): ByteArray =
        ByteArrayOutputStream().also { XlsxWriter().write(tables, it) }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> result[entry.name] = zip.readBytes() }
        }
        return result
    }

    private fun parse(path: String): Document = parse(parts, path)

    private fun parse(from: Map<String, ByteArray>, path: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(from.getValue(path)))
    }

    private fun elements(document: Document, localName: String): List<Element> {
        val nodes = document.getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun cellsOf(path: String): Map<String, Element> = cellsOf(parts, path)

    private fun cellsOf(from: Map<String, ByteArray>, path: String): Map<String, Element> =
        elements(parse(from, path), "c").associateBy { it.getAttribute("r") }

    private fun Element.textAndStyle(): Pair<String, String> = textContent to getAttribute("s")
}
