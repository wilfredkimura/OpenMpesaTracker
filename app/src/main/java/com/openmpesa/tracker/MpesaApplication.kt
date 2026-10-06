package com.openmpesa.tracker

import android.app.Application

/**
 * Main Application class for OpenMpesaTracker.
 *
 * This class serves as the top-level entry point when the Android operating system
 * starts the application. It lives for the entire lifecycle of the app and is used
 * to hold application-wide resources and initialize offline components.
 */
class MpesaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialization logic for offline database and local storage will be wired here
    }
}
