package com.openmpesa.tracker.data.repository

import com.openmpesa.tracker.data.database.TransactionDao
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.model.CategorySpending
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [TransactionRepository] that delegates data persistence
 * to Room's [TransactionDao] while running operations safely on [Dispatchers.IO].
 */
class DefaultTransactionRepository(
    private val transactionDao: TransactionDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TransactionRepository {

    override fun getAllTransactions(): Flow<List<MpesaTransactionEntity>> {
        return transactionDao.getAllTransactions()
    }

    override fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): Flow<List<MpesaTransactionEntity>> {
        return transactionDao.getTransactionsBetween(startEpoch, endEpoch)
    }

    override fun getTransactionsByDirection(direction: TransactionDirection): Flow<List<MpesaTransactionEntity>> {
        return transactionDao.getTransactionsByDirection(direction)
    }

    override fun getTotalInbound(startEpoch: Long, endEpoch: Long): Flow<Double> {
        return transactionDao.getTotalInboundBetween(startEpoch, endEpoch).map { it ?: 0.0 }
    }

    override fun getTotalOutbound(startEpoch: Long, endEpoch: Long): Flow<Double> {
        return transactionDao.getTotalOutboundBetween(startEpoch, endEpoch).map { it ?: 0.0 }
    }

    override fun getCategorySpending(startEpoch: Long, endEpoch: Long): Flow<List<CategorySpending>> {
        return transactionDao.getCategorySpendingBetween(startEpoch, endEpoch)
    }

    override suspend fun insertTransaction(transaction: MpesaTransactionEntity): Boolean {
        return withContext(ioDispatcher) {
            val result = transactionDao.insertTransaction(transaction)
            result != -1L
        }
    }

    override suspend fun insertTransactions(transactions: List<MpesaTransactionEntity>): Int {
        return withContext(ioDispatcher) {
            val results = transactionDao.insertTransactions(transactions)
            // Count how many IDs are not -1L (indicating successfully inserted, not ignored duplicate)
            results.count { it != -1L }
        }
    }

    override suspend fun getTransactionByCode(code: String): MpesaTransactionEntity? {
        return withContext(ioDispatcher) {
            transactionDao.getTransactionByCode(code)
        }
    }

    override suspend fun updateCategory(code: String, category: String) {
        withContext(ioDispatcher) {
            transactionDao.updateCategory(code, category)
        }
    }

    override suspend fun updateNotes(code: String, notes: String) {
        withContext(ioDispatcher) {
            transactionDao.updateNotes(code, notes)
        }
    }
}
