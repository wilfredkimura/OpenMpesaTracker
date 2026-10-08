package com.openmpesa.tracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local Room database entity representing an individual M-Pesa transaction.
 *
 * Each row corresponds to a single Safaricom SMS transaction notification.
 * The primary key is the unique 10-character alphanumeric transaction code found directly
 * inside the message text (for example: "UIUNA8IXS2"). Using this code guarantees idempotence:
 * duplicate messages arriving via real-time broadcasts or historical inbox reads will never
 * create duplicate spending entries.
 */
@Entity(
    tableName = "mpesa_transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["direction"]),
        Index(value = ["type"]),
        Index(value = ["party"]),
        Index(value = ["category"])
    ]
)
data class MpesaTransactionEntity(
    /**
     * Unique 10-character transaction code extracted from the Safaricom message body.
     * Examples: "UIUNA8IXS2", "UJ6NA9AFW1", "UIINA74UMI".
     */
    @PrimaryKey
    @ColumnInfo(name = "code")
    val code: String,

    /**
     * The monetary value of the transaction in Kenya Shillings (KES).
     */
    @ColumnInfo(name = "amount")
    val amount: Double,

    /**
     * Direction of the cash flow: INBOUND (money received) or OUTBOUND (money spent).
     */
    @ColumnInfo(name = "direction")
    val direction: TransactionDirection,

    /**
     * The specific transaction classification (Paybill, Till, Send Money, Pochi).
     */
    @ColumnInfo(name = "type")
    val type: TransactionType,

    /**
     * The name of the other entity involved: recipient, merchant, business, or sender.
     */
    @ColumnInfo(name = "party")
    val party: String,

    /**
     * Phone number of the counterparty if present:
     * - Outbound P2P: Plain unmasked digits (e.g. "0748099854").
     * - Inbound P2P: Masked digits for privacy (e.g. "0720***167").
     * - Paybill, Till, Pochi: null.
     */
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null,

    /**
     * Account number specified for Paybill transactions (e.g. "92104387870").
     */
    @ColumnInfo(name = "account_number")
    val accountNumber: String? = null,

    /**
     * Remaining M-Pesa account balance after transaction completed, if stated in the SMS.
     */
    @ColumnInfo(name = "balance")
    val balance: Double? = null,

    /**
     * Safaricom service fee charged for this transaction, if stated in the SMS.
     */
    @ColumnInfo(name = "transaction_fee")
    val transactionFee: Double? = null,

    /**
     * Timestamp of the transaction in epoch milliseconds.
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /**
     * The verbatim original SMS text body, kept for offline auditing and regex debugging.
     */
    @ColumnInfo(name = "raw_message")
    val rawMessage: String,

    /**
     * User-assigned category for budgeting (defaults to "Personal").
     */
    @ColumnInfo(name = "category")
    val category: String = com.openmpesa.tracker.data.model.CategoryPresets.PERSONAL,

    /**
     * Optional personal notes or memos attached by the user.
     */
    @ColumnInfo(name = "notes")
    val notes: String = ""
)
