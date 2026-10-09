package com.openmpesa.tracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.entity.TransactionDirection
import com.openmpesa.tracker.ui.theme.ExpenseRed
import com.openmpesa.tracker.ui.theme.FeeAmber
import com.openmpesa.tracker.ui.theme.IncomeGreen
import com.openmpesa.tracker.ui.theme.MpesaDarkGreen
import com.openmpesa.tracker.ui.theme.MpesaGreen

/**
 * The main Financial Dashboard screen.
 *
 * Displays financial summary cards (income, expenses, fees, wallet balance),
 * visual spending breakdown by category, and recent M-Pesa transactions.
 *
 * @param viewModel State manager supplying aggregated metrics and transaction streams.
 * @param onNavigateToAllTransactions Callback when the user taps "See All" transactions.
 * @param onNavigateToExport Callback when the user taps the Export button.
 * @param onNavigateToSettings Callback when the user taps the Settings button.
 * @param onTransactionClick Callback when the user taps a specific transaction to view details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAllTransactions: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onTransactionClick: (MpesaTransactionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "OpenMpesaTracker",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    // Category settings button (just beside the sync button)
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Category Settings"
                        )
                    }

                    // Sync SMS button
                    IconButton(
                        onClick = { viewModel.syncSmsInbox() },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MpesaGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync SMS Inbox"
                            )
                        }
                    }

                    // Export reports button
                    IconButton(onClick = onNavigateToExport) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Export CSV or PDF"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Optional Sync Feedback Banner
            item {
                AnimatedVisibility(visible = uiState.syncMessage != null) {
                    uiState.syncMessage?.let { message ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissSyncMessage() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Period Selector Chips (This Month / Last 30 Days / All Time)
            item {
                PeriodSelectorRow(
                    selectedPeriod = uiState.selectedPeriod,
                    onPeriodSelected = { viewModel.selectPeriod(it) }
                )
            }

            // Hero Balance Card
            item {
                HeroBalanceCard(
                    latestBalance = uiState.latestBalance,
                    netCashFlow = uiState.netCashFlow
                )
            }

            // Inbound, Outbound, and Fees Metrics Grid
            item {
                MetricsSummaryRow(
                    inbound = uiState.totalInbound,
                    outbound = uiState.totalOutbound,
                    fees = uiState.totalFees
                )
            }

            // Spending by Category Breakdown
            item {
                CategoryBreakdownCard(
                    breakdown = uiState.categoryBreakdown,
                    totalExpenses = uiState.totalExpenses
                )
            }

            // Recent Transactions Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToAllTransactions) {
                        Text(
                            text = "View All",
                            color = MpesaGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Empty State or Recent Transactions List
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    EmptyTransactionsPlaceholder()
                }
            } else {
                items(uiState.recentTransactions, key = { it.code }) { tx ->
                    TransactionItemRow(
                        transaction = tx,
                        onClick = { onTransactionClick(tx) }
                    )
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Filter chip row to toggle between time periods.
 */
@Composable
private fun PeriodSelectorRow(
    selectedPeriod: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(DashboardPeriod.values()) { period ->
            val isSelected = period == selectedPeriod
            FilterChip(
                selected = isSelected,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MpesaGreen,
                    selectedLabelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

/**
 * Hero card displaying the user's latest recorded M-Pesa balance and net flow.
 */
@Composable
private fun HeroBalanceCard(
    latestBalance: Double?,
    netCashFlow: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MpesaDarkGreen
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M-PESA WALLET BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val balanceText = if (latestBalance != null) {
                DashboardFormatter.formatKsh(latestBalance)
            } else {
                "Ksh --.--"
            }

            Text(
                text = balanceText,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Net Cash Flow indicator pill
            val isPositive = netCashFlow >= 0
            val netFlowPrefix = if (isPositive) "+ " else "- "
            val absNetFlow = kotlin.math.abs(netCashFlow)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Net Flow: $netFlowPrefix${DashboardFormatter.formatKsh(absNetFlow)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Row displaying Inbound, Outbound, and Fees metric cards side by side.
 */
@Composable
private fun MetricsSummaryRow(
    inbound: Double,
    outbound: Double,
    fees: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Inbound card
        MetricMiniCard(
            modifier = Modifier.weight(1f),
            title = "Inbound",
            amount = DashboardFormatter.formatKsh(inbound),
            icon = Icons.AutoMirrored.Filled.CallReceived,
            accentColor = IncomeGreen
        )

        // Outbound card
        MetricMiniCard(
            modifier = Modifier.weight(1f),
            title = "Outbound",
            amount = DashboardFormatter.formatKsh(outbound),
            icon = Icons.AutoMirrored.Filled.CallMade,
            accentColor = ExpenseRed
        )

        // Fees card
        MetricMiniCard(
            modifier = Modifier.weight(1f),
            title = "Fees",
            amount = DashboardFormatter.formatKsh(fees),
            icon = Icons.Default.Receipt,
            accentColor = FeeAmber
        )
    }
}

/**
 * Individual small card for summary metrics (income, expenses, fees).
 */
@Composable
private fun MetricMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = amount,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Card displaying spending distribution grouped by category with visual progress bars.
 */
@Composable
private fun CategoryBreakdownCard(
    breakdown: List<CategorySpendShare>,
    totalExpenses: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (breakdown.isEmpty() || totalExpenses == 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No outbound expenses in this time period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                breakdown.forEach { item ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${DashboardFormatter.formatKsh(item.totalAmount)} (${item.percentage.toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { item.percentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MpesaGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single transaction item row shown in the recent transactions preview.
 */
@Composable
fun TransactionItemRow(
    transaction: MpesaTransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInbound = transaction.direction == TransactionDirection.INBOUND
    val amountColor = if (isInbound) IncomeGreen else ExpenseRed
    val prefix = if (isInbound) "+ " else "- "
    val icon = if (isInbound) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction Icon Bubble
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details: Name, Category, Date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.party,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " • ${DashboardFormatter.formatDate(transaction.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Amount and Fee
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${DashboardFormatter.formatKsh(transaction.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                val fee = transaction.transactionFee
                if (fee != null && fee > 0.0) {
                    Text(
                        text = "Fee: ${DashboardFormatter.formatKsh(fee)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FeeAmber
                    )
                }
            }
        }
    }
}

/**
 * Placeholder when no transactions exist yet.
 */
@Composable
private fun EmptyTransactionsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Transactions Recorded",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the sync icon above to scan your SMS inbox for M-Pesa receipts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
