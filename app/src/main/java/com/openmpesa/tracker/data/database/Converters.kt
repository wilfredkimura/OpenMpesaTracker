package com.openmpesa.tracker.data.database

import androidx.room.TypeConverter
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.data.entity.TransactionType

/**
 * Room Type Converters for custom enum types.
 *
 * SQLite databases only natively store primitive types (text, integer, real, blob).
 * These converter functions tell Room how to convert our Kotlin enum values into text strings
 * when saving to SQLite, and how to convert them back into typed enums when reading from the database.
 */
class Converters {

    /**
     * Converts a TransactionDirection enum to its String representation for database storage.
     */
    @TypeConverter
    fun fromDirection(direction: TransactionDirection?): String? {
        return direction?.name
    }

    /**
     * Converts a database text string back into a TransactionDirection enum.
     * Defaults to OUTBOUND if the string is unrecognized.
     */
    @TypeConverter
    fun toDirection(value: String?): TransactionDirection {
        if (value.isNullOrBlank()) return TransactionDirection.OUTBOUND
        return try {
            TransactionDirection.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TransactionDirection.OUTBOUND
        }
    }

    /**
     * Converts a TransactionType enum to its String representation for database storage.
     */
    @TypeConverter
    fun fromType(type: TransactionType?): String? {
        return type?.name
    }

    /**
     * Converts a database text string back into a TransactionType enum.
     * Defaults to UNKNOWN if the stored value is unrecognized.
     */
    @TypeConverter
    fun toType(value: String?): TransactionType {
        if (value.isNullOrBlank()) return TransactionType.UNKNOWN
        return try {
            TransactionType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TransactionType.UNKNOWN
        }
    }
}
