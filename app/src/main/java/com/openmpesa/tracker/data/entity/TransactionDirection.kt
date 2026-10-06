package com.openmpesa.tracker.data.entity

/**
 * Indicates whether money entered or left the user's M-Pesa wallet.
 */
enum class TransactionDirection {
    /** Money received into the user's M-Pesa wallet (e.g. Received P2P transfer) */
    INBOUND,

    /** Money sent out of the user's M-Pesa wallet (e.g. Paybill, Buy Goods, P2P Send, Pochi) */
    OUTBOUND
}
