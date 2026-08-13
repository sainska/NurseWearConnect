package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogisticsTrackingScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logistics & Returns", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // "Try Before You Buy" Fitting Section
            item {
                FittingStatusCard(uiState.isFittingRequested, uiState.fittingDate, uiState.fittingSlot)
            }

            // Returns Section
            item {
                Text(
                    "Recent Return Requests",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            }

            if (uiState.returnRequests.isEmpty()) {
                item {
                    EmptyReturnsCard()
                }
            } else {
                items(uiState.returnRequests) { request ->
                    ReturnRequestCard(request)
                }
            }

            // General Logistics Info
            item {
                LogisticsInfoCard()
            }
        }
    }
}

@Composable
fun FittingStatusCard(isRequested: Boolean, date: String?, slot: String?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Brand100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Brand50
                ) {
                    Icon(Icons.Default.Event, null, tint = Brand600, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Home Fitting Service", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text("Try before you buy", fontSize = 12.sp, color = Slate500)
                }
                Spacer(Modifier.weight(1f))
                if (isRequested) {
                    Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                        Text("SCHEDULED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isRequested && date != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate50, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Slate400, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scheduled for: $date at $slot", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("A fitting agent will bring your selected items to your location.", fontSize = 11.sp, color = Slate500)
                }
            } else {
                Text(
                    "You haven't requested a home fitting yet. You can opt-in for this service during checkout for a small fee.",
                    fontSize = 13.sp,
                    color = Slate600,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun ReturnRequestCard(request: Map<String, Any>) {
    val orderId = request["order_id"]?.toString()?.take(8) ?: "N/A"
    val status = request["status"]?.toString() ?: "pending"
    val reason = request["reason"]?.toString() ?: "No reason provided"
    val date = request["created_at"]?.toString()?.split("T")?.first() ?: "Recently"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Replay, null, tint = Slate400, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Order #$orderId", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
                Surface(
                    color = when(status.lowercase()) {
                        "approved" -> Color(0xFFDCFCE7)
                        "pending" -> Color(0xFFFEF9C3)
                        else -> Color(0xFFF1F5F9)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        status.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when(status.lowercase()) {
                            "approved" -> Color(0xFF166534)
                            "pending" -> Color(0xFF854D0E)
                            else -> Slate600
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(reason, fontSize = 13.sp, color = Slate600)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Requested on: $date", fontSize = 11.sp, color = Slate400)
            }
        }
    }
}

@Composable
fun EmptyReturnsCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100, )
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Replay, null, tint = Slate200, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No active returns", fontSize = 14.sp, color = Slate400)
        }
    }
}

@Composable
fun LogisticsInfoCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Slate900
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("Standard Delivery Info", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "• Nairobi: Same-day delivery (Order before 11 AM)\n• Other Counties: 1-3 business days\n• Delivery Partners: Sendy, G4S, Wells Fargo\n• Free delivery for orders above KES 10,000",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )
        }
    }
}
