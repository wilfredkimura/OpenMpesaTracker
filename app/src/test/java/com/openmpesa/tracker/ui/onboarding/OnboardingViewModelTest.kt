package com.openmpesa.tracker.ui.onboarding

import com.openmpesa.tracker.ingestion.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OnboardingViewModel].
 *
 * Verifies permission state changes, rationale toggles, inbox sync progress,
 * and error handling during the first-time onboarding flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // Set test dispatcher as the main thread dispatcher for ViewModel coroutine execution
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // Reset dispatcher after test execution
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() {
        val viewModel = OnboardingViewModel()
        val state = viewModel.uiState.value

        assertFalse("Read SMS permission should default to false", state.hasReadSmsPermission)
        assertFalse("Receive SMS permission should default to false", state.hasReceiveSmsPermission)
        assertFalse("Permissions granted check should be false", state.areAllPermissionsGranted)
        assertFalse("Rationale should not be shown by default", state.showRationale)
        assertFalse("Permanently denied should be false", state.isPermanentlyDenied)
        assertFalse("Sync should not be running initially", state.isSyncing)
        assertFalse("Sync should not be complete initially", state.isSyncComplete)
        assertEquals(0, state.scannedCount)
        assertEquals(0, state.savedCount)
        assertNull(state.errorMessage)
    }

    @Test
    fun onPermissionsResult_whenDeniedWithRationale_showsRationale() {
        val viewModel = OnboardingViewModel()

        viewModel.onPermissionsResult(
            hasReadSms = false,
            hasReceiveSms = false,
            shouldShowRationale = true
        )

        val state = viewModel.uiState.value
        assertFalse(state.areAllPermissionsGranted)
        assertTrue("Rationale should be displayed when denied with rationale flag", state.showRationale)
        assertFalse(state.isSyncing)
    }

    @Test
    fun dismissRationale_hidesRationaleCard() {
        val viewModel = OnboardingViewModel()

        viewModel.onPermissionsResult(
            hasReadSms = false,
            hasReceiveSms = false,
            shouldShowRationale = true
        )
        assertTrue(viewModel.uiState.value.showRationale)

        viewModel.dismissRationale()
        assertFalse("Rationale should be dismissed", viewModel.uiState.value.showRationale)
    }

    @Test
    fun onPermissionsResult_whenOnlyOnePermissionGranted_doesNotStartSync() {
        val viewModel = OnboardingViewModel()

        viewModel.onPermissionsResult(
            hasReadSms = true,
            hasReceiveSms = false,
            shouldShowRationale = true
        )

        val state = viewModel.uiState.value
        assertTrue(state.hasReadSmsPermission)
        assertFalse(state.hasReceiveSmsPermission)
        assertFalse(state.areAllPermissionsGranted)
        assertFalse("Sync should not start if both permissions are not granted", state.isSyncing)
    }

    @Test
    fun onPermissionsResult_whenBothGranted_triggersHistoricalSyncSuccessfully() = runTest {
        // Mock sync provider that reports intermediate progress before finishing
        val mockSyncProvider: suspend (onProgress: (Int, Int) -> Unit) -> SyncResult = { onProgress ->
            onProgress(10, 5)
            onProgress(25, 18)
            SyncResult(scannedCount = 25, savedCount = 18, skippedCount = 7)
        }

        val viewModel = OnboardingViewModel(syncProvider = mockSyncProvider)

        viewModel.onPermissionsResult(
            hasReadSms = true,
            hasReceiveSms = true,
            shouldShowRationale = false
        )

        // Advance coroutines to run the background sync
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasReadSmsPermission)
        assertTrue(state.hasReceiveSmsPermission)
        assertTrue(state.areAllPermissionsGranted)
        assertFalse("Sync should no longer be running", state.isSyncing)
        assertTrue("Sync should be marked as complete", state.isSyncComplete)
        assertEquals(25, state.scannedCount)
        assertEquals(18, state.savedCount)
        assertNull(state.errorMessage)
    }

    @Test
    fun startHistoricalSync_whenFails_recordsErrorMessage() = runTest {
        val failingSyncProvider: suspend (onProgress: (Int, Int) -> Unit) -> SyncResult = {
            throw IllegalStateException("Database read error during SMS scan")
        }

        val viewModel = OnboardingViewModel(syncProvider = failingSyncProvider)

        viewModel.startHistoricalSync()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Sync should not be active after failure", state.isSyncing)
        assertFalse("Sync should not be marked complete on failure", state.isSyncComplete)
        assertEquals("Database read error during SMS scan", state.errorMessage)
    }
}
