package com.openmpesa.tracker.export

import android.content.Context
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporter class that formats a list of transactions into a comma-separated values (CSV) spreadsheet.
 *
 * CSV files are lightweight, completely offline, and can be opened immediately
 * by users in Google Sheets, Microsoft Excel, or LibreOffice.
 */
class CsvReportExporter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Generates a CSV spreadsheet from the provided list of transactions.
     *
     * @param context Android context used to access the secure internal cache directory.
     * @param transactions The list of transactions to include in the exported report.
     * @return The created [File] in `context.cacheDir/mpesa_reports/`, or null if writing fails.
     */
    suspend fun exportToCsv(
        context: Context,
        transactions: List<MpesaTransactionEntity>
    ): File? = withContext(ioDispatcher) {
        try {
            // 1. Ensure the dedicated internal reports directory exists
            val reportDir = File(context.cacheDir, "mpesa_reports")
            if (!reportDir.exists()) {
                reportDir.mkdirs()
            }

            // 2. Generate a unique timestamped file name
            val csvFile = File(reportDir, "Mpesa_Report_${System.currentTimeMillis()}.csv")

            FileWriter(csvFile).use { writer ->
                // 3. Write Column Headers
                writer.append(
                    "Transaction Code,Timestamp,Date & Time,Direction,Type,Amount (KES),Party,Phone / Account,Category,Fee (KES),Balance (KES),Notes\n"
                )

                // 4. Populate Data Rows
                for (tx in transactions) {
                    val formattedDate = dateFormat.format(Date(tx.timestamp))
                    val phoneOrAccount = tx.phoneNumber ?: tx.accountNumber ?: ""
                    val feeStr = if (tx.transactionFee != null) String.format(Locale.US, "%.2f", tx.transactionFee) else ""
                    val balanceStr = if (tx.balance != null) String.format(Locale.US, "%.2f", tx.balance) else ""
                    val amountStr = String.format(Locale.US, "%.2f", tx.amount)

                    // Quote string fields that might contain commas or special characters
                    val row = listOf(
                        escapeCsv(tx.code),
                        tx.timestamp.toString(),
                        escapeCsv(formattedDate),
                        tx.direction.name,
                        tx.type.name,
                        amountStr,
                        escapeCsv(tx.party),
                        escapeCsv(phoneOrAccount),
                        escapeCsv(tx.category),
                        feeStr,
                        balanceStr,
                        escapeCsv(tx.notes)
                    ).joinToString(",")

                    writer.append(row).append("\n")
                }

                writer.flush()
            }

            csvFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escapes fields for CSV compliance by wrapping in quotes and doubling any internal quotes.
     */
    fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
