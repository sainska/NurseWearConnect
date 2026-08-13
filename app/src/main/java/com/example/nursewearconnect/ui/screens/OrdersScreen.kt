package com.example.nursewearconnect.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.components.NursePullToRefresh
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.nursewearconnect.ui.components.EmptyState
import com.example.nursewearconnect.ui.components.OrderIllustration
import com.example.nursewearconnect.ui.components.OrderTimelineView
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

data class UpdateNotification(
    val title: String,
    val description: String,
    val time: String,
    val isUnread: Boolean,
    val icon: ImageVector,
    val color: Color,
    val bgColor: Color
)

@Composable
fun LoyaltyTierProgress(points: Int, tier: String) {
    val nextTierPoints = when(tier.lowercase()) {
        "bronze" -> 1000
        "silver" -> 5000
        "gold" -> 10000
        else -> 20000
    }
    val progress = (points.toFloat() / nextTierPoints).coerceIn(0f, 1f)
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Brand600,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Your Status: ${tier.uppercase()}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Path to Gold Tier",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Stars,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$points Points",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "Next Tier: $nextTierPoints",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onNavigateToNotifications: () -> Unit = {},
    onSupportClick: () -> Unit = {},
    onOrderClick: (String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("Active") }
    val filters = listOf("Active", "Processing", "Delivered", "Cancelled", "Returned")

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSupportDialog by remember { mutableStateOf(false) }

    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = uiState.adminEmail,
            adminPhone = uiState.adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = "Order Inquiry"
        )
    }

    // Observe receipt file generation
    LaunchedEffect(uiState.receiptFile) {
        uiState.receiptFile?.let { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Open Receipt"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(bottom = innerPadding.calculateBottomPadding())
    ) {
        // Custom Responsive Header
        Surface(
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Orders & Tracking",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = Slate900,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box {
                            Icon(
                                Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                modifier = Modifier.size(24.dp),
                                tint = Slate900
                            )
                            if (uiState.unreadNotificationsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFF43F5E), CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }

                // Filter Bar integrated into the sticky header
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        FilterChip(
                            label = filter,
                            isSelected = filter.contains(selectedFilter),
                            onClick = { selectedFilter = filter.split(" ")[0] }
                        )
                    }
                }
            }
        }

        // Loyalty Progress Gamification
        LoyaltyTierProgress(points = uiState.userPoints, tier = uiState.userTier)

        NursePullToRefresh(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadHomeData() },
            screenIcon = Icons.Default.Inventory2,
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.isLoading && uiState.allOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand600)
                }
            } else {
                val filteredOrders = uiState.allOrders.filter { order ->
                    val status = order["status"]?.toString()?.lowercase() ?: ""
                    when (selectedFilter) {
                        "Active" -> status in listOf("pending", "processing", "shipped", "paid")
                        "Processing" -> status == "processing"
                        "Delivered" -> status == "delivered"
                        "Cancelled" -> status == "cancelled"
                        "Returned" -> status == "returned"
                        else -> true
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Active Order Section (only show if Active filter is selected and there's an active order)
                    if (selectedFilter == "Active") {
                        val activeOrder = filteredOrders.find { it["status"]?.toString()?.lowercase() in listOf("pending", "processing", "shipped", "paid") }
                        if (activeOrder != null) {
                            val orderId = activeOrder["id"]?.toString() ?: ""
                            val history = uiState.orderHistory[orderId] ?: emptyList()
                            
                            item {
                                LaunchedEffect(orderId) {
                                    if (orderId.isNotEmpty()) {
                                        viewModel.loadOrderStatusHistory(orderId)
                                    }
                                }

                                ActiveOrderCard(
                                    order = activeOrder, 
                                    history = history,
                                    onSupportClick = { showSupportDialog = true },
                                    onDownloadReceipt = { viewModel.generateDigitalReceipt(activeOrder["id"]?.toString() ?: "", context) },
                                    isGenerating = uiState.isGeneratingPdf,
                                    onClick = { onOrderClick(orderId) }
                                )
                            }
                        }
                    }

                    // Orders Section
                    item {
                        SectionHeader(title = if (selectedFilter == "Active") "Past Orders" else "$selectedFilter Orders")
                        if (filteredOrders.isEmpty()) {
                            EmptyOrdersState()
                        }
                    }

                    items(filteredOrders.filter { order -> 
                        // If "Active" filter, we already showed the top one in ActiveOrderCard, but let's show all in the list for now or filter out the active one
                        if (selectedFilter == "Active") {
                            // Optionally hide the one shown above
                            // order["id"] != activeOrder?.get("id")
                            true
                        } else true
                    }) { order ->
                        PastOrderCard(
                            order = order,
                            onDownloadReceipt = { viewModel.generateDigitalReceipt(order["id"]?.toString() ?: "", context) },
                            onReorder = { viewModel.reorder(it) },
                            isGenerating = uiState.isGeneratingPdf,
                            onClick = { onOrderClick(order["id"]?.toString() ?: "") },
                            currency = uiState.selectedCurrency,
                            exchangeRates = uiState.exchangeRates
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyOrdersState() {
    EmptyState(
        illustration = { OrderIllustration() },
        title = "No orders yet",
        description = "When you place an order, it will appear here for you to track.",
        modifier = Modifier.padding(top = 40.dp)
    )
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Brand600 else Color.White,
        border = if (isSelected) null else BorderStroke(1.dp, Slate200),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Color.White else Slate600,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, badge: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Slate800
        )
        if (badge != null) {
            Surface(
                color = Brand50,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand700,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ActiveOrderCard(
    order: Map<String, Any>, 
    history: List<Map<String, Any>>,
    onSupportClick: () -> Unit,
    onDownloadReceipt: () -> Unit,
    isGenerating: Boolean = false,
    onClick: () -> Unit
) {
    val currentStatus = order["status"]?.toString()?.lowercase() ?: "pending"
    val orderId = order["id"]?.toString() ?: ""
    val orderItems = order["order_items"] as? List<Map<String, Any>> ?: emptyList()
    
    val currentStep = when (currentStatus) {
        "pending" -> 0
        "processing" -> 2
        "shipped", "in transit" -> 2
        "delivered" -> 3
        else -> 0
    }
    
    // Adjust step if we have payment confirmation in history
    val isPaid = order["payment_status"]?.toString()?.lowercase() == "paid"
    val finalStep = if (isPaid && currentStep < 1) 1 else currentStep

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Brand50,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = Brand600
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Order #${orderId.takeLast(8)}",
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        "Last Update: ${history.firstOrNull()?.get("created_at")?.toString()?.split("T")?.firstOrNull() ?: "Just now"}",
                        fontSize = 13.sp,
                        color = Slate500
                    )
                }
                Surface(
                    color = Color(0xFFE0F2FE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        order["status"]?.toString() ?: "Unknown",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OrderTimelineView(currentStep = finalStep)
            
            if (orderItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Order Items", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(orderItems) { item ->
                        val product = item["products"] as? Map<*, *>
                        val productName = product?.get("name")?.toString() ?: "Product"
                        val productImages = product?.get("images") as? List<*>
                        val imageUrl = productImages?.firstOrNull()?.toString()

                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Slate100)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(32.dp).background(Color.White, RoundedCornerShape(4.dp))) {
                                    if (imageUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.ShoppingBag, null, tint = Slate200, modifier = Modifier.padding(4.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(productName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800, maxLines = 1)
                                    Text("${item["quantity"]}x Size ${item["size"]}", fontSize = 10.sp, color = Slate600)
                                }
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Latest: ${history.firstOrNull()?.get("notes") ?: "Order is being processed"}",
                    fontSize = 12.sp,
                    color = Slate600,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSupportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate50),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = Slate700, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Help", color = Slate700, fontWeight = FontWeight.SemiBold)
                }

                if (isPaid) {
                    Button(
                        onClick = onDownloadReceipt,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand600),
                        contentPadding = PaddingValues(12.dp),
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Receipt", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatesList(notifications: List<Map<String, Any>>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            notifications.forEachIndexed { index, notification ->
                UpdateItem(
                    title = notification["title"]?.toString() ?: "Order Update",
                    description = notification["message"]?.toString() ?: "Status updated",
                    time = "2h ago",
                    icon = Icons.Default.Inventory,
                    color = Brand600,
                    bgColor = Brand50
                )
                if (index < notifications.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Slate100)
                }
            }
        }
    }
}

@Composable
fun UpdateItem(
    title: String,
    description: String,
    time: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = bgColor,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp), tint = color)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
            Text(description, fontSize = 13.sp, color = Slate500, maxLines = 1)
        }
        Text(time, fontSize = 12.sp, color = Slate400)
    }
}

@Composable
fun PastOrderCard(
    order: Map<String, Any>, 
    onDownloadReceipt: () -> Unit,
    onReorder: (String) -> Unit,
    isGenerating: Boolean = false,
    onClick: () -> Unit,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    val isPaid = order["payment_status"]?.toString()?.lowercase() == "paid"
    val orderId = order["id"]?.toString() ?: ""
    val orderItems = order["order_items"] as? List<Map<String, Any>> ?: emptyList()
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Slate100, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Slate500)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Order #${orderId.takeLast(8)}",
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        "Total: ${AppUtils.formatCurrency((order["total_amount"] as? Number)?.toDouble(), currency, exchangeRates)} • ${orderItems.size} items",
                        fontSize = 13.sp,
                        color = Slate500
                    )
                }

                if (isPaid) {
                    IconButton(
                        onClick = onDownloadReceipt,
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Brand600, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = "Download Receipt", tint = Brand600)
                        }
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Slate400
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    orderItems.forEach { item ->
                        val product = item["products"] as? Map<*, *>
                        val productName = product?.get("name")?.toString() ?: "Product"
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "• ${item["quantity"]}x ${item["size"]}",
                                fontSize = 12.sp,
                                color = Slate600,
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = productName,
                                fontSize = 12.sp,
                                color = Slate800,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { onReorder(orderId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand50, contentColor = Brand700),
                        border = BorderStroke(1.dp, Brand100)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reorder Items", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
