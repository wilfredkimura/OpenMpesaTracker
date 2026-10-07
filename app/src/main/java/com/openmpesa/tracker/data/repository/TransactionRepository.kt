package com.openmpesa.tracker.data.repository

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.model.CategorySpending
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface providing a single clean point of access for all transaction data.
 *
 * It decouples the user interface (ViewModels and Composable screens) from the underlying
 * Room database implementation. Screens observe reactive Kotlin [Flow] streams from this
 * repository, allowing the UI to update automatically whenever new transactions arrive.
 */
interface TransactionRepository {

    /**
     * Observes all recorded transactions sorted newest first.
     */
    fun getAllTransactions(): Flow<List<MpesaTransactionEntity>>

    /**
     * Observes transactions that occurred between [startEpoch] and [endEpoch].
     */
    fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): Flow<List<MpesaTransactionEntity>>

    /**
     * Observes transactions filtered by flow direction ([TransactionDirection.INBOUND] or [TransactionDirection.OUTBOUND]).
     */
    fun getTransactionsByDirection(direction: TransactionDirection): Flow<List<MpesaTransactionEntity>>

    /**
     * Observes the sum of all money received within the given time window, defaulting to 0.0 if empty.
     */
    fun getTotalInbound(startEpoch: Long, endEpoch: Long): Flow<Double>

    /**
     * Observes the sum of all money spent within the given time window, defaulting to 0.0 if empty.
     */
    fun getTotalOutbound(startEpoch: Long, endEpoch: Long): Flow<Double>

    /**
     * Observes outbound expenses grouped by category for charts and budget tracking.
     */
    fun getCategorySpending(startEpoch: Long, endEpoch: Long): Flow<List<CategorySpending>>

    /**
     * Inserts a single transaction into local storage.
     * Returns true if successfully inserted, or false if it was a duplicate and ignored.
     */
    suspend fun insertTransaction(transaction: MpesaTransactionEntity): Boolean

    /**
     * Inserts a batch of transactions (e.g. during inbox history sync).
     * Returns the count of newly inserted rows (excluding ignored duplicates).
     */
    suspend fun insertTransactions(transactions: List<MpesaTransactionEntity>): Int

    /**
     * Looks up an individual transaction by its unique 10-character code.
     */
    suspend fun getTransactionByCode(code: String): MpesaTransactionEntity?

    /**
     * Updates the user-defined category tag for a transaction.
     */
    suspend fun updateCategory(code: String, category: String)

    /**
     * Updates personal user notes or memos for a transaction.
     */
    suspend fun updateNotes(code: String, notes: String)
}
