package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailsScreen(
    orderId: String,
    viewModel: HomeViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val order = uiState.allOrders.find { it["id"] == orderId }
    val history = uiState.orderHistory[orderId] ?: emptyList()
    val context = LocalContext.current
    var showSupportDialog by remember { mutableStateOf(false) }

    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = uiState.adminEmail,
            adminPhone = uiState.adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = "Order Inquiry - #$orderId"
        )
    }

    var showReturnDialog by remember { mutableStateOf(false) }
    var returnReason by remember { mutableStateOf("") }

    LaunchedEffect(orderId) {
        viewModel.loadOrderStatusHistory(orderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt & Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSupportDialog = true }) {
                        Icon(Icons.Default.HeadsetMic, contentDescription = "Support", tint = Brand600)
                    }
                    IconButton(onClick = { /* Share or Export Logic */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (order == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Receipt Status Header
                item {
                    ReceiptStatusCard(order)
                }

                // Order Journey / Tracking
                item {
                    OrderTrackingCard(order["status"]?.toString() ?: "pending")
                }

                // Delivery Information
                item {
                    OrderInfoCard(
                        title = "Delivery Information",
                        icon = Icons.Default.LocationOn,
                        content = {
                            Column {
                                Text(
                                    text = order["shipping_address"]?.toString() ?: "No address",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Estimated Delivery: ${order["estimated_date"] ?: "Standard (2-3 days)"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }

                // Order Items
                val items = order["order_items"] as? List<Map<String, Any>> ?: emptyList()
                item {
                    Text(
                        "Order Items (${items.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(items) { item ->
                    OrderItemRow(item, uiState.selectedCurrency, uiState.exchangeRates)
                }

                // Payment Summary
                item {
                    PaymentSummaryCard(order, uiState.selectedCurrency, uiState.exchangeRates)
                }

                // Timeline / Status History
                item {
                    Text(
                        "Order Timeline",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                itemsIndexed(history) { index, status ->
                    TimelineItem(status, isLast = index == history.size - 1)
                }

                item {
                    val status = order["status"]?.toString()?.lowercase() ?: ""
                    val isDelivered = status == "delivered"
                    val canReturn = isDelivered && uiState.userRole == "student" // Simplification
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.userRole == "admin" || isDelivered) {
                            Button(
                                onClick = { viewModel.generateDigitalReceipt(orderId, context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !uiState.isGeneratingPdf
                            ) {
                                if (uiState.isGeneratingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download Invoice PDF")
                                }
                            }
                        }

                        if (canReturn) {
                            OutlinedButton(
                                onClick = { showReturnDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Undo, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Initiate Return")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("Initiate Return") },
            text = {
                Column {
                    Text("Please provide a reason for the return (min 10 characters):", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = returnReason,
                        onValueChange = { returnReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Wrong size, damaged on arrival...") },
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.initiateReturnRequest(orderId, returnReason)
                        showReturnDialog = false
                    },
                    enabled = returnReason.length >= 10,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Submit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun OrderTrackingCard(status: String) {
    val steps = listOf("Ordered", "Confirmed", "Shipped", "Delivered")
    val currentStep = when (status.lowercase()) {
        "pending" -> 0
        "confirmed", "processing" -> 1
        "shipped", "out_for_delivery" -> 2
        "delivered" -> 3
        else -> 0
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Order Journey",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (status.lowercase() == "shipped" || status.lowercase() == "out_for_delivery") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Live Tracking", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                steps.forEachIndexed { index, label ->
                    val isActive = index <= currentStep
                    val isCompleted = index < currentStep
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Line connecting to NEXT step
                            if (index < steps.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 12.dp) // half circle size
                                        .height(2.dp)
                                        .fillMaxWidth()
                                        .background(if (index < currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .align(Alignment.CenterStart)
                                )
                            }
                            
                            // Circle
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape)
                                    .border(2.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptStatusCard(
    order: Map<String, Any>,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    val status = order["status"]?.toString() ?: "Pending"
    val isPaid = order["payment_status"]?.toString()?.lowercase() == "paid"
    val mpesaReceipt = order["mpesa_receipt"]?.toString()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(if (isPaid) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.Payments,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isPaid) Color(0xFF059669) else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = if (isPaid) AppUtils.formatCurrency((order["total_amount"] as? Number)?.toDouble(), currency, exchangeRates) else "Payment Pending",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Order #${order["id"]?.toString()?.takeLast(8)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val trackingNumber = order["tracking_number"]?.toString()
            val courierName = order["courier_name"]?.toString()
            if (!trackingNumber.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Tracking Info", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$courierName: $trackingNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Transaction ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mpesaReceipt ?: "Awaiting...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Order Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        color = if (status.lowercase() == "delivered") Color(0xFFD1FAE5) else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            status.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status.lowercase() == "delivered") Color(0xFF059669) else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderInfoCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
fun OrderItemRow(
    item: Map<String, Any>,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    val product = item["products"] as? Map<*, *> ?: item["product"] as? Map<*, *>
    val name = product?.get("name")?.toString() ?: "Unknown Item"
    val price = (item["unit_price"] as? Number)?.toDouble() ?: 0.0
    val qty = (item["quantity"] as? Number)?.toInt() ?: 1
    val image = (product?.get("images") as? List<*>)?.firstOrNull()?.toString()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Size: ${item["size"]} • Color: ${item["color"]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(AppUtils.formatCurrency(price, currency, exchangeRates), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Qty: $qty", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PaymentSummaryCard(
    order: Map<String, Any>,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    val subtotalValue = (order["total_amount"] as? Number)?.toDouble() ?: 0.0
    val tax = subtotalValue * 0.16
    val total = subtotalValue

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Payment Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            
            SummaryDetailRow("Subtotal", AppUtils.formatCurrency(total - tax, currency, exchangeRates))
            SummaryDetailRow("Tax (16%)", AppUtils.formatCurrency(tax, currency, exchangeRates))
            SummaryDetailRow("Shipping", "Free", isFree = true)
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Paid", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(AppUtils.formatCurrency(total, currency, exchangeRates), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SummaryDetailRow(label: String, value: String, isFree: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isFree) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun TimelineItem(status: Map<String, Any>, isLast: Boolean) {
    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Text(
                text = status["status"]?.toString()?.uppercase() ?: "UPDATE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = status["notes"]?.toString() ?: "No additional notes",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = status["created_at"]?.toString()?.split("T")?.firstOrNull() ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
