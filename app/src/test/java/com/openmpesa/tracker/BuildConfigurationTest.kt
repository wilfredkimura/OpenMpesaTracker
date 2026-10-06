package com.openmpesa.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test verifying the project's build, manifest, and security configurations.
 * Ensures privacy compliance (strictly no INTERNET permission) and proper component declarations.
 */
class BuildConfigurationTest {

    @Test
    fun testManifestDeclaresRequiredSmsPermissions() {
        // Path to the AndroidManifest.xml file
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestContent = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            // Fallback for relative path when running from root project directory
            File("app/src/main/AndroidManifest.xml").readText()
        }

        // Must declare SMS read permission for historical message indexing
        assertTrue(
            "Manifest must declare READ_SMS permission",
            manifestContent.contains("android.permission.READ_SMS")
        )

        // Must declare SMS receive permission for real-time transaction detection
        assertTrue(
            "Manifest must declare RECEIVE_SMS permission",
            manifestContent.contains("android.permission.RECEIVE_SMS")
        )
    }

    @Test
    fun testManifestStrictlyOmitsInternetPermissionForPrivacy() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestContent = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            File("app/src/main/AndroidManifest.xml").readText()
        }

        // Privacy guarantee: The app must NEVER request INTERNET permission
        assertFalse(
            "Manifest must NOT request INTERNET permission (100% offline guarantee)",
            manifestContent.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun testManifestDeclaresMpesaReceiverAndFileProvider() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestContent = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            File("app/src/main/AndroidManifest.xml").readText()
        }

        // Verify MpesaReceiver is declared
        assertTrue(
            "Manifest must register MpesaReceiver component",
            manifestContent.contains("android:name=\".receiver.MpesaReceiver\"")
        )

        // Verify FileProvider is declared for sharing reports
        assertTrue(
            "Manifest must register androidx.core.content.FileProvider",
            manifestContent.contains("androidx.core.content.FileProvider")
        )

        // Verify FileProvider authority matches expected package pattern
        assertTrue(
            "FileProvider authority must use application ID placeholder",
            manifestContent.contains("\${applicationId}.fileprovider")
        )
    }

    @Test
    fun testFilePathsXmlConfiguresMpesaReportsCachePath() {
        val filePathsFile = File("src/main/res/xml/file_paths.xml")
        val filePathsContent = if (filePathsFile.exists()) {
            filePathsFile.readText()
        } else {
            File("app/src/main/res/xml/file_paths.xml").readText()
        }

        // Verify that the cache-path for reports is configured
        assertTrue(
            "file_paths.xml must configure cache-path for mpesa_reports/",
            filePathsContent.contains("path=\"mpesa_reports/\"")
        )
    }
}
