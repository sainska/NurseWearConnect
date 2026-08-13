package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionManagementScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Subscriptions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        if (uiState.subscriptions.isEmpty()) {
            EmptySubscriptionsView(innerPadding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SubscriptionInfoCard()
                }
                items(uiState.subscriptions) { subscription ->
                    SubscriptionItemCard(
                        subscription = subscription,
                        onStatusChange = { newStatus ->
                            val id = subscription["id"] as? String
                            if (id != null) {
                                viewModel.updateSubscriptionStatus(id, newStatus)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionInfoCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Brand50,
        border = BorderStroke(1.dp, Brand100)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, null, tint = Brand600)
            Spacer(Modifier.width(12.dp))
            Text(
                "Subscription orders are automatically generated and billed every 30 days. You can pause or cancel anytime.",
                fontSize = 13.sp,
                color = Brand800,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SubscriptionItemCard(
    subscription: Map<String, Any>,
    onStatusChange: (String) -> Unit
) {
    val product = subscription["products"] as? Map<*, *>
    val name = product?.get("name") as? String ?: "Subscription Item"
    val price = (product?.get("price_kes") as? Number)?.toInt() ?: 0
    val imageUrl = (product?.get("images") as? List<*>)?.firstOrNull() as? String
    val status = subscription["status"] as? String ?: "active"
    val quantity = (subscription["quantity"] as? Number)?.toInt() ?: 1
    val frequency = (subscription["frequency_days"] as? Number)?.toInt() ?: 30
    val nextDate = subscription["next_delivery_date"] as? String ?: "TBD"
    val size = subscription["size"] as? String ?: "M"
    val color = subscription["color"] as? String ?: "Navy"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate50)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("👕", fontSize = 32.sp)
                        }
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Surface(
                            color = if (status == "active") Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                status.uppercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status == "active") Color(0xFF166534) else Slate600
                            )
                        }
                    }
                    
                    Text("KES ${String.format("%,d", price)} / $frequency days", fontSize = 14.sp, color = Brand600, fontWeight = FontWeight.Bold)
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size: $size", fontSize = 12.sp, color = Slate500)
                        Text(" • ", color = Slate300)
                        Text("Color: $color", fontSize = 12.sp, color = Slate500)
                        Text(" • ", color = Slate300)
                        Text("Qty: $quantity", fontSize = 12.sp, color = Slate500)
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Slate50)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = Slate400, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Next Delivery", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                    }
                    Text(nextDate.split("T").first(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status == "active") {
                        OutlinedButton(
                            onClick = { onStatusChange("paused") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp), tint = Slate600)
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", fontSize = 13.sp, color = Slate600)
                        }
                    } else {
                        Button(
                            onClick = { onStatusChange("active") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                        ) {
                            Icon(Icons.Default.Loop, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySubscriptionsView(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(30.dp),
            color = Slate100
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Loop, null, modifier = Modifier.size(64.dp), tint = Slate300)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("No active subscriptions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Spacer(Modifier.height(8.dp))
        Text(
            "Subscribe to your favorite scrubs to get them delivered automatically every month with exclusive discounts.",
            fontSize = 15.sp,
            color = Slate500,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
