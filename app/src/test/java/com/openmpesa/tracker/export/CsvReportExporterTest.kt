package com.openmpesa.tracker.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests verifying CsvReportExporter spreadsheet formatting and comma/quote escaping.
 */
@RunWith(RobolectricTestRunner::class)
class CsvReportExporterTest {

    private lateinit var context: Context
    private lateinit var exporter: CsvReportExporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exporter = CsvReportExporter(Dispatchers.Unconfined)
    }

    @Test
    fun testExportToCsvWritesHeadersAndRows() = runBlocking {
        val transactions = listOf(
            MpesaTransactionEntity(
                code = "UIUNA8IXS2",
                amount = 50.00,
                direction = TransactionDirection.OUTBOUND,
                type = TransactionType.PAYBILL,
                party = "KPLC PREPAID",
                accountNumber = "92104387870",
                balance = 1450.00,
                transactionFee = 0.00,
                timestamp = 1729000000000L,
                rawMessage = "raw msg",
                category = "Utilities",
                notes = "Token purchase"
            ),
            MpesaTransactionEntity(
                code = "UHNK63KR46",
                amount = 500.00,
                direction = TransactionDirection.INBOUND,
                type = TransactionType.SEND_MONEY_INBOUND,
                party = "NAOMI NJERI",
                phoneNumber = "0720***167",
                balance = 1950.00,
                transactionFee = null,
                timestamp = 1729100000000L,
                rawMessage = "raw msg"
            )
        )

        val file = exporter.exportToCsv(context, transactions)
        assertNotNull("Generated CSV file must not be null", file)
        assertTrue("CSV file must exist on disk", file!!.exists())

        val lines = file.readLines()
        assertEquals("Must have 1 header line + 2 data lines", 3, lines.size)

        // Verify Header Line
        assertTrue("Header must contain Transaction Code", lines[0].startsWith("Transaction Code"))
        assertTrue("Header must contain Amount (KES)", lines[0].contains("Amount (KES)"))

        // Verify First Row
        assertTrue("Row 1 must contain code UIUNA8IXS2", lines[1].contains("\"UIUNA8IXS2\""))
        assertTrue("Row 1 must contain 50.00", lines[1].contains("50.00"))
        assertTrue("Row 1 must contain KPLC PREPAID", lines[1].contains("\"KPLC PREPAID\""))
        assertTrue("Row 1 must contain Utilities", lines[1].contains("\"Utilities\""))

        // Verify Second Row
        assertTrue("Row 2 must contain code UHNK63KR46", lines[2].contains("\"UHNK63KR46\""))
        assertTrue("Row 2 must contain 500.00", lines[2].contains("500.00"))
        assertTrue("Row 2 must contain INBOUND", lines[2].contains("INBOUND"))
    }

    @Test
    fun testExportToCsvEscapesCommasAndQuotes() = runBlocking {
        val txWithCommas = MpesaTransactionEntity(
            code = "TX_COMMA",
            amount = 120.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "QUICKMART, NAIROBI CBD BRANCH",
            category = "Groceries, Food & Drink",
            notes = "Purchased milk with \"special\" discount",
            timestamp = 1729200000000L,
            rawMessage = "raw msg"
        )

        val file = exporter.exportToCsv(context, listOf(txWithCommas))
        assertNotNull(file)

        val content = file!!.readText()
        // Quotes inside fields must be escaped as double quotes: ""special""
        assertTrue(content.contains("\"\"special\"\""))
        assertTrue(content.contains("\"QUICKMART, NAIROBI CBD BRANCH\""))
        assertTrue(content.contains("\"Groceries, Food & Drink\""))
    }

    @Test
    fun testExportToCsvEmptyListWritesHeadersOnly() = runBlocking {
        val file = exporter.exportToCsv(context, emptyList())
        assertNotNull(file)

        val lines = file!!.readLines()
        assertEquals("Empty export should only contain the header row", 1, lines.size)
    }
}
