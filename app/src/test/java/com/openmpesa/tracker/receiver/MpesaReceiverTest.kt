package com.openmpesa.tracker.receiver

import android.content.Intent
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import com.openmpesa.tracker.data.repository.DefaultTransactionRepository
import com.openmpesa.tracker.data.repository.FakeTransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying real-time SMS broadcast interception and sender filtering in MpesaReceiver.
 */
@RunWith(RobolectricTestRunner::class)
class MpesaReceiverTest {

    private lateinit var receiver: MpesaReceiver
    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: DefaultTransactionRepository

    @Before
    fun setUp() {
        receiver = MpesaReceiver()
        fakeDao = FakeTransactionDao()
        repository = DefaultTransactionRepository(fakeDao, Dispatchers.Unconfined)
    }

    @Test
    fun testIgnoresNonMpesaSenderMessages() = runBlocking {
        val nonMpesaSms = "Your bank balance is KES 50,000. Ref: 102948."
        val result = receiver.processMessage(
            sender = "BANK_ALERT",
            body = nonMpesaSms,
            timestamp = System.currentTimeMillis(),
            repository = repository
        )

        assertNull("Messages from non-MPESA senders must be ignored", result)
        val stored = repository.getAllTransactions().first()
        assertTrue("Database must remain empty when non-MPESA SMS arrives", stored.isEmpty())
    }

    @Test
    fun testProcessesValidMpesaIncomingTransaction() = runBlocking {
        val mpesaSms = "UIUNA8IXS2 Confirmed. Ksh50.00 sent to KPLC PREPAID for account 92104387870 on 12/9/26 at 8:15 PM."
        val result = receiver.processMessage(
            sender = "MPESA",
            body = mpesaSms,
            timestamp = 1729000000000L,
            repository = repository
        )

        assertNotNull("Valid M-Pesa transaction should be parsed successfully", result)
        assertEquals("UIUNA8IXS2", result!!.code)
        assertEquals(50.00, result.amount, 0.001)
        assertEquals(TransactionType.PAYBILL, result.type)
        assertEquals(TransactionDirection.OUTBOUND, result.direction)

        // Verify it was stored in the repository
        val stored = repository.getAllTransactions().first()
        assertEquals(1, stored.size)
        assertEquals("UIUNA8IXS2", stored[0].code)
    }

    @Test
    fun testDeduplicatesAlreadySavedTransaction() = runBlocking {
        val mpesaSms = "UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on 14/9/26 at 8:30 PM."

        // First arrival
        val firstResult = receiver.processMessage(
            sender = "MPESA",
            body = mpesaSms,
            timestamp = 1000L,
            repository = repository
        )
        assertNotNull(firstResult)

        // Duplicate broadcast arrival
        val duplicateResult = receiver.processMessage(
            sender = "MPESA",
            body = mpesaSms,
            timestamp = 1000L,
            repository = repository
        )
        assertNull("Duplicate broadcast must not insert duplicate row", duplicateResult)

        val stored = repository.getAllTransactions().first()
        assertEquals(1, stored.size)
    }

    @Test
    fun testRejectsMpesaPromotionalSms() = runBlocking {
        val promo = "Dear Customer, buy Tunukiwa 100MB daily bundle for only 20bob."
        val result = receiver.processMessage(
            sender = "MPESA",
            body = promo,
            timestamp = System.currentTimeMillis(),
            repository = repository
        )

        assertNull("Promotional texts from MPESA sender must be ignored", result)
        val stored = repository.getAllTransactions().first()
        assertTrue(stored.isEmpty())
    }

    @Test
    fun testProcessIntentWithEmptyExtrasReturnsEmpty() = runBlocking {
        val emptyIntent = Intent("android.provider.Telephony.SMS_RECEIVED")
        val results = receiver.processIntent(emptyIntent, repository)
        assertTrue(results.isEmpty())
    }
}
