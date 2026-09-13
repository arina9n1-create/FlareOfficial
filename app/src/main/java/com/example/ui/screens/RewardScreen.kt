package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import java.util.Locale

enum class RewardSubTab {
    TASKS,
    REFERRAL,
    WITHDRAW,
    HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardScreen(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wallet by viewModel.rewardWallet.collectAsState()
    val adminConfig by viewModel.adminConfig.collectAsState()
    val tasks by viewModel.rewardTasks.collectAsState()
    val referrals by viewModel.referrals.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()
    val authState by viewModel.userAuthState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val currency by viewModel.selectedCurrency.collectAsState()

    var selectedTab by remember { mutableStateOf(RewardSubTab.TASKS) }
    val usdEquivalent = remember(wallet.totalCredits, adminConfig.creditsPerDollar) {
        String.format(Locale.US, "%.2f", wallet.totalCredits.toDouble() / adminConfig.creditsPerDollar)
    }
    val bdtEquivalent = remember(wallet.totalCredits, adminConfig.creditsPerDollar, adminConfig.usdToBdtRate) {
        String.format(Locale.US, "%.2f", (wallet.totalCredits.toDouble() / adminConfig.creditsPerDollar) * adminConfig.usdToBdtRate)
    }
    val primaryBalanceFormatted = remember(currency, usdEquivalent, bdtEquivalent) {
        if (currency == com.example.data.model.AppCurrency.BDT) "৳ $bdtEquivalent BDT" else "$ $usdEquivalent USD"
    }
    val secondaryBalanceFormatted = remember(currency, usdEquivalent, bdtEquivalent) {
        if (currency == com.example.data.model.AppCurrency.BDT) "$ $usdEquivalent USD" else "৳ $bdtEquivalent BDT"
    }

    BackHandler {
        if (selectedTab != RewardSubTab.TASKS) {
            selectedTab = RewardSubTab.TASKS
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Rewards & Earnings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Complete tasks, watch reels & withdraw real cash",
                            fontSize = 11.sp,
                            color = FlareTextSecondary
                        )
                    }
                },
                actions = {
                    // Currency Switch Action
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable {
                                val next = viewModel.toggleCurrency()
                                Toast.makeText(context, "Currency switched to ${next.displayName}", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF6C5CE7).copy(alpha = 0.12f),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "💱 ${currency.code}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF6C5CE7)
                            )
                            Text(
                                text = "(${currency.symbol})",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color(0xFFE84393)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reward_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Balance Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6C5CE7),
                                    Color(0xFF8E44AD),
                                    Color(0xFFE84393)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Available Balance",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${wallet.totalCredits}",
                                        color = Color.White,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Credits",
                                        color = Color(0xFFFFEAA7),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Dynamic Currency Switch Pill Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.clickable {
                                    val next = viewModel.toggleCurrency()
                                    Toast.makeText(context, "Currency set to ${next.displayName}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "≈ $primaryBalanceFormatted",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Icon(
                                            Icons.Default.SyncAlt,
                                            contentDescription = "Switch currency",
                                            tint = Color(0xFFFFEAA7),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = "($secondaryBalanceFormatted)",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "1 USD = ৳${adminConfig.usdToBdtRate.toInt()} BDT",
                                        color = Color(0xFFFFEAA7),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { selectedTab = RewardSubTab.HISTORY },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF6C5CE7)
                                ),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("History", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { selectedTab = RewardSubTab.REFERRAL },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = Brush.horizontalGradient(listOf(Color.White, Color.White))
                                ),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Invite +${adminConfig.referralBonusCredits}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Tabs Selector (Tasks, Refer, Wallet)
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Color(0xFF6C5CE7)
            ) {
                Tab(
                    selected = selectedTab == RewardSubTab.TASKS,
                    onClick = { selectedTab = RewardSubTab.TASKS },
                    text = { Text("Daily Task", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == RewardSubTab.REFERRAL,
                    onClick = { selectedTab = RewardSubTab.REFERRAL },
                    text = { Text("Refer & Earn", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == RewardSubTab.WITHDRAW,
                    onClick = { selectedTab = RewardSubTab.WITHDRAW },
                    text = { Text("Withdraw", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == RewardSubTab.HISTORY,
                    onClick = { selectedTab = RewardSubTab.HISTORY },
                    text = { Text("History", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            // Tab Content
            when (selectedTab) {
                RewardSubTab.TASKS -> TasksTabContent(viewModel, tasks, wallet, adminConfig)
                RewardSubTab.REFERRAL -> ReferralTabContent(viewModel, wallet, adminConfig, referrals, context)
                RewardSubTab.WITHDRAW -> WithdrawTabContent(viewModel, wallet, adminConfig, withdrawals, authState.email.ifBlank { profile.handle }, context)
                RewardSubTab.HISTORY -> HistoryTabContent(viewModel, wallet, adminConfig, withdrawals, referrals, authState.email.ifBlank { profile.handle }, context)
            }
        }
    }
}

@Composable
fun TasksTabContent(
    viewModel: SocialViewModel,
    tasks: List<RewardTask>,
    wallet: com.example.data.model.UserRewardWallet,
    adminConfig: com.example.data.model.AdminConfig
) {
    val context = LocalContext.current
    val masterTask = tasks.find { it.id == "daily_reels_master" }
    val isMasterClaimed = masterTask?.isClaimed == true
    val isWatchDone = wallet.todayReelsWatched >= adminConfig.dailyTaskRequiredWatchReels
    val isUploadDone = wallet.todayReelsUploaded >= adminConfig.dailyTaskRequiredUploadReels
    val isAllDone = isWatchDone && isUploadDone

    val watchProgress = (wallet.todayReelsWatched.toFloat() / adminConfig.dailyTaskRequiredWatchReels.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val uploadProgress = (wallet.todayReelsUploaded.toFloat() / adminConfig.dailyTaskRequiredUploadReels.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Daily Quest Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = FlareOffWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🎯 Daily Reels Quest",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Watch ${adminConfig.dailyTaskRequiredWatchReels} reels + Upload ${adminConfig.dailyTaskRequiredUploadReels} reels today",
                                fontSize = 12.sp,
                                color = FlareTextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF6C5CE7).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "+${adminConfig.dailyTaskRewardCredits} Credits",
                                color = Color(0xFF6C5CE7),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 1: Watch Reels Progress
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(18.dp))
                                Text("1. Watch Reels", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "${wallet.todayReelsWatched} / ${adminConfig.dailyTaskRequiredWatchReels} Reels",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isWatchDone) Color(0xFF27AE60) else Color(0xFF6C5CE7)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { watchProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isWatchDone) Color(0xFF27AE60) else Color(0xFF6C5CE7),
                            trackColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step 2: Upload Reels Progress
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color(0xFFE84393), modifier = Modifier.size(18.dp))
                                Text("2. Upload Reels", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "${wallet.todayReelsUploaded} / ${adminConfig.dailyTaskRequiredUploadReels} Uploaded",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isUploadDone) Color(0xFF27AE60) else Color(0xFFE84393)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isUploadDone) Color(0xFF27AE60) else Color(0xFFE84393),
                            trackColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Claim Button
                    if (isMasterClaimed) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF27AE60).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF27AE60), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Claimed Today! +${adminConfig.dailyTaskRewardCredits} Credits Received ✓",
                                    color = Color(0xFF27AE60),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (isAllDone) {
                        Button(
                            onClick = {
                                val rewarded = viewModel.claimReward("daily_reels_master")
                                if (rewarded > 0) {
                                    Toast.makeText(context, "🎉 Claimed +$rewarded Credits for completing today's Daily Task!", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Claim +${adminConfig.dailyTaskRewardCredits} Credits Reward 🎉", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF6C5CE7).copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⏳ In Progress: Watch ${adminConfig.dailyTaskRequiredWatchReels - wallet.todayReelsWatched.coerceAtMost(adminConfig.dailyTaskRequiredWatchReels)} more reel(s) & upload ${adminConfig.dailyTaskRequiredUploadReels - wallet.todayReelsUploaded.coerceAtMost(adminConfig.dailyTaskRequiredUploadReels)} more reel(s) to unlock claim.",
                                fontSize = 12.sp,
                                color = Color(0xFF6C5CE7),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Buttons
        item {
            Text("⚡ Quick Actions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.watchReelEarnCredit(10)
                        Toast.makeText(context, "Watched Reel (+${adminConfig.creditsPerReel} pts)! 🎬", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch Reel (+1)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.recordReelUploaded()
                        Toast.makeText(context, "Reel Upload Count Recorded! 📤", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload Reel (+1)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Rule Explanation Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💡 Daily Quest Rules & Referral Linkage", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "• Daily Task resets every 24 hours at midnight.\n" +
                        "• Complete both watching and uploading to claim +${adminConfig.dailyTaskRewardCredits} Credits.\n" +
                        "• When someone joins using your referral code, their referral becomes SUCCESS after they complete their Daily Task for ${adminConfig.referralRequiredDailyTaskDays} days!",
                        fontSize = 12.sp,
                        color = FlareTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ReferralTabContent(
    viewModel: SocialViewModel,
    wallet: com.example.data.model.UserRewardWallet,
    adminConfig: com.example.data.model.AdminConfig,
    referrals: List<com.example.data.model.ReferralRecord>,
    context: Context
) {
    var inputRefCode by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val shareText = "🎉 Join FlareOfficial social app & start earning real money by watching and uploading reels! Use my invite code: ${wallet.referralCode} to sign up: https://flareofficial.app/join?ref=${wallet.referralCode}"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Viral Refer Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = FlareOffWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Invite Friends & Earn Real Money 🚀",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Earn +${adminConfig.referralBonusCredits} Credits for every friend who joins & completes Daily Tasks for ${adminConfig.referralRequiredDailyTaskDays} days!",
                        fontSize = 13.sp,
                        color = FlareTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )

                    // Referral Code Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF6C5CE7).copy(alpha = 0.08f),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), Color(0xFF8E44AD)))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("YOUR REFERRAL CODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
                                Text(
                                    text = wallet.referralCode,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF6C5CE7),
                                    letterSpacing = 2.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("FlareOfficial Referral Code", wallet.referralCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Referral code copied!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Social Share Button
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val chooser = Intent.createChooser(intent, "Share invite via").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open share dialog", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Referral Link", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Referral How It Works Explanation
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF6C5CE7).copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ℹ️ How Referral Verification Works", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF6C5CE7))
                    Text(
                        "1. Share your code with your friend.\n" +
                        "2. When your friend activates the code, +${adminConfig.referralBonusCredits} Credits is marked as PENDING.\n" +
                        "3. When your friend completes their Daily Task for ${adminConfig.referralRequiredDailyTaskDays} days, the referral is SUCCESS.\n" +
                        "4. You can then Claim your +${adminConfig.referralBonusCredits} Credits reward directly!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Referral Stats Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = FlareOffWhite
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pending / Claimable", fontSize = 12.sp, color = FlareTextSecondary)
                        Text(
                            text = "${referrals.count { it.status != "CLAIMED" }} Friends",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = FlareOffWhite
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Claimed Earnings", fontSize = 12.sp, color = FlareTextSecondary)
                        Text(
                            text = "+${wallet.referralEarnings} pts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF27AE60)
                        )
                    }
                }
            }
        }

        // Enter Referral Code Section (For referee)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Have a Friend's Referral Code?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Enter and activate their code below.", fontSize = 12.sp, color = FlareTextSecondary)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputRefCode,
                            onValueChange = { inputRefCode = it.uppercase() },
                            placeholder = { Text("e.g. FLAREOFFICIALWIN") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (inputRefCode.isNotBlank()) {
                                    viewModel.applyReferralCode(inputRefCode) { success, msg ->
                                        statusMsg = msg
                                        if (success) {
                                            inputRefCode = ""
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Apply")
                        }
                    }

                    statusMsg?.let {
                        Text(text = it, fontSize = 12.sp, color = Color(0xFF6C5CE7), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        // Referral Activity & Claim List
        item {
            Text("Referred Friends & Rewards (${referrals.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (referrals.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FlareOffWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No referred friends yet. Share your invite code above to get started!",
                        fontSize = 12.sp,
                        color = FlareTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(referrals) { ref ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlareOffWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6C5CE7).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("@${ref.refereeHandle}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "Daily Tasks: ${ref.dailyTasksCompleted} / ${ref.requiredDays} Days",
                                        fontSize = 11.sp,
                                        color = FlareTextSecondary
                                    )
                                }
                            }

                            // Status Tag / Claim Button
                            when (ref.status) {
                                "COMPLETED" -> {
                                    Button(
                                        onClick = {
                                            val claimed = viewModel.claimReferralReward(ref.id)
                                            if (claimed > 0) {
                                                Toast.makeText(context, "🎉 Claimed +$claimed Credits for referral!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Claim +${ref.bonusCredits}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                "CLAIMED" -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.LightGray.copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            "Claimed +${ref.bonusCredits} ✓",
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFE67E22).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "⏳ Pending (${ref.dailyTasksCompleted}/${ref.requiredDays} Days)",
                                            color = Color(0xFFE67E22),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Progress Bar for 2-day daily task
                        val refProgress = (ref.dailyTasksCompleted.toFloat() / ref.requiredDays.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { refProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (ref.status == "COMPLETED" || ref.status == "CLAIMED") Color(0xFF27AE60) else Color(0xFFE67E22),
                            trackColor = Color.LightGray.copy(alpha = 0.3f)
                        )

                        // Testing Simulation Helper for Pending Referrals
                        if (ref.status == "PENDING") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val result = viewModel.simulateRefereeDailyTask(ref.id)
                                        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Simulate Friend Daily Task (+1 Day)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun WithdrawTabContent(
    viewModel: SocialViewModel,
    wallet: com.example.data.model.UserRewardWallet,
    adminConfig: com.example.data.model.AdminConfig,
    withdrawals: List<WithdrawalRequest>,
    userEmail: String,
    context: Context
) {
    val currency by viewModel.selectedCurrency.collectAsState()
    val activeMethods = remember(adminConfig.customPaymentMethods) {
        val list = adminConfig.customPaymentMethods.filter { it.isEnabled }
        if (list.isEmpty()) com.example.data.model.defaultPaymentMethodsList() else list
    }

    var selectedMethodItem by remember(activeMethods) {
        mutableStateOf(activeMethods.firstOrNull() ?: com.example.data.model.defaultPaymentMethodsList().first())
    }
    var accountNumber by remember { mutableStateOf("") }
    var selectedOperator by remember { mutableStateOf("Grameenphone") }
    var selectedSimType by remember { mutableStateOf("Prepaid") }
    var withdrawCredits by remember { mutableStateOf("2000") }
    var isLoading by remember { mutableStateOf(false) }

    val isMobileRecharge = selectedMethodItem.type == "MOBILE_RECHARGE" || selectedMethodItem.name.contains("Recharge", ignoreCase = true)

    // Auto detect operator from phone prefix
    LaunchedEffect(accountNumber) {
        val clean = accountNumber.trim()
        if (clean.startsWith("017") || clean.startsWith("013")) {
            if (selectedOperator != "Skitto" && selectedOperator != "Grameenphone") selectedOperator = "Grameenphone"
        } else if (clean.startsWith("019") || clean.startsWith("014")) {
            selectedOperator = "Banglalink"
        } else if (clean.startsWith("018")) {
            selectedOperator = "Robi"
        } else if (clean.startsWith("016")) {
            selectedOperator = "Airtel"
        } else if (clean.startsWith("015")) {
            selectedOperator = "Teletalk"
        }
    }

    val enteredCredits = withdrawCredits.toIntOrNull() ?: 0
    val creditsPerDollar = adminConfig.creditsPerDollar.takeIf { it > 0 } ?: 2000
    val calculatedUSDVal = enteredCredits.toDouble() / creditsPerDollar
    val calculatedBDTVal = calculatedUSDVal * adminConfig.usdToBdtRate
    val calculatedUSD = String.format(Locale.US, "%.2f", calculatedUSDVal)
    val calculatedBDT = String.format(Locale.US, "%.2f", calculatedBDTVal)

    val minWithdrawUSD = selectedMethodItem.minWithdrawalUSD.takeIf { it > 0 } ?: adminConfig.minWithdrawalUSD
    val minCreditsRequired = (minWithdrawUSD * creditsPerDollar).toInt()
    val minWithdrawBDT = String.format(Locale.US, "%.2f", minWithdrawUSD * adminConfig.usdToBdtRate)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Notice Box
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF39C12).copy(alpha = 0.12f),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFF39C12), Color(0xFFF39C12)))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD35400))
                    Column {
                        Text(
                            text = adminConfig.noticeMessage,
                            fontSize = 12.sp,
                            color = Color(0xFF7E3B00)
                        )
                        Text(
                            text = "Exchange Rate: 1 USD = ৳${adminConfig.usdToBdtRate.toInt()} BDT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6C5CE7),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Withdraw Request Form
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = FlareOffWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Payment Gateway", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        // Currency Switch pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF6C5CE7).copy(alpha = 0.15f),
                            modifier = Modifier.clickable {
                                viewModel.toggleCurrency()
                            }
                        ) {
                            Text(
                                text = "View in ${currency.code} (${currency.symbol})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6C5CE7),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gateway Selectors - Flow / Chunked rows
                    val chunks = activeMethods.chunked(3)
                    chunks.forEach { rowMethods ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMethods.forEach { method ->
                                val isSelected = selectedMethodItem.id == method.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF6C5CE7) else Color.White,
                                    border = if (!isSelected) ButtonDefaults.outlinedButtonBorder(enabled = true) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedMethodItem = method }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = method.name,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (currency == com.example.data.model.AppCurrency.BDT) "Min ৳${(method.minWithdrawalUSD * adminConfig.usdToBdtRate).toInt()}" else "Min $${method.minWithdrawalUSD}",
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else FlareTextSecondary,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            // Filler if last chunk is incomplete
                            for (i in 0 until (3 - rowMethods.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Method Info / Instructions
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF6C5CE7).copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Instructions for ${selectedMethodItem.name}:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF6C5CE7)
                            )
                            Text(
                                text = selectedMethodItem.instructions,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mobile Recharge Operator & SIM Type Selection
                    if (isMobileRecharge) {
                        Text("Select Mobile Operator:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        val operators = listOf("Grameenphone", "Banglalink", "Robi", "Airtel", "Teletalk", "Skitto")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            operators.take(3).forEach { op ->
                                val isSel = selectedOperator == op
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) Color(0xFF6C5CE7) else Color.White,
                                    border = if (!isSel) ButtonDefaults.outlinedButtonBorder(enabled = true) else null,
                                    modifier = Modifier.weight(1f).clickable { selectedOperator = op }
                                ) {
                                    Text(
                                        text = when(op) {
                                            "Grameenphone" -> "GP"
                                            "Banglalink" -> "BL"
                                            else -> op
                                        },
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            operators.drop(3).forEach { op ->
                                val isSel = selectedOperator == op
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) Color(0xFF6C5CE7) else Color.White,
                                    border = if (!isSel) ButtonDefaults.outlinedButtonBorder(enabled = true) else null,
                                    modifier = Modifier.weight(1f).clickable { selectedOperator = op }
                                ) {
                                    Text(
                                        text = op,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SIM Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Prepaid", "Postpaid").forEach { sim ->
                                    val isSel = selectedSimType == sim
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSel) Color(0xFF27AE60) else Color.LightGray.copy(alpha = 0.3f),
                                        modifier = Modifier.clickable { selectedSimType = sim }
                                    ) {
                                        Text(
                                            text = sim,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Account Number
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = {
                            Text(
                                if (isMobileRecharge) "Mobile Recharge Number (01XXXXXXXXX)"
                                else "${selectedMethodItem.name} Account / Address"
                            )
                        },
                        placeholder = {
                            Text(
                                when {
                                    isMobileRecharge -> "017XXXXXXXX / 018XXXXXXXX"
                                    selectedMethodItem.name.contains("Binance", ignoreCase = true) -> "Enter TRC20 USDT / UID"
                                    selectedMethodItem.name.contains("PayPal", ignoreCase = true) -> "you@email.com"
                                    selectedMethodItem.name.contains("Bank", ignoreCase = true) -> "Acc No, Bank & Branch name"
                                    else -> "017XXXXXXXX"
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick recharge amount pills
                    if (isMobileRecharge) {
                        Text("Quick Recharge Amount:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(20, 50, 100, 200).forEach { bdt ->
                                val requiredCredits = ((bdt.toDouble() / adminConfig.usdToBdtRate) * creditsPerDollar).toInt()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF6C5CE7).copy(alpha = 0.1f),
                                    modifier = Modifier.weight(1f).clickable {
                                        withdrawCredits = requiredCredits.toString()
                                    }
                                ) {
                                    Text(
                                        text = "৳$bdt",
                                        color = Color(0xFF6C5CE7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Credits to withdraw
                    OutlinedTextField(
                        value = withdrawCredits,
                        onValueChange = { withdrawCredits = it },
                        label = { Text("Credits Amount (Min. $minCreditsRequired pts)") },
                        supportingText = {
                            Text(
                                "Payout: $$calculatedUSD USD  |  ৳$calculatedBDT BDT (Rate: ৳${adminConfig.usdToBdtRate.toInt()}/$)",
                                color = Color(0xFF6C5CE7),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (accountNumber.isBlank()) {
                                Toast.makeText(context, "Please enter your ${if (isMobileRecharge) "recharge mobile number" else selectedMethodItem.name}", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (enteredCredits < minCreditsRequired) {
                                Toast.makeText(context, "Minimum withdrawal for ${selectedMethodItem.name} is $minCreditsRequired Credits ($$minWithdrawUSD / ৳$minWithdrawBDT)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (enteredCredits > wallet.totalCredits) {
                                Toast.makeText(context, "Insufficient balance! You have ${wallet.totalCredits} Credits.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val formattedAccount = if (isMobileRecharge) {
                                "${accountNumber.trim()} [$selectedOperator • $selectedSimType]"
                            } else {
                                accountNumber.trim()
                            }

                            isLoading = true
                            viewModel.requestWithdrawal(
                                method = if (isMobileRecharge) "Mobile Recharge" else selectedMethodItem.name,
                                account = formattedAccount,
                                credits = enteredCredits,
                                usd = calculatedUSD.toDoubleOrNull() ?: 1.0,
                                onSuccess = { txnId ->
                                    isLoading = false
                                    accountNumber = ""
                                    Toast.makeText(context, "Recharge/Payout Requested! Trx ID: $txnId (Payout: ৳$calculatedBDT / $$calculatedUSD)", Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    isLoading = false
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text(
                                text = "Submit Request (৳$calculatedBDT / $$calculatedUSD)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun HistoryTabContent(
    viewModel: SocialViewModel,
    wallet: com.example.data.model.UserRewardWallet,
    adminConfig: com.example.data.model.AdminConfig,
    withdrawals: List<WithdrawalRequest>,
    referrals: List<com.example.data.model.ReferralRecord>,
    userEmail: String,
    context: Context
) {
    val currency by viewModel.selectedCurrency.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Referral Summary Item
        item {
            Text(
                text = "👥 Referral Rewards",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (referrals.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FlareOffWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No referral history yet.",
                        fontSize = 12.sp,
                        color = FlareTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(referrals) { ref ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlareOffWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("@${ref.refereeHandle}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Status: ${ref.status}",
                                fontSize = 11.sp,
                                color = if (ref.status == "CLAIMED") Color(0xFF27AE60) else FlareTextSecondary
                            )
                        }
                        Text(
                            text = "+${ref.bonusCredits} pts",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = if (ref.status == "CLAIMED") Color(0xFF27AE60) else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Withdrawal History Item
        item {
            Text(
                text = "📜 Withdrawal History",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (withdrawals.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FlareOffWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No withdrawal requests yet.",
                        fontSize = 12.sp,
                        color = FlareTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(withdrawals) { req ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FlareOffWhite,
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(req.method, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("(${req.accountNumber})", fontSize = 12.sp, color = FlareTextSecondary)
                            }
                            Text("Trx: ${req.id} • ${req.requestDate}", fontSize = 11.sp, color = FlareTextSecondary)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (currency == com.example.data.model.AppCurrency.BDT) "৳${String.format(Locale.US, "%.2f", req.amountBDT)} BDT" else "$${String.format(Locale.US, "%.2f", req.amountUSD)} USD",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (req.status) {
                                    "PAID" -> Color(0xFF27AE60).copy(alpha = 0.15f)
                                    "APPROVED" -> Color(0xFF2980B9).copy(alpha = 0.15f)
                                    "REJECTED" -> Color(0xFFE74C3C).copy(alpha = 0.15f)
                                    else -> Color(0xFFF39C12).copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = req.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (req.status) {
                                        "PAID" -> Color(0xFF27AE60)
                                        "APPROVED" -> Color(0xFF2980B9)
                                        "REJECTED" -> Color(0xFFE74C3C)
                                        else -> Color(0xFFF39C12)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
