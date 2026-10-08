package com.openmpesa.tracker.data.model

import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import com.openmpesa.tracker.engine.MpesaEngineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying default category presets and automatic tagging rules.
 *
 * Rules:
 * - Paybills, Tills, and Pochi La Biashara must default to "Utilities/Bills/Fees".
 * - Send Money Outbound and Inbound transfers must default to "Personal".
 * - Only these two categories are the built-in presets.
 */
class CategoryPresetsTest {

    @Test
    fun defaultCategories_containsOnlyUtilitiesAndPersonal() {
        assertEquals(2, CategoryPresets.defaultCategories.size)
        assertTrue(CategoryPresets.defaultCategories.contains(CategoryPresets.UTILITIES_BILLS_FEES))
        assertTrue(CategoryPresets.defaultCategories.contains(CategoryPresets.PERSONAL))
        assertEquals("Utilities/Bills/Fees", CategoryPresets.UTILITIES_BILLS_FEES)
        assertEquals("Personal", CategoryPresets.PERSONAL)
    }

    @Test
    fun defaultCategoryForType_paybillMapsToUtilitiesBillsFees() {
        assertEquals(
            CategoryPresets.UTILITIES_BILLS_FEES,
            CategoryPresets.defaultCategoryForType(TransactionType.PAYBILL)
        )
    }

    @Test
    fun defaultCategoryForType_buyGoodsMapsToUtilitiesBillsFees() {
        assertEquals(
            CategoryPresets.UTILITIES_BILLS_FEES,
            CategoryPresets.defaultCategoryForType(TransactionType.BUY_GOODS)
        )
    }

    @Test
    fun defaultCategoryForType_pochiMapsToUtilitiesBillsFees() {
        assertEquals(
            CategoryPresets.UTILITIES_BILLS_FEES,
            CategoryPresets.defaultCategoryForType(TransactionType.POCHI_LA_BIASHARA)
        )
    }

    @Test
    fun defaultCategoryForType_sendMoneyOutboundMapsToPersonal() {
        assertEquals(
            CategoryPresets.PERSONAL,
            CategoryPresets.defaultCategoryForType(TransactionType.SEND_MONEY_OUTBOUND)
        )
    }

    @Test
    fun defaultCategoryForType_sendMoneyInboundMapsToPersonal() {
        assertEquals(
            CategoryPresets.PERSONAL,
            CategoryPresets.defaultCategoryForType(TransactionType.SEND_MONEY_INBOUND)
        )
    }

    @Test
    fun defaultCategoryForType_unknownMapsToPersonal() {
        assertEquals(
            CategoryPresets.PERSONAL,
            CategoryPresets.defaultCategoryForType(TransactionType.UNKNOWN)
        )
    }

    @Test
    fun entityDefault_hasPersonalCategory() {
        val entity = MpesaTransactionEntity(
            code = "TEST000001",
            amount = 100.0,
            direction = TransactionDirection.OUTBOUND,
            type = TransactionType.SEND_MONEY_OUTBOUND,
            party = "TEST PERSON",
            timestamp = 1000L,
            rawMessage = "TEST"
        )
        assertEquals(CategoryPresets.PERSONAL, entity.category)
    }

    @Test
    fun engineParser_assignsUtilitiesBillsFeesToPaybill() {
        val sms = "UIUNA8IXS2 Confirmed. Ksh50.00 sent to KPLC PREPAID for account 92104387870 on 12/9/26 at 8:15 PM."
        val parsed = MpesaEngineParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(CategoryPresets.UTILITIES_BILLS_FEES, parsed!!.category)
    }

    @Test
    fun engineParser_assignsUtilitiesBillsFeesToTill() {
        val sms = "UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on 13/9/26 at 1:45 PM."
        val parsed = MpesaEngineParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(CategoryPresets.UTILITIES_BILLS_FEES, parsed!!.category)
    }

    @Test
    fun engineParser_assignsUtilitiesBillsFeesToPochi() {
        val sms = "UIJNA77UEK Confirmed. Ksh100.00 sent to ERICK OPEL on 19/9/26 at 10:14 AM."
        val parsed = MpesaEngineParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(CategoryPresets.UTILITIES_BILLS_FEES, parsed!!.category)
    }

    @Test
    fun engineParser_assignsPersonalToSendMoneyOutbound() {
        val sms = "UIINA74UMI Confirmed. Ksh110.00 sent to SAMANTHA OGOSI 0748099854 on 19/9/26 at 8:36 AM."
        val parsed = MpesaEngineParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(CategoryPresets.PERSONAL, parsed!!.category)
    }

    @Test
    fun engineParser_assignsPersonalToSendMoneyInbound() {
        val sms = "UHNK63KR46 Confirmed.You have received Ksh500.00 from NAOMI NJERI NDUNGU 0720***167 on 15/9/26 at 9:02 AM."
        val parsed = MpesaEngineParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(CategoryPresets.PERSONAL, parsed!!.category)
    }
}
