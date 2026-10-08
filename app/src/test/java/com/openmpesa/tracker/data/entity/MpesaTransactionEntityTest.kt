package com.openmpesa.tracker.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests verifying data integrity and property mapping of MpesaTransactionEntity and enums.
 */
class MpesaTransactionEntityTest {

    @Test
    fun testEntityInstantiationWithDefaults() {
        val entity = MpesaTransactionEntity(
            code = "UIUNA8IXS2",
            amount = 50.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.PAYBILL,
            party = "KPLC PREPAID",
            accountNumber = "92104387870",
            timestamp = 1729000000000L,
            rawMessage = "UIUNA8IXS2 Confirmed. Ksh50.00 sent to KPLC PREPAID for account 92104387870 on..."
        )

        assertEquals("UIUNA8IXS2", entity.code)
        assertEquals(50.00, entity.amount, 0.001)
        assertEquals(TransactionDirection.OUTBOUND, entity.direction)
        assertEquals(TransactionType.PAYBILL, entity.type)
        assertEquals("KPLC PREPAID", entity.party)
        assertEquals("92104387870", entity.accountNumber)
        assertNull(entity.phoneNumber)
        assertNull(entity.balance)
        assertNull(entity.transactionFee)
        assertEquals(com.openmpesa.tracker.data.model.CategoryPresets.PERSONAL, entity.category)
        assertEquals("", entity.notes)
    }

    @Test
    fun testInboundTransactionProperties() {
        val entity = MpesaTransactionEntity(
            code = "UHNK63KR46",
            amount = 500.00,
            direction = TransactionDirection.INBOUND,
            type = TransactionType.SEND_MONEY_INBOUND,
            party = "NAOMI NJERI NDUNGU",
            phoneNumber = "0720***167",
            balance = 12500.00,
            timestamp = 1729100000000L,
            rawMessage = "UHNK63KR46 Confirmed.You have received Ksh500.00 from NAOMI NJERI NDUNGU 0720***167 on..."
        )

        assertEquals(TransactionDirection.INBOUND, entity.direction)
        assertEquals(TransactionType.SEND_MONEY_INBOUND, entity.type)
        assertEquals("0720***167", entity.phoneNumber)
        assertEquals(12500.00, entity.balance ?: 0.0, 0.001)
    }

    @Test
    fun testEntityCopyMutation() {
        val original = MpesaTransactionEntity(
            code = "UJ6NA9AFW1",
            amount = 105.00,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.BUY_GOODS,
            party = "DICII SUPERMARKET",
            timestamp = 1729200000000L,
            rawMessage = "UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on..."
        )

        val updated = original.copy(category = "Groceries", notes = "Weekly shopping")
        assertEquals("Groceries", updated.category)
        assertEquals("Weekly shopping", updated.notes)
        assertEquals(original.code, updated.code)
    }
}
