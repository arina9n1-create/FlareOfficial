package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppCurrency
import com.example.ui.theme.FlareTextSecondary
import com.example.ui.viewmodel.SocialViewModel
import java.util.Locale

// ==========================================
// FULL SCREEN: VERIFICATION BADGE (PAID)
// Fee & USD→BDT rate are set by the Super Admin;
// the fee is deducted from the user's My Wallet balance.
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationBadgeFullScreenPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val summary by viewModel.walletSummary.collectAsState()
    val adminConfig by viewModel.adminConfig.collectAsState()
    val currency by viewModel.selectedCurrency.collectAsState()
    val hasBadge by viewModel.hasVerificationBadge.collectAsState()

    var isBusy by remember { mutableStateOf(false) }

    val rate = adminConfig.usdToBdtRate.takeIf { it > 0 } ?: 120.0
    val feeUsd = adminConfig.verificationBadgeFeeUSD
    val feeBdt = feeUsd * rate
    val cpd = summary.creditsPerDollar.takeIf { it > 0 } ?: adminConfig.creditsPerDollar.takeIf { it > 0 } ?: 2000
    val balanceUsd = summary.withdrawableCredits.toDouble() / cpd
    val balanceBdt = balanceUsd * rate
    val feeCoins = kotlin.math.ceil(feeUsd * cpd).toInt().coerceAtLeast(1)
    val canAfford = summary.withdrawableCredits >= feeCoins

    fun money(usd: Double): String =
        if (currency == AppCurrency.BDT)
            "৳${String.format(Locale.US, "%.2f", usd * rate)} BDT"
        else "$${String.format(Locale.US, "%.2f", usd)} USD"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Verification Badge", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (hasBadge) Color(0xFF27AE60) else Color(0xFF6C5CE7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            if (hasBadge) "BADGE ACTIVE" else "VERIFICATION BADGE",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                            Text(
                                if (hasBadge) "Verified ✔️" else "Not Active",
                                fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (hasBadge) "Your account displays the official verification badge."
                            else "Pay the activation fee below to display the verification badge on your profile.",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Wallet balance + currency selector (also inside the hero card)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "≈ $${String.format(Locale.US, "%.2f", balanceUsd)} USD · ৳${String.format(Locale.US, "%.2f", balanceBdt)} BDT",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.95f)
                            )
                            CurrencyChipUSD(selected = currency == AppCurrency.USD) {
                                viewModel.setCurrency(AppCurrency.USD)
                            }
                            CurrencyChipBDT(selected = currency == AppCurrency.BDT) {
                                viewModel.setCurrency(AppCurrency.BDT)
                            }
                        }
                    }
                }
            }
            if (!hasBadge) {
                // Activation fee card (fee set by Super Admin)
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ACTIVATION FEE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
                            Text(money(feeUsd), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF27AE60))
                            Text(
                                "$${String.format(Locale.US, "%.2f", feeUsd)} USD = ৳${String.format(Locale.US, "%.2f", feeBdt)} BDT",
                                fontSize = 12.sp, color = FlareTextSecondary
                            )
                            Text(
                                "$feeCoins Coins will be deducted from My Wallet",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF39C12)
                            )
                        }
                    }
                }

                // Activate button
                item {
                    Button(
                        onClick = {
                            isBusy = true
                            viewModel.purchaseVerificationBadge { ok, msg ->
                                isBusy = false
                                Toast.makeText(context, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isBusy && canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) Color(0xFF6C5CE7) else Color(0xFF95A5A6),
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            when {
                                isBusy -> "Processing…"
                                canAfford -> "Pay ${money(feeUsd)} & Activate Badge"
                                else -> "Insufficient Wallet Balance"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF27AE60).copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF27AE60))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Badge Activated", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF27AE60))
                                Text("The fee was deducted from your My Wallet balance. Thank you for verifying your account!", fontSize = 12.sp, color = FlareTextSecondary)
                            }
                        }
                    }
                }
            }
            // How it works card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7).copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF6C5CE7))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("How it works", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF6C5CE7))
                            Text(
                                "The activation fee and the USD → BDT conversion rate are set by the Super Admin in the Admin Panel. The cost is deducted directly from your My Wallet balance.",
                                fontSize = 12.sp, color = FlareTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyChipUSD(selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
        onClick = onClick
    ) {
        Text(
            "$ USD", color = if (selected) Color(0xFF6C5CE7) else Color.White,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CurrencyChipBDT(selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
        onClick = onClick
    ) {
        Text(
            "৳ BDT", color = if (selected) Color(0xFF6C5CE7) else Color.White,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
