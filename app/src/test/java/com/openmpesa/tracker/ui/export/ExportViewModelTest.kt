package com.openmpesa.tracker.ui.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import com.openmpesa.tracker.data.model.CategorySpending
import com.openmpesa.tracker.data.repository.TransactionRepository
import com.openmpesa.tracker.export.CsvReportExporter
import com.openmpesa.tracker.export.FileUriProvider
import com.openmpesa.tracker.export.PdfCanvasWriter
import com.openmpesa.tracker.export.PdfReportExporter
import com.openmpesa.tracker.export.PdfStatementSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat

/**
 * Unit tests verifying export state transitions, CSV & PDF generation triggers,
 * and Share Sheet intent creation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test canvas writer avoiding native Skia calls during JVM tests.
     */
    private class TestPdfCanvasWriter : PdfCanvasWriter {
        override fun writeStatement(
            outputStream: OutputStream,
            transactions: List<MpesaTransactionEntity>,
            title: String,
            summary: PdfStatementSummary,
            dateFormat: SimpleDateFormat
        ): Int {
            outputStream.write("%PDF-1.4 mock content".toByteArray())
            return 1
        }
    }

    /**
     * Test URI provider returning synthetic URIs without requiring manifest provider registration.
     */
    private class TestFileUriProvider : FileUriProvider {
        override fun getUriForFile(context: Context, file: File, authority: String): Uri {
            return Uri.parse("content://$authority/${file.name}")
        }
    }

    /**
     * Fake repository for export testing.
     */
    private class FakeTransactionRepository(
        initialList: List<MpesaTransactionEntity> = emptyList()
    ) : TransactionRepository {
        val transactionsFlow = MutableStateFlow(initialList)

        override fun getAllTransactions(): Flow<List<MpesaTransactionEntity>> = transactionsFlow.asStateFlow()
        override fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): Flow<List<MpesaTransactionEntity>> =
            flowOf(transactionsFlow.value.filter { it.timestamp in startEpoch..endEpoch })
        override fun getTransactionsByDirection(direction: TransactionDirection): Flow<List<MpesaTransactionEntity>> =
            flowOf(transactionsFlow.value.filter { it.direction == direction })
        override fun getTotalInbound(startEpoch: Long, endEpoch: Long): Flow<Double> = flowOf(0.0)
        override fun getTotalOutbound(startEpoch: Long, endEpoch: Long): Flow<Double> = flowOf(0.0)
        override fun getCategorySpending(startEpoch: Long, endEpoch: Long): Flow<List<CategorySpending>> = flowOf(emptyList())
        override suspend fun insertTransaction(transaction: MpesaTransactionEntity): Boolean = true
        override suspend fun insertTransactions(transactions: List<MpesaTransactionEntity>): Int = transactions.size
        override suspend fun getTransactionByCode(code: String): MpesaTransactionEntity? =
            transactionsFlow.value.firstOrNull { it.code == code }
        override suspend fun updateCategory(code: String, category: String) {}
        override suspend fun updateNotes(code: String, notes: String) {}
    }

    private val sampleTransactions = listOf(
        MpesaTransactionEntity(
            code = "TX001PAY",
            amount = 1500.0,
            party = "KPLC PREPAID",
            phoneNumber = null,
            accountNumber = "998877",
            timestamp = System.currentTimeMillis() - 1000,
            type = TransactionType.PAYBILL,
            direction = TransactionDirection.OUTBOUND,
            balance = 5000.0,
            transactionFee = 23.0,
            category = "Utilities & Bills",
            notes = "",
            rawMessage = "TX001PAY Confirmed..."
        ),
        MpesaTransactionEntity(
            code = "TX002REC",
            amount = 3000.0,
            party = "JOHN DOE",
            phoneNumber = "0712***456",
            accountNumber = null,
            timestamp = System.currentTimeMillis() - 2000,
            type = TransactionType.SEND_MONEY_INBOUND,
            direction = TransactionDirection.INBOUND,
            balance = 8000.0,
            transactionFee = 0.0,
            category = "General",
            notes = "",
            rawMessage = "TX002REC Confirmed..."
        )
    )

    @Test
    fun initialState_hasDefaultValues() {
        val repo = FakeTransactionRepository()
        val viewModel = ExportViewModel(repository = repo)

        val state = viewModel.uiState.value
        assertEquals(ExportFormat.PDF, state.selectedFormat)
        assertEquals(ExportDateRange.THIS_MONTH, state.selectedDateRange)
        assertFalse(state.isExporting)
        assertNull(state.exportedFile)
        assertNull(state.shareIntent)
        assertNull(state.errorMessage)
    }

    @Test
    fun selectFormatAndDateRange_updatesState() {
        val repo = FakeTransactionRepository()
        val viewModel = ExportViewModel(repository = repo)

        viewModel.selectFormat(ExportFormat.CSV)
        assertEquals(ExportFormat.CSV, viewModel.uiState.value.selectedFormat)

        viewModel.selectDateRange(ExportDateRange.ALL_TIME)
        assertEquals(ExportDateRange.ALL_TIME, viewModel.uiState.value.selectedDateRange)
    }

    @Test
    fun startExport_withCsvFormat_generatesFileAndShareIntent() = runTest {
        val repo = FakeTransactionRepository(sampleTransactions)
        val viewModel = ExportViewModel(
            repository = repo,
            csvExporter = CsvReportExporter(ioDispatcher = testDispatcher),
            fileUriProvider = TestFileUriProvider()
        )

        viewModel.selectFormat(ExportFormat.CSV)
        viewModel.selectDateRange(ExportDateRange.ALL_TIME)
        viewModel.startExport(context)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNotNull("Exported file should be created", state.exportedFile)
        assertTrue(state.exportedFile!!.exists())
        assertTrue(state.exportedFile!!.name.endsWith(".csv"))
        assertNotNull("Share intent should be created", state.shareIntent)
        assertNull(state.errorMessage)
    }

    @Test
    fun startExport_withPdfFormat_generatesFileAndShareIntent() = runTest {
        val repo = FakeTransactionRepository(sampleTransactions)
        val viewModel = ExportViewModel(
            repository = repo,
            pdfExporter = PdfReportExporter(
                ioDispatcher = testDispatcher,
                canvasWriter = TestPdfCanvasWriter()
            ),
            fileUriProvider = TestFileUriProvider()
        )

        viewModel.selectFormat(ExportFormat.PDF)
        viewModel.selectDateRange(ExportDateRange.ALL_TIME)
        viewModel.startExport(context)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNotNull("Exported file should be created", state.exportedFile)
        assertTrue(state.exportedFile!!.exists())
        assertTrue(state.exportedFile!!.name.endsWith(".pdf"))
        assertNotNull("Share intent should be created", state.shareIntent)
        assertNull(state.errorMessage)
    }

    @Test
    fun startExport_whenTransactionsEmpty_setsErrorMessage() = runTest {
        val repo = FakeTransactionRepository(emptyList())
        val viewModel = ExportViewModel(
            repository = repo,
            fileUriProvider = TestFileUriProvider()
        )

        viewModel.startExport(context)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNull(state.exportedFile)
        assertNull(state.shareIntent)
        assertEquals("No transactions found to export for the selected date range.", state.errorMessage)
    }

    @Test
    fun clearExportEvent_resetsExportState() = runTest {
        val repo = FakeTransactionRepository(sampleTransactions)
        val viewModel = ExportViewModel(
            repository = repo,
            csvExporter = CsvReportExporter(ioDispatcher = testDispatcher),
            fileUriProvider = TestFileUriProvider()
        )

        viewModel.selectFormat(ExportFormat.CSV)
        viewModel.selectDateRange(ExportDateRange.ALL_TIME)
        viewModel.startExport(context)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.exportedFile)
        assertNotNull(viewModel.uiState.value.shareIntent)

        viewModel.clearExportEvent()

        val clearedState = viewModel.uiState.value
        assertNull(clearedState.exportedFile)
        assertNull(clearedState.shareIntent)
        assertNull(clearedState.errorMessage)
    }
}
