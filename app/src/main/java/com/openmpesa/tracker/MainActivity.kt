package com.openmpesa.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openmpesa.tracker.ingestion.MpesaHistoryReader
import com.openmpesa.tracker.ui.dashboard.DashboardScreen
import com.openmpesa.tracker.ui.dashboard.DashboardViewModel
import com.openmpesa.tracker.ui.export.ExportBottomSheet
import com.openmpesa.tracker.ui.export.ExportViewModel
import com.openmpesa.tracker.ui.onboarding.OnboardingScreen
import com.openmpesa.tracker.ui.onboarding.OnboardingViewModel
import com.openmpesa.tracker.ui.settings.SettingScreen
import com.openmpesa.tracker.ui.settings.SettingViewModel
import com.openmpesa.tracker.ui.theme.OpenMpesaTheme
import com.openmpesa.tracker.ui.transactions.TransactionListScreen
import com.openmpesa.tracker.ui.transactions.TransactionListViewModel

/**
 * Navigation destination enum representing top-level application screens.
 */
enum class AppScreen {
    /** Initial screen explaining offline privacy and requesting runtime SMS permissions */
    ONBOARDING,

    /** Primary financial dashboard with balance, monthly metrics, and category breakdown */
    DASHBOARD,

    /** Searchable and filterable full transaction ledger with personal memo editing */
    TRANSACTIONS,

    /** Category settings screen for managing custom budgeting categories */
    SETTINGS
}

/**
 * Primary activity window hosting the Jetpack Compose user interface.
 *
 * This Activity coordinates screen transitions (Onboarding -> Dashboard -> Transaction List),
 * initializes ViewModels with app-wide repository singletons from [MpesaApplication.container],
 * and applies the Material 3 [OpenMpesaTheme].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Determine if SMS permissions are already granted from a previous launch
        val hasReadSms = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasReceiveSms = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val initialScreen = if (hasReadSms && hasReceiveSms) {
            AppScreen.DASHBOARD
        } else {
            AppScreen.ONBOARDING
        }

        setContent {
            OpenMpesaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        activity = this,
                        initialScreen = initialScreen,
                        hasReadSms = hasReadSms,
                        hasReceiveSms = hasReceiveSms
                    )
                }
            }
        }
    }
}

/**
 * Top-level navigation coordinator routing between Onboarding, Dashboard, and Transactions.
 */
@Composable
private fun AppNavigation(
    activity: MainActivity,
    initialScreen: AppScreen,
    hasReadSms: Boolean,
    hasReceiveSms: Boolean
) {
    val container = (activity.application as MpesaApplication).container
    val historyReader = remember {
        MpesaHistoryReader(activity, container.transactionRepository)
    }

    // ViewModels scoped to this Activity
    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.Factory(historyReader = historyReader)
    )

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            repository = container.transactionRepository,
            historyReader = historyReader
        )
    )

    val transactionListViewModel: TransactionListViewModel = viewModel(
        factory = TransactionListViewModel.Factory(
            repository = container.transactionRepository,
            categoryRepository = container.categoryRepository
        )
    )

    val exportViewModel: ExportViewModel = viewModel(
        factory = ExportViewModel.Factory(
            repository = container.transactionRepository
        )
    )

    val settingViewModel: SettingViewModel = viewModel(
        factory = SettingViewModel.Factory(
            repository = container.categoryRepository
        )
    )

    // Initialize Onboarding state with initial permission checks if on onboarding
    remember {
        if (initialScreen == AppScreen.ONBOARDING) {
            onboardingViewModel.onPermissionsResult(
                hasReadSms = hasReadSms,
                hasReceiveSms = hasReceiveSms,
                shouldShowRationale = false
            )
        }
        true
    }

    var currentScreen by remember { mutableStateOf(initialScreen) }
    var showExportSheet by remember { mutableStateOf(false) }

    // Intercept back button when viewing Transactions or Settings to navigate back to Dashboard
    BackHandler(enabled = currentScreen == AppScreen.TRANSACTIONS || currentScreen == AppScreen.SETTINGS) {
        currentScreen = AppScreen.DASHBOARD
    }

    when (currentScreen) {
        AppScreen.ONBOARDING -> {
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigateToDashboard = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.DASHBOARD -> {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToAllTransactions = {
                    currentScreen = AppScreen.TRANSACTIONS
                },
                onNavigateToExport = {
                    showExportSheet = true
                },
                onNavigateToSettings = {
                    currentScreen = AppScreen.SETTINGS
                },
                onTransactionClick = { tx ->
                    transactionListViewModel.selectTransaction(tx)
                    currentScreen = AppScreen.TRANSACTIONS
                }
            )
        }

        AppScreen.TRANSACTIONS -> {
            TransactionListScreen(
                viewModel = transactionListViewModel,
                onNavigateBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.SETTINGS -> {
            SettingScreen(
                viewModel = settingViewModel,
                onNavigateBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }
    }

    // Export bottom sheet modal overlay
    if (showExportSheet) {
        ExportBottomSheet(
            viewModel = exportViewModel,
            onDismiss = { showExportSheet = false }
        )
    }
}
