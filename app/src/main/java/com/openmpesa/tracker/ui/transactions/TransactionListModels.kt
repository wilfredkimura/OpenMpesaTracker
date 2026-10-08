package com.openmpesa.tracker.ui.transactions

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.model.CategoryPresets

/**
 * Filter options for cash flow direction in the transaction list screen.
 */
enum class DirectionFilter(val displayName: String) {
    /** Show all transactions regardless of cash flow direction */
    ALL("All"),

    /** Show only inbound transactions (money received) */
    INBOUND("Money In"),

    /** Show only outbound transactions (money sent/spent) */
    OUTBOUND("Money Out")
}

/**
 * UI State representing the transaction list, search query, active filters,
 * and currently selected transaction for detail viewing.
 */
data class TransactionListUiState(
    /** Current text entered into the search bar */
    val searchQuery: String = "",

    /** Selected cash flow direction filter */
    val directionFilter: DirectionFilter = DirectionFilter.ALL,

    /** Selected category filter, or null if all categories should be displayed */
    val selectedCategory: String? = null,

    /** Available categories detected across all transactions plus presets */
    val availableCategories: List<String> = CategoryPresets.defaultCategories,

    /** The complete raw list of all recorded transactions */
    val allTransactions: List<MpesaTransactionEntity> = emptyList(),

    /** The filtered transactions currently displayed on screen matching search and filters */
    val filteredTransactions: List<MpesaTransactionEntity> = emptyList(),

    /** Transaction currently open in the details bottom sheet, or null if closed */
    val selectedTransaction: MpesaTransactionEntity? = null,

    /** True while transactions are first loading from the local Room database */
    val isLoading: Boolean = true
)
