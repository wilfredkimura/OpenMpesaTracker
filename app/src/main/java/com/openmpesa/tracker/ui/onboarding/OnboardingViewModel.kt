package com.openmpesa.tracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openmpesa.tracker.ingestion.MpesaHistoryReader
import com.openmpesa.tracker.ingestion.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State manager and coordinator for the Onboarding and Permissions flow.
 *
 * This ViewModel tracks whether the user has approved SMS permissions, shows
 * friendly explanations if permissions were denied, and coordinates the first-time
 * background scan of past M-Pesa text messages into our offline Room database.
 *
 * @param historyReader Optional history reader that queries the SMS inbox.
 * @param syncProvider Optional custom sync function useful for tests to simulate historical inbox reads.
 */
class OnboardingViewModel(
    private val historyReader: MpesaHistoryReader? = null,
    private val syncProvider: (suspend (onProgress: (scanned: Int, saved: Int) -> Unit) -> SyncResult)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())

    /**
     * Public, read-only stream of UI state for Jetpack Compose to observe.
     */
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Updates the permission status when Android returns the result of the permission request prompt.
     *
     * @param hasReadSms True if user approved reading past SMS messages.
     * @param hasReceiveSms True if user approved listening for new incoming SMS.
     * @param shouldShowRationale True if Android recommends explaining why we need permission.
     */
    fun onPermissionsResult(
        hasReadSms: Boolean,
        hasReceiveSms: Boolean,
        shouldShowRationale: Boolean = false
    ) {
        val allGranted = hasReadSms && hasReceiveSms
        _uiState.update { current ->
            current.copy(
                hasReadSmsPermission = hasReadSms,
                hasReceiveSmsPermission = hasReceiveSms,
                showRationale = !allGranted && shouldShowRationale,
                isPermanentlyDenied = !allGranted && !shouldShowRationale && (current.showRationale || current.isPermanentlyDenied)
            )
        }

        // If both permissions were granted and we haven't synced yet, start syncing automatically
        if (allGranted && !_uiState.value.isSyncComplete && !_uiState.value.isSyncing) {
            startHistoricalSync()
        }
    }

    /**
     * Dismisses the explanatory rationale banner or card when the user taps Dismiss.
     */
    fun dismissRationale() {
        _uiState.update { it.copy(showRationale = false) }
    }

    /**
     * Starts the one-time background scan of existing M-Pesa receipts in the phone's inbox.
     *
     * As messages are read and parsed, progress counts are continually updated on screen.
     */
    fun startHistoricalSync() {
        if (_uiState.value.isSyncing) return

        _uiState.update {
            it.copy(
                isSyncing = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val result: SyncResult = when {
                    syncProvider != null -> {
                        syncProvider.invoke { scanned, saved ->
                            _uiState.update { it.copy(scannedCount = scanned, savedCount = saved) }
                        }
                    }
                    historyReader != null -> {
                        historyReader.readHistory { scanned, saved ->
                            _uiState.update { it.copy(scannedCount = scanned, savedCount = saved) }
                        }
                    }
                    else -> {
                        // Fallback when no reader is attached (e.g. preview or mock mode)
                        SyncResult(scannedCount = 0, savedCount = 0, skippedCount = 0)
                    }
                }

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        isSyncComplete = true,
                        scannedCount = result.scannedCount,
                        savedCount = result.savedCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = e.message ?: "An unexpected error occurred during SMS scan."
                    )
                }
            }
        }
    }

    /**
     * Simple factory helper to create [OnboardingViewModel] with runtime dependencies.
     */
    class Factory(
        private val historyReader: MpesaHistoryReader? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                return OnboardingViewModel(historyReader = historyReader) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
