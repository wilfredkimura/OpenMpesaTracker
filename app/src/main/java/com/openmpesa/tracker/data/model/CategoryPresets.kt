package com.openmpesa.tracker.data.model

import com.openmpesa.tracker.data.entity.TransactionType

/**
 * Standard category definitions and rules for automatic transaction tagging.
 *
 * By default, this app groups transactions into two simple categories:
 * 1. "Utilities/Bills/Fees" - for all bill payments (Paybill), merchant tills (Buy Goods),
 *    and small business payments (Pochi La Biashara).
 * 2. "Personal" - for all personal money transfers between people (both money sent and money received).
 *
 * Users can always re-assign transactions to their own custom categories if they choose.
 */
object CategoryPresets {
    /** Category name applied to bill payments, merchant tills, and small business payments */
    const val UTILITIES_BILLS_FEES = "Utilities/Bills/Fees"

    /** Category name applied to personal peer-to-peer transfers (both inbound and outbound) */
    const val PERSONAL = "Personal"

    /**
     * The only default built-in categories offered in the application.
     */
    val defaultCategories = listOf(
        UTILITIES_BILLS_FEES,
        PERSONAL
    )

    /**
     * Returns the automatic default category based on how the transaction was made.
     *
     * @param type The transaction classification (such as Paybill, Till, or Send Money).
     * @return The default category name to assign.
     */
    fun defaultCategoryForType(type: TransactionType): String {
        return when (type) {
            TransactionType.PAYBILL,
            TransactionType.BUY_GOODS,
            TransactionType.POCHI_LA_BIASHARA -> UTILITIES_BILLS_FEES
            TransactionType.SEND_MONEY_OUTBOUND,
            TransactionType.SEND_MONEY_INBOUND -> PERSONAL
            TransactionType.UNKNOWN -> PERSONAL
        }
    }
}
