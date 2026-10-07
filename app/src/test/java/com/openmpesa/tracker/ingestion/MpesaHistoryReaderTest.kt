package com.openmpesa.tracker.ingestion

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.openmpesa.tracker.data.repository.DefaultTransactionRepository
import com.openmpesa.tracker.data.repository.FakeTransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying MpesaHistoryReader ContentProvider querying and batch insertion.
 */
@RunWith(RobolectricTestRunner::class)
class MpesaHistoryReaderTest {

    private lateinit var contentResolver: ContentResolver
    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: DefaultTransactionRepository
    private lateinit var reader: MpesaHistoryReader

    @Before
    fun setUp() {
        contentResolver = mock(ContentResolver::class.java)
        fakeDao = FakeTransactionDao()
        repository = DefaultTransactionRepository(fakeDao, Dispatchers.Unconfined)
        reader = MpesaHistoryReader(contentResolver, repository, Dispatchers.Unconfined)
    }

    @Test
    fun testReadHistoryProcessesValidMessagesAndFiltersNonFinancial() = runBlocking {
        val testMessages = listOf(
            // Valid Paybill
            "UIUNA8IXS2 Confirmed. Ksh50.00 sent to KPLC PREPAID for account 92104387870 on 12/9/26 at 8:15 PM.",
            // Valid Buy Goods
            "UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on 14/9/26 at 8:30 PM. New M-PESA balance is Ksh1,200.00.",
            // Promo SMS (Should be ignored by parser)
            "Dear Customer, get 50% extra airtime with Tunukiwa today."
        )

        val cursor = mock(Cursor::class.java)
        `when`(cursor.getColumnIndex("body")).thenReturn(0)
        `when`(cursor.getColumnIndex("date")).thenReturn(1)

        // Simulate 3 cursor steps then end
        `when`(cursor.moveToNext()).thenReturn(true, true, true, false)
        `when`(cursor.getString(0)).thenReturn(
            testMessages[0],
            testMessages[1],
            testMessages[2]
        )
        `when`(cursor.getLong(1)).thenReturn(
            1729000000000L,
            1729000010000L,
            1729000020000L
        )

        `when`(
            contentResolver.query(
                eq(Uri.parse("content://sms/inbox")),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(cursor)

        var progressCalled = false
        val result = reader.readHistory(
            batchSize = 2,
            onProgress = { scanned: Int, saved: Int ->
                progressCalled = true
            }
        )

        assertEquals(3, result.scannedCount)
        assertEquals(2, result.savedCount)
        assertEquals(1, result.skippedCount)
        assertTrue("onProgress callback must be invoked", progressCalled)

        // Verify transactions are actually in the repository
        val stored = repository.getAllTransactions().first()
        assertEquals(2, stored.size)
    }

    @Test
    fun testReadHistoryGracefullyHandlesNullCursor() = runBlocking {
        `when`(
            contentResolver.query(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(null)

        val result = reader.readHistory()
        assertEquals(0, result.scannedCount)
        assertEquals(0, result.savedCount)
        assertEquals(0, result.skippedCount)
    }
}
