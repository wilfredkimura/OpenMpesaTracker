package com.openmpesa.tracker.ui.export

import android.content.Intent
import java.io.File
import java.util.Calendar

/**
 * Supported report formats for data export.
 */
enum class ExportFormat(
    val title: String,
    val description: String,
    val extension: String
) {
    /** Formatted multi-page PDF document suitable for printing and archiving */
    PDF(
        title = "PDF Statement",
        description = "Printable statement with summary breakdown and category charts.",
        extension = ".pdf"
    ),

    /** Comma-separated values spreadsheet compatible with Excel and Google Sheets */
    CSV(
        title = "CSV Spreadsheet",
        description = "Raw tabular data formatted for Microsoft Excel, Numbers, and Google Sheets.",
        extension = ".csv"
    )
}

/**
 * Date range options for scoping exported transaction reports.
 */
enum class ExportDateRange(val displayName: String) {
    /** Export transactions from the start of the current month to today */
    THIS_MONTH("This Month"),

    /** Export transactions from the last 30 calendar days */
    LAST_30_DAYS("Last 30 Days"),

    /** Export every transaction recorded in the database */
    ALL_TIME("All Time");

    /**
     * Calculates the start epoch millisecond for the selected window.
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
 * State machine representation for the Export dialog/modal.
 */
data class ExportUiState(
    /** Currently chosen export file format */
    val selectedFormat: ExportFormat = ExportFormat.PDF,

    /** Currently chosen date range */
    val selectedDateRange: ExportDateRange = ExportDateRange.THIS_MONTH,

    /** True while report generation coroutine is running */
    val isExporting: Boolean = false,

    /** File handle of the newly created report file */
    val exportedFile: File? = null,

    /** Android share intent ready to trigger the system share sheet */
    val shareIntent: Intent? = null,

    /** Error message to show if export could not be completed */
    val errorMessage: String? = null
)
