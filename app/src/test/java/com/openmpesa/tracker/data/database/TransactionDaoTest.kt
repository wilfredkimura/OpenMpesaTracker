package com.openmpesa.tracker.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Unit tests verifying the TransactionDao against an in-memory SQLite Room database.
 * Uses Robolectric to run natively on the JVM without needing an Android emulator or device.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Create a fast, isolated in-memory database that is destroyed after the test finishes
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveTransactions() = runBlocking {
        val tx1 = MpesaTransactionEntity(
            code = "UIUNA8IXS2",
            amount = 50.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.PAYBILL,
            party = "KPLC PREPAID",
            accountNumber = "92104387870",
            timestamp = 1000L,
            rawMessage = "UIUNA8IXS2 Confirmed. Ksh50.00..."
        )

        val tx2 = MpesaTransactionEntity(
            code = "UHNK63KR46",
            amount = 500.00,
            direction = TransactionDirection.INBOUND,
            type = TransactionType.SEND_MONEY_INBOUND,
            party = "NAOMI NJERI",
            phoneNumber = "0720***167",
            timestamp = 2000L,
            rawMessage = "UHNK63KR46 Confirmed.You have received..."
        )

        transactionDao.insertTransactions(listOf(tx1, tx2))

        val all = transactionDao.getAllTransactions().first()
        assertEquals(2, all.size)
        // Ordered by timestamp DESC (tx2 with 2000L comes before tx1 with 1000L)
        assertEquals("UHNK63KR46", all[0].code)
        assertEquals("UIUNA8IXS2", all[1].code)
    }

    @Test
    fun testDeduplicationIgnoresDuplicateTransactionCodes() = runBlocking {
        val original = MpesaTransactionEntity(
            code = "UJ6NA9AFW1",
            amount = 105.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "DICII SUPERMARKET",
            timestamp = 1000L,
            rawMessage = "UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on...",
            category = "Groceries"
        )

        // Insert first time
        val firstInsertResult = transactionDao.insertTransaction(original)
        assertEquals(1L, firstInsertResult)

        // Attempt to insert the identical transaction code a second time (e.g. historical scan after broadcast)
        val duplicate = original.copy(category = "ShouldNotOverwrite")
        val secondInsertResult = transactionDao.insertTransaction(duplicate)

        // OnConflictStrategy.IGNORE returns -1L on duplicate key
        assertEquals(-1L, secondInsertResult)

        // Verify that the table still has exactly 1 entry and existing category was preserved
        val all = transactionDao.getAllTransactions().first()
        assertEquals(1, all.size)
        assertEquals("Groceries", all[0].category)
    }

    @Test
    fun testTotalsCalculationBetweenDateRanges() = runBlocking {
        val in1 = MpesaTransactionEntity(
            code = "CODE_IN_1",
            amount = 1000.00,
            direction = TransactionDirection.INBOUND,
            type = TransactionType.SEND_MONEY_INBOUND,
            party = "John",
            timestamp = 1500L,
            rawMessage = "msg"
        )
        val out1 = MpesaTransactionEntity(
            code = "CODE_OUT_1",
            amount = 300.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.PAYBILL,
            party = "KPLC",
            timestamp = 1600L,
            rawMessage = "msg"
        )
        val out2 = MpesaTransactionEntity(
            code = "CODE_OUT_2",
            amount = 200.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "Quickmart",
            timestamp = 1700L,
            rawMessage = "msg"
        )
        // Transaction outside the query range
        val outOld = MpesaTransactionEntity(
            code = "CODE_OUT_OLD",
            amount = 5000.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.PAYBILL,
            party = "Old Paybill",
            timestamp = 500L,
            rawMessage = "msg"
        )

        transactionDao.insertTransactions(listOf(in1, out1, out2, outOld))

        val totalIn = transactionDao.getTotalInboundBetween(1000L, 2000L).first()
        val totalOut = transactionDao.getTotalOutboundBetween(1000L, 2000L).first()

        assertEquals(1000.00, totalIn ?: 0.0, 0.001)
        assertEquals(500.00, totalOut ?: 0.0, 0.001)
    }

    @Test
    fun testCategorySpendingGrouping() = runBlocking {
        val tx1 = MpesaTransactionEntity(
            code = "TX1",
            amount = 200.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "Naivas",
            category = "Groceries",
            timestamp = 1000L,
            rawMessage = "msg"
        )
        val tx2 = MpesaTransactionEntity(
            code = "TX2",
            amount = 350.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "Carrefour",
            category = "Groceries",
            timestamp = 1100L,
            rawMessage = "msg"
        )
        val tx3 = MpesaTransactionEntity(
            code = "TX3",
            amount = 100.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.PAYBILL,
            party = "KPLC",
            category = "Utilities",
            timestamp = 1200L,
            rawMessage = "msg"
        )

        transactionDao.insertTransactions(listOf(tx1, tx2, tx3))

        val spending = transactionDao.getCategorySpendingBetween(500L, 2000L).first()
        assertEquals(2, spending.size)

        // Groceries should be first (highest total: 550.00)
        assertEquals("Groceries", spending[0].category)
        assertEquals(550.00, spending[0].totalAmount, 0.001)
        assertEquals(2, spending[0].transactionCount)

        // Utilities second (100.00)
        assertEquals("Utilities", spending[1].category)
        assertEquals(100.00, spending[1].totalAmount, 0.001)
        assertEquals(1, spending[1].transactionCount)
    }

    @Test
    fun testUpdateCategoryAndNotes() = runBlocking {
        val tx = MpesaTransactionEntity(
            code = "TX_UPDATE",
            amount = 150.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.POCHI_LA_BIASHARA,
            party = "Mama Mboga",
            timestamp = 1000L,
            rawMessage = "msg"
        )
        transactionDao.insertTransaction(tx)

        // Update category and notes
        transactionDao.updateCategory("TX_UPDATE", "Vegetables")
        transactionDao.updateNotes("TX_UPDATE", "Bought tomatoes and onions")

        val retrieved = transactionDao.getTransactionByCode("TX_UPDATE")
        assertNotNull(retrieved)
        assertEquals("Vegetables", retrieved!!.category)
        assertEquals("Bought tomatoes and onions", retrieved.notes)
    }

    @Test
    fun testGetNonExistentTransactionReturnsNull() = runBlocking {
        val nonExistent = transactionDao.getTransactionByCode("DOES_NOT_EXIST")
        assertNull(nonExistent)
    }
}
