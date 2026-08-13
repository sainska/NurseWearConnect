package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrderDashboardScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOrderDetails: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    var showAuditLogs by remember { mutableStateOf(false) }
    var selectedOrderIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkActionMenu by remember { mutableStateOf(false) }
    
    val statuses = listOf("All", "Pending", "Paid", "Processing", "Shipped", "Delivered", "Cancelled")

    LaunchedEffect(Unit) {
        viewModel.loadAdminData() // Load data including orders and logs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (selectedOrderIds.isNotEmpty()) "${selectedOrderIds.size} Selected" 
                        else if (showAuditLogs) "Audit Log Explorer" 
                        else "Master Order Dashboard", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (selectedOrderIds.isNotEmpty()) selectedOrderIds = emptySet()
                        else if (showAuditLogs) showAuditLogs = false 
                        else onBack() 
                    }) {
                        Icon(
                            if (selectedOrderIds.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedOrderIds.isNotEmpty()) {
                        IconButton(onClick = { showBulkActionMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Bulk Actions")
                        }
                    } else {
                        IconButton(onClick = { showAuditLogs = !showAuditLogs }) {
                            Icon(if (showAuditLogs) Icons.Default.Dashboard else Icons.Default.ReceiptLong, contentDescription = "Logs")
                        }
                        IconButton(onClick = { viewModel.loadAdminData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = Slate50
    ) { padding ->
        if (showAuditLogs) {
            AuditLogExplorer(uiState.systemLogs, Modifier.padding(padding))
        } else {
            Column(modifier = Modifier.padding(padding)) {
                // Search and Filter Bar
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search by Order ID or Customer...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Brand600,
                                unfocusedBorderColor = Slate200
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(statuses) { status ->
                                FilterChip(
                                    selected = selectedStatus == status,
                                    onClick = { selectedStatus = status },
                                    label = { Text(status) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Brand600,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Brand600)
                    }
                } else {
                    val filteredOrders = uiState.allOrders.filter { order ->
                        val matchesSearch = (order["id"]?.toString()?.contains(searchQuery, ignoreCase = true) ?: false) ||
                                          (order["profiles"]?.let { it as? Map<*, *> }?.get("full_name")?.toString()?.contains(searchQuery, ignoreCase = true) ?: false)
                        val matchesStatus = selectedStatus == "All" || order["status"]?.toString()?.equals(selectedStatus, ignoreCase = true) == true
                        matchesSearch && matchesStatus
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            DashboardSummaryRow(filteredOrders)
                        }

                        items(filteredOrders) { order ->
                            val orderId = order["id"].toString()
                            val isSelected = selectedOrderIds.contains(orderId)
                            
                            AdminOrderCard(
                                order = order,
                                isSelected = isSelected,
                                onClick = { 
                                    if (selectedOrderIds.isNotEmpty()) {
                                        selectedOrderIds = if (isSelected) selectedOrderIds - orderId else selectedOrderIds + orderId
                                    } else {
                                        onOrderDetails(orderId)
                                    }
                                },
                                onLongClick = {
                                    selectedOrderIds = selectedOrderIds + orderId
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showBulkActionMenu) {
            AlertDialog(
                onDismissRequest = { showBulkActionMenu = false },
                title = { Text("Bulk Actions (${selectedOrderIds.size} Orders)") },
                text = {
                    Column {
                        val actions = listOf("Processing", "Shipped", "Delivered", "Cancelled")
                        actions.forEach { action ->
                            ListItem(
                                headlineContent = { Text("Mark as $action") },
                                leadingContent = { 
                                    Icon(
                                        when(action) {
                                            "Processing" -> Icons.Default.Settings
                                            "Shipped" -> Icons.Default.LocalShipping
                                            "Delivered" -> Icons.Default.CheckCircle
                                            "Cancelled" -> Icons.Default.Cancel
                                            else -> Icons.Default.Edit
                                        }, 
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable {
                                    viewModel.bulkUpdateOrderStatus(selectedOrderIds.toList(), action.lowercase())
                                    selectedOrderIds = emptySet()
                                    showBulkActionMenu = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBulkActionMenu = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AuditLogExplorer(logs: List<Map<String, Any>>, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Brand600, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            log["action"]?.toString() ?: "Unknown Action",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            log["created_at"]?.toString()?.split("T")?.firstOrNull() ?: "",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        log["description"]?.toString() ?: "",
                        fontSize = 13.sp,
                        color = Slate700
                    )
                    Text(
                        "User: ${log["user_email"] ?: "System"}",
                        fontSize = 11.sp,
                        color = Slate400,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardSummaryRow(orders: List<Map<String, Any>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            label = "Total Orders",
            value = orders.size.toString(),
            icon = Icons.Default.ShoppingCart,
            color = Brand600
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            label = "Total Revenue",
            value = "KSh ${orders.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 }.toInt()}",
            icon = Icons.Default.Payments,
            color = Color(0xFF10B981)
        )
    }
    
    Spacer(modifier = Modifier.height(12.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val commission = orders.sumOf { order ->
            (order["order_items"] as? List<Map<String, Any>>)?.sumOf { item ->
                val price = (item["unit_price"] as? Number)?.toDouble() ?: 0.0
                val qty = (item["quantity"] as? Number)?.toInt() ?: 0
                val rate = (item["commission_rate"] as? Number)?.toDouble() ?: 10.0
                (price * qty * (rate / 100.0))
            } ?: 0.0
        }
        
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            label = "Est. Commission",
            value = "KSh ${commission.toInt()}",
            icon = Icons.Default.AccountBalance,
            color = Color(0xFF8B5CF6)
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            label = "Net Payouts",
            value = "KSh ${(orders.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 } - commission).toInt()}",
            icon = Icons.Default.AccountBalanceWallet,
            color = Color(0xFFF59E0B)
        )
    }
}

@Composable
fun SummaryStatCard(modifier: Modifier, label: String, value: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Slate500)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate900)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AdminOrderCard(
    order: Map<String, Any>, 
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val status = order["status"]?.toString() ?: "Unknown"
    val customerName = (order["profiles"] as? Map<*, *>)?.get("full_name")?.toString() ?: "Unknown Customer"
    val date = order["created_at"]?.toString()?.split("T")?.firstOrNull() ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Brand50 else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Brand600) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = "Selected", 
                            tint = Brand600,
                            modifier = Modifier.size(20.dp).padding(end = 8.dp)
                        )
                    }
                    Column {
                        Text(
                            "Order #${order["id"]?.toString()?.takeLast(8)}",
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Brand700 else Slate900
                        )
                        Text(date, fontSize = 12.sp, color = Slate500)
                    }
                }
                StatusBadge(status)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = if (isSelected) Brand100 else Slate100)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = Slate400)
                Spacer(modifier = Modifier.width(8.dp))
                Text(customerName, fontSize = 14.sp, color = Slate700)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "KSh ${order["total_amount"]}",
                    fontWeight = FontWeight.ExtraBold,
                    color = Brand700
                )
            }
            
            val itemsCount = (order["order_items"] as? List<*>)?.size ?: 0
            Text(
                "$itemsCount items from multiple vendors",
                fontSize = 12.sp,
                color = Slate500,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "pending" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        "paid" -> Color(0xFFD1FAE5) to Color(0xFF065F46)
        "processing" -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        "shipped" -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
        "delivered" -> Color(0xFFD1FAE5) to Color(0xFF065F46)
        "cancelled" -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        else -> Slate100 to Slate600
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            status.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
