package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
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
import com.example.ui.components.FlareImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class MonetizationPage {
    MAIN,
    APPLY,
    CONTENT_MONETIZATION,
    REELS_MONETIZATION,
    CONTENT_REVENUE_ANALYTICS,
    EARNINGS_WALLET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonetizationScreen(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(MonetizationPage.MAIN) }

    val settings by viewModel.monetizationSettings.collectAsState()
    val userProfile by viewModel.userMonetizationProfile.collectAsState()
    val wallet by viewModel.earningsWallet.collectAsState()
    val transactions by viewModel.monetizationTransactions.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val contentEarnings by viewModel.contentEarnings.collectAsState()

    val totalUserViews = remember(profile.postsCount, profile.followersCount) {
        (profile.postsCount * 2500) + (profile.followersCount * 120) + 1500
    }

    BackHandler {
        if (currentPage != MonetizationPage.MAIN) {
            currentPage = MonetizationPage.MAIN
        } else {
            onBack()
        }
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            if (targetState == MonetizationPage.MAIN) {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            }
        },
        label = "monetization_page_anim"
    ) { page ->
        when (page) {
            MonetizationPage.MAIN -> {
                UserMonetizationMainPage(
                    viewModel = viewModel,
                    settings = settings,
                    userProfile = userProfile,
                    wallet = wallet,
                    contentEarnings = contentEarnings,
                    onBack = onBack,
                    onNavigate = { currentPage = it }
                )
            }
            MonetizationPage.APPLY -> {
                MonetizationApplyPage(
                    viewModel = viewModel,
                    settings = settings,
                    userProfile = userProfile,
                    followers = profile.followersCount,
                    views = totalUserViews,
                    onBack = { currentPage = MonetizationPage.MAIN }
                )
            }
            MonetizationPage.CONTENT_MONETIZATION -> {
                ContentMonetizationPage(
                    settings = settings,
                    userProfile = userProfile,
                    onBack = { currentPage = MonetizationPage.MAIN },
                    onNavigateApply = { currentPage = MonetizationPage.APPLY },
                    onNavigateAnalytics = { currentPage = MonetizationPage.CONTENT_REVENUE_ANALYTICS }
                )
            }
            MonetizationPage.REELS_MONETIZATION -> {
                ReelsMonetizationPage(
                    settings = settings,
                    userProfile = userProfile,
                    onBack = { currentPage = MonetizationPage.MAIN },
                    onNavigateApply = { currentPage = MonetizationPage.APPLY },
                    onNavigateAnalytics = { currentPage = MonetizationPage.CONTENT_REVENUE_ANALYTICS }
                )
            }
            MonetizationPage.CONTENT_REVENUE_ANALYTICS -> {
                ContentRevenueAnalyticsPage(
                    viewModel = viewModel,
                    userHandle = userProfile.userHandle,
                    onBack = { currentPage = MonetizationPage.MAIN }
                )
            }
            MonetizationPage.EARNINGS_WALLET -> {
                EarningsWalletPage(
                    viewModel = viewModel,
                    settings = settings,
                    wallet = wallet,
                    transactions = transactions,
                    onBack = { currentPage = MonetizationPage.MAIN }
                )
            }
        }
    }
}

// ==============================================================================
// 1. USER MONETIZATION MAIN PAGE
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMonetizationMainPage(
    viewModel: SocialViewModel,
    settings: MonetizationSettings,
    userProfile: UserMonetizationProfile,
    wallet: EarningsWallet,
    contentEarnings: List<ContentEarning>,
    onBack: () -> Unit,
    onNavigate: (MonetizationPage) -> Unit
) {
    val myEarnings = remember(contentEarnings, userProfile.userHandle) {
        viewModel.getUserContentEarnings(userProfile.userHandle)
    }

    val estimatedTotal = myEarnings.filter { it.status == "ESTIMATED" }.sumOf { it.creatorShare }
    val finalizedTotal = myEarnings.filter { it.status == "FINALIZED" || it.status == "ADJUSTED" || it.status == "PAID" }.sumOf { it.creatorShare }

    val postEarn = myEarnings.filter { it.contentType == "POST" }.sumOf { it.creatorShare }
    val videoEarn = myEarnings.filter { it.contentType == "VIDEO" }.sumOf { it.creatorShare }
    val reelsEarn = myEarnings.filter { it.contentType == "REEL" }.sumOf { it.creatorShare }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Monetization",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6C5CE7).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "CREATOR",
                                color = Color(0xFF6C5CE7),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("monetization_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Earnings Balance Card (Real database authoritative metrics)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Available Balance",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", wallet.availableBalance)}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00B894)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Lifetime Earnings",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", wallet.lifetimeEarnings)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Estimated vs Finalized Revenue Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Estimated This Cycle", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", estimatedTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF39C12))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Finalized Payouts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$${String.format(Locale.US, "%.2f", finalizedTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(16.dp))
                                Text(
                                    text = when (userProfile.monetizationStatus) {
                                        "APPROVED" -> "Monetization Active ✓"
                                        "PENDING" -> "Status: Pending Review ⏳"
                                        "REJECTED" -> "Status: Application Rejected"
                                        else -> "Status: Not Monetized"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (userProfile.monetizationStatus) {
                                        "APPROVED" -> Color(0xFF00B894)
                                        "PENDING" -> Color(0xFFF39C12)
                                        "REJECTED" -> Color(0xFFD63031)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            TextButton(
                                onClick = { onNavigate(MonetizationPage.EARNINGS_WALLET) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Wallet Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C5CE7))
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF6C5CE7))
                            }
                        }
                    }
                }
            }

            // Content Format Breakdown Cards
            item {
                Text(
                    text = "Content Revenue Breakdown",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContentFormatStatCard(
                        title = "Posts",
                        amount = "$${String.format(Locale.US, "%.2f", postEarn)}",
                        icon = Icons.AutoMirrored.Filled.Article,
                        color = Color(0xFF0984E3),
                        modifier = Modifier.weight(1f)
                    )
                    ContentFormatStatCard(
                        title = "Videos",
                        amount = "$${String.format(Locale.US, "%.2f", videoEarn)}",
                        icon = Icons.Default.VideoLibrary,
                        color = Color(0xFF6C5CE7),
                        modifier = Modifier.weight(1f)
                    )
                    ContentFormatStatCard(
                        title = "Reels",
                        amount = "$${String.format(Locale.US, "%.2f", reelsEarn)}",
                        icon = Icons.Default.PlayCircle,
                        color = Color(0xFFE84393),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Featured Analytics Button
            item {
                Button(
                    onClick = { onNavigate(MonetizationPage.CONTENT_REVENUE_ANALYTICS) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(imageVector = Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Per-Content Revenue Analytics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Programs Navigation
            item {
                Text(
                    text = "Monetization Programs",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 1. Monetization Apply
            item {
                MonetizationNavCard(
                    title = "1. Monetization Apply",
                    subtitle = when (userProfile.monetizationStatus) {
                        "APPROVED" -> "Monetization Approved · Account Eligible"
                        "PENDING" -> "Application submitted & under review"
                        "REJECTED" -> "Review completed · Check feedback"
                        else -> "Check criteria (Followers & Views) & apply"
                    },
                    icon = Icons.Outlined.AssignmentTurnedIn,
                    statusText = when (userProfile.monetizationStatus) {
                        "APPROVED" -> "Approved ✓"
                        "PENDING" -> "Pending ⏳"
                        "REJECTED" -> "Rejected"
                        else -> "Apply Now"
                    },
                    statusColor = when (userProfile.monetizationStatus) {
                        "APPROVED" -> Color(0xFF00B894)
                        "PENDING" -> Color(0xFFF39C12)
                        "REJECTED" -> Color(0xFFD63031)
                        else -> Color(0xFF6C5CE7)
                    },
                    onClick = { onNavigate(MonetizationPage.APPLY) }
                )
            }

            // 2. Content Monetization
            item {
                MonetizationNavCard(
                    title = "2. Content Monetization",
                    subtitle = "Earn AdMob revenue on image & text posts",
                    icon = Icons.AutoMirrored.Outlined.Article,
                    statusText = if (settings.enablePostMonetization) "Active" else "Disabled",
                    statusColor = if (settings.enablePostMonetization) Color(0xFF00B894) else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onNavigate(MonetizationPage.CONTENT_MONETIZATION) }
                )
            }

            // 3. Reels Monetization
            item {
                MonetizationNavCard(
                    title = "3. Reels Monetization",
                    subtitle = "Short video ad revenue attribution",
                    icon = Icons.Outlined.SlowMotionVideo,
                    statusText = if (settings.enableReelsMonetization) "Active" else "Disabled",
                    statusColor = if (settings.enableReelsMonetization) Color(0xFF00B894) else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onNavigate(MonetizationPage.REELS_MONETIZATION) }
                )
            }

            // 4. Earnings Wallet
            item {
                MonetizationNavCard(
                    title = "4. Earnings Wallet",
                    subtitle = "Real-money balance, deposits & withdrawal requests",
                    icon = Icons.Outlined.AccountBalanceWallet,
                    statusText = "$${String.format(Locale.US, "%.2f", wallet.availableBalance)}",
                    statusColor = Color(0xFF00B894),
                    onClick = { onNavigate(MonetizationPage.EARNINGS_WALLET) }
                )
            }
        }
    }
}

// ==============================================================================
// 2. CONTENT REVENUE ATTRIBUTION ANALYTICS PAGE (PHASE 2)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentRevenueAnalyticsPage(
    viewModel: SocialViewModel,
    userHandle: String,
    onBack: () -> Unit
) {
    val contentEarnings by viewModel.contentEarnings.collectAsState()
    val myEarnings = remember(contentEarnings, userHandle) {
        viewModel.getUserContentEarnings(userHandle)
    }

    var selectedTab by remember { mutableStateOf("ALL") }
    var selectedEarningDetail by remember { mutableStateOf<ContentEarning?>(null) }

    val filteredList = remember(myEarnings, selectedTab) {
        if (selectedTab == "ALL") myEarnings else myEarnings.filter { it.contentType == selectedTab }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Content Revenue Analytics", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF6C5CE7).copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFF6C5CE7))
                        Text(
                            text = "Revenue is calculated authoritatively per cycle from AdMob revenue pools based on verified valid ad impressions on your content.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL", "POST", "VIDEO", "REEL").forEach { type ->
                        FilterChip(
                            selected = selectedTab == type,
                            onClick = { selectedTab = type },
                            label = { Text(if (type == "ALL") "All Content" else "${type}s") }
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
                            Text("No content revenue attributions found for this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEarningDetail = item },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (item.status) {
                                        "FINALIZED" -> Color(0xFF00B894).copy(alpha = 0.15f)
                                        "ADJUSTED" -> Color(0xFF6C5CE7).copy(alpha = 0.15f)
                                        else -> Color(0xFFF39C12).copy(alpha = 0.15f)
                                    }
                                ) {
                                    Text(
                                        text = item.status,
                                        color = when (item.status) {
                                            "FINALIZED" -> Color(0xFF00B894)
                                            "ADJUSTED" -> Color(0xFF6C5CE7)
                                            else -> Color(0xFFF39C12)
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = item.contentTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "Total Views", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "${item.contentViews}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Column {
                                    Text(text = "Ad Impressions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "${item.eligibleImpressions}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Column {
                                    Text(text = "Pool Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "${String.format(Locale.US, "%.2f", item.attributionShare * 100)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0984E3))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "Your Earning", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "$${String.format(Locale.US, "%.2f", item.creatorShare)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF00B894))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedEarningDetail != null) {
        val earn = selectedEarningDetail!!
        AlertDialog(
            onDismissRequest = { selectedEarningDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = Color(0xFF6C5CE7))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attribution Breakdown", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = earn.contentTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "Format: ${earn.contentType} • ID: ${earn.contentId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Eligible Ad Impressions:", fontSize = 12.sp)
                        Text(text = "${earn.eligibleImpressions}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Attribution Pool Share:", fontSize = 12.sp)
                        Text(text = "${String.format(Locale.US, "%.4f", earn.attributionShare * 100)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0984E3))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Calculated Creator Net:", fontSize = 12.sp)
                        Text(text = "$${String.format(Locale.US, "%.4f", earn.creatorShare)} ${earn.currency}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF00B894))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Attribution Status:", fontSize = 12.sp)
                        Text(text = earn.status, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        text = "Formula: (Content Impressions / Total Format Impressions) × Content Creator Pool",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedEarningDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                ) {
                    Text("Close")
                }
            }
        )
    }
}

// ==============================================================================
// 3. MONETIZATION APPLY PAGE
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonetizationApplyPage(
    viewModel: SocialViewModel,
    settings: MonetizationSettings,
    userProfile: UserMonetizationProfile,
    followers: Int,
    views: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isSubmitting by remember { mutableStateOf(false) }

    val hasEnoughFollowers = followers >= settings.minimumFollowers
    val hasEnoughViews = views >= settings.minimumViews
    val isEligible = hasEnoughFollowers && hasEnoughViews

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Monetization Application", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Eligibility Criteria",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Text(
                    text = "To maintain high content quality, creators must meet the following baseline thresholds.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Criteria 1: Followers
            item {
                CriteriaCard(
                    title = "Followers Requirement",
                    current = followers,
                    required = settings.minimumFollowers,
                    unit = "Followers",
                    isMet = hasEnoughFollowers
                )
            }

            // Criteria 2: Views
            item {
                CriteriaCard(
                    title = "Total Views Requirement",
                    current = views,
                    required = settings.minimumViews,
                    unit = "Views",
                    isMet = hasEnoughViews
                )
            }

            // Application Status Message Card
            item {
                when (userProfile.monetizationStatus) {
                    "APPROVED" -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF00B894).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Congratulations! 🎉", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF00B894))
                                Text("Your account is approved for FlareOfficial Native Monetization. Ad impressions on your posts, videos, and reels are attributed to your Earnings Wallet.", fontSize = 12.sp)
                            }
                        }
                    }
                    "PENDING" -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF39C12).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Application Under Review ⏳", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFF39C12))
                                Text("Your application has been received and is being verified by the moderation team.", fontSize = 12.sp)
                            }
                        }
                    }
                    "REJECTED" -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFD63031).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Application Rejected", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFD63031))
                                Text(userProfile.rejectionReason.ifBlank { "Please continue posting quality content and reapply." }, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Submit Button
            if (userProfile.monetizationStatus != "APPROVED" && userProfile.monetizationStatus != "PENDING") {
                item {
                    Button(
                        onClick = {
                            isSubmitting = true
                            viewModel.submitMonetizationApplication { success, message ->
                                isSubmitting = false
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = isEligible && !isSubmitting && settings.enableMonetizationApplication,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text(
                                text = if (isEligible) "Submit Monetization Application" else "Criteria Not Met Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 4. CONTENT & REELS MONETIZATION PAGES
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentMonetizationPage(
    settings: MonetizationSettings,
    userProfile: UserMonetizationProfile,
    onBack: () -> Unit,
    onNavigateApply: () -> Unit,
    onNavigateAnalytics: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Content Monetization", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0984E3).copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Post & Article Earnings", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0984E3))
                        Text(
                            "Earn revenue from Native Ad placements seamlessly displayed inside your feed posts. Revenue is calculated dynamically per cycle based on verified ad impressions.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onNavigateAnalytics,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View Posts Revenue Breakdown", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsMonetizationPage(
    settings: MonetizationSettings,
    userProfile: UserMonetizationProfile,
    onBack: () -> Unit,
    onNavigateApply: () -> Unit,
    onNavigateAnalytics: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reels Monetization", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE84393).copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Short Video & Reels Revenue", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFE84393))
                        Text(
                            "Monetize vertical short videos with AdMob Interstitial and Banner overlays. The ${settings.reelsRevenuePoolShare.toInt()}% Reels Pool is distributed among creators based on verified impressions.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onNavigateAnalytics,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE84393)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View Reels Revenue Breakdown", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==============================================================================
// 5. EARNINGS WALLET PAGE
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsWalletPage(
    viewModel: SocialViewModel,
    settings: MonetizationSettings,
    wallet: EarningsWallet,
    transactions: List<MonetizationTransaction>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showAddFundDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Earnings Wallet", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "AVAILABLE TO WITHDRAW", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "$${String.format(Locale.US, "%.2f", wallet.availableBalance)}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)

                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "Lifetime Earnings", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                Text(text = "$${String.format(Locale.US, "%.2f", wallet.lifetimeEarnings)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Total Withdrawn", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                Text(text = "$${String.format(Locale.US, "%.2f", wallet.totalWithdrawn)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Actions (Add Fund & Withdraw)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showAddFundDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0984E3)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Funds", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showWithdrawDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Withdraw", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Transactions History
            item {
                Text("Transaction Ledger", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (transactions.isEmpty()) {
                item {
                    Text("No transactions yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(transactions) { txn ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.description.ifBlank { txn.type },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "Ref: ${txn.referenceId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = (if (txn.type == "WITHDRAWAL") "-" else "+") + "$${String.format(Locale.US, "%.2f", txn.amount)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (txn.type == "WITHDRAWAL") Color(0xFFD63031) else Color(0xFF00B894)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddFundDialog) {
        com.example.ui.components.PaymentGatewayDialog(
            initialAmountBdt = 500.0,
            title = "Deposit Funds to Wallet",
            description = "Official bKash, Nagad, SSLCommerz, Stripe & Crypto Gateways",
            onDismiss = { showAddFundDialog = false },
            onPaymentSuccess = { usdAmount, bdtAmount, gateway, trxId ->
                viewModel.addMonetizationFunds(
                    amount = usdAmount,
                    gateway = gateway.displayName,
                    trxId = trxId
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showWithdrawDialog) {
        com.example.ui.components.CreatorWithdrawDialog(
            availableBalanceUsd = wallet.availableBalance,
            minimumWithdrawalUsd = settings.minimumWithdrawal,
            onDismiss = { showWithdrawDialog = false },
            onWithdrawSubmit = { amount, method, accountDetails ->
                viewModel.requestMonetizationWithdrawal(amount, method, accountDetails) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showWithdrawDialog = false
                }
            }
        )
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS
// -------------------------------------------------------------
@Composable
fun ContentFormatStatCard(
    title: String,
    amount: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = amount, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
fun MonetizationNavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    statusText: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CriteriaCard(
    title: String,
    current: Int,
    required: Int,
    unit: String,
    isMet: Boolean
) {
    val progress = (current.toFloat() / required.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = if (isMet) "Completed ✓" else "$current / $required $unit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isMet) Color(0xFF00B894) else Color(0xFFF39C12)
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (isMet) Color(0xFF00B894) else Color(0xFF6C5CE7),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
