package com.openmpesa.tracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.model.CategoryPresets
import com.openmpesa.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State manager and controller for the Transaction List, Search, and Filter screen.
 *
 * It provides instant offline search across merchant names, Safaricom receipt codes,
 * account numbers, and personal memos, while enabling filtering by cash flow direction
 * and budgeting category.
 *
 * @param repository The local Room database transaction repository.
 */
class TransactionListViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _directionFilter = MutableStateFlow(DirectionFilter.ALL)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedTransaction = MutableStateFlow<MpesaTransactionEntity?>(null)

    /**
     * Reactively emits the complete and filtered list of transactions along with active filters.
     */
    val uiState: StateFlow<TransactionListUiState> = combine(
        repository.getAllTransactions(),
        _searchQuery,
        _directionFilter,
        _selectedCategory,
        _selectedTransaction
    ) { allTransactions, query, directionFilter, selectedCategory, selectedTransaction ->
        val filtered = filterTransactions(
            transactions = allTransactions,
            query = query,
            directionFilter = directionFilter,
            selectedCategory = selectedCategory
        )

        // Combine default category presets with any custom ones in the ledger
        val customCategories = allTransactions.map { it.category }.distinct()
        val allCategories = (CategoryPresets.defaultCategories + customCategories).distinct()

        TransactionListUiState(
            searchQuery = query,
            directionFilter = directionFilter,
            selectedCategory = selectedCategory,
            availableCategories = allCategories,
            allTransactions = allTransactions,
            filteredTransactions = filtered,
            selectedTransaction = selectedTransaction,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionListUiState(isLoading = true)
    )

    /**
     * Updates the active text search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Updates the cash flow direction filter (All, Money In, Money Out).
     */
    fun setDirectionFilter(filter: DirectionFilter) {
        _directionFilter.value = filter
    }

    /**
     * Filters transactions by a specific spending category, or null to view all.
     */
    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    /**
     * Opens or closes the transaction details bottom sheet.
     * Passing null closes the sheet.
     */
    fun selectTransaction(transaction: MpesaTransactionEntity?) {
        _selectedTransaction.value = transaction
    }

    /**
     * Updates the category tag for a transaction and persists it to the database.
     *
     * @param code The 10-character transaction code.
     * @param category The newly assigned category (e.g. "Utilities & Bills").
     */
    fun updateCategory(code: String, category: String) {
        viewModelScope.launch {
            repository.updateCategory(code, category)
            // If the transaction is currently open in the bottom sheet, update the sheet preview
            _selectedTransaction.update { current ->
                if (current?.code == code) current.copy(category = category) else current
            }
        }
    }

    /**
     * Updates custom notes or memos for a transaction and persists them to the database.
     *
     * @param code The 10-character transaction code.
     * @param notes User-written notes.
     */
    fun updateNotes(code: String, notes: String) {
        viewModelScope.launch {
            repository.updateNotes(code, notes)
            // If the transaction is currently open in the bottom sheet, update the sheet preview
            _selectedTransaction.update { current ->
                if (current?.code == code) current.copy(notes = notes) else current
            }
        }
    }

    companion object {
        /**
         * Pure filtering algorithm for unit testing and deterministic results.
         *
         * Matches search queries against party names, 10-char transaction codes,
         * account/till numbers, counterparty phone numbers, and notes.
         */
        fun filterTransactions(
            transactions: List<MpesaTransactionEntity>,
            query: String,
            directionFilter: DirectionFilter,
            selectedCategory: String?
        ): List<MpesaTransactionEntity> {
            val trimmedQuery = query.trim()

            return transactions.filter { tx ->
                // Check direction match
                val matchesDirection = when (directionFilter) {
                    DirectionFilter.ALL -> true
                    DirectionFilter.INBOUND -> tx.direction == TransactionDirection.INBOUND
                    DirectionFilter.OUTBOUND -> tx.direction == TransactionDirection.OUTBOUND
                }
                if (!matchesDirection) return@filter false

                // Check category match
                val matchesCategory = if (selectedCategory == null) {
                    true
                } else {
                    tx.category.equals(selectedCategory, ignoreCase = true)
                }
                if (!matchesCategory) return@filter false

                // Check text query match across multiple fields
                if (trimmedQuery.isEmpty()) {
                    true
                } else {
                    tx.party.contains(trimmedQuery, ignoreCase = true) ||
                        tx.code.contains(trimmedQuery, ignoreCase = true) ||
                        (tx.accountNumber?.contains(trimmedQuery, ignoreCase = true) == true) ||
                        (tx.phoneNumber?.contains(trimmedQuery, ignoreCase = true) == true) ||
                        tx.notes.contains(trimmedQuery, ignoreCase = true)
                }
            }
        }
    }

    /**
     * Factory for creating [TransactionListViewModel] with its repository dependency.
     */
    class Factory(
        private val repository: TransactionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TransactionListViewModel::class.java)) {
                return TransactionListViewModel(repository = repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
