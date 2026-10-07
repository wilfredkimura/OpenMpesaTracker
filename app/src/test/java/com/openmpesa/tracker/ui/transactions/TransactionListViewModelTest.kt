package com.openmpesa.tracker.ui.transactions

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import com.openmpesa.tracker.data.model.CategorySpending
import com.openmpesa.tracker.data.repository.TransactionRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying transaction list filtering, search algorithms,
 * category updates, and notes editing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModelTest {

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
     * Fake repository recording updates for category and notes.
     */
    private class FakeTransactionRepository(
        initialList: List<MpesaTransactionEntity> = emptyList()
    ) : TransactionRepository {
        val transactionsFlow = MutableStateFlow(initialList)
        val updatedCategories = mutableMapOf<String, String>()
        val updatedNotes = mutableMapOf<String, String>()

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

        override suspend fun updateCategory(code: String, category: String) {
            updatedCategories[code] = category
            transactionsFlow.value = transactionsFlow.value.map {
                if (it.code == code) it.copy(category = category) else it
            }
        }

        override suspend fun updateNotes(code: String, notes: String) {
            updatedNotes[code] = notes
            transactionsFlow.value = transactionsFlow.value.map {
                if (it.code == code) it.copy(notes = notes) else it
            }
        }
    }

    private val sampleTransactions = listOf(
        MpesaTransactionEntity(
            code = "TX001PAY",
            amount = 1500.0,
            party = "KPLC PREPAID",
            phoneNumber = null,
            accountNumber = "998877",
            timestamp = 1000L,
            type = TransactionType.PAYBILL,
            direction = TransactionDirection.OUTBOUND,
            balance = 5000.0,
            transactionFee = 23.0,
            category = "Utilities & Bills",
            notes = "Electricity token",
            rawMessage = "TX001PAY Confirmed..."
        ),
        MpesaTransactionEntity(
            code = "TX002TIL",
            amount = 800.0,
            party = "CARREFOUR SUPERMARKET",
            phoneNumber = null,
            accountNumber = "123456",
            timestamp = 2000L,
            type = TransactionType.BUY_GOODS,
            direction = TransactionDirection.OUTBOUND,
            balance = 4200.0,
            transactionFee = 0.0,
            category = "Shopping",
            notes = "",
            rawMessage = "TX002TIL Confirmed..."
        ),
        MpesaTransactionEntity(
            code = "TX003REC",
            amount = 3000.0,
            party = "JOHN DOE",
            phoneNumber = "0712***456",
            accountNumber = null,
            timestamp = 3000L,
            type = TransactionType.SEND_MONEY_INBOUND,
            direction = TransactionDirection.INBOUND,
            balance = 7200.0,
            transactionFee = 0.0,
            category = "General",
            notes = "Lunch reimbursement",
            rawMessage = "TX003REC Confirmed..."
        )
    )

    @Test
    fun filterTransactions_withEmptyQueryAndAllDirection_returnsAll() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "",
            directionFilter = DirectionFilter.ALL,
            selectedCategory = null
        )
        assertEquals(3, result.size)
    }

    @Test
    fun filterTransactions_searchByPartyName() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "carrefour",
            directionFilter = DirectionFilter.ALL,
            selectedCategory = null
        )
        assertEquals(1, result.size)
        assertEquals("TX002TIL", result.first().code)
    }

    @Test
    fun filterTransactions_searchByTransactionCode() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "003rec",
            directionFilter = DirectionFilter.ALL,
            selectedCategory = null
        )
        assertEquals(1, result.size)
        assertEquals("TX003REC", result.first().code)
    }

    @Test
    fun filterTransactions_searchByAccountNumber() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "998877",
            directionFilter = DirectionFilter.ALL,
            selectedCategory = null
        )
        assertEquals(1, result.size)
        assertEquals("TX001PAY", result.first().code)
    }

    @Test
    fun filterTransactions_searchByNotes() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "reimbursement",
            directionFilter = DirectionFilter.ALL,
            selectedCategory = null
        )
        assertEquals(1, result.size)
        assertEquals("TX003REC", result.first().code)
    }

    @Test
    fun filterTransactions_filterByInbound() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "",
            directionFilter = DirectionFilter.INBOUND,
            selectedCategory = null
        )
        assertEquals(1, result.size)
        assertEquals("TX003REC", result.first().code)
    }

    @Test
    fun filterTransactions_filterByOutbound() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "",
            directionFilter = DirectionFilter.OUTBOUND,
            selectedCategory = null
        )
        assertEquals(2, result.size)
    }

    @Test
    fun filterTransactions_filterByCategory() {
        val result = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "",
            directionFilter = DirectionFilter.ALL,
            selectedCategory = "Utilities & Bills"
        )
        assertEquals(1, result.size)
        assertEquals("TX001PAY", result.first().code)
    }

    @Test
    fun filterTransactions_combiningQueryDirectionAndCategory() {
        // Query "carrefour", OUTBOUND, "Shopping" -> 1 match
        val match = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "carrefour",
            directionFilter = DirectionFilter.OUTBOUND,
            selectedCategory = "Shopping"
        )
        assertEquals(1, match.size)

        // Query "carrefour", INBOUND, "Shopping" -> 0 matches
        val noMatch = TransactionListViewModel.filterTransactions(
            transactions = sampleTransactions,
            query = "carrefour",
            directionFilter = DirectionFilter.INBOUND,
            selectedCategory = "Shopping"
        )
        assertEquals(0, noMatch.size)
    }

    @Test
    fun viewModel_setSearchQueryAndFilters_updatesFilteredList() = runTest {
        val repo = FakeTransactionRepository(sampleTransactions)
        val viewModel = TransactionListViewModel(repository = repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.filteredTransactions.size)

        viewModel.setSearchQuery("KPLC")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.filteredTransactions.size)
        assertEquals("TX001PAY", viewModel.uiState.value.filteredTransactions.first().code)

        viewModel.setSearchQuery("")
        viewModel.setDirectionFilter(DirectionFilter.INBOUND)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.filteredTransactions.size)
        assertEquals("TX003REC", viewModel.uiState.value.filteredTransactions.first().code)
    }

    @Test
    fun viewModel_updateCategoryAndNotes_persistsChanges() = runTest {
        val repo = FakeTransactionRepository(sampleTransactions)
        val viewModel = TransactionListViewModel(repository = repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val tx = sampleTransactions.first()
        viewModel.selectTransaction(tx)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("TX001PAY", viewModel.uiState.value.selectedTransaction?.code)

        // Update category
        viewModel.updateCategory("TX001PAY", "Entertainment")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Entertainment", repo.updatedCategories["TX001PAY"])
        assertEquals("Entertainment", viewModel.uiState.value.selectedTransaction?.category)

        // Update notes
        viewModel.updateNotes("TX001PAY", "Tokens for rental unit")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Tokens for rental unit", repo.updatedNotes["TX001PAY"])
        assertEquals("Tokens for rental unit", viewModel.uiState.value.selectedTransaction?.notes)

        // Dismiss sheet
        viewModel.selectTransaction(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedTransaction)
    }
}
