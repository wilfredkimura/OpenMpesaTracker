package com.openmpesa.tracker.di

import android.content.Context

/**
 * Dependency container interface.
 *
 * Provides a clean, centralized way to supply shared objects (like repositories and databases)
 * throughout the app without requiring heavy reflection-based injection frameworks.
 */
interface AppContainer {
    val applicationContext: Context
    val isOfflineOnly: Boolean
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
}
