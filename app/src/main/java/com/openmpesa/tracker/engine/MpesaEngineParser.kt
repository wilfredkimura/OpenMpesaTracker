package com.openmpesa.tracker.engine

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import java.util.regex.Pattern

/**
 * Battery-efficient parser engine for Safaricom M-Pesa SMS messages.
 *
 * This parser uses pre-compiled static [Pattern] objects so that regular expressions
 * are never re-compiled when iterating through hundreds of messages during historical
 * sync or when receiving real-time broadcasts.
 *
 * It uses a fast O(1) guard check to instantly reject non-M-Pesa messages and executes
 * deterministic signature checks according to the verified 2026 M-Pesa structural blueprints.
 */
object MpesaEngineParser {

    /**
     * Fast guard pattern: Verifies that the message starts with a 10-character alphanumeric
     * code followed immediately by "Confirmed". If not present, we abort immediately before
     * running heavier matching logic.
     */
    private val GUARD_PATTERN: Pattern = Pattern.compile(
        """^[A-Z0-9]{10}\s+Confirmed""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Blueprint 1: PAYBILL (Outbound)
     * Format: [10-char code] Confirmed. Ksh[Amount] sent to [Business] for account [Account] on...
     * Example: UIUNA8IXS2 Confirmed. Ksh50.00 sent to KPLC PREPAID for account 92104387870 on...
     */
    private val PAYBILL_PATTERN: Pattern = Pattern.compile(
        """^([A-Z0-9]{10})\s+Confirmed\.\s*Ksh\s*([\d,]+\.?\d*)\s+sent\s+to\s+(.+?)\s+for\s+account\s+(.+?)\s+on\s+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Blueprint 2: BUY GOODS / TILL (Outbound)
     * Format: [10-char code] Confirmed. Ksh[Amount] paid to [Merchant Name]. on...
     * Example: UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on...
     */
    private val BUY_GOODS_PATTERN: Pattern = Pattern.compile(
        """^([A-Z0-9]{10})\s+Confirmed\.\s*Ksh\s*([\d,]+\.?\d*)\s+paid\s+to\s+(.+?)\.?\s+on\s+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Blueprint 3: SEND MONEY (Outbound P2P with Plain Phone Digits)
     * Format: [10-char code] Confirmed. Ksh[Amount] sent to [Name] [Plain Phone Digits] on...
     * Example: UIINA74UMI Confirmed. Ksh110.00 sent to SAMANTHA OGOSI 0748099854 on...
     */
    private val SEND_MONEY_OUTBOUND_PATTERN: Pattern = Pattern.compile(
        """^([A-Z0-9]{10})\s+Confirmed\.\s*Ksh\s*([\d,]+\.?\d*)\s+sent\s+to\s+([A-Za-z\s'.]+?)\s+(\+?\d{10,13})\s+on\s+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Blueprint 4: POCHI LA BIASHARA (Outbound)
     * Format: [10-char code] Confirmed. Ksh[Amount] sent to [Name] on...
     * Key rule: In Pochi, the recipient phone number is omitted by Safaricom.
     * Example: UIJNA77UEK Confirmed. Ksh100.00 sent to ERICK OPEL on 19/9/26...
     */
    private val POCHI_OUTBOUND_PATTERN: Pattern = Pattern.compile(
        """^([A-Z0-9]{10})\s+Confirmed\.\s*Ksh\s*([\d,]+\.?\d*)\s+sent\s+to\s+([A-Za-z\s'.]+?)\s+on\s+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Blueprint 5: SEND MONEY (Inbound P2P with Masked Phone)
     * Format: [10-char code] Confirmed.You have received Ksh[Amount] from [Name] [Masked Phone] on...
     * Example: UHNK63KR46 Confirmed.You have received Ksh500.00 from NAOMI NJERI NDUNGU 0720***167 on...
     */
    private val SEND_MONEY_INBOUND_PATTERN: Pattern = Pattern.compile(
        """^([A-Z0-9]{10})\s+Confirmed\.?\s*You\s+have\s+received\s+Ksh\s*([\d,]+\.?\d*)\s+from\s+([A-Za-z\s'.]+?)\s+(\+?[\d*]{10,13})\s+on\s+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Auxiliary pattern: Extracts the remaining wallet balance after the transaction.
     * Example: "New M-PESA balance is Ksh14,200.50."
     */
    private val BALANCE_PATTERN: Pattern = Pattern.compile(
        """New\s+M-PESA\s+balance\s+is\s+Ksh\s*([\d,]+\.?\d*)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Auxiliary pattern: Extracts the transaction cost / fee charged by Safaricom.
     * Example: "Transaction cost, Ksh15.00."
     */
    private val COST_PATTERN: Pattern = Pattern.compile(
        """Transaction\s+cost,?\s*Ksh\s*([\d,]+\.?\d*)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses an SMS text message into a structured [MpesaTransactionEntity].
     *
     * @param smsBody The raw text of the incoming or stored SMS message.
     * @param smsTimestamp The timestamp in epoch milliseconds from the Android SMS provider.
     * @return A parsed [MpesaTransactionEntity], or null if the message is not a valid M-Pesa transaction.
     */
    fun parse(smsBody: String, smsTimestamp: Long = System.currentTimeMillis()): MpesaTransactionEntity? {
        val trimmed = smsBody.trim()

        // Step 1: Fast Guard Check
        // If the message is shorter than 25 characters or does not begin with code + "Confirmed",
        // exit immediately to save CPU cycles and battery life.
        if (trimmed.length < 25 || !GUARD_PATTERN.matcher(trimmed).find()) {
            return null
        }

        // Step 2: Route through verified 2026 blueprints in deterministic order
        // Order is critical to avoid matching ambiguity:
        // 1. Inbound P2P ("You have received")
        // 2. Paybill ("for account")
        // 3. Outbound P2P (must end in phone digits before "on")
        // 4. Pochi La Biashara (name without phone digits directly before "on")
        // 5. Buy Goods / Till ("paid to")

        // 1. Inbound P2P Check
        val inboundMatcher = SEND_MONEY_INBOUND_PATTERN.matcher(trimmed)
        if (inboundMatcher.find()) {
            return buildEntity(
                code = inboundMatcher.group(1).orEmpty(),
                amountStr = inboundMatcher.group(2).orEmpty(),
                type = TransactionType.SEND_MONEY_INBOUND,
                direction = TransactionDirection.INBOUND,
                party = inboundMatcher.group(3)?.trim().orEmpty(),
                phoneNumber = inboundMatcher.group(4)?.trim(),
                accountNumber = null,
                smsBody = trimmed,
                smsTimestamp = smsTimestamp
            )
        }

        // 2. Paybill Check
        val paybillMatcher = PAYBILL_PATTERN.matcher(trimmed)
        if (paybillMatcher.find()) {
            return buildEntity(
                code = paybillMatcher.group(1).orEmpty(),
                amountStr = paybillMatcher.group(2).orEmpty(),
                type = TransactionType.PAYBILL,
                direction = TransactionDirection.OUTBOUND,
                party = paybillMatcher.group(3)?.trim().orEmpty(),
                phoneNumber = null,
                accountNumber = paybillMatcher.group(4)?.trim(),
                smsBody = trimmed,
                smsTimestamp = smsTimestamp
            )
        }

        // 3. Outbound P2P Check (requires phone digits before 'on')
        val p2pMatcher = SEND_MONEY_OUTBOUND_PATTERN.matcher(trimmed)
        if (p2pMatcher.find()) {
            return buildEntity(
                code = p2pMatcher.group(1).orEmpty(),
                amountStr = p2pMatcher.group(2).orEmpty(),
                type = TransactionType.SEND_MONEY_OUTBOUND,
                direction = TransactionDirection.OUTBOUND,
                party = p2pMatcher.group(3)?.trim().orEmpty(),
                phoneNumber = p2pMatcher.group(4)?.trim(),
                accountNumber = null,
                smsBody = trimmed,
                smsTimestamp = smsTimestamp
            )
        }

        // 4. Pochi La Biashara Check (no phone digits before 'on')
        val pochiMatcher = POCHI_OUTBOUND_PATTERN.matcher(trimmed)
        if (pochiMatcher.find()) {
            return buildEntity(
                code = pochiMatcher.group(1).orEmpty(),
                amountStr = pochiMatcher.group(2).orEmpty(),
                type = TransactionType.POCHI_LA_BIASHARA,
                direction = TransactionDirection.OUTBOUND,
                party = pochiMatcher.group(3)?.trim().orEmpty(),
                phoneNumber = null,
                accountNumber = null,
                smsBody = trimmed,
                smsTimestamp = smsTimestamp
            )
        }

        // 5. Buy Goods / Till Check
        val buyGoodsMatcher = BUY_GOODS_PATTERN.matcher(trimmed)
        if (buyGoodsMatcher.find()) {
            return buildEntity(
                code = buyGoodsMatcher.group(1).orEmpty(),
                amountStr = buyGoodsMatcher.group(2).orEmpty(),
                type = TransactionType.BUY_GOODS,
                direction = TransactionDirection.OUTBOUND,
                party = buyGoodsMatcher.group(3)?.trim().orEmpty(),
                phoneNumber = null,
                accountNumber = null,
                smsBody = trimmed,
                smsTimestamp = smsTimestamp
            )
        }

        return null
    }

    /**
     * Assembles the parsed fields into an immutable [MpesaTransactionEntity] instance.
     */
    private fun buildEntity(
        code: String,
        amountStr: String,
        type: TransactionType,
        direction: TransactionDirection,
        party: String,
        phoneNumber: String?,
        accountNumber: String?,
        smsBody: String,
        smsTimestamp: Long
    ): MpesaTransactionEntity {
        val amount = cleanAmount(amountStr)
        val balance = extractBalance(smsBody)
        val fee = extractCost(smsBody)

        return MpesaTransactionEntity(
            code = code.uppercase(),
            amount = amount,
            direction = direction,
            type = type,
            party = party,
            phoneNumber = phoneNumber,
            accountNumber = accountNumber,
            balance = balance,
            transactionFee = fee,
            timestamp = smsTimestamp,
            rawMessage = smsBody
        )
    }

    /**
     * Safely parses monetary amounts without throwing NumberFormatException.
     * Removes commas (e.g. "1,500.00" -> 1500.00).
     */
    private fun cleanAmount(amountStr: String): Double {
        val clean = amountStr.replace(",", "").trim()
        return clean.toDoubleOrNull() ?: 0.0
    }

    /**
     * Extracts wallet balance from trailing SMS metadata if present.
     */
    private fun extractBalance(smsBody: String): Double? {
        val matcher = BALANCE_PATTERN.matcher(smsBody)
        return if (matcher.find()) cleanAmount(matcher.group(1).orEmpty()) else null
    }

    /**
     * Extracts transaction cost/fee from trailing SMS metadata if present.
     */
    private fun extractCost(smsBody: String): Double? {
        val matcher = COST_PATTERN.matcher(smsBody)
        return if (matcher.find()) cleanAmount(matcher.group(1).orEmpty()) else null
    }
}
