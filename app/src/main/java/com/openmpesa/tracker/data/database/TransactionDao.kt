package com.openmpesa.tracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.model.CategorySpending
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for interacting with the mpesa_transactions table.
 *
 * Provides typed methods for inserting, updating, and querying M-Pesa transactions.
 * Queries return reactive Kotlin [Flow]s so the user interface updates automatically
 * whenever new SMS transactions are inserted.
 */
@Dao
interface TransactionDao {

    /**
     * Inserts a list of transactions into the database.
     * Uses OnConflictStrategy.IGNORE: if a transaction with the same 10-character code
     * already exists in the table, it is safely ignored to preserve existing user categories
     * and avoid double-counting.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<MpesaTransactionEntity>): List<Long>

    /**
     * Inserts a single transaction, ignoring conflicts if already present.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: MpesaTransactionEntity): Long

    /**
     * Updates an existing transaction (e.g. when the user edits category or personal notes).
     */
    @Update
    suspend fun updateTransaction(transaction: MpesaTransactionEntity)

    /**
     * Retrieves all recorded transactions ordered from newest to oldest.
     */
    @Query("SELECT * FROM mpesa_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<MpesaTransactionEntity>>

    /**
     * Retrieves transactions that occurred within a specific time window.
     */
    @Query("SELECT * FROM mpesa_transactions WHERE timestamp >= :startEpoch AND timestamp <= :endEpoch ORDER BY timestamp DESC")
    fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): Flow<List<MpesaTransactionEntity>>

    /**
     * Filters transactions by cash flow direction (INBOUND or OUTBOUND).
     */
    @Query("SELECT * FROM mpesa_transactions WHERE direction = :direction ORDER BY timestamp DESC")
    fun getTransactionsByDirection(direction: TransactionDirection): Flow<List<MpesaTransactionEntity>>

    /**
     * Calculates the total amount received within a given date range.
     */
    @Query("SELECT SUM(amount) FROM mpesa_transactions WHERE direction = 'INBOUND' AND timestamp >= :startEpoch AND timestamp <= :endEpoch")
    fun getTotalInboundBetween(startEpoch: Long, endEpoch: Long): Flow<Double?>

    /**
     * Calculates the total amount spent within a given date range.
     */
    @Query("SELECT SUM(amount) FROM mpesa_transactions WHERE direction = 'OUTBOUND' AND timestamp >= :startEpoch AND timestamp <= :endEpoch")
    fun getTotalOutboundBetween(startEpoch: Long, endEpoch: Long): Flow<Double?>

    /**
     * Aggregates outbound spending grouped by category for a given date range.
     */
    @Query("""
        SELECT category, SUM(amount) as total_amount, COUNT(*) as transaction_count 
        FROM mpesa_transactions 
        WHERE direction = 'OUTBOUND' AND timestamp >= :startEpoch AND timestamp <= :endEpoch 
        GROUP BY category 
        ORDER BY total_amount DESC
    """)
    fun getCategorySpendingBetween(startEpoch: Long, endEpoch: Long): Flow<List<CategorySpending>>

    /**
     * Finds a single transaction by its unique 10-character code.
     */
    @Query("SELECT * FROM mpesa_transactions WHERE code = :code LIMIT 1")
    suspend fun getTransactionByCode(code: String): MpesaTransactionEntity?

    /**
     * Updates the user-assigned category for a specific transaction.
     */
    @Query("UPDATE mpesa_transactions SET category = :category WHERE code = :code")
    suspend fun updateCategory(code: String, category: String)

    /**
     * Updates the personal note memo for a specific transaction.
     */
    @Query("UPDATE mpesa_transactions SET notes = :notes WHERE code = :code")
    suspend fun updateNotes(code: String, notes: String)
}
