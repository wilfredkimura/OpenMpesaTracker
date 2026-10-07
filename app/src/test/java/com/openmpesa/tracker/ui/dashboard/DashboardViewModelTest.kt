package com.openmpesa.tracker.ui.dashboard

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import com.openmpesa.tracker.data.model.CategorySpending
import com.openmpesa.tracker.data.repository.TransactionRepository
import com.openmpesa.tracker.ingestion.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * Unit tests verifying financial dashboard calculations, currency formatting,
 * and category percentage computations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Fake repository implementation for deterministic in-memory tests.
     */
    private class FakeTransactionRepository(
        initialList: List<MpesaTransactionEntity> = emptyList()
    ) : TransactionRepository {
        val transactionsFlow = MutableStateFlow(initialList)

        override fun getAllTransactions(): Flow<List<MpesaTransactionEntity>> = transactionsFlow.asStateFlow()

        override fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): Flow<List<MpesaTransactionEntity>> {
            return flowOf(transactionsFlow.value.filter { it.timestamp in startEpoch..endEpoch })
        }

        override fun getTransactionsByDirection(direction: TransactionDirection): Flow<List<MpesaTransactionEntity>> {
            return flowOf(transactionsFlow.value.filter { it.direction == direction })
        }

        override fun getTotalInbound(startEpoch: Long, endEpoch: Long): Flow<Double> {
            val sum = transactionsFlow.value
                .filter { it.direction == TransactionDirection.INBOUND && it.timestamp in startEpoch..endEpoch }
                .sumOf { it.amount }
            return flowOf(sum)
        }

        override fun getTotalOutbound(startEpoch: Long, endEpoch: Long): Flow<Double> {
            val sum = transactionsFlow.value
                .filter { it.direction == TransactionDirection.OUTBOUND && it.timestamp in startEpoch..endEpoch }
                .sumOf { it.amount }
            return flowOf(sum)
        }

        override fun getCategorySpending(startEpoch: Long, endEpoch: Long): Flow<List<CategorySpending>> {
            val list = transactionsFlow.value
                .filter { it.direction == TransactionDirection.OUTBOUND && it.timestamp in startEpoch..endEpoch }
                .groupBy { it.category }
                .map { (cat, txs) -> CategorySpending(cat, txs.sumOf { it.amount }, txs.size) }
            return flowOf(list)
        }

        override suspend fun insertTransaction(transaction: MpesaTransactionEntity): Boolean = true
        override suspend fun insertTransactions(transactions: List<MpesaTransactionEntity>): Int = transactions.size
        override suspend fun getTransactionByCode(code: String): MpesaTransactionEntity? =
            transactionsFlow.value.firstOrNull { it.code == code }
        override suspend fun updateCategory(code: String, category: String) {}
        override suspend fun updateNotes(code: String, notes: String) {}
    }

    @Test
    fun dashboardFormatter_formatsKshCorrectly() {
        assertEquals("Ksh 0.00", DashboardFormatter.formatKsh(0.0))
        assertEquals("Ksh 1,250.00", DashboardFormatter.formatKsh(1250.0))
        assertEquals("Ksh 105,432.50", DashboardFormatter.formatKsh(105432.5))
    }

    @Test
    fun dashboardFormatter_formatsDateNonEmpty() {
        val result = DashboardFormatter.formatDate(1773000000000L)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun dashboardPeriod_startEpochCalculations() {
        val fixedNow = 1773000000000L
        assertEquals(0L, DashboardPeriod.ALL_TIME.startEpochMillis(fixedNow))

        val thirtyDaysBack = fixedNow - (30L * 24L * 60L * 60L * 1000L)
        assertEquals(thirtyDaysBack, DashboardPeriod.LAST_30_DAYS.startEpochMillis(fixedNow))

        val monthStart = DashboardPeriod.THIS_MONTH.startEpochMillis(fixedNow)
        assertTrue("Month start must be before or equal to now", monthStart <= fixedNow)
    }

    @Test
    fun calculateUiState_aggregatesIncomeExpensesFeesAndPercentages() {
        val sampleTransactions = listOf(
            MpesaTransactionEntity(
                code = "TX001",
                amount = 5000.0,
                party = "JANE DOE",
                phoneNumber = "0720***167",
                accountNumber = null,
                timestamp = System.currentTimeMillis() - 1000,
                type = TransactionType.SEND_MONEY_INBOUND,
                direction = TransactionDirection.INBOUND,
                balance = 12500.0,
                transactionFee = 0.0,
                category = "General",
                notes = "",
                rawMessage = "Received Ksh5,000 from JANE DOE"
            ),
            MpesaTransactionEntity(
                code = "TX002",
                amount = 2000.0,
                party = "KPLC PREPAID",
                phoneNumber = null,
                accountNumber = "998877",
                timestamp = System.currentTimeMillis() - 2000,
                type = TransactionType.PAYBILL,
                direction = TransactionDirection.OUTBOUND,
                balance = 10477.0,
                transactionFee = 23.0,
                category = "Utilities",
                notes = "",
                rawMessage = "Paid Ksh2,000 to KPLC PREPAID"
            ),
            MpesaTransactionEntity(
                code = "TX003",
                amount = 1000.0,
                party = "NAIVAS SUPERMARKET",
                phoneNumber = null,
                accountNumber = "123456",
                timestamp = System.currentTimeMillis() - 3000,
                type = TransactionType.BUY_GOODS,
                direction = TransactionDirection.OUTBOUND,
                balance = 9477.0,
                transactionFee = 0.0,
                category = "Shopping",
                notes = "",
                rawMessage = "Paid Ksh1,000 to NAIVAS"
            )
        )

        val state = DashboardViewModel.calculateUiState(
            period = DashboardPeriod.THIS_MONTH,
            transactions = sampleTransactions,
            allTransactions = sampleTransactions,
            isSyncing = false,
            syncMessage = null
        )

        assertEquals(5000.0, state.totalIncome, 0.001)
        assertEquals(3000.0, state.totalExpenses, 0.001)
        assertEquals(23.0, state.totalFees, 0.001)
        assertEquals(2000.0, state.netCashFlow, 0.001)
        assertEquals(12500.0, state.latestBalance!!, 0.001)

        // Verify Category Breakdown
        assertEquals(2, state.categoryBreakdown.size)
        val utilities = state.categoryBreakdown.first { it.category == "Utilities" }
        assertEquals(2000.0, utilities.totalAmount, 0.001)
        assertEquals(1, utilities.transactionCount)
        // 2000 / 3000 = 66.666%
        assertEquals(66.66f, utilities.percentage, 0.1f)

        val shopping = state.categoryBreakdown.first { it.category == "Shopping" }
        assertEquals(1000.0, shopping.totalAmount, 0.001)
        assertEquals(1, shopping.transactionCount)
        // 1000 / 3000 = 33.333%
        assertEquals(33.33f, shopping.percentage, 0.1f)
    }

    @Test
    fun calculateUiState_handlesZeroExpensesSafely() {
        val onlyInbound = listOf(
            MpesaTransactionEntity(
                code = "TX001",
                amount = 1000.0,
                party = "JOHN DOE",
                phoneNumber = "0711***222",
                accountNumber = null,
                timestamp = System.currentTimeMillis(),
                type = TransactionType.SEND_MONEY_INBOUND,
                direction = TransactionDirection.INBOUND,
                balance = 1000.0,
                transactionFee = 0.0,
                category = "General",
                notes = "",
                rawMessage = "Received Ksh1,000"
            )
        )

        val state = DashboardViewModel.calculateUiState(
            period = DashboardPeriod.THIS_MONTH,
            transactions = onlyInbound,
            allTransactions = onlyInbound,
            isSyncing = false,
            syncMessage = null
        )

        assertEquals(1000.0, state.totalIncome, 0.001)
        assertEquals(0.0, state.totalExpenses, 0.001)
        assertEquals(0.0, state.totalFees, 0.001)
        assertEquals(1000.0, state.netCashFlow, 0.001)
        assertTrue("Category breakdown should be empty when no expenses exist", state.categoryBreakdown.isEmpty())
    }

    @Test
    fun selectPeriod_updatesSelectedPeriodState() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = DashboardViewModel(repository = repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.selectPeriod(DashboardPeriod.ALL_TIME)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify period state changed
        assertEquals(DashboardPeriod.ALL_TIME, viewModel.uiState.value.selectedPeriod)
    }

    @Test
    fun syncSmsInbox_executesAndSetsSyncMessage() = runTest {
        val repo = FakeTransactionRepository()
        val mockSyncProvider: suspend (onProgress: (Int, Int) -> Unit) -> SyncResult = {
            SyncResult(scannedCount = 10, savedCount = 4, skippedCount = 6)
        }

        val viewModel = DashboardViewModel(
            repository = repo,
            syncProvider = mockSyncProvider
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.syncSmsInbox()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals("Synced: 4 new transactions added.", viewModel.uiState.value.syncMessage)

        viewModel.dismissSyncMessage()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.syncMessage)
    }
}
