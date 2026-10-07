package com.openmpesa.tracker.ui.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openmpesa.tracker.data.repository.TransactionRepository
import com.openmpesa.tracker.export.CsvReportExporter
import com.openmpesa.tracker.export.DefaultFileUriProvider
import com.openmpesa.tracker.export.FileUriProvider
import com.openmpesa.tracker.export.PdfReportExporter
import com.openmpesa.tracker.export.ShareHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel managing the state machine and background generation of exported reports.
 *
 * It coordinates querying transactions within the chosen date range, invoking either
 * the CSV or PDF exporter, constructing the secure Android Share Sheet intent, and
 * notifying the UI when the file is ready to share.
 *
 * @param repository Transaction repository for fetching ledger records.
 * @param csvExporter Generator for tabular CSV spreadsheets.
 * @param pdfExporter Generator for printable PDF statements.
 * @param fileUriProvider Strategy for resolving secure content:// URIs for Android sharing.
 */
class ExportViewModel(
    private val repository: TransactionRepository,
    private val csvExporter: CsvReportExporter = CsvReportExporter(),
    private val pdfExporter: PdfReportExporter = PdfReportExporter(),
    private val fileUriProvider: FileUriProvider = DefaultFileUriProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())

    /**
     * Read-only stream of export UI state.
     */
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    /**
     * Updates the chosen report format (CSV or PDF).
     */
    fun selectFormat(format: ExportFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    /**
     * Updates the chosen date window (This Month, Last 30 Days, All Time).
     */
    fun selectDateRange(range: ExportDateRange) {
        _uiState.update { it.copy(selectedDateRange = range) }
    }

    /**
     * Dismisses any active error notification.
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clears one-time export success handles after the system share sheet has been opened.
     */
    fun clearExportEvent() {
        _uiState.update {
            it.copy(
                exportedFile = null,
                shareIntent = null,
                errorMessage = null
            )
        }
    }

    /**
     * Initiates report file generation on a background coroutine and constructs the share intent.
     *
     * @param context Android context used to access internal cache and create FileProvider URIs.
     */
    fun startExport(context: Context) {
        if (_uiState.value.isExporting) return

        _uiState.update {
            it.copy(
                isExporting = true,
                errorMessage = null,
                exportedFile = null,
                shareIntent = null
            )
        }

        viewModelScope.launch {
            try {
                val state = _uiState.value
                val now = System.currentTimeMillis()
                val startEpoch = state.selectedDateRange.startEpochMillis(now)
                val endEpoch = Long.MAX_VALUE

                val transactions = if (state.selectedDateRange == ExportDateRange.ALL_TIME) {
                    repository.getAllTransactions().first()
                } else {
                    repository.getTransactionsBetween(startEpoch, endEpoch).first()
                }

                if (transactions.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            errorMessage = "No transactions found to export for the selected date range."
                        )
                    }
                    return@launch
                }

                val generatedFile: File? = when (state.selectedFormat) {
                    ExportFormat.CSV -> {
                        csvExporter.exportToCsv(context, transactions)
                    }
                    ExportFormat.PDF -> {
                        pdfExporter.exportToPdf(
                            context = context,
                            transactions = transactions,
                            title = "M-Pesa Statement (${state.selectedDateRange.displayName})"
                        )
                    }
                }

                if (generatedFile != null && generatedFile.exists()) {
                    val shareIntent = ShareHelper.buildShareIntent(
                        context = context,
                        file = generatedFile,
                        chooserTitle = "Share ${state.selectedFormat.title}",
                        fileUriProvider = fileUriProvider
                    )

                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportedFile = generatedFile,
                            shareIntent = shareIntent
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            errorMessage = "Failed to create report file. Please try again."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.message ?: "An unexpected error occurred during export."
                    )
                }
            }
        }
    }

    /**
     * Factory for constructing [ExportViewModel] with custom dependencies.
     */
    class Factory(
        private val repository: TransactionRepository,
        private val csvExporter: CsvReportExporter = CsvReportExporter(),
        private val pdfExporter: PdfReportExporter = PdfReportExporter(),
        private val fileUriProvider: FileUriProvider = DefaultFileUriProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ExportViewModel::class.java)) {
                return ExportViewModel(
                    repository = repository,
                    csvExporter = csvExporter,
                    pdfExporter = pdfExporter,
                    fileUriProvider = fileUriProvider
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
