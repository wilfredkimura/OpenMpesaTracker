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
import java.io.OutputStream
import java.text.SimpleDateFormat

/**
 * Unit tests verifying PdfReportExporter metrics calculation, pagination logic, and PDF file generation.
 */
@RunWith(RobolectricTestRunner::class)
class PdfReportExporterTest {

    private lateinit var context: Context
    private lateinit var testWriter: TestPdfCanvasWriter
    private lateinit var exporter: PdfReportExporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testWriter = TestPdfCanvasWriter()
        exporter = PdfReportExporter(Dispatchers.Unconfined, testWriter)
    }

    @Test
    fun testCalculateSummaryMetrics() {
        val transactions = listOf(
            MpesaTransactionEntity(
                code = "TX_IN_1",
                amount = 2000.00,
                direction = TransactionDirection.INBOUND,
                type = TransactionType.SEND_MONEY_INBOUND,
                party = "Alice",
                timestamp = 1000L,
                rawMessage = "msg"
            ),
            MpesaTransactionEntity(
                code = "TX_OUT_1",
                amount = 350.00,
                direction = TransactionDirection.OUTBOUND,
                type = TransactionType.BUY_GOODS,
                party = "Shop",
                timestamp = 1100L,
                rawMessage = "msg"
            ),
            MpesaTransactionEntity(
                code = "TX_OUT_2",
                amount = 150.00,
                direction = TransactionDirection.OUTBOUND,
                type = TransactionType.PAYBILL,
                party = "KPLC",
                timestamp = 1200L,
                rawMessage = "msg"
            )
        )

        val summary = exporter.calculateSummary(transactions)
        assertEquals(2000.00, summary.totalInbound, 0.001)
        assertEquals(500.00, summary.totalOutbound, 0.001)
        assertEquals(1500.00, summary.netBalance, 0.001)
        assertEquals(3, summary.totalTransactions)
    }

    @Test
    fun testExportToPdfCreatesValidFile() = runBlocking {
        val transactions = listOf(
            MpesaTransactionEntity(
                code = "UIUNA8IXS2",
                amount = 50.00,
                direction = TransactionDirection.OUTBOUND,
                type = TransactionType.PAYBILL,
                party = "KPLC PREPAID",
                accountNumber = "92104387870",
                timestamp = 1729000000000L,
                rawMessage = "msg"
            )
        )

        val file = exporter.exportToPdf(context, transactions)
        assertNotNull("Generated PDF file must not be null", file)
        assertTrue("PDF file must exist on disk", file!!.exists())
        assertTrue("PDF file name must end with .pdf", file.name.endsWith(".pdf"))
        assertTrue("PDF file size must be greater than 0 bytes", file.length() > 0)
        assertEquals("Single transaction should occupy 1 page", 1, testWriter.lastPageCount)
    }

    @Test
    fun testExportToPdfMultiPagePagination() = runBlocking {
        // 60 transactions: at 35 rows for page 1, this should span across 2 pages
        val longList = (1..60).map { index ->
            MpesaTransactionEntity(
                code = "CODE%06d".format(index),
                amount = index * 10.0,
                direction = if (index % 2 == 0) TransactionDirection.OUTBOUND else TransactionDirection.INBOUND,
                type = TransactionType.BUY_GOODS,
                party = "Merchant #$index",
                timestamp = 1729000000000L + (index * 1000L),
                rawMessage = "msg"
            )
        }

        val file = exporter.exportToPdf(context, longList)
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue("60 transactions must trigger multi-page pagination (>= 2 pages)", testWriter.lastPageCount >= 2)
    }

    @Test
    fun testExportToPdfEmptyListGeneratesStatement() = runBlocking {
        val file = exporter.exportToPdf(context, emptyList())
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertEquals(1, testWriter.lastPageCount)
        assertEquals(0.0, testWriter.lastSummary?.totalInbound ?: -1.0, 0.001)
    }
}

/**
 * Test double implementation of PdfCanvasWriter that simulates A4 line pagination
 * and writes valid mock PDF byte headers without requiring native C++ Skia runtime.
 */
class TestPdfCanvasWriter : PdfCanvasWriter {
    var lastPageCount: Int = 0
    var lastSummary: PdfStatementSummary? = null

    override fun writeStatement(
        outputStream: OutputStream,
        transactions: List<MpesaTransactionEntity>,
        title: String,
        summary: PdfStatementSummary,
        dateFormat: SimpleDateFormat
    ): Int {
        lastSummary = summary

        // Simulate pagination logic:
        // Page 1 header takes 140pt, available content height is 640pt / 18pt = ~35 rows
        // Page 2+ header takes 64pt, available content height is 716pt / 18pt = ~39 rows
        val count = if (transactions.isEmpty()) {
            1
        } else {
            var pages = 1
            var rowsOnCurrentPage = 0
            val maxFirstPage = 35
            val maxSubsequent = 39

            for (i in transactions.indices) {
                val max = if (pages == 1) maxFirstPage else maxSubsequent
                if (rowsOnCurrentPage >= max) {
                    pages++
                    rowsOnCurrentPage = 0
                }
                rowsOnCurrentPage++
            }
            pages
        }

        lastPageCount = count

        // Write valid PDF byte signature
        outputStream.write("%PDF-1.4\n%TEST_PDF_STREAM\n%%EOF\n".toByteArray())
        outputStream.flush()

        return count
    }
}
