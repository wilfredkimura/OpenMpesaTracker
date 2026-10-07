package com.openmpesa.tracker.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Summary calculations displayed at the top of an exported PDF financial statement.
 */
data class PdfStatementSummary(
    val totalInbound: Double,
    val totalOutbound: Double,
    val netBalance: Double,
    val totalTransactions: Int
)

/**
 * Interface abstracting the physical writing of the PDF byte stream.
 *
 * Defaults to [NativePdfCanvasWriter] which uses Android's native [PdfDocument] API on-device.
 * Can be substituted in unit tests to verify layout calculations and multi-page boundaries
 * on JVM platforms where native Android PDF graphics binaries are not loaded.
 */
interface PdfCanvasWriter {
    fun writeStatement(
        outputStream: OutputStream,
        transactions: List<MpesaTransactionEntity>,
        title: String,
        summary: PdfStatementSummary,
        dateFormat: SimpleDateFormat
    ): Int
}

/**
 * Default production implementation utilizing Android's native [PdfDocument] canvas API.
 */
class NativePdfCanvasWriter(
    private val pageWidth: Int = 595,     // Standard A4 width in points
    private val pageHeight: Int = 842,    // Standard A4 height in points
    private val margin: Float = 40f,
    private val contentBottom: Float = 780f
) : PdfCanvasWriter {

    override fun writeStatement(
        outputStream: OutputStream,
        transactions: List<MpesaTransactionEntity>,
        title: String,
        summary: PdfStatementSummary,
        dateFormat: SimpleDateFormat
    ): Int {
        val pdfDocument = PdfDocument()

        try {
            val paint = Paint().apply { isAntiAlias = true }

            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = 50f

            // 1. Draw Document Title
            paint.color = Color.BLACK
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(title, margin, yPosition, paint)

            // 2. Draw Subheading with Timestamp
            yPosition += 25f
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.DKGRAY
            canvas.drawText("Generated locally on: ${dateFormat.format(Date())}", margin, yPosition, paint)

            // 3. Draw Summary Totals Banner
            yPosition += 25f
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.BLACK
            val summaryText = String.format(
                Locale.US,
                "Total Inbound: KES %.2f   |   Total Outbound: KES %.2f   |   Net: KES %.2f",
                summary.totalInbound,
                summary.totalOutbound,
                summary.netBalance
            )
            canvas.drawText(summaryText, margin, yPosition, paint)

            // 4. Draw Header Divider Line
            yPosition += 15f
            paint.strokeWidth = 1f
            paint.color = Color.LTGRAY
            canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, paint)

            fun drawTableHeader() {
                paint.color = Color.BLACK
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                yPosition += 18f
                canvas.drawText("Date", margin, yPosition, paint)
                canvas.drawText("Code", margin + 75f, yPosition, paint)
                canvas.drawText("Type", margin + 140f, yPosition, paint)
                canvas.drawText("Recipient / Sender", margin + 205f, yPosition, paint)
                canvas.drawText("Category", margin + 355f, yPosition, paint)
                canvas.drawText("Amount (KES)", margin + 440f, yPosition, paint)

                yPosition += 6f
                paint.color = Color.GRAY
                canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, paint)
            }

            drawTableHeader()

            // 5. Draw Transaction Rows with automatic pagination
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            for (tx in transactions) {
                // If content reaches page bottom, split onto a new page
                if (yPosition > contentBottom) {
                    paint.textSize = 9f
                    paint.color = Color.GRAY
                    canvas.drawText("Page $currentPageNumber", pageWidth / 2f - 15f, pageHeight - 25f, paint)

                    pdfDocument.finishPage(page)

                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = 40f

                    drawTableHeader()
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }

                yPosition += 18f
                paint.textSize = 8.5f
                paint.color = Color.DKGRAY

                val dateStr = dateFormat.format(Date(tx.timestamp))
                val partyTruncated = if (tx.party.length > 25) tx.party.take(23) + ".." else tx.party
                val categoryTruncated = if (tx.category.length > 15) tx.category.take(13) + ".." else tx.category
                val amountStr = String.format(Locale.US, "%.2f", tx.amount)

                canvas.drawText(dateStr, margin, yPosition, paint)
                canvas.drawText(tx.code, margin + 75f, yPosition, paint)
                canvas.drawText(tx.type.name.take(10), margin + 140f, yPosition, paint)
                canvas.drawText(partyTruncated, margin + 205f, yPosition, paint)
                canvas.drawText(categoryTruncated, margin + 355f, yPosition, paint)

                paint.color = if (tx.direction == TransactionDirection.INBOUND) Color.rgb(0, 130, 50) else Color.BLACK
                canvas.drawText(amountStr, margin + 440f, yPosition, paint)
            }

            // Draw Final Page Footer
            paint.textSize = 9f
            paint.color = Color.GRAY
            canvas.drawText("Page $currentPageNumber", pageWidth / 2f - 15f, pageHeight - 25f, paint)

            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)

            return currentPageNumber
        } finally {
            pdfDocument.close()
        }
    }
}

/**
 * Exporter class that formats transactions into a multi-page PDF document.
 *
 * Uses Android's native [PdfDocument] canvas API via [canvasWriter] to render a printable statement
 * completely offline without third-party external dependencies.
 */
class PdfReportExporter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val canvasWriter: PdfCanvasWriter = NativePdfCanvasWriter()
) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * Calculates statement metrics for header presentation.
     */
    fun calculateSummary(transactions: List<MpesaTransactionEntity>): PdfStatementSummary {
        val totalInbound = transactions.filter { it.direction == TransactionDirection.INBOUND }.sumOf { it.amount }
        val totalOutbound = transactions.filter { it.direction == TransactionDirection.OUTBOUND }.sumOf { it.amount }
        val netDifference = totalInbound - totalOutbound
        return PdfStatementSummary(
            totalInbound = totalInbound,
            totalOutbound = totalOutbound,
            netBalance = netDifference,
            totalTransactions = transactions.size
        )
    }

    /**
     * Generates a multi-page PDF financial statement.
     *
     * @param context Android context to access the local cache directory.
     * @param transactions List of transactions to render in the statement.
     * @param title Title banner text (e.g. "M-Pesa Expense Summary Report").
     * @return Generated [File], or null if writing fails.
     */
    suspend fun exportToPdf(
        context: Context,
        transactions: List<MpesaTransactionEntity>,
        title: String = "M-Pesa Expense Summary Report"
    ): File? = withContext(ioDispatcher) {
        try {
            val reportDir = File(context.cacheDir, "mpesa_reports")
            if (!reportDir.exists()) {
                reportDir.mkdirs()
            }
            val outputFile = File(reportDir, "Mpesa_Statement_${System.currentTimeMillis()}.pdf")
            val summary = calculateSummary(transactions)

            FileOutputStream(outputFile).use { outputStream ->
                canvasWriter.writeStatement(
                    outputStream = outputStream,
                    transactions = transactions,
                    title = title,
                    summary = summary,
                    dateFormat = dateFormat
                )
            }

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
