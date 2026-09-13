package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Super Admin / payout-staff wallet operations that were ADDED on top of the
 * existing Rewards section (Rules / Rates / Gateways / Payouts are untouched).
 */

// ==================================================================
// PLATFORM WALLET SUMMARY + USER EARNINGS OVERVIEW
// ==================================================================
@Composable
fun AdminWalletOverviewTab(viewModel: SocialViewModel) {
    val overview by viewModel.platformOverview.collectAsState()
    val earnings by viewModel.adminUserEarnings.collectAsState()
    val transactions by viewModel.walletTransactions.collectAsState()
    val df = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    LaunchedEffect(Unit) {
        viewModel.refreshAdminWalletData()
        viewModel.refreshAdminWalletTransactions()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("📊 Platform Wallet Summary", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Coins & payout totals across all users", fontSize = 12.sp, color = FlareTextSecondary)
                }
                TextButton(onClick = { viewModel.refreshAdminWalletData() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFFA502))
                    Text("Refresh", fontWeight = FontWeight.Bold, color = Color(0xFFFFA502), fontSize = 12.sp)
                }
            }
        }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminMiniStat("Total Platform Coins", "${overview.totalPlatformCoins}", "🪙", Color(0xFFFFA502), Modifier.weight(1f))
            AdminMiniStat("Coins Earned", "${overview.totalCoinsEarnedByUsers}", "💰", Color(0xFF6C5CE7), Modifier.weight(1f))
            AdminMiniStat("Coins Redeemed", "${overview.totalCoinsRedeemed}", "✅", Color(0xFF0984E3), Modifier.weight(1f))
        } }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminMiniStat("Withdrawn (Paid)", "$${String.format(Locale.US, "%.2f", overview.totalWithdrawnAmountUsd)}", "💸", Color(0xFF27AE60), Modifier.weight(1f))
            AdminMiniStat("Pending Payouts", "$${String.format(Locale.US, "%.2f", overview.pendingPayoutAmountUsd)}\n(${overview.pendingPayoutCount})", "⏳", Color(0xFFE17055), Modifier.weight(1f))
        } }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminMiniStat("Today Earned", "+${overview.todayEarnedCoins} 🪙", "📅", Color(0xFF00B894), Modifier.weight(1f))
            AdminMiniStat("Yesterday Earned", "+${overview.yesterdayEarnedCoins} 🪙", "📆", Color(0xFF636E72), Modifier.weight(1f))
        } }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminMiniStat("Withdrawn Today", "$${String.format(Locale.US, "%.2f", overview.todayWithdrawnUsd)}", "💳", Color(0xFF00CEC9), Modifier.weight(1f))
            AdminMiniStat("Withdrawn Yesterday", "$${String.format(Locale.US, "%.2f", overview.yesterdayWithdrawnUsd)}", "🧾", Color(0xFF0984E3), Modifier.weight(1f))
        } }

        item { Text("RECENT WALLET ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary) }
        val txs = transactions.take(8)
        if (txs.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No wallet transactions yet.", color = FlareTextSecondary, fontSize = 12.sp)
            } }
        } else {
            items(txs) { tx -> AdminRecentTxRow(tx, df) }
        }
        item {
            TextButton(onClick = { viewModel.refreshAdminWalletTransactions() }, modifier = Modifier.fillMaxWidth()) {
                Text("View Full History →", fontWeight = FontWeight.Bold, color = Color(0xFFFFA502), fontSize = 13.sp)
            }
        }

        item { Text("USER EARNINGS OVERVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary) }
        if (earnings.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No user wallet data yet.", color = FlareTextSecondary, fontSize = 13.sp)
            } }
        } else {
            items(earnings) { ue -> UserEarningsRow(ue) }
        }
    }
}

@Composable
private fun AdminMiniStat(title: String, value: String, icon: String, accent: Color, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.1f)), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("$icon $title", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent)
        }
    }
}

@Composable
private fun AdminRecentTxRow(tx: WalletTransaction, df: SimpleDateFormat) {
    val isCredit = tx.creditOrDebit == "CREDIT"
    val amtColor = if (isCredit) Color(0xFF27AE60) else Color(0xFFE17055)
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${tx.userHandle} · ${tx.source.ifBlank { tx.txType }}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${df.format(Date(tx.createdAt))} · ${tx.status}", fontSize = 10.sp, color = FlareTextSecondary)
            }
            Text("${if (isCredit) "+" else "-"}${kotlin.math.abs(tx.coinAmount)} 🪙", fontSize = 13.sp, fontWeight = FontWeight.Black, color = amtColor)
        }
    }
}

@Composable
private fun UserEarningsRow(ue: UserEarningsOverview) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${ue.userHandle}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("Withdrawable: ${ue.withdrawable}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF6C5CE7))
            }
            Text("Total Earned ${ue.totalEarned} · Ref ${ue.referralCredits} · Watch ${ue.watchCredits} · Challenge ${ue.challengeCredits}", fontSize = 11.sp, color = FlareTextSecondary)
            Text("Redeemed ${ue.redeemed} · Withdrawn ${ue.withdrawn}", fontSize = 11.sp, color = FlareTextSecondary)
        }
    }
}

// ==================================================================
// TRANSACTION MANAGEMENT (admin)
// ==================================================================
@Composable
fun AdminWalletTransactionsTab(viewModel: SocialViewModel) {
    val transactions by viewModel.walletTransactions.collectAsState()
    val adminConfig by viewModel.adminConfig.collectAsState()
    val currency by viewModel.selectedCurrency.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshAdminWalletTransactions() }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("🧾 Transaction Management", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${transactions.size} transactions", fontSize = 12.sp, color = FlareTextSecondary)
            }
            TextButton(onClick = { viewModel.refreshAdminWalletTransactions() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFFA502)); Text("Refresh", fontWeight = FontWeight.Bold, color = Color(0xFFFFA502), fontSize = 12.sp)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (transactions.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions. Tap Refresh to load from the server.", color = FlareTextSecondary, fontSize = 13.sp)
                } }
            } else {
                items(transactions) { tx -> AdminTransactionRow(tx, currency, adminConfig) }
            }
        }
    }
}

@Composable
private fun AdminTransactionRow(tx: WalletTransaction, currency: AppCurrency, adminConfig: AdminConfig) {
    val isCredit = tx.creditOrDebit == "CREDIT"
    val amountColor = if (isCredit) Color(0xFF27AE60) else Color(0xFFE74C3C)
    val statusColor = when (tx.status.uppercase()) {
        "COMPLETED", "PAID" -> Color(0xFF27AE60)
        "PENDING", "PROCESSING" -> Color(0xFFF39C12)
        "REJECTED", "CANCELLED" -> Color(0xFFE74C3C)
        else -> FlareTextSecondary
    }
    val df = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isCredit) "+" else "-", color = amountColor, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("${kotlin.math.abs(tx.coinAmount)} Coins", color = amountColor, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(tx.userHandle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tx.txType} · ${tx.source.ifBlank { "—" }}", fontSize = 11.sp, color = FlareTextSecondary, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(tx.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (tx.monetaryAmount > 0.0) {
                val rate = adminConfig.usdToBdtRate.takeIf { it > 0 } ?: 120.0
                val money = if (currency == AppCurrency.BDT) "৳${String.format(Locale.US, "%.2f", tx.monetaryAmount * rate)}" else "$${String.format(Locale.US, "%.2f", tx.monetaryAmount)}"
                Text("Money: $money", fontSize = 11.sp, color = FlareTextSecondary)
            }
            Text("${df.format(Date(tx.createdAt))} · Ref: ${tx.reference.ifBlank { tx.id }}", fontSize = 10.sp, color = FlareTextSecondary)
        }
    }
}

// ==================================================================
// MANUAL WALLET ADJUSTMENT (Super Admin only)
// ==================================================================
@Composable
fun AdminManualAdjustmentTab(viewModel: SocialViewModel) {
    val context = LocalContext.current
    var target by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ADD") }
    var showConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    val parsedAmount = amount.toIntOrNull() ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("✏️ Manual Wallet Adjustment", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Super Admin only. Every adjustment is validated server-side and recorded in the audit log with your admin ID, the previous/new balance, and a mandatory reason.", fontSize = 12.sp, color = FlareTextSecondary)
        }
        item {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target user @handle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "ADD", onClick = { type = "ADD" }, label = { Text("➕ Add Coins") })
                FilterChip(selected = type == "REMOVE", onClick = { type = "REMOVE" }, label = { Text("➖ Remove Coins") })
            }
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() } },
                label = { Text("Coin amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = parsedAmount <= 0,
                supportingText = { if (parsedAmount <= 0) Text("Enter an amount greater than 0") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason (mandatory)") },
                isError = reason.isBlank(),
                supportingText = { if (reason.isBlank()) Text("A reason is required and will be stored in the audit log") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { showConfirm = true },
                enabled = target.isNotBlank() && parsedAmount > 0 && reason.isNotBlank() && !isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (type == "ADD") Color(0xFF27AE60) else Color(0xFFE74C3C))
            ) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (type == "ADD") "Apply Adjustment (+$parsedAmount Coins)" else "Apply Adjustment (-$parsedAmount Coins)")
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirm Adjustment") },
            text = {
                Text("${if (type == "ADD") "ADD" else "REMOVE"} $parsedAmount Coins ${if (type == "ADD") "to" else "from"} @$target?\n\nReason: $reason\n\nThis action is permanent and will be recorded in the audit log.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    isBusy = true
                    viewModel.adminAdjustWallet(target.removePrefix("@"), parsedAmount, reason, type) { ok, msg ->
                        isBusy = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (ok) { amount = ""; reason = "" }
                    }
                }) { Text("Confirm", fontWeight = FontWeight.Bold, color = if (type == "ADD") Color(0xFF27AE60) else Color(0xFFE74C3C)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ==================================================================
// AUDIT LOG
// ==================================================================
@Composable
fun AdminAuditLogTab(viewModel: SocialViewModel) {
    val logs by viewModel.walletAuditLogs.collectAsState()
    val df = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(Unit) { viewModel.refreshAdminWalletData() }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("🛡 Audit Log", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Every administrative wallet/reward action", fontSize = 12.sp, color = FlareTextSecondary)
            }
            TextButton(onClick = { viewModel.refreshAdminWalletData() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFFA502)); Text("Refresh", fontWeight = FontWeight.Bold, color = Color(0xFFFFA502), fontSize = 12.sp)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (logs.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No audit entries yet.", color = FlareTextSecondary, fontSize = 13.sp)
                } }
            } else {
                items(logs) { log ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFA502), modifier = Modifier.weight(1f))
                                Text("@${log.adminHandle}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Target: @${log.targetHandle}${if (log.fieldName.isNotBlank()) " · Field: ${log.fieldName}" else ""}", fontSize = 11.sp, color = FlareTextSecondary)
                            if (log.previousValue.isNotBlank() || log.newValue.isNotBlank()) {
                                Text("Prev: ${log.previousValue.ifBlank { "—" }} → New: ${log.newValue.ifBlank { "—" }}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (log.reason.isNotBlank()) Text("Reason: ${log.reason}", fontSize = 11.sp, color = FlareTextSecondary)
                            Text(df.format(Date(log.createdAt)) + if (log.reference.isNotBlank()) " · Ref: ${log.reference}" else "", fontSize = 10.sp, color = FlareTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ==================================================================
// SUSPICIOUS / FRAUDULENT EARNINGS
// ==================================================================
@Composable
fun AdminFraudFlagsTab(viewModel: SocialViewModel) {
    val context = LocalContext.current
    val flags by viewModel.walletFraudFlags.collectAsState()
    var includeResolved by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf<WalletFraudFlag?>(null) }
    var resolveReason by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    LaunchedEffect(includeResolved) { viewModel.refreshAdminFraudFlags(includeResolved) }

    val severityColor: (String) -> Color = { s ->
        when (s.uppercase()) {
            "HIGH" -> Color(0xFFE74C3C)
            "MEDIUM" -> Color(0xFFF39C12)
            else -> Color(0xFF0984E3)
        }
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🚩 Suspicious Activity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Flagged for review — no user is affected automatically", fontSize = 12.sp, color = FlareTextSecondary)
            }
            TextButton(onClick = { viewModel.refreshAdminFraudFlags(includeResolved) }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFFA502))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = includeResolved, onClick = { includeResolved = !includeResolved }, label = { Text("Show resolved") })
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {
                viewModel.runFraudDetection { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    viewModel.refreshAdminFraudFlags(includeResolved)
                }
            }) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Run Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (flags.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(if (includeResolved) "No flags recorded." else "No unresolved flags. Tap Run Scan to check for suspicious reward activity.", color = FlareTextSecondary, fontSize = 13.sp)
                } }
            } else {
                items(flags) { flag ->
                    FraudFlagCard(flag, severityColor, df, onResolve = { resolving = flag; resolveReason = "" })
                }
            }
        }
    }

    resolving?.let { flag ->
        AlertDialog(
            onDismissRequest = { resolving = null },
            title = { Text("Resolve Flag") },
            text = {
                Column {
                    Text("Mark the flag for @${flag.userHandle} (${flag.flagType}) as reviewed/resolved?")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolveReason,
                        onValueChange = { resolveReason = it },
                        label = { Text("Resolution note (required)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = resolveReason.isNotBlank(),
                    onClick = {
                        viewModel.resolveFraudFlag(flag.id, resolveReason) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (ok) viewModel.refreshAdminFraudFlags(includeResolved)
                        }
                        resolving = null
                    }
                ) { Text("Resolve", fontWeight = FontWeight.Bold, color = Color(0xFF27AE60)) }
            },
            dismissButton = { TextButton(onClick = { resolving = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FraudFlagCard(
    flag: WalletFraudFlag,
    severityColor: (String) -> Color,
    df: SimpleDateFormat,
    onResolve: () -> Unit
) {
    val sc = severityColor(flag.severity)
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${flag.userHandle}", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = sc.copy(alpha = 0.15f)) {
                    Text("${flag.severity} · ${flag.flagType}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = sc, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(flag.description, fontSize = 11.sp, color = FlareTextSecondary)
            Text(df.format(Date(flag.createdAt)), fontSize = 10.sp, color = FlareTextSecondary)
            if (flag.resolved) {
                Text("✅ Resolved by @${flag.resolvedBy}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF27AE60))
            } else {
                OutlinedButton(onClick = onResolve, modifier = Modifier.align(Alignment.End)) {
                    Text("Resolve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}