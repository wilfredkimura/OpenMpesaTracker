package com.openmpesa.tracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity

/**
 * The main Room Database class for OpenMpesaTracker.
 *
 * Provides the local SQLite database structure. Contains the mpesa_transactions table
 * and applies [Converters] to handle custom enums safely.
 */
@Database(
    entities = [MpesaTransactionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Exposes the Data Access Object for transaction queries.
     */
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of the Room database.
         * Creates the database file "mpesa_tracker.db" on disk if it does not yet exist.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mpesa_tracker.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
