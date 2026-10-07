package com.openmpesa.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.repository.TransactionRepository
import com.openmpesa.tracker.ingestion.MpesaHistoryReader
import com.openmpesa.tracker.ingestion.SyncResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel powering the main Financial Dashboard.
 *
 * It pulls raw transactions from the Room [TransactionRepository], calculates summary
 * financial metrics (income, expenses, fees, net flow), aggregates spending by category,
 * and maintains the selected time period filter (This Month, Last 30 Days, All Time).
 *
 * @param repository The transaction repository for accessing stored receipts.
 * @param historyReader Optional history reader to re-scan the SMS inbox on demand.
 * @param syncProvider Optional custom sync action for testing without Android components.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: TransactionRepository,
    private val historyReader: MpesaHistoryReader? = null,
    private val syncProvider: (suspend (onProgress: (scanned: Int, saved: Int) -> Unit) -> SyncResult)? = null
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(DashboardPeriod.THIS_MONTH)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    /**
     * Reactively queries transactions according to the selected time period.
     * Whenever a new transaction is recorded or the user switches time periods,
     * this stream automatically updates with fresh data.
     */
    private val transactionsInPeriod = _selectedPeriod.flatMapLatest { period ->
        val now = System.currentTimeMillis()
        val startEpoch = period.startEpochMillis(now)
        val endEpoch = Long.MAX_VALUE
        if (period == DashboardPeriod.ALL_TIME) {
            repository.getAllTransactions()
        } else {
            repository.getTransactionsBetween(startEpoch, endEpoch)
        }
    }

    /**
     * Combined reactive UI state exposed to the Dashboard composables.
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        _selectedPeriod,
        transactionsInPeriod,
        repository.getAllTransactions(), // used to get overall latest balance regardless of period filter
        _isSyncing,
        _syncMessage
    ) { period, periodTransactions, allTransactions, isSyncing, syncMessage ->
        calculateUiState(
            period = period,
            transactions = periodTransactions,
            allTransactions = allTransactions,
            isSyncing = isSyncing,
            syncMessage = syncMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    /**
     * Switches the active time period filter (e.g. from "This Month" to "Last 30 Days").
     */
    fun selectPeriod(period: DashboardPeriod) {
        _selectedPeriod.value = period
    }

    /**
     * Dismisses any active sync notification or banner.
     */
    fun dismissSyncMessage() {
        _syncMessage.value = null
    }

    /**
     * Runs an on-demand scan of the SMS inbox to import any newly received M-Pesa receipts.
     */
    fun syncSmsInbox() {
        if (_isSyncing.value) return

        _isSyncing.value = true
        _syncMessage.value = null

        viewModelScope.launch {
            try {
                val result: SyncResult = when {
                    syncProvider != null -> syncProvider.invoke { _, _ -> }
                    historyReader != null -> historyReader.readHistory()
                    else -> SyncResult(scannedCount = 0, savedCount = 0, skippedCount = 0)
                }

                _syncMessage.value = "Synced: ${result.savedCount} new transactions added."
            } catch (e: Exception) {
                _syncMessage.value = "Sync failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    companion object {
        /**
         * Pure helper function that calculates income, expenses, fees, and category percentages.
         * Separating this logic makes it straightforward to test with deterministic inputs.
         */
        fun calculateUiState(
            period: DashboardPeriod,
            transactions: List<MpesaTransactionEntity>,
            allTransactions: List<MpesaTransactionEntity>,
            isSyncing: Boolean,
            syncMessage: String?
        ): DashboardUiState {
            var totalIncome = 0.0
            var totalExpenses = 0.0
            var totalFees = 0.0

            val categorySpendingMap = mutableMapOf<String, Pair<Double, Int>>()

            for (tx in transactions) {
                when (tx.direction) {
                    TransactionDirection.INBOUND -> {
                        totalIncome += tx.amount
                    }
                    TransactionDirection.OUTBOUND -> {
                        totalExpenses += tx.amount
                        val current = categorySpendingMap.getOrDefault(tx.category, Pair(0.0, 0))
                        categorySpendingMap[tx.category] = Pair(
                            current.first + tx.amount,
                            current.second + 1
                        )
                    }
                }

                // Accumulate transaction fees if recorded on the receipt
                tx.transactionFee?.let { fee ->
                    totalFees += fee
                }
            }

            // Calculate percentage share for each category based on total expenses
            val categoryBreakdown = categorySpendingMap.map { (cat, data) ->
                val amount = data.first
                val count = data.second
                val percentage = if (totalExpenses > 0.0) {
                    ((amount / totalExpenses) * 100.0).toFloat()
                } else {
                    0.0f
                }
                CategorySpendShare(
                    category = cat,
                    totalAmount = amount,
                    transactionCount = count,
                    percentage = percentage
                )
            }.sortedByDescending { it.totalAmount }

            // Find the most recent transaction across all records that contains a verified wallet balance
            val latestBalance = allTransactions.firstOrNull { it.balance != null }?.balance

            // Take the 5 most recent transactions for the dashboard quick preview
            val recentPreview = transactions.take(5)

            return DashboardUiState(
                isLoading = false,
                selectedPeriod = period,
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                totalFees = totalFees,
                netCashFlow = totalIncome - totalExpenses,
                latestBalance = latestBalance,
                categoryBreakdown = categoryBreakdown,
                recentTransactions = recentPreview,
                isSyncing = isSyncing,
                syncMessage = syncMessage
            )
        }
    }

    /**
     * Factory for creating [DashboardViewModel] with its repository dependencies.
     */
    class Factory(
        private val repository: TransactionRepository,
        private val historyReader: MpesaHistoryReader? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(
                    repository = repository,
                    historyReader = historyReader
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
