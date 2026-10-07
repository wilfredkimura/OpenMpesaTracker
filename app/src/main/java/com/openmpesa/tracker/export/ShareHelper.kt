package com.openmpesa.tracker.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Strategy interface for resolving a secure content URI for a local report file.
 */
interface FileUriProvider {
    fun getUriForFile(context: Context, file: File, authority: String): Uri
}

/**
 * Default production implementation that uses AndroidX [FileProvider].
 */
class DefaultFileUriProvider : FileUriProvider {
    override fun getUriForFile(context: Context, file: File, authority: String): Uri {
        return FileProvider.getUriForFile(context, authority, file)
    }
}

/**
 * Helper utility for safely sharing exported CSV and PDF reports with other Android apps.
 *
 * Security & Scoped Storage Compliance:
 * Modern versions of Android (Android 7.0+ through Android 15+) forbid sharing raw file system paths
 * (`file://`). Instead, this helper maps files in `context.cacheDir/mpesa_reports/` into a temporary,
 * secure `content://` URI using [FileProvider], granting one-time read permission to the recipient app.
 */
object ShareHelper {

    /**
     * Determines the appropriate MIME type for an exported report file based on its extension.
     */
    fun getMimeType(file: File): String {
        return when {
            file.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
            file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            else -> "*/*"
        }
    }

    /**
     * Obtains a secure content URI for the generated report via [FileUriProvider].
     */
    fun getFileUri(
        context: Context,
        file: File,
        authority: String = "${context.packageName}.fileprovider",
        fileUriProvider: FileUriProvider = DefaultFileUriProvider()
    ): Uri {
        return fileUriProvider.getUriForFile(context, file, authority)
    }

    /**
     * Builds an Android Chooser Intent ready to launch the system share sheet.
     *
     * @param context Application context.
     * @param file The exported CSV or PDF file to share.
     * @param chooserTitle Title shown at the top of the Android share sheet dialog.
     * @param authority The FileProvider authority matching the manifest.
     * @param fileUriProvider Strategy for resolving the file URI.
     * @return An [Intent] configured with ACTION_CHOOSER.
     */
    fun buildShareIntent(
        context: Context,
        file: File,
        chooserTitle: String = "Export M-Pesa Report Via...",
        authority: String = "${context.packageName}.fileprovider",
        fileUriProvider: FileUriProvider = DefaultFileUriProvider()
    ): Intent {
        val fileUri = getFileUri(context, file, authority, fileUriProvider)
        val mimeType = getMimeType(file)

        // 1. Create the base send intent with the file URI
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            // Grant temporary read permission so the chosen app (Sheets, Mail, Drive) can read the file
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 2. Wrap in system chooser dialog so the user can select their preferred sharing app
        return Intent.createChooser(sendIntent, chooserTitle).apply {
            // Also forward read permission on the chooser intent wrapper
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Directly launches the Android system share sheet from an Activity context.
     */
    fun shareReport(
        context: Context,
        file: File,
        chooserTitle: String = "Export M-Pesa Report Via...",
        authority: String = "${context.packageName}.fileprovider",
        fileUriProvider: FileUriProvider = DefaultFileUriProvider()
    ) {
        val chooserIntent = buildShareIntent(context, file, chooserTitle, authority, fileUriProvider)
        context.startActivity(chooserIntent)
    }
}
