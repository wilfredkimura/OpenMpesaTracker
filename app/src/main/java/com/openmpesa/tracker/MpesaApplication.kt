package com.openmpesa.tracker

import android.app.Application
import com.openmpesa.tracker.di.AppContainer
import com.openmpesa.tracker.di.DefaultAppContainer

/**
 * Main Application class for OpenMpesaTracker.
 *
 * This class serves as the top-level entry point when the Android operating system
 * starts the application. It creates and holds the [container] instance for dependency
 * injection, making app-wide repositories accessible throughout the lifecycle.
 */
class MpesaApplication : Application() {

    /**
     * Centralized dependency container holding app-wide singletons.
     */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize our manual dependency container with application context
        container = DefaultAppContainer(this)
    }
}
