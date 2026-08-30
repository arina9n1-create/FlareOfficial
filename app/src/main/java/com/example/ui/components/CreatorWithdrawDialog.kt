package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

enum class CreatorPayoutMethod(
    val id: String,
    val displayName: String,
    val bengaliName: String,
    val color: Color,
    val icon: String,
    val minWithdrawUsd: Double = 10.0
) {
    BKASH("BKASH", "bKash Personal", "বিকাশ পার্সোনাল", Color(0xFFD12053), "📱", 10.0),
    NAGAD("NAGAD", "Nagad Personal", "নগদ পার্সোনাল", Color(0xFFE85025), "💳", 10.0),
    ROCKET("ROCKET", "Rocket (DBBL)", "রকেট অ্যাকাউন্ট", Color(0xFF8C3494), "🚀", 10.0),
    BANK("BANK", "Bank Wire", "বাংলাদেশ ব্যাংক ট্রান্সফার", Color(0xFF00796B), "🏦", 25.0),
    BINANCE("BINANCE", "Binance USDT (TRC20)", "বাইনান্স পে (USDT)", Color(0xFFF3BA2F), "🪙", 15.0),
    PAYPAL("PAYPAL", "PayPal / Payoneer", "পেপ্যাল / পাইওনিয়ার", Color(0xFF003087), "🌐", 20.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorWithdrawDialog(
    availableBalanceUsd: Double,
    minimumWithdrawalUsd: Double = 10.0,
    maximumWithdrawalUsd: Double = 5000.0,
    onDismiss: () -> Unit,
    onWithdrawSubmit: (amountUsd: Double, method: String, accountDetails: String) -> Unit
) {
    val bdtExchangeRate = 120.0
    var selectedMethod by remember { mutableStateOf(CreatorPayoutMethod.BKASH) }
    var amountUsdText by remember { mutableStateOf(String.format(Locale.US, "%.2f", maxOf(minimumWithdrawalUsd, minOf(availableBalanceUsd, 25.0)))) }

    // Account form fields
    var phoneNumber by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var routingNumber by remember { mutableStateOf("") }
    var cryptoAddressOrEmail by remember { mutableStateOf("") }

    val amountUsd = amountUsdText.toDoubleOrNull() ?: 0.0
    val amountBdt = amountUsd * bdtExchangeRate
    val isAmountValid = amountUsd >= selectedMethod.minWithdrawUsd && amountUsd <= availableBalanceUsd && amountUsd <= maximumWithdrawalUsd

    val isFormComplete = when (selectedMethod) {
        CreatorPayoutMethod.BKASH, CreatorPayoutMethod.NAGAD, CreatorPayoutMethod.ROCKET -> phoneNumber.length >= 11
        CreatorPayoutMethod.BANK -> bankName.isNotBlank() && accountHolderName.isNotBlank() && accountNumber.length >= 8
        CreatorPayoutMethod.BINANCE, CreatorPayoutMethod.PAYPAL -> cryptoAddressOrEmail.length >= 5
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Withdraw Earnings",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00B894).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "0% Fee ⚡",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00B894),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Instant payout to your local Bangladeshi accounts",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Balance Banner
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF00B894), Color(0xFF0984E3))
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Available to Withdraw", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", availableBalanceUsd)} USD",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "≈ ৳${String.format(Locale.US, "%.2f", availableBalanceUsd * bdtExchangeRate)} BDT (@ ৳120/$)",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("💸", fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Payout Methods Selection
                    item {
                        Text(
                            text = "Select Payout Gateway",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CreatorPayoutMethod.values().toList().chunked(2).forEach { rowMethods ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowMethods.forEach { method ->
                                        val isSelected = selectedMethod == method
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedMethod = method }
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) method.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) method.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(text = method.icon, fontSize = 20.sp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = method.displayName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        color = if (isSelected) method.color else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = method.bengaliName,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = method.color,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Amount Input & Quick Chips
                    item {
                        Text(
                            text = "Withdrawal Amount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = amountUsdText,
                            onValueChange = { amountUsdText = it },
                            label = { Text("Amount in USD ($)") },
                            leadingIcon = { Text("$", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                Text(
                                    text = "≈ ৳${String.format(Locale.US, "%.0f", amountBdt)} BDT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF00B894),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            isError = amountUsd > 0 && !isAmountValid
                        )

                        if (amountUsd > availableBalanceUsd) {
                            Text(
                                text = "Amount exceeds available balance ($${String.format(Locale.US, "%.2f", availableBalanceUsd)})",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        } else if (amountUsd > 0 && amountUsd < selectedMethod.minWithdrawUsd) {
                            Text(
                                text = "Minimum withdrawal for ${selectedMethod.displayName} is $${selectedMethod.minWithdrawUsd} (৳${(selectedMethod.minWithdrawUsd * bdtExchangeRate).toInt()})",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(10.0, 25.0, 50.0, 100.0).forEach { chipAmount ->
                                val isSelected = amountUsdText == String.format(Locale.US, "%.2f", chipAmount)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { amountUsdText = String.format(Locale.US, "%.2f", chipAmount) },
                                    label = { Text("$$chipAmount", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            FilterChip(
                                selected = amountUsdText == String.format(Locale.US, "%.2f", availableBalanceUsd),
                                onClick = { amountUsdText = String.format(Locale.US, "%.2f", availableBalanceUsd) },
                                label = { Text("ALL", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Dynamic Account Form Fields based on Selected Method
                    item {
                        Text(
                            text = "Account Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        when (selectedMethod) {
                            CreatorPayoutMethod.BKASH, CreatorPayoutMethod.NAGAD, CreatorPayoutMethod.ROCKET -> {
                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("${selectedMethod.displayName} Mobile Number (11 digits)") },
                                    placeholder = { Text("017XXXXXXXX") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = selectedMethod.color) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "💡 Payouts will be sent via official ${selectedMethod.displayName} disbursement within 1-6 hours.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CreatorPayoutMethod.BANK -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = bankName,
                                        onValueChange = { bankName = it },
                                        label = { Text("Bank Name") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = accountHolderName,
                                        onValueChange = { accountHolderName = it },
                                        label = { Text("Account Holder Name (as in NID/Bank)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = accountNumber,
                                        onValueChange = { accountNumber = it },
                                        label = { Text("Account Number") },
                                        placeholder = { Text("e.g. 2050213010045678") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = branchName,
                                            onValueChange = { branchName = it },
                                            label = { Text("Branch Name") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = routingNumber,
                                            onValueChange = { routingNumber = it },
                                            label = { Text("Routing No (Opt)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            CreatorPayoutMethod.BINANCE -> {
                                OutlinedTextField(
                                    value = cryptoAddressOrEmail,
                                    onValueChange = { cryptoAddressOrEmail = it },
                                    label = { Text("Binance Pay ID / USDT TRC-20 Address") },
                                    placeholder = { Text("Pay ID or T...") },
                                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = selectedMethod.color) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            CreatorPayoutMethod.PAYPAL -> {
                                OutlinedTextField(
                                    value = cryptoAddressOrEmail,
                                    onValueChange = { cryptoAddressOrEmail = it },
                                    label = { Text("PayPal / Payoneer Email Address") },
                                    placeholder = { Text("you@email.com") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = selectedMethod.color) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Payout Summary Card
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Payout Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Requested USD", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$${String.format(Locale.US, "%.2f", amountUsd)} USD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Disbursement in BDT", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("৳${String.format(Locale.US, "%.2f", amountBdt)} BDT", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00B894))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Disbursement Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("FREE (0.00)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Estimated Processing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("1 - 6 Hours Instant", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0984E3))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Submit Button
                Button(
                    onClick = {
                        val details = when (selectedMethod) {
                            CreatorPayoutMethod.BKASH, CreatorPayoutMethod.NAGAD, CreatorPayoutMethod.ROCKET ->
                                "${selectedMethod.displayName}: $phoneNumber"
                            CreatorPayoutMethod.BANK ->
                                "Bank: $bankName | A/C: $accountNumber | Name: $accountHolderName | Branch: $branchName ${if (routingNumber.isNotBlank()) "| Routing: $routingNumber" else ""}"
                            CreatorPayoutMethod.BINANCE ->
                                "Binance/USDT: $cryptoAddressOrEmail"
                            CreatorPayoutMethod.PAYPAL ->
                                "PayPal/Payoneer: $cryptoAddressOrEmail"
                        }
                        onWithdrawSubmit(amountUsd, selectedMethod.displayName, details)
                    },
                    enabled = isAmountValid && isFormComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B894)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text(
                            text = "Submit Withdrawal of $${String.format(Locale.US, "%.2f", amountUsd)} (৳${amountBdt.toInt()})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
