package com.openmpesa.tracker.ui.onboarding

/**
 * Holds all the visual and interactive states for the Onboarding and Permissions screen.
 *
 * This simple data holder tells the user interface what to display:
 * whether permissions are approved, if a background SMS scan is running,
 * how many messages have been scanned so far, and if the user is ready to proceed.
 */
data class OnboardingUiState(
    /** True if the user has allowed the app to read past SMS receipts */
    val hasReadSmsPermission: Boolean = false,

    /** True if the user has allowed the app to listen for new incoming SMS receipts in real-time */
    val hasReceiveSmsPermission: Boolean = false,

    /** True if the user denied permissions before and we should show an explanatory helper message */
    val showRationale: Boolean = false,

    /** True if the user permanently denied permission (e.g. checked "Don't ask again") */
    val isPermanentlyDenied: Boolean = false,

    /** True while the app is actively reading through past SMS messages from the phone's inbox */
    val isSyncing: Boolean = false,

    /** Total number of M-Pesa messages found during the scan */
    val scannedCount: Int = 0,

    /** Number of new M-Pesa transactions successfully saved to the local database */
    val savedCount: Int = 0,

    /** True when the initial historical sync has finished and user can open the dashboard */
    val isSyncComplete: Boolean = false,

    /** Human-readable error message if something went wrong during the scan */
    val errorMessage: String? = null
) {
    /**
     * Helper check: Returns true only when both required SMS permissions are fully granted.
     */
    val areAllPermissionsGranted: Boolean
        get() = hasReadSmsPermission && hasReceiveSmsPermission
}
