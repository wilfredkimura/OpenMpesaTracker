package com.openmpesa.tracker.di

import android.content.Context

/**
 * Dependency container interface.
 *
 * Provides a clean, centralized way to supply shared objects (like repositories and databases)
 * throughout the app without requiring heavy reflection-based injection frameworks.
 */
import com.openmpesa.tracker.data.database.AppDatabase
import com.openmpesa.tracker.data.repository.CategoryRepository
import com.openmpesa.tracker.data.repository.DefaultCategoryRepository
import com.openmpesa.tracker.data.repository.DefaultTransactionRepository
import com.openmpesa.tracker.data.repository.TransactionRepository

/**
 * Dependency container interface.
 *
 * Provides a clean, centralized way to supply shared objects (like repositories and databases)
 * throughout the app without requiring heavy reflection-based injection frameworks.
 */
interface AppContainer {
    val applicationContext: Context
    val isOfflineOnly: Boolean
    val transactionRepository: TransactionRepository
    val categoryRepository: CategoryRepository
}

/**
 * Default implementation of AppContainer created during application startup.
 *
 * Holds singleton instances that live as long as the application process lives.
 * As features are completed in later phases, repository and database references
 * will be registered here.
 *
 * @param context The Android application context.
 */
class DefaultAppContainer(
    override val applicationContext: Context
) : AppContainer {

    /**
     * Strict privacy guarantee flag confirming network operations are disabled.
     */
    override val isOfflineOnly: Boolean = true

    /**
     * Shared singleton instance of the TransactionRepository backed by the Room database.
     */
    override val transactionRepository: TransactionRepository by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        DefaultTransactionRepository(database.transactionDao())
    }

    /**
     * Shared singleton instance of the CategoryRepository for managing custom spending categories.
     */
    override val categoryRepository: CategoryRepository by lazy {
        val prefs = applicationContext.getSharedPreferences("category_preferences", Context.MODE_PRIVATE)
        DefaultCategoryRepository(prefs)
    }
}
