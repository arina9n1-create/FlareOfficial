package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.VynAvatar
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

enum class AdminMonetizationSubTab {
    OVERVIEW,
    REVENUE_PERIODS,
    CONTENT_EARNINGS,
    CREATOR_REPORTS,
    REVENUE_SETTINGS,
    AD_UNIT_MAPPING,
    DIAGNOSTICS,
    APPLICATIONS,
    WALLET,
    TRANSACTIONS
}

@Composable
fun AdminMonetizationSection(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableStateOf(AdminMonetizationSubTab.OVERVIEW) }

    val settings by viewModel.monetizationSettings.collectAsState()
    val applications by viewModel.monetizationApplications.collectAsState()
    val userProfile by viewModel.userMonetizationProfile.collectAsState()
    val wallet by viewModel.earningsWallet.collectAsState()
    val transactions by viewModel.monetizationTransactions.collectAsState()
    val revenuePeriods by viewModel.revenuePeriods.collectAsState()
    val contentEarnings by viewModel.contentEarnings.collectAsState()
    val adUnitMappings by viewModel.adUnitMappings.collectAsState()
    val diagnostics by viewModel.attributionDiagnostics.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Sub-Tab Navigation Header
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Color(0xFF6C5CE7),
            edgePadding = 12.dp
        ) {
            AdminMonetizationSubTab.values().forEach { tab ->
                val pendingCount = if (tab == AdminMonetizationSubTab.APPLICATIONS) {
                    val c = applications.count { it.status == "PENDING" }
                    if (c > 0) " ($c)" else ""
                } else ""

                Tab(
                    selected = selectedSubTab == tab,
                    onClick = { selectedSubTab = tab },
                    text = {
                        Text(
                            text = when (tab) {
                                AdminMonetizationSubTab.OVERVIEW -> "Overview"
                                AdminMonetizationSubTab.REVENUE_PERIODS -> "Revenue Periods"
                                AdminMonetizationSubTab.CONTENT_EARNINGS -> "Content Earnings"
                                AdminMonetizationSubTab.CREATOR_REPORTS -> "Creator Reports"
                                AdminMonetizationSubTab.REVENUE_SETTINGS -> "Revenue Settings"
                                AdminMonetizationSubTab.AD_UNIT_MAPPING -> "Ad Unit Mapping"
                                AdminMonetizationSubTab.DIAGNOSTICS -> "Diagnostics"
                                AdminMonetizationSubTab.APPLICATIONS -> "Applications$pendingCount"
                                AdminMonetizationSubTab.WALLET -> "Wallet"
                                AdminMonetizationSubTab.TRANSACTIONS -> "Transactions"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }

        // Sub-Tab Content
        when (selectedSubTab) {
            AdminMonetizationSubTab.OVERVIEW -> {
                AdminMonetizationOverviewTab(
                    viewModel = viewModel,
                    settings = settings,
                    applications = applications,
                    revenuePeriods = revenuePeriods,
                    contentEarnings = contentEarnings,
                    diagnostics = diagnostics,
                    onNavigateTab = { selectedSubTab = it }
                )
            }
            AdminMonetizationSubTab.REVENUE_PERIODS -> {
                AdminRevenuePeriodsTab(
                    viewModel = viewModel,
                    revenuePeriods = revenuePeriods
                )
            }
            AdminMonetizationSubTab.CONTENT_EARNINGS -> {
                AdminContentEarningsTab(
                    viewModel = viewModel,
                    contentEarnings = contentEarnings,
                    revenuePeriods = revenuePeriods
                )
            }
            AdminMonetizationSubTab.CREATOR_REPORTS -> {
                AdminCreatorReportsTab(
                    viewModel = viewModel,
                    contentEarnings = contentEarnings
                )
            }
            AdminMonetizationSubTab.REVENUE_SETTINGS -> {
                AdminRevenueSettingsTab(
                    viewModel = viewModel,
                    currentSettings = settings
                )
            }
            AdminMonetizationSubTab.AD_UNIT_MAPPING -> {
                AdminAdUnitMappingTab(
                    viewModel = viewModel,
                    mappings = adUnitMappings
                )
            }
            AdminMonetizationSubTab.DIAGNOSTICS -> {
                AdminDiagnosticsTab(
                    viewModel = viewModel,
                    diagnostics = diagnostics
                )
            }
            AdminMonetizationSubTab.APPLICATIONS -> {
                AdminApplicationsTab(
                    viewModel = viewModel,
                    applications = applications
                )
            }
            AdminMonetizationSubTab.WALLET -> {
                AdminWalletTab(
                    viewModel = viewModel,
                    wallet = wallet
                )
            }
            AdminMonetizationSubTab.TRANSACTIONS -> {
                AdminTransactionsTab(
                    viewModel = viewModel,
                    transactions = transactions
                )
            }
        }
    }
}

// ==============================================================================
// 1. OVERVIEW TAB
// ==============================================================================
@Composable
fun AdminMonetizationOverviewTab(
    viewModel: SocialViewModel,
    settings: MonetizationSettings,
    applications: List<MonetizationApplication>,
    revenuePeriods: List<RevenuePeriod>,
    contentEarnings: List<ContentEarning>,
    diagnostics: AttributionDiagnostics,
    onNavigateTab: (AdminMonetizationSubTab) -> Unit
) {
    val totalAdMobRev = revenuePeriods.sumOf { it.totalAdMobRevenue }
    val totalCreatorPool = revenuePeriods.sumOf { it.totalCreatorPool }
    val totalPlatformRev = revenuePeriods.sumOf { it.totalPlatformRevenue }
    val totalImpressions = contentEarnings.sumOf { it.eligibleImpressions }
    val pendingApps = applications.count { it.status == "PENDING" }
    val activePeriod = revenuePeriods.firstOrNull { it.status != "FINALIZED" } ?: revenuePeriods.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Revenue & Monetization Command Center",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Authoritative AdMob revenue attribution, content pools, and creator payouts.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Top Metrics Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminMetricCard(
                    title = "Total AdMob Revenue",
                    value = "$${String.format(Locale.US, "%.2f", totalAdMobRev)}",
                    subtitle = "Imported reports",
                    color = Color(0xFF0984E3),
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Creator Revenue Pool",
                    value = "$${String.format(Locale.US, "%.2f", totalCreatorPool)}",
                    subtitle = "${settings.creatorRevenueShare.toInt()}% pool share",
                    color = Color(0xFF00B894),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminMetricCard(
                    title = "Platform Revenue",
                    value = "$${String.format(Locale.US, "%.2f", totalPlatformRev)}",
                    subtitle = "${settings.platformRevenueShare.toInt()}% platform split",
                    color = Color(0xFF6C5CE7),
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Attributed Impressions",
                    value = "${totalImpressions / 1000}k",
                    subtitle = "${contentEarnings.size} content items",
                    color = Color(0xFFE17055),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Active Period Snapshot
        if (activePeriod != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "CURRENT REVENUE PERIOD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF6C5CE7))
                                Text(text = activePeriod.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (activePeriod.status) {
                                    "FINALIZED" -> Color(0xFF00B894).copy(alpha = 0.15f)
                                    "CALCULATED" -> Color(0xFF0984E3).copy(alpha = 0.15f)
                                    "IMPORTED" -> Color(0xFFF39C12).copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = activePeriod.status,
                                    color = when (activePeriod.status) {
                                        "FINALIZED" -> Color(0xFF00B894)
                                        "CALCULATED" -> Color(0xFF0984E3)
                                        "IMPORTED" -> Color(0xFFF39C12)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "Period Dates", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${activePeriod.startDate} → ${activePeriod.endDate}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Creator Pool", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", activePeriod.totalCreatorPool)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                            }
                        }

                        Button(
                            onClick = { onNavigateTab(AdminMonetizationSubTab.REVENUE_PERIODS) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage Revenue Cycle & Finalize", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Navigation Tiles
        item {
            Text(text = "Quick Navigation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTileButton(
                    title = "Revenue Periods",
                    subtitle = "${revenuePeriods.size} Cycles",
                    icon = Icons.Default.CalendarMonth,
                    onClick = { onNavigateTab(AdminMonetizationSubTab.REVENUE_PERIODS) },
                    modifier = Modifier.weight(1f)
                )
                AdminTileButton(
                    title = "Content Earnings",
                    subtitle = "${contentEarnings.size} Items",
                    icon = Icons.Default.VideoLibrary,
                    onClick = { onNavigateTab(AdminMonetizationSubTab.CONTENT_EARNINGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTileButton(
                    title = "Creator Reports",
                    subtitle = "Audited Shares",
                    icon = Icons.Default.People,
                    onClick = { onNavigateTab(AdminMonetizationSubTab.CREATOR_REPORTS) },
                    modifier = Modifier.weight(1f)
                )
                AdminTileButton(
                    title = "Revenue Settings",
                    subtitle = "Pool Splits",
                    icon = Icons.Default.Settings,
                    onClick = { onNavigateTab(AdminMonetizationSubTab.REVENUE_SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTileButton(
                    title = "Ad Unit Mapping",
                    subtitle = "Placements",
                    icon = Icons.Default.AdsClick,
                    onClick = { onNavigateTab(AdminMonetizationSubTab.AD_UNIT_MAPPING) },
                    modifier = Modifier.weight(1f)
                )
                AdminTileButton(
                    title = "Diagnostics",
                    subtitle = "${diagnostics.validEvents} Valid",
                    icon = Icons.Default.Speed,
                    onClick = { onNavigateTab(AdminMonetizationSubTab.DIAGNOSTICS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            AdminTileButton(
                title = "Creator Applications ($pendingApps Pending)",
                subtitle = "Review creator onboarding",
                icon = Icons.AutoMirrored.Filled.FactCheck,
                onClick = { onNavigateTab(AdminMonetizationSubTab.APPLICATIONS) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==============================================================================
// 2. REVENUE PERIODS TAB
// ==============================================================================
@Composable
fun AdminRevenuePeriodsTab(
    viewModel: SocialViewModel,
    revenuePeriods: List<RevenuePeriod>
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPeriodForImport by remember { mutableStateOf<RevenuePeriod?>(null) }
    var selectedPeriodForPreview by remember { mutableStateOf<RevenuePeriod?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Revenue Periods", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(text = "AdMob Cycles & Finalization", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Period", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (revenuePeriods.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No revenue periods found. Create one to begin attribution.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(revenuePeriods) { period ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = period.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(text = "ID: ${period.id} • ${period.startDate} to ${period.endDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (period.status) {
                                    "FINALIZED" -> Color(0xFF00B894).copy(alpha = 0.15f)
                                    "CALCULATED" -> Color(0xFF0984E3).copy(alpha = 0.15f)
                                    "IMPORTED" -> Color(0xFFF39C12).copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = period.status,
                                    color = when (period.status) {
                                        "FINALIZED" -> Color(0xFF00B894)
                                        "CALCULATED" -> Color(0xFF0984E3)
                                        "IMPORTED" -> Color(0xFFF39C12)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Metric Breakdown
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "AdMob Revenue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", period.totalAdMobRevenue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0984E3))
                            }
                            Column {
                                Text(text = "Creator Pool", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", period.totalCreatorPool)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF00B894))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Platform Share", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", period.totalPlatformRevenue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF6C5CE7))
                            }
                        }

                        // Content Pools
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Posts: $${String.format(Locale.US, "%.2f", period.postPool)}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Videos: $${String.format(Locale.US, "%.2f", period.videoPool)}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Reels: $${String.format(Locale.US, "%.2f", period.reelsPool)}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }

                        // Action Buttons based on status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (period.status != "FINALIZED") {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.calculateRevenueAttribution(period.id) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Calculate", fontSize = 11.sp)
                                }
                            }

                            if (period.status == "CALCULATED" || period.status == "IMPORTED") {
                                Button(
                                    onClick = {
                                        viewModel.finalizeRevenuePeriod(period.id) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Finalize & Credit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AdminCreatePeriodDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { id, start, end, name ->
                viewModel.createRevenuePeriod(id, start, end, name) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showCreateDialog = false
                }
            }
        )
    }
}

@Composable
fun AdminCreatePeriodDialog(
    onDismiss: () -> Unit,
    onCreate: (id: String, start: String, end: String, name: String) -> Unit
) {
    var periodId by remember { mutableStateOf("PERIOD-2026-09") }
    var periodName by remember { mutableStateOf("September 2026 Revenue Cycle") }
    var startDate by remember { mutableStateOf("2026-09-01") }
    var endDate by remember { mutableStateOf("2026-09-30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Revenue Period", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = periodId,
                    onValueChange = { periodId = it },
                    label = { Text("Period ID (e.g. PERIOD-2026-09)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = periodName,
                    onValueChange = { periodName = it },
                    label = { Text("Period Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End Date") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(periodId, startDate, endDate, periodName) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
            ) {
                Text("Create Period")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ==============================================================================
// 3. CONTENT EARNINGS TAB
// ==============================================================================
@Composable
fun AdminContentEarningsTab(
    viewModel: SocialViewModel,
    contentEarnings: List<ContentEarning>,
    revenuePeriods: List<RevenuePeriod>
) {
    val context = LocalContext.current
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedEarningForAdjustment by remember { mutableStateOf<ContentEarning?>(null) }

    val filteredList = remember(contentEarnings, selectedTypeFilter, searchQuery) {
        contentEarnings.filter { item ->
            val matchType = if (selectedTypeFilter == "ALL") true else item.contentType == selectedTypeFilter
            val matchQuery = if (searchQuery.isBlank()) true else {
                item.userHandle.contains(searchQuery, ignoreCase = true) ||
                item.contentTitle.contains(searchQuery, ignoreCase = true) ||
                item.contentId.contains(searchQuery, ignoreCase = true)
            }
            matchType && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "Content-Level Revenue Attributions", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = "Attributed earnings calculated dynamically from verified AdMob revenue.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Creator, Title, or Content ID") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Filter Chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "POST", "VIDEO", "REEL").forEach { type ->
                    FilterChip(
                        selected = selectedTypeFilter == type,
                        onClick = { selectedTypeFilter = type },
                        label = { Text(if (type == "ALL") "All Formats" else "${type}s") }
                    )
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No content attribution records match the filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (item.contentType) {
                                        "POST" -> Color(0xFF0984E3).copy(alpha = 0.15f)
                                        "VIDEO" -> Color(0xFF6C5CE7).copy(alpha = 0.15f)
                                        "REEL" -> Color(0xFFE84393).copy(alpha = 0.15f)
                                        else -> Color.Gray.copy(alpha = 0.15f)
                                    }
                                ) {
                                    Text(
                                        text = item.contentType,
                                        color = when (item.contentType) {
                                            "POST" -> Color(0xFF0984E3)
                                            "VIDEO" -> Color(0xFF6C5CE7)
                                            "REEL" -> Color(0xFFE84393)
                                            else -> Color.Gray
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "@${item.userHandle}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (item.status) {
                                    "FINALIZED" -> Color(0xFF00B894).copy(alpha = 0.15f)
                                    "ADJUSTED" -> Color(0xFF6C5CE7).copy(alpha = 0.15f)
                                    "ESTIMATED" -> Color(0xFFF39C12).copy(alpha = 0.15f)
                                    else -> Color.Gray.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = item.status,
                                    color = when (item.status) {
                                        "FINALIZED" -> Color(0xFF00B894)
                                        "ADJUSTED" -> Color(0xFF6C5CE7)
                                        "ESTIMATED" -> Color(0xFFF39C12)
                                        else -> Color.Gray
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = item.contentTitle,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Stats Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "Views", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${item.contentViews}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column {
                                Text(text = "Ad Impressions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${item.eligibleImpressions}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column {
                                Text(text = "Attribution Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${String.format(Locale.US, "%.2f", item.attributionShare * 100)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0984E3))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Creator Net", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", item.creatorShare)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF00B894))
                            }
                        }

                        // Adjustment trigger
                        if (item.status == "FINALIZED" || item.status == "ADJUSTED") {
                            OutlinedButton(
                                onClick = { selectedEarningForAdjustment = item },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply AdMob Audit Adjustment", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedEarningForAdjustment != null) {
        AdminApplyAdjustmentDialog(
            earning = selectedEarningForAdjustment!!,
            onDismiss = { selectedEarningForAdjustment = null },
            onApply = { amount, reason ->
                viewModel.applyRevenueAdjustment(
                    periodId = selectedEarningForAdjustment!!.revenuePeriodId,
                    contentEarningId = selectedEarningForAdjustment!!.id,
                    adjustmentAmount = amount,
                    reason = reason
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) selectedEarningForAdjustment = null
                }
            }
        )
    }
}

@Composable
fun AdminApplyAdjustmentDialog(
    earning: ContentEarning,
    onDismiss: () -> Unit,
    onApply: (amount: Double, reason: String) -> Unit
) {
    var adjustmentAmountStr by remember { mutableStateOf("0.00") }
    var reason by remember { mutableStateOf("AdMob post-settlement audit reconciliation") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply Revenue Adjustment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Content: ${earning.contentTitle}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Current Creator Earning: $${String.format(Locale.US, "%.4f", earning.creatorShare)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                OutlinedTextField(
                    value = adjustmentAmountStr,
                    onValueChange = { adjustmentAmountStr = it },
                    label = { Text("Adjustment Amount (+ or - USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Audit Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = adjustmentAmountStr.toDoubleOrNull() ?: 0.0
                    onApply(amount, reason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
            ) {
                Text("Apply Adjustment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ==============================================================================
// 4. CREATOR REPORTS TAB
// ==============================================================================
@Composable
fun AdminCreatorReportsTab(
    viewModel: SocialViewModel,
    contentEarnings: List<ContentEarning>
) {
    val creatorSummaries = remember(contentEarnings) {
        viewModel.getCreatorSummaries()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "Creator Revenue & Wallet Audits", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = "Aggregated lifetime earnings, balances, and format breakdowns per creator.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(creatorSummaries) { creator ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VynAvatar(avatarType = creator.userHandle, size = 42.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = creator.userName, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text(text = "@${creator.userHandle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Lifetime Earnings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "$${String.format(Locale.US, "%.2f", creator.lifetimeEarnings)}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF00B894))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Formats Breakdown
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "Posts Earnings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "$${String.format(Locale.US, "%.2f", creator.postsEarnings)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column {
                            Text(text = "Videos Earnings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "$${String.format(Locale.US, "%.2f", creator.videosEarnings)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column {
                            Text(text = "Reels Earnings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "$${String.format(Locale.US, "%.2f", creator.reelsEarnings)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Available Bal.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "$${String.format(Locale.US, "%.2f", creator.availableBalance)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0984E3))
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 5. REVENUE SETTINGS TAB
// ==============================================================================
@Composable
fun AdminRevenueSettingsTab(
    viewModel: SocialViewModel,
    currentSettings: MonetizationSettings
) {
    val context = LocalContext.current

    var creatorShareStr by remember(currentSettings) { mutableStateOf(currentSettings.creatorRevenueShare.toInt().toString()) }
    var platformShareStr by remember(currentSettings) { mutableStateOf(currentSettings.platformRevenueShare.toInt().toString()) }
    var postPoolStr by remember(currentSettings) { mutableStateOf(currentSettings.postRevenuePoolShare.toInt().toString()) }
    var videoPoolStr by remember(currentSettings) { mutableStateOf(currentSettings.videoRevenuePoolShare.toInt().toString()) }
    var reelsPoolStr by remember(currentSettings) { mutableStateOf(currentSettings.reelsRevenuePoolShare.toInt().toString()) }

    var minFollowersStr by remember(currentSettings) { mutableStateOf(currentSettings.minimumFollowers.toString()) }
    var minViewsStr by remember(currentSettings) { mutableStateOf(currentSettings.minimumViews.toString()) }
    var allowEstEarnings by remember(currentSettings) { mutableStateOf(currentSettings.allowEstimatedEarnings) }
    var autoFinalize by remember(currentSettings) { mutableStateOf(currentSettings.autoFinalizeRevenue) }

    val creatorShare = creatorShareStr.toDoubleOrNull() ?: 0.0
    val platformShare = platformShareStr.toDoubleOrNull() ?: 0.0
    val totalRevenueShare = creatorShare + platformShare

    val postShare = postPoolStr.toDoubleOrNull() ?: 0.0
    val videoShare = videoPoolStr.toDoubleOrNull() ?: 0.0
    val reelsShare = reelsPoolStr.toDoubleOrNull() ?: 0.0
    val totalPoolShare = postShare + videoShare + reelsShare

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "Revenue Allocation & Pool Configuration", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = "Configurable percentage splits for AdMob pools and creator payouts.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Revenue Share Split (Creator vs Platform)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "AdMob Revenue Share Split", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (totalRevenueShare == 100.0) Color(0xFF00B894).copy(alpha = 0.15f) else Color(0xFFD63031).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Sum: ${totalRevenueShare.toInt()}% / 100%",
                                color = if (totalRevenueShare == 100.0) Color(0xFF00B894) else Color(0xFFD63031),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = creatorShareStr,
                            onValueChange = {
                                creatorShareStr = it
                                val num = it.toDoubleOrNull()
                                if (num != null && num <= 100) {
                                    platformShareStr = (100 - num).toInt().toString()
                                }
                            },
                            label = { Text("Creator Share %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = platformShareStr,
                            onValueChange = {
                                platformShareStr = it
                                val num = it.toDoubleOrNull()
                                if (num != null && num <= 100) {
                                    creatorShareStr = (100 - num).toInt().toString()
                                }
                            },
                            label = { Text("Platform Share %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Content Pool Allocation Split
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Creator Pool Allocation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (totalPoolShare == 100.0) Color(0xFF00B894).copy(alpha = 0.15f) else Color(0xFFD63031).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Sum: ${totalPoolShare.toInt()}% / 100%",
                                color = if (totalPoolShare == 100.0) Color(0xFF00B894) else Color(0xFFD63031),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = postPoolStr,
                            onValueChange = { postPoolStr = it },
                            label = { Text("Post Pool %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = videoPoolStr,
                            onValueChange = { videoPoolStr = it },
                            label = { Text("Video Pool %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reelsPoolStr,
                            onValueChange = { reelsPoolStr = it },
                            label = { Text("Reels Pool %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Switches
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Allow Estimated Earnings Preview", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(checked = allowEstEarnings, onCheckedChange = { allowEstEarnings = it })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Auto Finalize on Period Close", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(checked = autoFinalize, onCheckedChange = { autoFinalize = it })
                    }
                }
            }
        }

        // Save Button
        item {
            Button(
                onClick = {
                    if (totalRevenueShare != 100.0) {
                        Toast.makeText(context, "Creator Share + Platform Share must equal exactly 100%", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (totalPoolShare != 100.0) {
                        Toast.makeText(context, "Post + Video + Reels pool share must equal exactly 100%", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    val updated = currentSettings.copy(
                        creatorRevenueShare = creatorShare,
                        platformRevenueShare = platformShare,
                        postRevenuePoolShare = postShare,
                        videoRevenuePoolShare = videoShare,
                        reelsRevenuePoolShare = reelsShare,
                        minimumFollowers = minFollowersStr.toIntOrNull() ?: 1000,
                        minimumViews = minViewsStr.toIntOrNull() ?: 10000,
                        allowEstimatedEarnings = allowEstEarnings,
                        autoFinalizeRevenue = autoFinalize,
                        updatedAt = System.currentTimeMillis()
                    )

                    viewModel.updateMonetizationSettings(updated) { success ->
                        if (success) {
                            Toast.makeText(context, "Revenue settings saved successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed saving settings.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Revenue Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ==============================================================================
// 6. AD UNIT MAPPING TAB
// ==============================================================================
@Composable
fun AdminAdUnitMappingTab(
    viewModel: SocialViewModel,
    mappings: List<AdMobAdUnitMapping>
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "AdMob Ad Unit Mapping", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(text = "Configure AdMob IDs to placements & formats", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Unit", fontSize = 12.sp)
                }
            }
        }

        items(mappings) { map ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF6C5CE7).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = map.contentType,
                                    color = Color(0xFF6C5CE7),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = map.placement, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = map.adUnitId, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Switch(
                        checked = map.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.saveAdUnitMapping(map.copy(enabled = enabled))
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var adUnitId by remember { mutableStateOf("") }
        var placement by remember { mutableStateOf("CONTENT_FEED_NATIVE") }
        var contentType by remember { mutableStateOf("POST") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add AdMob Unit Mapping", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = adUnitId,
                        onValueChange = { adUnitId = it },
                        label = { Text("AdMob Ad Unit ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = placement,
                        onValueChange = { placement = it },
                        label = { Text("Placement (e.g. CONTENT_FEED_NATIVE)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contentType,
                        onValueChange = { contentType = it.uppercase() },
                        label = { Text("Content Type (POST / VIDEO / REEL)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveAdUnitMapping(
                            AdMobAdUnitMapping(
                                id = "MAP-${System.currentTimeMillis().toString().takeLast(6)}",
                                adUnitId = adUnitId,
                                placement = placement,
                                contentType = contentType,
                                enabled = true
                            )
                        )
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                ) {
                    Text("Save Mapping")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ==============================================================================
// 7. DIAGNOSTICS TAB
// ==============================================================================
@Composable
fun AdminDiagnosticsTab(
    viewModel: SocialViewModel,
    diagnostics: AttributionDiagnostics
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "Attribution Telemetry & Diagnostics", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = "Real-time verification metrics and offline attribution queue stats.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminMetricCard(
                    title = "Total Events",
                    value = "${diagnostics.totalEvents}",
                    subtitle = "Logged impressions",
                    color = Color(0xFF0984E3),
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Valid Verified",
                    value = "${diagnostics.validEvents}",
                    subtitle = "Attributed to pool",
                    color = Color(0xFF00B894),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminMetricCard(
                    title = "Throttled / Dups",
                    value = "${diagnostics.duplicateEvents}",
                    subtitle = "Deduplicated safe",
                    color = Color(0xFFF39C12),
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Rejected Events",
                    value = "${diagnostics.rejectedEvents}",
                    subtitle = "Invalid telemetry",
                    color = Color(0xFFD63031),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Diagnostics Health Check", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "• AdAttributionManager is active and processing callbacks on IO threads.", fontSize = 12.sp)
                    Text(text = "• Client deduplication active (5-second window).", fontSize = 12.sp)
                    Text(text = "• Offline event queue persistence ready.", fontSize = 12.sp)

                    Button(
                        onClick = {
                            viewModel.adAttributionManager.flushOfflineQueue()
                            Toast.makeText(context, "Offline queue flushed successfully.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Flush Offline Attribution Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 8. APPLICATIONS TAB
// ==============================================================================
@Composable
fun AdminApplicationsTab(
    viewModel: SocialViewModel,
    applications: List<MonetizationApplication>
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("PENDING") }

    val filteredApps = remember(applications, selectedFilter) {
        if (selectedFilter == "ALL") applications else applications.filter { it.status == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PENDING", "APPROVED", "REJECTED", "ALL").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }
        }

        if (filteredApps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No $selectedFilter applications.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredApps) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VynAvatar(avatarType = app.userAvatarType, size = 42.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = app.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = "@${app.userHandle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (app.status) {
                                    "APPROVED" -> Color(0xFF00B894).copy(alpha = 0.15f)
                                    "REJECTED" -> Color(0xFFD63031).copy(alpha = 0.15f)
                                    else -> Color(0xFFF39C12).copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = app.status,
                                    color = when (app.status) {
                                        "APPROVED" -> Color(0xFF00B894)
                                        "REJECTED" -> Color(0xFFD63031)
                                        else -> Color(0xFFF39C12)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Followers: ${app.followerCountAtApplication}", fontSize = 12.sp)
                            Text(text = "Views: ${app.viewCountAtApplication}", fontSize = 12.sp)
                        }

                        if (app.status == "PENDING") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.approveMonetizationApplication(app.id) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Approve")
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.rejectMonetizationApplication(app.id, "Followers or views guideline threshold not met.") { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 9. WALLET TAB
// ==============================================================================
@Composable
fun AdminWalletTab(
    viewModel: SocialViewModel,
    wallet: EarningsWallet
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "Creator Earnings Wallet System", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = "Real money payouts and ledger monitoring.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "LEDGER BALANCE (CURRENT CREATOR)", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = "$${String.format(Locale.US, "%.2f", wallet.availableBalance)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "Lifetime Earnings", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            Text(text = "$${String.format(Locale.US, "%.2f", wallet.lifetimeEarnings)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(text = "Total Added Funds", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            Text(text = "$${String.format(Locale.US, "%.2f", wallet.totalAddedFunds)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Total Withdrawn", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            Text(text = "$${String.format(Locale.US, "%.2f", wallet.totalWithdrawn)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 10. TRANSACTIONS TAB
// ==============================================================================
@Composable
fun AdminTransactionsTab(
    viewModel: SocialViewModel,
    transactions: List<MonetizationTransaction>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }
    var txnToApprove by remember { mutableStateOf<MonetizationTransaction?>(null) }
    var txnToReject by remember { mutableStateOf<MonetizationTransaction?>(null) }
    var approvalPayoutRef by remember { mutableStateOf("") }
    var rejectReason by remember { mutableStateOf("Invalid account number / Name mismatch") }

    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            "PENDING_WITHDRAWALS" -> transactions.filter { it.type == "WITHDRAWAL" && it.status == "PENDING" }
            "WITHDRAWALS" -> transactions.filter { it.type == "WITHDRAWAL" }
            "DEPOSITS" -> transactions.filter { it.type == "ADD_FUND" }
            "EARNINGS" -> transactions.filter { it.type == "CREATOR_EARNING" }
            else -> transactions
        }
    }

    val pendingWithdrawalsCount = transactions.count { it.type == "WITHDRAWAL" && it.status == "PENDING" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Monetization Financial Audit Log", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(text = "Real ledger transaction entries with unique idempotency reference IDs and instant payout approval.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${transactions.size})", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = selectedFilter == "PENDING_WITHDRAWALS",
                    onClick = { selectedFilter = "PENDING_WITHDRAWALS" },
                    label = {
                        Text(
                            text = "Pending ($pendingWithdrawalsCount)",
                            fontSize = 11.sp,
                            fontWeight = if (pendingWithdrawalsCount > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (pendingWithdrawalsCount > 0) Color(0xFFE17055) else Color.Unspecified
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = selectedFilter == "DEPOSITS",
                    onClick = { selectedFilter = "DEPOSITS" },
                    label = { Text("Deposits", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = selectedFilter == "EARNINGS",
                    onClick = { selectedFilter = "EARNINGS" },
                    label = { Text("Earnings", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No matching transactions found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredTransactions) { txn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (txn.type) {
                                            "CREATOR_EARNING" -> Color(0xFF00B894).copy(alpha = 0.15f)
                                            "ADD_FUND" -> Color(0xFF0984E3).copy(alpha = 0.15f)
                                            "WITHDRAWAL" -> Color(0xFFE17055).copy(alpha = 0.15f)
                                            "ADJUSTMENT" -> Color(0xFF6C5CE7).copy(alpha = 0.15f)
                                            "REFUND" -> Color(0xFFFD79A8).copy(alpha = 0.15f)
                                            else -> Color.Gray.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = txn.type,
                                            color = when (txn.type) {
                                                "CREATOR_EARNING" -> Color(0xFF00B894)
                                                "ADD_FUND" -> Color(0xFF0984E3)
                                                "WITHDRAWAL" -> Color(0xFFE17055)
                                                "ADJUSTMENT" -> Color(0xFF6C5CE7)
                                                "REFUND" -> Color(0xFFFD79A8)
                                                else -> Color.Gray
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "@${txn.userHandle}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = txn.description.ifBlank { "Ref: ${txn.referenceId}" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = (if (txn.type == "WITHDRAWAL") "-" else "+") + "$${String.format(Locale.US, "%.2f", txn.amount)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = if (txn.type == "WITHDRAWAL") Color(0xFFE17055) else Color(0xFF00B894)
                                )
                                Text(
                                    text = txn.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (txn.status) {
                                        "COMPLETED" -> Color(0xFF00B894)
                                        "PENDING" -> Color(0xFFE17055)
                                        "REJECTED" -> Color(0xFFD63031)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        // If pending withdrawal, provide quick admin actions
                        if (txn.type == "WITHDRAWAL" && txn.status == "PENDING") {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        approvalPayoutRef = "DISB-${System.currentTimeMillis().toString().takeLast(6)}"
                                        txnToApprove = txn
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve Payout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        rejectReason = "Account information unverified"
                                        txnToReject = txn
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD63031)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reject & Refund", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Approval Dialog
    txnToApprove?.let { txn ->
        AlertDialog(
            onDismissRequest = { txnToApprove = null },
            title = { Text("Approve Withdrawal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("User: @${txn.userHandle}", fontWeight = FontWeight.Bold)
                    Text("Amount: $${String.format(Locale.US, "%.2f", txn.amount)} USD (≈ ৳${(txn.amount * 120).toInt()} BDT)")
                    Text("Account: ${txn.description}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = approvalPayoutRef,
                        onValueChange = { approvalPayoutRef = it },
                        label = { Text("Disbursement / Bank Reference ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveMonetizationWithdrawal(txn.id, approvalPayoutRef) { success, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            if (success) txnToApprove = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894))
                ) {
                    Text("Confirm Payout")
                }
            },
            dismissButton = {
                TextButton(onClick = { txnToApprove = null }) { Text("Cancel") }
            }
        )
    }

    // Reject Dialog
    txnToReject?.let { txn ->
        AlertDialog(
            onDismissRequest = { txnToReject = null },
            title = { Text("Reject & Refund Withdrawal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to reject @${txn.userHandle}'s withdrawal of $${String.format(Locale.US, "%.2f", txn.amount)}?")
                    Text("The amount will be automatically refunded back to the creator's wallet balance.", fontSize = 12.sp, color = Color(0xFF00B894))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Rejection Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectMonetizationWithdrawal(txn.id, rejectReason) { success, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            if (success) txnToReject = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD63031))
                ) {
                    Text("Reject & Refund")
                }
            },
            dismissButton = {
                TextButton(onClick = { txnToReject = null }) { Text("Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS
// -------------------------------------------------------------
@Composable
fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AdminTileButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF6C5CE7).copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
