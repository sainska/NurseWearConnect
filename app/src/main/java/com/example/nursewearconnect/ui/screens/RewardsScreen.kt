package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(24.dp), tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("Rewards & Loyalty", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHomeData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh, 
                            contentDescription = "Refresh", 
                            tint = Color.White,
                            modifier = if (uiState.isLoading) Modifier.rotate(rotation) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand600,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.loyaltyHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Brand50
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Brand600,
                                    modifier = Modifier.size(40.dp)
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(80.dp),
                                    color = Brand600,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Loading your rewards...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Slate50),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tier Progress Card
                    item {
                        TierProgressCard(
                            points = uiState.userPoints,
                            currentTier = uiState.userTier,
                            allTiers = uiState.loyaltyTiers
                        )
                    }

                    // Wallet Balance Card
                    item {
                        WalletBalanceCard(balance = uiState.walletBalance)
                    }

                    // Benefits Section
                    item {
                        Text(
                            "Your Tier Benefits",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    item {
                        TierBenefitsList(
                            currentTier = uiState.userTier,
                            allTiers = uiState.loyaltyTiers
                        )
                    }

                    // Points History
                    item {
                        Text(
                            "Points History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    if (uiState.loyaltyHistory.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.History,
                                message = "No points history yet. Start shopping to earn rewards!"
                            )
                        }
                    } else {
                        items(uiState.loyaltyHistory) { history ->
                            LoyaltyHistoryItem(history)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TierProgressCard(
    points: Int,
    currentTier: String,
    allTiers: List<Map<String, Any>>
) {
    val tierInfo = allTiers.find { it["tier_name"]?.toString()?.lowercase() == currentTier.lowercase() }
    val nextTier = allTiers
        .filter { (it["min_points"] as? Number)?.toInt() ?: 0 > points }
        .minByOrNull { (it["min_points"] as? Number)?.toInt() ?: 0 }

    val progress = nextTier?.let {
        val min = (tierInfo?.get("min_points") as? Number)?.toFloat() ?: 0f
        val max = (it["min_points"] as? Number)?.toFloat() ?: 1f
        ((points - min) / (max - min)).coerceIn(0f, 1f)
    } ?: 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Brand50)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                currentTier.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Brand900
            )

            Text(
                "$points Points",
                style = MaterialTheme.typography.titleLarge,
                color = Brand600
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (nextTier != null) {
                val nextTierName = nextTier["tier_name"]?.toString()?.uppercase() ?: ""
                val pointsNeeded = (nextTier["min_points"] as? Number)?.toInt() ?: 0
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Progress to $nextTierName", style = MaterialTheme.typography.bodyMedium, color = Slate700)
                    Text("${pointsNeeded - points} points left", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Brand600,
                    trackColor = Slate200,
                )
            } else {
                Text(
                    "MAX LEVEL REACHED",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WalletBalanceCard(balance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Wallet Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    "KSh ${String.format(Locale.getDefault(), "%,.2f", balance)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            IconButton(
                onClick = { /* Handle top up */ },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Top Up", tint = Color.White)
            }
        }
    }
}

@Composable
fun TierBenefitsList(currentTier: String, allTiers: List<Map<String, Any>>) {
    val tier = allTiers.find { it["tier_name"]?.toString()?.lowercase() == currentTier.lowercase() }
    val benefits = tier?.get("benefits") as? List<*> ?: emptyList<Any>()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        benefits.forEach { benefit ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(benefit.toString(), style = MaterialTheme.typography.bodyMedium, color = Slate700)
            }
        }
    }
}

@Composable
fun LoyaltyHistoryItem(history: Map<String, Any>) {
    val points = (history["points"] as? Number)?.toInt() ?: 0
    val action = history["action_type"]?.toString() ?: "Loyalty Award"
    val date = history["created_at"]?.toString()?.split("T")?.firstOrNull() ?: ""
    val isGain = points >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGain) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (isGain) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        action.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, 
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(date, style = MaterialTheme.typography.bodySmall, color = Slate400)
                }
            }
            
            Text(
                "${if (isGain) "+" else ""}$points pts",
                fontWeight = FontWeight.Black,
                color = if (isGain) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Slate200
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Slate400,
            fontSize = 14.sp
        )
    }
}
