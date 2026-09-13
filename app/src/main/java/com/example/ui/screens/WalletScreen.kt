package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

/** Pages within the professional Wallet hub. */
enum class WalletPage { MAIN, WITHDRAW, TRANSACTIONS, WITHDRAWALS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary by viewModel.walletSummary.collectAsState()
    val transactions by viewModel.walletTransactions.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()
    val currency by viewModel.selectedCurrency.collectAsState()
    val adminConfig by viewModel.adminConfig.collectAsState()
    val profile by viewModel.profile.collectAsState()

    var page by remember { mutableStateOf(WalletPage.MAIN) }

    BackHandler {
        if (page != WalletPage.MAIN) page = WalletPage.MAIN
        else onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    val title = when(page) {
                        WalletPage.MAIN -> "My Wallet"
                        WalletPage.WITHDRAW -> "Withdraw"
                        WalletPage.TRANSACTIONS -> "Transaction History"
                        WalletPage.WITHDRAWALS -> "Withdrawal History"
                    }
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) 
                },
                navigationIcon = {
                    IconButton(onClick = { if (page == WalletPage.MAIN) onBack() else page = WalletPage.MAIN }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshWalletData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        when (page) {
            WalletPage.MAIN -> WalletMain(
                viewModel = viewModel,
                summary = summary,
                transactions = transactions,
                withdrawals = withdrawals,
                currency = currency,
                adminConfig = adminConfig,
                onOpenWithdraw = { page = WalletPage.WITHDRAW },
                onOpenTransactions = { page = WalletPage.TRANSACTIONS },
                onOpenWithdrawals = { page = WalletPage.WITHDRAWALS },
                paddingValues = padding
            )
            WalletPage.WITHDRAW -> WithdrawPage(
                viewModel = viewModel,
                summary = summary,
                adminConfig = adminConfig,
                userHandle = profile.handle,
                paddingValues = padding
            )
            WalletPage.TRANSACTIONS -> WalletTransactionsPage(
                viewModel = viewModel,
                transactions = transactions,
                currency = currency,
                paddingValues = padding
            )
            WalletPage.WITHDRAWALS -> WalletWithdrawalsPage(
                viewModel = viewModel,
                withdrawals = withdrawals,
                currency = currency,
                paddingValues = padding
            )
        }
    }
}

@Composable
private fun WalletMain(
    viewModel: SocialViewModel,
    summary: WalletSummary,
    transactions: List<WalletTransaction>,
    withdrawals: List<WithdrawalRequest>,
    currency: AppCurrency,
    adminConfig: AdminConfig,
    onOpenWithdraw: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenWithdrawals: () -> Unit,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val cpd = summary.creditsPerDollar.takeIf { it > 0 } ?: adminConfig.creditsPerDollar.takeIf { it > 0 } ?: 2000

    fun formatMoney(usd: Double): String {
        val rate = adminConfig.usdToBdtRate.takeIf { it > 0 } ?: 120.0
        return if (currency == AppCurrency.BDT) "৳${String.format(Locale.US, "%.2f", usd * rate)}"
        else "$${String.format(Locale.US, "%.2f", usd)}"
    }
    fun creditsToUsd(coins: Int): Double = coins.toDouble() / cpd

    val redeemable = summary.referralCredits + summary.watchCredits + summary.challengeCredits
    val canWithdraw = summary.withdrawableCredits > 0 && adminConfig.customPaymentMethods.any { it.isEnabled }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("AVAILABLE BALANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${summary.withdrawableCredits} Coins", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("≈ ${formatMoney(summary.withdrawableUsd)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD8D5FF))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        WalletCurrencyChip("$ USD", currency == AppCurrency.USD) { viewModel.setCurrency(AppCurrency.USD) }
                        WalletCurrencyChip("৳ BDT", currency == AppCurrency.BDT) { viewModel.setCurrency(AppCurrency.BDT) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "1 $ USD = ৳${String.format(Locale.US, "%.2f", adminConfig.usdToBdtRate.takeIf { it > 0 } ?: 120.0)} BDT · rate set by Super Admin",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenWithdraw,
                            enabled = canWithdraw,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6C5CE7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("Withdraw", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { viewModel.refreshWalletData() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("Refresh", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                WalletStatCard("Pending Balance", "${summary.pendingWithdrawalCredits} Coins", "≈ ${formatMoney(summary.pendingWithdrawalUsd)}", Color(0xFFF39C12), Modifier.weight(1f))
                WalletStatCard("Total Earned", "${summary.totalEarnedCredits} Coins", "Lifetime rewards", Color(0xFF27AE60), Modifier.weight(1f))
            }
        }

        item { Text("EARNING BY SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary) }

        item {
            SourceBreakdownRow("Refer & Earn", summary.referralCredits, formatMoney(creditsToUsd(summary.referralCredits)), "referral") {
                if (summary.referralCredits <= 0) Toast.makeText(context, "No eligible referral credits to redeem", Toast.LENGTH_SHORT).show()
                else viewModel.redeemRewardCredits("REFERRAL") { ok, msg -> Toast.makeText(context, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show() }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SourceBreakdownRow("Watch & Earn", summary.watchCredits, formatMoney(creditsToUsd(summary.watchCredits)), "watch") {
                if (summary.watchCredits <= 0) Toast.makeText(context, "No eligible watch credits to redeem", Toast.LENGTH_SHORT).show()
                else viewModel.redeemRewardCredits("WATCH") { ok, msg -> Toast.makeText(context, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show() }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SourceBreakdownRow("New User Challenge", summary.challengeCredits, formatMoney(creditsToUsd(summary.challengeCredits)), "challenge") {
                if (summary.challengeCredits <= 0) Toast.makeText(context, "No eligible challenge credits to redeem", Toast.LENGTH_SHORT).show()
                else viewModel.redeemRewardCredits("CHALLENGE") { ok, msg -> Toast.makeText(context, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show() }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF27AE60).copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Savings, contentDescription = null, tint = Color(0xFF27AE60))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$redeemable Eligible Credits", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF27AE60))
                        Text("Redeem your Refer, Watch & Challenge credits into your withdrawable balance.", fontSize = 12.sp, color = FlareTextSecondary)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WalletActionCard("Transaction History", "${transactions.size} entries", Icons.AutoMirrored.Filled.Article, Color(0xFF0984E3), Modifier.weight(1f), onOpenTransactions)
            }
        }

        item { Text("RECENT TRANSACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary) }
        if (transactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = FlareTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No transactions yet.", color = FlareTextSecondary, fontSize = 13.sp)
                        Text("Complete daily tasks, watch reels & invite friends to start earning.", color = FlareTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(transactions.take(5)) { tx -> TransactionRow(tx, currency, adminConfig) }
            item {
                TextButton(onClick = onOpenTransactions, modifier = Modifier.fillMaxWidth()) {
                    Text("View all transactions", fontWeight = FontWeight.Bold, color = Color(0xFF6C5CE7))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ==================================================================
// Helper cards & rows
// ==================================================================
@Composable
private fun WalletCurrencyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color.White else Color.White.copy(alpha = 0.5f)
        ),
        onClick = onClick
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF6C5CE7) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun WalletStatCard(
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = accent)
            Text(sub, fontSize = 11.sp, color = FlareTextSecondary)
        }
    }
}

@Composable
private fun WalletActionCard(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.2f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = accent) }
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(sub, fontSize = 11.sp, color = FlareTextSecondary)
            }
        }
    }
}

@Composable
private fun SourceBreakdownRow(
    label: String,
    coins: Int,
    usd: String,
    icon: String,
    onRedeem: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FlareOffWhite),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (icon) { "referral" -> "👥"; "challenge" -> "🎯"; else -> "🎬" }, fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("$coins Coins · $usd", fontSize = 12.sp, color = FlareTextSecondary)
            }
            OutlinedButton(
                onClick = onRedeem,
                enabled = coins > 0,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Redeem", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun TransactionRow(
    tx: WalletTransaction,
    currency: AppCurrency,
    adminConfig: AdminConfig
) {
    val isCredit = tx.creditOrDebit == "CREDIT"
    val sign = if (isCredit) "+" else "-"
    val color = if (isCredit) Color(0xFF27AE60) else Color(0xFFE74C3C)
    val rate = adminConfig.usdToBdtRate.takeIf { it > 0 } ?: 120.0
    val money = when (tx.txType) {
        "WITHDRAWAL", "WITHDRAWAL_REFUND" ->
            if (currency == AppCurrency.BDT) "৳${String.format(Locale.US, "%.2f", tx.monetaryAmount * rate)}"
            else "$${String.format(Locale.US, "%.2f", tx.monetaryAmount)}"
        else -> "${sign}${tx.coinAmount} Coins"
    }
    val date = try { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(tx.createdAt)) } catch (e: Exception) { "" }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(when (tx.txType) {
                    "REFERRAL" -> "Refer & Earn"
                    "WATCH" -> "Watch & Earn"
                    "CHALLENGE" -> "Challenge"
                    "REDEEM" -> "Redeemed to Wallet"
                    "WITHDRAWAL" -> "Withdrawal"
                    "WITHDRAWAL_REFUND" -> "Withdrawal Refund"
                    "MANUAL_ADD" -> "Admin Bonus"
                    "MANUAL_REMOVE" -> "Admin Adjustment"
                    else -> tx.source
                }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${tx.source} · ${tx.status} · ${date}", fontSize = 11.sp, color = FlareTextSecondary, maxLines = 1)
                Text("Trx: ${tx.id.take(22)}", fontSize = 10.sp, color = FlareTextSecondary)
            }
            Text(money, fontWeight = FontWeight.Black, fontSize = 14.sp, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawPage(
    viewModel: SocialViewModel,
    summary: WalletSummary,
    adminConfig: AdminConfig,
    userHandle: String,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val activeMethods = remember(adminConfig.customPaymentMethods) {
        val l = adminConfig.customPaymentMethods.filter { it.isEnabled }
        if (l.isEmpty()) defaultPaymentMethodsList() else l
    }
    var method by remember(activeMethods) { mutableStateOf(activeMethods.firstOrNull()) }
    var account by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val cpd = summary.creditsPerDollar.takeIf { it > 0 } ?: adminConfig.creditsPerDollar.takeIf { it > 0 } ?: 2000
    val entered = credits.toIntOrNull() ?: 0
    val usd = entered.toDouble() / cpd
    val bdt = usd * (adminConfig.usdToBdtRate.takeIf { it > 0 } ?: 120.0)
    val minUsd = (method?.minWithdrawalUSD?.takeIf { it > 0 }) ?: adminConfig.minWithdrawalUSD
    val minCredits = (minUsd * cpd).toInt().coerceAtLeast(1)
    val canSubmit = entered >= minCredits && entered <= summary.withdrawableCredits && account.isNotBlank() && method != null && !isBusy

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7).copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Withdrawable Balance: ${summary.withdrawableCredits} Coins", fontWeight = FontWeight.Bold, color = Color(0xFF6C5CE7))
                    Text("Minimum: $minCredits Coins (≈ $${String.format(Locale.US, "%.2f", minUsd)})", fontSize = 12.sp, color = FlareTextSecondary)
                }
            }
        }
        item { Text("Select Payment Gateway", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        activeMethods.chunked(3).forEach { rowMethods ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowMethods.forEach { m ->
                        val isSel = method?.id == m.id
                        Surface(shape = RoundedCornerShape(10.dp), color = if (isSel) Color(0xFF6C5CE7) else Color.White,
                            border = if (!isSel) androidx.compose.foundation.BorderStroke(1.dp, FlareBorder) else null,
                            modifier = Modifier.weight(1f).clickable { method = m }) {
                            Text(m.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp))
                        }
                    }
                }
            }
        }
        item { OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text(method?.instructions ?: "Account / Wallet number") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = credits, onValueChange = { input -> credits = input.filter { it.isDigit() }.take(9) }, label = { Text("Coins to withdraw") },
            suffix = { Text("min $minCredits") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA502).copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Live Conversion", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFA502))
                    Text("$entered Coins = $${String.format(Locale.US, "%.2f", usd)} USD = ৳${String.format(Locale.US, "%.2f", bdt)} BDT", fontSize = 13.sp)
                    if (entered > summary.withdrawableCredits) Text("Exceeds your withdrawable balance!", fontSize = 12.sp, color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
                    else if (entered in 1 until minCredits) Text("Below the $minCredits coin minimum.", fontSize = 12.sp, color = Color(0xFFE74C3C))
                }
            }
        }
        item { Button(onClick = { showConfirm = true }, enabled = canSubmit, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Request Withdrawal", fontWeight = FontWeight.Bold) } }
    }

    val selectedMethod = method
    if (showConfirm && selectedMethod != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirm Withdrawal", fontWeight = FontWeight.Bold) },
            text = { Text("Withdraw $entered Coins (≈ $${String.format(Locale.US, "%.2f", usd)}) to ${selectedMethod.name}\nvia $account?\n\nFunds are reserved and released after review.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false; isBusy = true
                    viewModel.requestWalletWithdrawal(selectedMethod.name, account, entered, usd, bdt) { ok, msg ->
                        isBusy = false
                        Toast.makeText(context, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        if (ok) { viewModel.refreshWalletData() }
                    }
                }) { Text("Confirm", fontWeight = FontWeight.Bold, color = Color(0xFF6C5CE7)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletTransactionsPage(
    viewModel: SocialViewModel,
    transactions: List<WalletTransaction>,
    currency: AppCurrency,
    paddingValues: PaddingValues
) {
    val adminConfig by viewModel.adminConfig.collectAsState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (transactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = FlareTextSecondary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No transactions yet.", color = FlareTextSecondary, fontSize = 14.sp)
                        Text("Earn coins by watching reels, completing daily tasks and inviting friends.", color = FlareTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(transactions) { tx -> TransactionRow(tx, currency, adminConfig) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletWithdrawalsPage(
    viewModel: SocialViewModel,
    withdrawals: List<WithdrawalRequest>,
    currency: AppCurrency,
    paddingValues: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (withdrawals.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CreditCardOff, contentDescription = null, tint = FlareTextSecondary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No withdrawal requests yet.", color = FlareTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(withdrawals) { req -> WithdrawalRow(req, currency) }
        }
    }
}

@Composable
private fun WithdrawalRow(req: WithdrawalRequest, currency: AppCurrency) {
    val statusColor = when (req.status) {
        "PAID" -> Color(0xFF27AE60)
        "APPROVED", "PROCESSING" -> Color(0xFF2980B9)
        "REJECTED", "CANCELLED" -> Color(0xFFE74C3C)
        else -> Color(0xFFF39C12)
    }
    val money = if (currency == AppCurrency.BDT) "৳${String.format(Locale.US, "%.2f", req.amountBDT)} BDT" else "$${String.format(Locale.US, "%.2f", req.amountUSD)} USD"
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(req.method, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("(${req.accountNumber})", fontSize = 12.sp, color = FlareTextSecondary)
                Spacer(Modifier.weight(1f))
                Text(money, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Text("Ref: ${req.id} · ${req.requestDate}", fontSize = 11.sp, color = FlareTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${req.creditsUsed} Coins", fontSize = 12.sp, color = FlareTextSecondary)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(req.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            if (req.transactionNote.isNotBlank()) Text(req.transactionNote, fontSize = 11.sp, color = Color(0xFF27AE60))
        }
    }
}
