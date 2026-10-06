package com.openmpesa.tracker.engine

import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Comprehensive unit test suite for MpesaEngineParser.
 * Validates deterministic parsing of the 5 verified 2026 M-Pesa structural blueprints,
 * amount normalization, auxiliary fee/balance extraction, and rejection of non-transaction messages.
 */
class MpesaEngineParserTest {

    // ==========================================
    // BLUEPRINT 1: PAYBILL TESTS
    // ==========================================

    @Test
    fun testParsePaybillStandardKplcPrepaid() {
        val sms = "UIUNA8IXS2 Confirmed. Ksh50.00 sent to KPLC PREPAID for account 92104387870 on 12/9/26 at 8:15 PM."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIUNA8IXS2", result!!.code)
        assertEquals(50.00, result.amount, 0.001)
        assertEquals(TransactionType.PAYBILL, result.type)
        assertEquals(TransactionDirection.OUTBOUND, result.direction)
        assertEquals("KPLC PREPAID", result.party)
        assertEquals("92104387870", result.accountNumber)
        assertNull(result.phoneNumber)
    }

    @Test
    fun testParsePaybillWithCommasAndFeesAndBalance() {
        val sms = "UIUNA8IXS3 Confirmed. Ksh12,450.00 sent to NAIROBI WATER for account NW-44829 on 12/8/26 at 2:15 PM. New M-PESA balance is Ksh4,550.00. Transaction cost, Ksh23.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIUNA8IXS3", result!!.code)
        assertEquals(12450.00, result.amount, 0.001)
        assertEquals(TransactionType.PAYBILL, result.type)
        assertEquals(TransactionDirection.OUTBOUND, result.direction)
        assertEquals("NAIROBI WATER", result.party)
        assertEquals("NW-44829", result.accountNumber)
        assertEquals(4550.00, result.balance ?: 0.0, 0.001)
        assertEquals(23.00, result.transactionFee ?: 0.0, 0.001)
    }

    @Test
    fun testParsePaybillZukuFiber() {
        val sms = "UIUNA8IXS4 Confirmed. Ksh3,299.00 sent to ZUKU FIBER for account 1049281 on 10/9/26 at 11:00 AM."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIUNA8IXS4", result!!.code)
        assertEquals(3299.00, result.amount, 0.001)
        assertEquals("ZUKU FIBER", result.party)
        assertEquals("1049281", result.accountNumber)
    }

    // ==========================================
    // BLUEPRINT 2: BUY GOODS / TILL TESTS
    // ==========================================

    @Test
    fun testParseBuyGoodsStandardDiciiSupermarket() {
        val sms = "UJ6NA9AFW1 Confirmed. Ksh105.00 paid to DICII SUPERMARKET. on 14/9/26 at 8:30 PM. New M-PESA balance is Ksh1,200.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UJ6NA9AFW1", result!!.code)
        assertEquals(105.00, result.amount, 0.001)
        assertEquals(TransactionType.BUY_GOODS, result.type)
        assertEquals(TransactionDirection.OUTBOUND, result.direction)
        assertEquals("DICII SUPERMARKET", result.party)
        assertEquals(1200.00, result.balance ?: 0.0, 0.001)
        assertNull(result.accountNumber)
        assertNull(result.phoneNumber)
    }

    @Test
    fun testParseBuyGoodsNaivasWithCommaAmount() {
        val sms = "UJ6NA9AFW2 Confirmed. Ksh4,820.50 paid to NAIVAS MOI AVENUE. on 15/9/26 at 1:45 PM. New M-PESA balance is Ksh8,100.00. Transaction cost, Ksh0.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UJ6NA9AFW2", result!!.code)
        assertEquals(4820.50, result.amount, 0.001)
        assertEquals(TransactionType.BUY_GOODS, result.type)
        assertEquals("NAIVAS MOI AVENUE", result.party)
        assertEquals(8100.00, result.balance ?: 0.0, 0.001)
        assertEquals(0.00, result.transactionFee ?: 0.0, 0.001)
    }

    @Test
    fun testParseBuyGoodsJavaHouseCafe() {
        val sms = "UJ6NA9AFW3 Confirmed. Ksh650.00 paid to JAVA HOUSE GALLERIA. on 16/9/26 at 9:15 AM."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UJ6NA9AFW3", result!!.code)
        assertEquals(650.00, result.amount, 0.001)
        assertEquals("JAVA HOUSE GALLERIA", result.party)
    }

    // ==========================================
    // BLUEPRINT 3: SEND MONEY (OUTBOUND P2P) TESTS
    // ==========================================

    @Test
    fun testParseSendMoneyOutboundSamanthaOgosi() {
        val sms = "UIINA74UMI Confirmed. Ksh110.00 sent to SAMANTHA OGOSI 0748099854 on 18/9/26 at 4:20 PM. New M-PESA balance is Ksh2,300.00. Transaction cost, Ksh0.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIINA74UMI", result!!.code)
        assertEquals(110.00, result.amount, 0.001)
        assertEquals(TransactionType.SEND_MONEY_OUTBOUND, result.type)
        assertEquals(TransactionDirection.OUTBOUND, result.direction)
        assertEquals("SAMANTHA OGOSI", result.party)
        assertEquals("0748099854", result.phoneNumber)
        assertEquals(2300.00, result.balance ?: 0.0, 0.001)
        assertEquals(0.0, result.transactionFee ?: 0.0, 0.001)
    }

    @Test
    fun testParseSendMoneyOutboundWithCountryCode() {
        val sms = "UIINA74UM2 Confirmed. Ksh2,500.00 sent to KEVIN MWANGI +254712345678 on 19/9/26 at 10:00 AM. New M-PESA balance is Ksh5,000.00. Transaction cost, Ksh15.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIINA74UM2", result!!.code)
        assertEquals(2500.00, result.amount, 0.001)
        assertEquals("KEVIN MWANGI", result.party)
        assertEquals("+254712345678", result.phoneNumber)
        assertEquals(15.00, result.transactionFee ?: 0.0, 0.001)
    }

    @Test
    fun testParseSendMoneyOutboundThreeWordName() {
        val sms = "UIINA74UM3 Confirmed. Ksh1,000.00 sent to MARY WANJIRU NJOROGE 0722123456 on 20/9/26 at 7:30 PM."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIINA74UM3", result!!.code)
        assertEquals(1000.00, result.amount, 0.001)
        assertEquals("MARY WANJIRU NJOROGE", result.party)
        assertEquals("0722123456", result.phoneNumber)
    }

    // ==========================================
    // BLUEPRINT 4: POCHI LA BIASHARA (OUTBOUND) TESTS
    // ==========================================

    @Test
    fun testParsePochiLaBiasharaErickOpel() {
        // Crucial test: Phone number field is omitted by Safaricom
        val sms = "UIJNA77UEK Confirmed. Ksh100.00 sent to ERICK OPEL on 19/9/26 at 8:00 AM. New M-PESA balance is Ksh900.00. Transaction cost, Ksh0.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIJNA77UEK", result!!.code)
        assertEquals(100.00, result.amount, 0.001)
        assertEquals(TransactionType.POCHI_LA_BIASHARA, result.type)
        assertEquals(TransactionDirection.OUTBOUND, result.direction)
        assertEquals("ERICK OPEL", result.party)
        assertNull(result.phoneNumber)
        assertNull(result.accountNumber)
        assertEquals(900.00, result.balance ?: 0.0, 0.001)
    }

    @Test
    fun testParsePochiLaBiasharaMamaMbogaWithCommas() {
        val sms = "UIJNA77UE2 Confirmed. Ksh1,250.00 sent to MAMA MBOGA MUTHAIGA on 21/9/26 at 6:45 PM. New M-PESA balance is Ksh3,450.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIJNA77UE2", result!!.code)
        assertEquals(1250.00, result.amount, 0.001)
        assertEquals(TransactionType.POCHI_LA_BIASHARA, result.type)
        assertEquals("MAMA MBOGA MUTHAIGA", result.party)
        assertNull(result.phoneNumber)
    }

    @Test
    fun testParsePochiLaBiasharaSingleName() {
        val sms = "UIJNA77UE3 Confirmed. Ksh500.00 sent to MWANGI on 22/9/26 at 3:10 PM."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UIJNA77UE3", result!!.code)
        assertEquals(500.00, result.amount, 0.001)
        assertEquals(TransactionType.POCHI_LA_BIASHARA, result.type)
        assertEquals("MWANGI", result.party)
    }

    // ==========================================
    // BLUEPRINT 5: SEND MONEY (INBOUND P2P) TESTS
    // ==========================================

    @Test
    fun testParseSendMoneyInboundNaomiNjeri() {
        val sms = "UHNK63KR46 Confirmed.You have received Ksh500.00 from NAOMI NJERI NDUNGU 0720***167 on 23/9/26 at 12:45 PM. New M-PESA balance is Ksh15,500.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UHNK63KR46", result!!.code)
        assertEquals(500.00, result.amount, 0.001)
        assertEquals(TransactionType.SEND_MONEY_INBOUND, result.type)
        assertEquals(TransactionDirection.INBOUND, result.direction)
        assertEquals("NAOMI NJERI NDUNGU", result.party)
        assertEquals("0720***167", result.phoneNumber)
        assertEquals(15500.00, result.balance ?: 0.0, 0.001)
    }

    @Test
    fun testParseSendMoneyInboundWithSpaceAfterConfirmed() {
        val sms = "UHNK63KR47 Confirmed. You have received Ksh25,000.00 from PETER OCHIENG 0711***999 on 24/9/26 at 9:00 AM. New M-PESA balance is Ksh40,500.00."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UHNK63KR47", result!!.code)
        assertEquals(25000.00, result.amount, 0.001)
        assertEquals(TransactionType.SEND_MONEY_INBOUND, result.type)
        assertEquals(TransactionDirection.INBOUND, result.direction)
        assertEquals("PETER OCHIENG", result.party)
        assertEquals("0711***999", result.phoneNumber)
        assertEquals(40500.00, result.balance ?: 0.0, 0.001)
    }

    @Test
    fun testParseSendMoneyInboundFromBank() {
        val sms = "UHNK63KR48 Confirmed.You have received Ksh1,500.00 from EQUITY BANK 0700***001 on 25/9/26 at 5:20 PM."
        val result = MpesaEngineParser.parse(sms)

        assertNotNull(result)
        assertEquals("UHNK63KR48", result!!.code)
        assertEquals(1500.00, result.amount, 0.001)
        assertEquals(TransactionType.SEND_MONEY_INBOUND, result.type)
        assertEquals("EQUITY BANK", result.party)
    }

    // ==========================================
    // EDGE CASES & NON-MPESA TESTS
    // ==========================================

    @Test
    fun testRejectsBankPromoOrOtp() {
        val otpSms = "Your verification code is 492019. Do not share this with anyone."
        assertNull(MpesaEngineParser.parse(otpSms))

        val bankPromo = "Dear customer, get 10% cash back when you pay with your Platinum Card today!"
        assertNull(MpesaEngineParser.parse(bankPromo))
    }

    @Test
    fun testRejectsIncompleteOrCorruptedMpesaText() {
        val shortSms = "UIUNA8IXS2 Confirmed"
        assertNull(MpesaEngineParser.parse(shortSms))

        val gibberishSms = "UIUNA8IXS2 Confirmed random unparseable content without financial fields"
        assertNull(MpesaEngineParser.parse(gibberishSms))
    }

    @Test
    fun testRejectsSafaricomPromotionalMessage() {
        val promo = "Dear Customer, dial *544# to enjoy 20GB for 1000bob valid for 30days."
        assertNull(MpesaEngineParser.parse(promo))
    }
}
