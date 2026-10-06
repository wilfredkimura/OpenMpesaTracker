package com.openmpesa.tracker.data.model

import androidx.room.ColumnInfo

/**
 * Data model representing aggregated spending grouped by category.
 * Used to render spending distribution charts and budget progress bars.
 */
data class CategorySpending(
    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double,

    @ColumnInfo(name = "transaction_count")
    val transactionCount: Int
)
