package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

enum class PaymentGateway(
    val id: String,
    val displayName: String,
    val bengaliName: String,
    val themeColor: Color,
    val iconVector: ImageVector,
    val currency: String
) {
    BKASH("bkash", "bKash Merchant", "বিকাশ পেমেন্ট", Color(0xFFE2136E), Icons.Default.AccountBalanceWallet, "BDT"),
    NAGAD("nagad", "Nagad Direct", "নগদ পে", Color(0xFFF7941D), Icons.Default.Payments, "BDT"),
    SSLCOMMERZ("sslcommerz", "SSLCommerz Cards & Banking", "ভিসা / মাস্টারকার্ড", Color(0xFF005F9E), Icons.Default.CreditCard, "BDT"),
    STRIPE("stripe", "Stripe Global Cards", "আন্তর্জাতিক কার্ড", Color(0xFF635BFF), Icons.Default.Payment, "USD"),
    ROCKET("rocket", "DBBL Rocket", "রকেট একাউন্ট", Color(0xFF8C3494), Icons.Default.PhoneAndroid, "BDT"),
    BINANCE("binance", "Binance Pay USDT", "বাইনান্স পে", Color(0xFFF3BA2F), Icons.Default.CurrencyBitcoin, "USDT")
}

@Composable
fun PaymentGatewayDialog(
    initialAmountBdt: Double = 500.0,
    title: String = "Add Funds to Wallet",
    description: String = "Top up your wallet balance securely via official payment gateways",
    onDismiss: () -> Unit,
    onPaymentSuccess: (amountUsd: Double, amountBdt: Double, gateway: PaymentGateway, trxId: String) -> Unit
) {
    var selectedGateway by remember { mutableStateOf(PaymentGateway.BKASH) }
    var currentStep by remember { mutableStateOf(1) } // 1: Select Gateway & Amount, 2: Gateway Auth / OTP, 3: Success Receipt

    var amountBdtStr by remember { mutableStateOf(initialAmountBdt.toInt().toString()) }
    val bdtAmount = amountBdtStr.toDoubleOrNull() ?: 0.0
    val usdAmount = bdtAmount / 120.0 // Standard 1 USD = 120 BDT exchange rate

    // Form inputs
    var phoneNumber by remember { mutableStateOf("01712345678") }
    var otpCode by remember { mutableStateOf("123456") }
    var pinCode by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("4532 8912 3456 7890") }
    var cardExpiry by remember { mutableStateOf("12/28") }
    var cardCvc by remember { mutableStateOf("888") }

    var isProcessing by remember { mutableStateOf(false) }
    var generatedTrxId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var otpCountdown by remember { mutableStateOf(45) }

    LaunchedEffect(currentStep) {
        if (currentStep == 2) {
            otpCountdown = 45
            while (otpCountdown > 0) {
                delay(1000)
                otpCountdown--
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .testTag("payment_gateway_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // ==========================================
                // STEP 1: GATEWAY & AMOUNT SELECTION
                // ==========================================
                if (currentStep == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset Amount Chips
                    Text(
                        text = "Select Deposit Amount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(100, 500, 1000, 2000, 5000).forEach { amt ->
                            val isSelected = amountBdtStr == amt.toString()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) selectedGateway.themeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { amountBdtStr = amt.toString() }
                            ) {
                                Text(
                                    text = "৳$amt",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Amount Input + Currency Converter display
                    OutlinedTextField(
                        value = amountBdtStr,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) amountBdtStr = it },
                        label = { Text("Custom Amount (BDT ৳)") },
                        leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = selectedGateway.themeColor) },
                        trailingIcon = {
                            Text(
                                text = "≈ $${String.format(Locale.US, "%.2f", usdAmount)} USD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gateways Grid
                    Text(
                        text = "Choose Payment Gateway",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentGateway.values().toList().chunked(2).forEach { rowGateways ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowGateways.forEach { gateway ->
                                    val isSelected = selectedGateway == gateway
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) gateway.themeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, gateway.themeColor) else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedGateway = gateway }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(gateway.themeColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = gateway.iconVector,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = gateway.displayName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = gateway.bengaliName,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (bdtAmount >= 50.0) {
                                currentStep = 2
                            }
                        },
                        enabled = bdtAmount >= 50.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("proceed_to_payment_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = selectedGateway.themeColor)
                    ) {
                        Text(
                            text = "Proceed to Pay ৳$amountBdtStr (${selectedGateway.displayName})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                // ==========================================
                // STEP 2: GATEWAY SECURE CHECKOUT FLOW
                // ==========================================
                if (currentStep == 2) {
                    // Gateway Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(selectedGateway.themeColor)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedGateway.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Merchant ID: FLAREOFFICIAL-ENTERPRISE-BD",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "৳$amountBdtStr",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (selectedGateway) {
                        PaymentGateway.BKASH, PaymentGateway.NAGAD, PaymentGateway.ROCKET -> {
                            // Mobile Banking OTP & PIN Flow
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("${selectedGateway.displayName} Account Number") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = selectedGateway.themeColor) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { if (it.length <= 6) otpCode = it },
                                        label = { Text("Verification OTP Code") },
                                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = selectedGateway.themeColor) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = if (otpCountdown > 0) "${otpCountdown}s" else "Resend",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = selectedGateway.themeColor,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = pinCode,
                                    onValueChange = { if (it.length <= 5) pinCode = it },
                                    label = { Text("Enter ${selectedGateway.displayName} PIN") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = selectedGateway.themeColor) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "🔒 Your PIN is 256-bit encrypted and verified directly with ${selectedGateway.displayName} Core Banking.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        PaymentGateway.SSLCOMMERZ, PaymentGateway.STRIPE -> {
                            // Credit / Debit Card Flow
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = cardNumber,
                                    onValueChange = { cardNumber = it },
                                    label = { Text("Card Number (Visa / Mastercard / AMEX)") },
                                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = selectedGateway.themeColor) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = cardExpiry,
                                        onValueChange = { cardExpiry = it },
                                        label = { Text("MM/YY") },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = cardCvc,
                                        onValueChange = { cardCvc = it },
                                        label = { Text("CVC / CVV") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text(
                                    text = "🛡️ 3D Secure Verification is enabled. Supported by SSLCommerz & Stripe.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        PaymentGateway.BINANCE -> {
                            // Binance Pay USDT QR Flow
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = "QR Code",
                                        tint = Color.Black,
                                        modifier = Modifier.size(120.dp)
                                    )
                                }
                                Text(
                                    text = "Scan with Binance App or send to Pay ID: 89421038",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Amount: ${String.format(Locale.US, "%.2f", usdAmount)} USDT",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF3BA2F)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = selectedGateway.themeColor, strokeWidth = 3.dp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Back")
                            }

                            Button(
                                onClick = {
                                    isProcessing = true
                                    val prefix = when (selectedGateway) {
                                        PaymentGateway.BKASH -> "TRX-BK-"
                                        PaymentGateway.NAGAD -> "TRX-NG-"
                                        PaymentGateway.SSLCOMMERZ -> "SSL-TX-"
                                        PaymentGateway.STRIPE -> "STRIPE-CH-"
                                        PaymentGateway.ROCKET -> "RKT-TX-"
                                        PaymentGateway.BINANCE -> "BIN-USDT-"
                                    }
                                    val newTrx = prefix + UUID.randomUUID().toString().take(8).uppercase()
                                    generatedTrxId = newTrx

                                    // INTEGRATION POINT:
                                    // 1. Initialize Checkout (e.g., BKash Create Payment)
                                    // 2. Redirect to Gateway URL or show In-App SDK
                                    // 3. Capture/Verify Payment server-side
                                    
                                    // For now, we simulate a network delay and success.
                                    scope.launch {
                                        delay(1500)
                                        isProcessing = false
                                        currentStep = 3
                                        onPaymentSuccess(usdAmount, bdtAmount, selectedGateway, newTrx)
                                    }
                                },
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp)
                                    .testTag("confirm_gateway_payment_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = selectedGateway.themeColor)
                            ) {
                                Text(
                                    text = "Confirm & Pay ৳$amountBdtStr",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // STEP 3: TRANSACTION RECEIPT & CONFIRMATION
                // ==========================================
                if (currentStep == 3) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF00B894).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF00B894),
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Payment Completed Successfully!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "৳$amountBdtStr (+$${String.format(Locale.US, "%.2f", usdAmount)} USD) has been added to your wallet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Receipt Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReceiptRow("Gateway", selectedGateway.displayName)
                                ReceiptRow("Transaction ID", generatedTrxId)
                                ReceiptRow("Amount Paid", "৳$amountBdtStr BDT")
                                ReceiptRow("Credited Balance", "+$${String.format(Locale.US, "%.2f", usdAmount)} USD")
                                ReceiptRow("Status", "COMPLETED", isStatus = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894))
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, isStatus: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (label.contains("ID")) FontFamily.Monospace else FontFamily.Default,
            color = if (isStatus) Color(0xFF00B894) else MaterialTheme.colorScheme.onSurface
        )
    }
}
