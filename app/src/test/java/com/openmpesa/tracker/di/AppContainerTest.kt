package com.openmpesa.tracker.di

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * Unit tests verifying the functionality and integrity of the dependency container.
 */
class AppContainerTest {

    @Test
    fun testDefaultAppContainerInitializesCorrectly() {
        // Create a mocked Android Context using Mockito
        val mockContext = Mockito.mock(Context::class.java)
        val container: AppContainer = DefaultAppContainer(mockContext)

        // Verify the container stores the context and enforces offline security
        assertNotNull("Container applicationContext must not be null", container.applicationContext)
        assertTrue("Container must enforce isOfflineOnly = true", container.isOfflineOnly)
    }
}
