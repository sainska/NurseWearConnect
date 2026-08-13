package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.components.EmptyState
import com.example.nursewearconnect.ui.components.OrderIllustration
import com.example.nursewearconnect.ui.components.OrderTimelineView
import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorOrdersScreen(
    onBackClick: () -> Unit,
    onNavigateToMessages: (String) -> Unit = {},
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("New", "Processing", "Shipped", "Delivered", "Cancelled")
    var selectedOrderForDetail by remember { mutableStateOf<Map<String, Any>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

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

    selectedOrderForDetail?.let { map ->
        val customerName = map["customer_name"]?.toString() ?: "Unknown"
        val productName = map["product_name"]?.toString() ?: "Product"
        val status = map["item_status"]?.toString() ?: "Pending"
        val orderId = map["order_id"]?.toString()?.take(8) ?: ""
        val shippingAddress = map["shipping_address"]?.toString() ?: "No address provided"

        AlertDialog(
            onDismissRequest = { selectedOrderForDetail = null },
            title = { Text("Order Item Details #$orderId") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Customer: $customerName", fontWeight = FontWeight.Bold, color = Slate900)
                    Text("Date: ${map["created_at"]?.toString()?.split("T")?.get(0)}", fontSize = 13.sp, color = Slate500)
                    Text("Product: $productName", fontSize = 14.sp, color = Slate700)
                    Text("Status: $status", color = Brand600, fontWeight = FontWeight.SemiBold)
                    
                    Column(modifier = Modifier.fillMaxWidth().background(Slate50, RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Text("Shipping Address:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(shippingAddress, fontSize = 13.sp, color = Slate800)
                    }

                    OrderTimelineView(status = status)

                    HorizontalDivider(color = Slate100)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Item Total", fontWeight = FontWeight.Bold)
                        val unitPrice = (map["unit_price"] as? Number)?.toDouble() ?: 0.0
                        val qty = (map["quantity"] as? Number)?.toDouble() ?: 1.0
                        val total = unitPrice * qty
                        Text(AppUtils.formatCurrency(total, uiState.selectedCurrency, uiState.exchangeRates), fontWeight = FontWeight.Bold, color = Brand600)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedOrderForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = 1200.dp),
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(24.dp), tint = Brand600)
                            Spacer(Modifier.width(12.dp))
                            Text("Customer Orders", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            val csvData = viewModel.exportOrdersToCSV(isAdmin = false)
                            if (csvData.isNotEmpty()) {
                                AppUtils.exportAndShareData(
                                    context,
                                    csvData,
                                    "Vendor_Orders_${System.currentTimeMillis()}.csv"
                                )
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Export Orders", tint = Brand600)
                        }
                        IconButton(onClick = { viewModel.loadHomeData() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh, 
                                contentDescription = "Refresh", 
                                tint = if (uiState.isLoading) Brand600 else Slate400,
                                modifier = if (uiState.isLoading) Modifier.rotate(rotation) else Modifier
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Slate900
                    )
                )
            }
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 1200.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("Search by customer, product, or order ID") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) },
                        trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                        shape = RoundedCornerShape(12.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors(),
                        singleLine = true
                    )
                    
                    TabRow(
                        modifier = Modifier.fillMaxWidth(),
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = Brand600,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Brand600
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium) }
                            )
                        }
                    }
                }
            }

            val currentStatus = tabs[selectedTab].lowercase()
            val filteredOrders = uiState.vendorOrders.filter { map ->
                val s = map["item_status"]?.toString()?.lowercase() ?: ""
                val matchesStatus = s == currentStatus || (currentStatus == "new" && s == "pending")
                val matchesSearch = searchQuery.isEmpty() || 
                    map["customer_name"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                    map["product_name"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                    map["order_id"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                    map["customer_phone"]?.toString()?.contains(searchQuery) == true ||
                    map["customer_email"]?.toString()?.contains(searchQuery, ignoreCase = true) == true
                
                matchesStatus && matchesSearch
            }

            Box(modifier = Modifier.weight(1f).widthIn(max = 1200.dp).fillMaxWidth()) {
                if (uiState.isLoading && filteredOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = Brand50
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBag,
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
                            Text("Updating order queue...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                        }
                    }
                } else if (filteredOrders.isEmpty()) {
                    EmptyState(
                        illustration = { OrderIllustration() },
                        title = "No ${tabs[selectedTab]} Orders",
                        description = "When customers place new orders for your items, they will appear here.",
                        actionLabel = "Refresh Data",
                        onActionClick = { viewModel.loadHomeData() }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredOrders) { map ->
                            val unitPrice = (map["unit_price"] as? Number)?.toDouble() ?: 0.0
                            val quantity = (map["quantity"] as? Number)?.toInt() ?: 1
                            val total = unitPrice * quantity.toDouble()
                            
                            val order = VendorOrder(
                                id = (map["order_id"] as? String ?: "").takeLast(6).uppercase(),
                                fullId = map["order_id"] as? String ?: "",
                                customerName = map["customer_name"] as? String ?: "Customer",
                                itemName = map["product_name"] as? String ?: "Product",
                                quantity = quantity,
                                size = map["size"] as? String ?: "M",
                                total = total,
                                formattedTotal = AppUtils.formatCurrency(total, uiState.selectedCurrency, uiState.exchangeRates),
                                date = (map["created_at"] as? String)?.split("T")?.firstOrNull() ?: "",
                                status = map["item_status"] as? String ?: "Pending",
                                shippingAddress = map["shipping_address"]?.toString(),
                                isLate = map["is_late_fulfillment"] as? Boolean ?: false,
                                customerPhone = map["customer_phone"]?.toString(),
                                customerEmail = map["customer_email"]?.toString()
                            )

                            VendorOrderCard(
                                order = order,
                                onAccept = { viewModel.updateVendorOrderItemStatus(map["order_item_id"].toString(), order.fullId, "processing") },
                                onShip = { viewModel.updateVendorOrderItemStatus(map["order_item_id"].toString(), order.fullId, "shipped") },
                                onComplete = { viewModel.updateVendorOrderItemStatus(map["order_item_id"].toString(), order.fullId, "delivered") },
                                onCancel = { viewModel.updateVendorOrderItemStatus(map["order_item_id"].toString(), order.fullId, "cancelled") },
                                onChat = { map["customer_id"]?.let { onNavigateToMessages(it.toString()) } },
                                onClick = { selectedOrderForDetail = map }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VendorOrderCard(
    order: VendorOrder,
    onAccept: () -> Unit,
    onShip: () -> Unit = {},
    onComplete: () -> Unit,
    onCancel: () -> Unit = {},
    onChat: () -> Unit = {},
    onClick: () -> Unit
) {
    val isLate = order.isLate
    
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isLate && order.status.lowercase() == "pending") Color(0xFFFFF1F2) else Color.White,
        border = BorderStroke(1.dp, if (isLate && order.status.lowercase() == "pending") Color(0xFFFECDD3) else Slate100),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isLate && order.status.lowercase() == "pending") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFE11D48), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Action Required: Past 24h fulfillment SLA",
                        color = Color(0xFFE11D48),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Order #${order.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                    Text(order.date, fontSize = 12.sp, color = Slate500)
                }
                Surface(
                    color = when(order.status.lowercase()) {
                        "pending" -> Color(0xFFFEF3C7)
                        "processing" -> Brand50
                        "shipped" -> Color(0xFFEFF6FF)
                        "delivered" -> Color(0xFFECFDF5)
                        else -> Color(0xFFFEE2E2)
                    },
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        order.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when(order.status.lowercase()) {
                            "pending" -> Color(0xFFD97706)
                            "processing" -> Brand600
                            "shipped" -> Color(0xFF2563EB)
                            "delivered" -> Color(0xFF059669)
                            else -> Color(0xFFDC2626)
                        }
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate100)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Slate50, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = Brand600)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(order.itemName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                    Text("Qty: ${order.quantity} • Size: ${order.size}", fontSize = 12.sp, color = Slate500)
                }
                Text(order.formattedTotal, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = Slate400, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(order.customerName, fontSize = 13.sp, color = Slate700)
                
                Spacer(Modifier.weight(1f))
                
                if (!order.customerPhone.isNullOrEmpty()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${order.customerPhone}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Phone, null, tint = Brand600, modifier = Modifier.size(18.dp))
                    }
                    
                    IconButton(
                        onClick = onChat,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Brand600, modifier = Modifier.size(18.dp))
                    }
                    
                    IconButton(
                        onClick = {
                            val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=${order.customerPhone}")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            val status = order.status.lowercase()
            if (status != "delivered" && status != "cancelled") {
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (status) {
                        "pending" -> {
                            Button(
                                onClick = onAccept,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                            ) {
                                Text("Accept")
                            }
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Text("Cancel")
                            }
                        }
                        "processing" -> {
                            Button(
                                onClick = onShip,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                            ) {
                                Text("Mark Shipped")
                            }
                        }
                        "shipped" -> {
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Confirm Delivery")
                            }
                        }
                    }
                }
            }
        }
    }
}

data class VendorOrder(
    val id: String,
    val fullId: String,
    val customerName: String,
    val itemName: String,
    val quantity: Int,
    val size: String,
    val total: Double,
    val formattedTotal: String,
    val date: String,
    val status: String,
    val customerNote: String? = null,
    val shippingAddress: String? = null,
    val isLate: Boolean = false,
    val customerPhone: String? = null,
    val customerEmail: String? = null
)
