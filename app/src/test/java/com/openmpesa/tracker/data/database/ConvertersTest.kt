package com.openmpesa.tracker.data.database

import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Room TypeConverters for TransactionDirection and TransactionType.
 */
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun testDirectionSerializationAndDeserialization() {
        // Standard conversions
        assertEquals("INBOUND", converters.fromDirection(TransactionDirection.INBOUND))
        assertEquals("OUTBOUND", converters.fromDirection(TransactionDirection.OUTBOUND))
        assertNull(converters.fromDirection(null))

        assertEquals(TransactionDirection.INBOUND, converters.toDirection("INBOUND"))
        assertEquals(TransactionDirection.OUTBOUND, converters.toDirection("OUTBOUND"))

        // Fallbacks for invalid/blank inputs
        assertEquals(TransactionDirection.OUTBOUND, converters.toDirection(null))
        assertEquals(TransactionDirection.OUTBOUND, converters.toDirection(""))
        assertEquals(TransactionDirection.OUTBOUND, converters.toDirection("INVALID_DIRECTION"))
    }

    @Test
    fun testTypeSerializationAndDeserialization() {
        // Test all valid enum values
        for (type in TransactionType.entries) {
            val serialized = converters.fromType(type)
            assertEquals(type.name, serialized)
            val deserialized = converters.toType(serialized)
            assertEquals(type, deserialized)
        }

        // Test null serialization
        assertNull(converters.fromType(null))

        // Fallbacks for invalid/blank inputs
        assertEquals(TransactionType.UNKNOWN, converters.toType(null))
        assertEquals(TransactionType.UNKNOWN, converters.toType(""))
        assertEquals(TransactionType.UNKNOWN, converters.toType("RANDOM_TEXT_TYPE"))
    }
}
