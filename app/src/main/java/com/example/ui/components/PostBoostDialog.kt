package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PostBoostCampaign
import com.example.data.model.PostEntity
import com.example.ui.theme.*
import java.util.Locale
import java.util.UUID

@Composable
fun PostBoostDialog(
    post: PostEntity,
    walletBalanceUsd: Double,
    onDismiss: () -> Unit,
    onBoostConfirmed: (campaign: PostBoostCampaign, payWithWallet: Boolean, gateway: PaymentGateway?) -> Unit
) {
    var selectedGoal by remember { mutableStateOf("Reach & Engagement") }
    var selectedAudience by remember { mutableStateOf("All Bangladesh (18-35)") }
    var durationDays by remember { mutableStateOf(3) }
    var dailyBudgetBdt by remember { mutableStateOf(200) }

    var payMethod by remember { mutableStateOf("WALLET") } // "WALLET" or "GATEWAY"
    var selectedGateway by remember { mutableStateOf(PaymentGateway.BKASH) }
    var isSuccess by remember { mutableStateOf(false) }

    val totalCostBdt = durationDays * dailyBudgetBdt
    val totalCostUsd = totalCostBdt / 120.0
    val walletBalanceBdt = walletBalanceUsd * 120.0
    val canPayWithWallet = walletBalanceUsd >= totalCostUsd

    // Estimated Reach formula
    val minReach = (totalCostBdt * 35).toInt()
    val maxReach = (totalCostBdt * 90).toInt()
    val formattedReach = "${String.format(Locale.US, "%,d", minReach)} - ${String.format(Locale.US, "%,d", maxReach)}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
                .testTag("post_boost_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                if (!isSuccess) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Brush.linearGradient(listOf(InstagramPink, InstagramPurple)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = "Boost & Promote Post",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Reach thousands of new targeted viewers",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Post Preview Strip
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    VynImage(
                                        imageResName = post.postImageRes,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = post.caption.ifBlank { "Post #${post.id}" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${post.likesCount} likes · ${post.commentsCount} comments",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = InstagramPink.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "SPONSORED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = InstagramPink,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Campaign Goal
                        item {
                            Text("1. Select Boost Goal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            listOf(
                                "Reach & Engagement" to "Maximize views, likes and comment reactions",
                                "Profile Visits & Growth" to "Direct users to visit your profile & follow",
                                "Direct Messages (DMs)" to "Encourage clients to message you directly"
                            ).forEach { (goalTitle, goalDesc) ->
                                val isSelected = selectedGoal == goalTitle
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) InstagramPurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, InstagramPurple) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedGoal = goalTitle }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedGoal = goalTitle },
                                            colors = RadioButtonDefaults.colors(selectedColor = InstagramPurple)
                                        )
                                        Column {
                                            Text(goalTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(goalDesc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Target Audience
                        item {
                            Text("2. Target Audience", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("All Bangladesh", "Dhaka & Ctg", "Creators & Tech").forEach { aud ->
                                    val isSelected = selectedAudience.startsWith(aud)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedAudience = "$aud (18-35)" }
                                    ) {
                                        Text(
                                            text = aud,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Budget & Duration
                        item {
                            Text("3. Duration & Daily Budget", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Duration Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 3, 7, 14).forEach { days ->
                                    val isSelected = durationDays == days
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) InstagramPink else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { durationDays = days }
                                    ) {
                                        Text(
                                            text = "$days Days",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Daily Budget Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(100, 200, 500, 1000).forEach { budget ->
                                    val isSelected = dailyBudgetBdt == budget
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFF00B894) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { dailyBudgetBdt = budget }
                                    ) {
                                        Text(
                                            text = "৳$budget/day",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Estimated Reach Banner
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D3436))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Estimated Reach", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                        Text(formattedReach, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF00CEC9))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Total Cost", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                        Text("৳$totalCostBdt (≈$${String.format(Locale.US, "%.2f", totalCostUsd)})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF7675))
                                    }
                                }
                            }
                        }

                        // 6. Payment Selection
                        item {
                            Text("4. Payment Method", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Option 1: Wallet Balance
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (payMethod == "WALLET") Color(0xFF0984E3).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = if (payMethod == "WALLET") androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0984E3)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { payMethod = "WALLET" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RadioButton(selected = payMethod == "WALLET", onClick = { payMethod = "WALLET" })
                                        Column {
                                            Text("Vyn9 Wallet Balance", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Available: $${String.format(Locale.US, "%.2f", walletBalanceUsd)} (≈৳${walletBalanceBdt.toInt()})", fontSize = 10.sp, color = Color(0xFF00B894))
                                        }
                                    }
                                    if (!canPayWithWallet) {
                                        Text("Insufficient", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Option 2: Instant Gateway (bKash / Nagad / Cards)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (payMethod == "GATEWAY") InstagramPink.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = if (payMethod == "GATEWAY") androidx.compose.foundation.BorderStroke(1.5.dp, InstagramPink) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { payMethod = "GATEWAY" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RadioButton(selected = payMethod == "GATEWAY", onClick = { payMethod = "GATEWAY" })
                                        Text("Direct Mobile Banking / Card", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    if (payMethod == "GATEWAY") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(PaymentGateway.BKASH, PaymentGateway.NAGAD, PaymentGateway.SSLCOMMERZ, PaymentGateway.STRIPE).forEach { gw ->
                                                val isSelected = selectedGateway == gw
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) gw.themeColor else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { selectedGateway = gw }
                                                ) {
                                                    Text(
                                                        text = gw.displayName.split(" ")[0],
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Launch Campaign Button
                    Button(
                        onClick = {
                            val campaign = PostBoostCampaign(
                                postId = post.id,
                                postTitle = post.caption.ifBlank { "Post #${post.id}" },
                                postImageRes = post.postImageRes,
                                budgetBdt = totalCostBdt.toDouble(),
                                budgetUsd = totalCostUsd,
                                durationDays = durationDays,
                                targetAudience = selectedAudience,
                                goal = selectedGoal,
                                status = "ACTIVE",
                                paymentGateway = if (payMethod == "WALLET") "Wallet Balance" else selectedGateway.displayName,
                                estimatedReach = formattedReach
                            )
                            onBoostConfirmed(campaign, payMethod == "WALLET", if (payMethod == "GATEWAY") selectedGateway else null)
                            isSuccess = true
                        },
                        enabled = if (payMethod == "WALLET") canPayWithWallet else true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("confirm_boost_campaign_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (payMethod == "WALLET") Color(0xFF0984E3) else selectedGateway.themeColor
                        )
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Launch Boost for ৳$totalCostBdt", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                } else {
                    // Campaign Launch Success Card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF00B894).copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00B894), modifier = Modifier.size(50.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Campaign Launched! 🚀", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your post is now actively sponsored and reaching audience in $selectedAudience.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Campaign Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("LIVE & ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Est. Deliveries", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formattedReach, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Duration", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$durationDays Days", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894))
                        ) {
                            Text("Awesome", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
