package com.openmpesa.tracker.data.repository

import com.openmpesa.tracker.data.database.TransactionDao
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import com.openmpesa.tracker.data.model.CategorySpending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying DefaultTransactionRepository using a lightweight FakeTransactionDao.
 */
class TransactionRepositoryTest {

    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        fakeDao = FakeTransactionDao()
        repository = DefaultTransactionRepository(
            transactionDao = fakeDao,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun testInsertTransactionReturnsTrueWhenNewAndFalseWhenDuplicate() = runBlocking {
        val tx = MpesaTransactionEntity(
            code = "TEST0001AA",
            amount = 150.0,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.PAYBILL,
            party = "KPLC",
            timestamp = 1000L,
            rawMessage = "msg"
        )

        // First insertion should succeed
        val firstResult = repository.insertTransaction(tx)
        assertTrue("New transaction should return true", firstResult)

        // Second insertion with identical code should be ignored and return false
        val duplicateResult = repository.insertTransaction(tx)
        assertFalse("Duplicate transaction should return false", duplicateResult)
    }

    @Test
    fun testBatchInsertCountsOnlyNonDuplicates() = runBlocking {
        val tx1 = MpesaTransactionEntity(
            code = "CODE1",
            amount = 100.0,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "Shop 1",
            timestamp = 1000L,
            rawMessage = "msg"
        )
        val tx2 = MpesaTransactionEntity(
            code = "CODE2",
            amount = 200.0,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "Shop 2",
            timestamp = 1100L,
            rawMessage = "msg"
        )

        // Insert tx1 first
        repository.insertTransaction(tx1)

        // Now batch insert tx1 (duplicate) and tx2 (new)
        val insertedCount = repository.insertTransactions(listOf(tx1, tx2))
        assertEquals("Only 1 new transaction should be counted as inserted", 1, insertedCount)
    }

    @Test
    fun testTotalsEmitZeroWhenEmptyInsteadOfNull() = runBlocking {
        val inboundTotal = repository.getTotalInbound(1000L, 2000L).first()
        val outboundTotal = repository.getTotalOutbound(1000L, 2000L).first()

        assertEquals("Empty inbound total must coalesce to 0.0", 0.0, inboundTotal, 0.001)
        assertEquals("Empty outbound total must coalesce to 0.0", 0.0, outboundTotal, 0.001)
    }

    @Test
    fun testCategoryUpdateAndNotesUpdate() = runBlocking {
        val tx = MpesaTransactionEntity(
            code = "CODE_MEMO",
            amount = 50.0,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.POCHI_LA_BIASHARA,
            party = "Fruit Stall",
            timestamp = 1000L,
            rawMessage = "msg"
        )
        repository.insertTransaction(tx)

        repository.updateCategory("CODE_MEMO", "Groceries")
        repository.updateNotes("CODE_MEMO", "Bought bananas")

        val updated = repository.getTransactionByCode("CODE_MEMO")
        assertNotNull(updated)
        assertEquals("Groceries", updated!!.category)
        assertEquals("Bought bananas", updated.notes)
    }

    @Test
    fun testLookupNonExistentTransactionReturnsNull() = runBlocking {
        val missing = repository.getTransactionByCode("NON_EXISTENT")
        assertNull(missing)
    }
}

/**
 * Lightweight fake in-memory implementation of TransactionDao for repository testing.
 */
class FakeTransactionDao : TransactionDao {

    private val storage = mutableMapOf<String, MpesaTransactionEntity>()
    private val transactionsFlow = MutableStateFlow<List<MpesaTransactionEntity>>(emptyList())

    private fun emitUpdate() {
        transactionsFlow.value = storage.values.sortedByDescending { it.timestamp }
    }

    override suspend fun insertTransactions(transactions: List<MpesaTransactionEntity>): List<Long> {
        return transactions.map { insertTransaction(it) }
    }

    override suspend fun insertTransaction(transaction: MpesaTransactionEntity): Long {
        return if (storage.containsKey(transaction.code)) {
            -1L // Simulates Room OnConflictStrategy.IGNORE
        } else {
            storage[transaction.code] = transaction
            emitUpdate()
            1L
        }
    }

    override suspend fun updateTransaction(transaction: MpesaTransactionEntity) {
        storage[transaction.code] = transaction
        emitUpdate()
    }

    override fun getAllTransactions(): Flow<List<MpesaTransactionEntity>> = transactionsFlow

    override fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): Flow<List<MpesaTransactionEntity>> {
        return transactionsFlow.map { list ->
            list.filter { it.timestamp in startEpoch..endEpoch }
        }
    }

    override fun getTransactionsByDirection(direction: TransactionDirection): Flow<List<MpesaTransactionEntity>> {
        return transactionsFlow.map { list ->
            list.filter { it.direction == direction }
        }
    }

    override fun getTotalInboundBetween(startEpoch: Long, endEpoch: Long): Flow<Double?> {
        return transactionsFlow.map { list ->
            val filtered = list.filter { it.direction == TransactionDirection.INBOUND && it.timestamp in startEpoch..endEpoch }
            if (filtered.isEmpty()) null else filtered.sumOf { it.amount }
        }
    }

    override fun getTotalOutboundBetween(startEpoch: Long, endEpoch: Long): Flow<Double?> {
        return transactionsFlow.map { list ->
            val filtered = list.filter { it.direction == TransactionDirection.OUTBOUND && it.timestamp in startEpoch..endEpoch }
            if (filtered.isEmpty()) null else filtered.sumOf { it.amount }
        }
    }

    override fun getCategorySpendingBetween(startEpoch: Long, endEpoch: Long): Flow<List<CategorySpending>> {
        return transactionsFlow.map { list ->
            list.filter { it.direction == TransactionDirection.OUTBOUND && it.timestamp in startEpoch..endEpoch }
                .groupBy { it.category }
                .map { (cat, txs) -> CategorySpending(cat, txs.sumOf { it.amount }, txs.size) }
                .sortedByDescending { it.totalAmount }
        }
    }

    override suspend fun getTransactionByCode(code: String): MpesaTransactionEntity? {
        return storage[code]
    }

    override suspend fun updateCategory(code: String, category: String) {
        val existing = storage[code]
        if (existing != null) {
            storage[code] = existing.copy(category = category)
            emitUpdate()
        }
    }

    override suspend fun updateNotes(code: String, notes: String) {
        val existing = storage[code]
        if (existing != null) {
            storage[code] = existing.copy(notes = notes)
            emitUpdate()
        }
    }
}
