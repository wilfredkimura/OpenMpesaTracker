package com.openmpesa.tracker.ui.dashboard

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Filter options for viewing financial summaries across different time windows.
 */
enum class DashboardPeriod(val displayName: String) {
    /** Summarizes transactions from the first day of the current calendar month to now */
    THIS_MONTH("This Month"),

    /** Summarizes transactions from the last 30 calendar days */
    LAST_30_DAYS("Last 30 Days"),

    /** Summarizes every transaction recorded in the ledger */
    ALL_TIME("All Time");

    /**
     * Calculates the start time in epoch milliseconds for this period relative to [nowMillis].
     */
    fun startEpochMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return when (this) {
            THIS_MONTH -> {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            }
            LAST_30_DAYS -> {
                val thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L
                (nowMillis - thirtyDaysInMillis).coerceAtLeast(0L)
            }
            ALL_TIME -> 0L
        }
    }
}

/**
 * Represents the spending share of a specific category for visual charts and progress bars.
 */
data class CategorySpendShare(
    /** The category label (e.g. "Utilities", "Shopping", "General") */
    val category: String,

    /** Total amount spent in Kenyan Shillings */
    val totalAmount: Double,

    /** Total number of transactions in this category */
    val transactionCount: Int,

    /** Share of total spending represented as a percentage between 0.0 and 100.0 */
    val percentage: Float
)

/**
 * Holds all dashboard data and visual metrics to be displayed on screen.
 */
data class DashboardUiState(
    /** True while initial data is being queried from the database */
    val isLoading: Boolean = true,

    /** Currently selected time period filter */
    val selectedPeriod: DashboardPeriod = DashboardPeriod.THIS_MONTH,

    /** Total money received (inbound) during the selected period in Ksh */
    val totalIncome: Double = 0.0,

    /** Total money spent (outbound) during the selected period in Ksh */
    val totalExpenses: Double = 0.0,

    /** Total M-Pesa transaction fees paid during the selected period in Ksh */
    val totalFees: Double = 0.0,

    /** Net cash flow (Total Income minus Total Expenses) in Ksh */
    val netCashFlow: Double = 0.0,

    /** The most recently known wallet balance extracted from M-Pesa SMS receipts */
    val latestBalance: Double? = null,

    /** Spending grouped by category with calculated percentage shares */
    val categoryBreakdown: List<CategorySpendShare> = emptyList(),

    /** Recent transactions to preview on the dashboard */
    val recentTransactions: List<MpesaTransactionEntity> = emptyList(),

    /** True if a background SMS inbox refresh is actively running */
    val isSyncing: Boolean = false,

    /** Feedback or notification message after syncing SMS */
    val syncMessage: String? = null
)

/**
 * Plain-language formatting helpers for currency and dates.
 */
object DashboardFormatter {

    private val currencyFormatter = DecimalFormat("#,##0.00")

    /**
     * Formats a numeric amount into a friendly Kenyan Shilling string (e.g. "Ksh 1,250.00").
     */
    fun formatKsh(amount: Double): String {
        return "Ksh ${currencyFormatter.format(amount)}"
    }

    /**
     * Formats an epoch millisecond timestamp into a human-readable date and time (e.g. "12 Oct 2026, 14:30").
     */
    fun formatDate(epochMillis: Long): String {
        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return dateFormat.format(Date(epochMillis))
    }
}
