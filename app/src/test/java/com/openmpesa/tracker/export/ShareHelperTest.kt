package com.openmpesa.tracker.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests verifying ShareHelper MIME resolution, FileProvider URI building, and Intent configurations.
 */
@RunWith(RobolectricTestRunner::class)
class ShareHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testMimeTypeResolution() {
        val csvFile = File("sample_report.csv")
        val pdfFile = File("sample_statement.pdf")
        val txtFile = File("sample_document.txt")

        assertEquals("text/csv", ShareHelper.getMimeType(csvFile))
        assertEquals("application/pdf", ShareHelper.getMimeType(pdfFile))
        assertEquals("*/*", ShareHelper.getMimeType(txtFile))
    }

    @Test
    fun testBuildShareIntentConfiguresFlagsAndStream() {
        val testFile = File("test_report.csv")
        val fakeUriProvider = object : FileUriProvider {
            override fun getUriForFile(context: Context, file: File, authority: String): Uri {
                return Uri.parse("content://$authority/mpesa_reports/${file.name}")
            }
        }

        val chooserIntent = ShareHelper.buildShareIntent(
            context = context,
            file = testFile,
            chooserTitle = "Share Via Test",
            authority = "com.openmpesa.tracker.fileprovider",
            fileUriProvider = fakeUriProvider
        )
        assertNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)

        // Verify the inner target intent
        @Suppress("DEPRECATION")
        val targetIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Chooser intent must wrap target Intent", targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent!!.action)
        assertEquals("text/csv", targetIntent.type)

        // Verify stream URI
        @Suppress("DEPRECATION")
        val streamUri = targetIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull("Target intent must contain stream URI", streamUri)
        assertEquals("content://com.openmpesa.tracker.fileprovider/mpesa_reports/test_report.csv", streamUri.toString())

        // Verify permission flag is granted
        val hasReadFlag = (targetIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        assertTrue("Intent must grant read URI permission flag", hasReadFlag)
    }
}
