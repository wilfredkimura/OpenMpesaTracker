package com.openmpesa.tracker.ingestion

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.repository.TransactionRepository
import com.openmpesa.tracker.engine.MpesaEngineParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result data holder providing statistics from a historical SMS inbox scan.
 */
data class SyncResult(
    /** Total number of Safaricom M-Pesa SMS messages found in the inbox */
    val scannedCount: Int,
    /** Number of newly added transactions stored into Room */
    val savedCount: Int,
    /** Number of messages skipped (either non-financial notes or already existing in database) */
    val skippedCount: Int
)

/**
 * Reader class that queries Android's built-in Telephony ContentProvider to scan
 * past M-Pesa text messages already stored in the user's messaging inbox.
 *
 * Privacy & Performance Safeguards:
 * 1. Strictly filters by `address = 'MPESA'`, ensuring the app never reads any personal or private texts.
 * 2. Runs strictly on a background coroutine ([Dispatchers.IO]) so the user interface never stutters.
 * 3. Batches insertions into the database to minimize SQLite transaction overhead.
 */
class MpesaHistoryReader(
    private val contentResolver: ContentResolver,
    private val repository: TransactionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    constructor(context: Context, repository: TransactionRepository) : this(
        contentResolver = context.contentResolver,
        repository = repository,
        ioDispatcher = Dispatchers.IO
    )

    /**
     * Reads all historical M-Pesa SMS messages from the phone's inbox and imports them.
     *
     * @param batchSize Number of transactions to accumulate before executing a batch insert into Room.
     * @param onProgress Optional callback invoked periodically with (scannedCount, savedCount) for UI progress bars.
     * @return [SyncResult] with total counts.
     */
    suspend fun readHistory(
        batchSize: Int = 50,
        onProgress: (scanned: Int, saved: Int) -> Unit = { _, _ -> }
    ): SyncResult = withContext(ioDispatcher) {
        val smsUri: Uri = Uri.parse("content://sms/inbox")

        // Specify only the columns we actually need
        val projection = arrayOf("body", "date", "address")

        // Filter strictly to Safaricom's official MPESA sender name to protect user privacy
        val selection = "address = ? OR address = ?"
        val selectionArgs = arrayOf("MPESA", "mpesa")
        val sortOrder = "date DESC"

        var totalScanned = 0
        var totalSaved = 0
        var totalSkipped = 0

        val currentBatch = mutableListOf<MpesaTransactionEntity>()

        val cursor = try {
            contentResolver.query(
                smsUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
        } catch (e: Exception) {
            // In case of permission errors or telephony provider issues, gracefully return empty results
            null
        }

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            val dateIndex = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val body = if (bodyIndex != -1) it.getString(bodyIndex).orEmpty() else ""
                val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()

                totalScanned++

                // Parse the SMS text using the deterministic engine
                val parsed = MpesaEngineParser.parse(body, date)
                if (parsed != null) {
                    currentBatch.add(parsed)

                    // When batch reaches configured size, commit to Room
                    if (currentBatch.size >= batchSize) {
                        val inserted = repository.insertTransactions(currentBatch)
                        totalSaved += inserted
                        totalSkipped += (currentBatch.size - inserted)
                        currentBatch.clear()
                        onProgress(totalScanned, totalSaved)
                    }
                } else {
                    totalSkipped++
                }
            }

            // Flush any remaining items in the batch
            if (currentBatch.isNotEmpty()) {
                val inserted = repository.insertTransactions(currentBatch)
                totalSaved += inserted
                totalSkipped += (currentBatch.size - inserted)
                currentBatch.clear()
                onProgress(totalScanned, totalSaved)
            }
        }

        SyncResult(
            scannedCount = totalScanned,
            savedCount = totalSaved,
            skippedCount = totalSkipped
        )
    }
}
