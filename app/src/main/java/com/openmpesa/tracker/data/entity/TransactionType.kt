package com.openmpesa.tracker.data.entity

/**
 * The specific Safaricom M-Pesa transaction category.
 *
 * M-Pesa messages follow distinct structural templates depending on how money was moved.
 * This enum classifies each transaction type deterministically based on its pattern signature.
 */
enum class TransactionType {
    /**
     * Outbound payment to a business PayBill number (e.g. utility bills like KPLC, Nairobi Water).
     * Characterized by: "sent to [Business] for account [Account Number]".
     */
    PAYBILL,

    /**
     * Outbound payment to a merchant's Buy Goods / Lipa Na M-Pesa Till number.
     * Characterized by: "paid to [Merchant Name]. on".
     */
    BUY_GOODS,

    /**
     * Outbound Peer-to-Peer transfer to another person where their phone number is shown.
     * Characterized by: "sent to [Name] [Plain Phone Digits]".
     */
    SEND_MONEY_OUTBOUND,

    /**
     * Outbound payment to a small business owner's Pochi La Biashara account.
     * In this transaction type, Safaricom completely omits the recipient's phone number.
     * Characterized by: "sent to [Name] on" with no phone digits or account number.
     */
    POCHI_LA_BIASHARA,

    /**
     * Inbound Peer-to-Peer transfer received from another person or entity.
     * Safaricom partially masks the sender's phone number for privacy (e.g. 0720***167).
     * Characterized by: "You have received Ksh[Amount] from [Name] [Masked Phone]".
     */
    SEND_MONEY_INBOUND,

    /**
     * Any transaction message that did not match the five verified structural signatures.
     */
    UNKNOWN
}
