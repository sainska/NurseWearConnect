package com.example.nursewearconnect.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.utils.AppUtils
import android.content.Intent
import android.net.Uri
import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import com.example.nursewearconnect.ui.components.EmptyState
import com.example.nursewearconnect.ui.components.SimpleEmptyState
import com.example.nursewearconnect.ui.components.OrderTimelineView
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeUiState
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrderManagementScreen(
    onBackClick: (() -> Unit)? = null,
    onNavigateToMessages: (String) -> Unit = {},
    viewModel: HomeViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var statusFilter by remember { mutableStateOf("All") }
    
    // Date Filtering State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()
    val dateFilterLabel = remember(datePickerState.selectedStartDateMillis, datePickerState.selectedEndDateMillis) {
        val startMillis = datePickerState.selectedStartDateMillis
        val endMillis = datePickerState.selectedEndDateMillis
        if (startMillis != null && endMillis != null) {
            val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
            val start = sdf.format(java.util.Date(startMillis))
            val end = sdf.format(java.util.Date(endMillis))
            "$start - $end"
        } else {
            "All Time"
        }
    }

    val tabs = listOf("Orders", "Payouts", "Financials", "Logs")
    
    val uiState by viewModel.uiState.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Share PDF when generated
    LaunchedEffect(uiState.receiptFile) {
        uiState.receiptFile?.let { file ->
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Report"))
        }
    }
    
    var selectedOrderForStatus by remember { mutableStateOf<AdminOrderItem?>(null) }
    var selectedOrderForDetail by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedPayoutForUpdate by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showCreatePayout by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedOrderForStatus != null || selectedOrderForDetail != null || selectedPayoutForUpdate != null || showCreatePayout || showDatePicker || (selectedTab != 0 && onBackClick != null)) {
        when {
            showDatePicker -> showDatePicker = false
            showCreatePayout -> showCreatePayout = false
            selectedOrderForStatus != null -> selectedOrderForStatus = null
            selectedOrderForDetail != null -> selectedOrderForDetail = null
            selectedPayoutForUpdate != null -> selectedPayoutForUpdate = null
            selectedTab != 0 -> selectedTab = 0
            else -> onBackClick?.invoke()
        }
    }

    // Update data when filters change (Server-side)
    LaunchedEffect(statusFilter, datePickerState.selectedStartDateMillis, datePickerState.selectedEndDateMillis, searchQuery) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val startDate = datePickerState.selectedStartDateMillis?.let { sdf.format(java.util.Date(it)) }
        val endDate = datePickerState.selectedEndDateMillis?.let { sdf.format(java.util.Date(it)) }
        
        viewModel.fetchAdminOrders(
            status = if (statusFilter == "All") null else statusFilter,
            startDate = startDate,
            endDate = endDate,
            searchQuery = if (searchQuery.length >= 3) searchQuery else null,
            page = 0,
            append = false
        )
    }

    // Dialogs
    if (showCreatePayout) {
        val vendors = allUsers.filter { (it["role"] as? String)?.lowercase() == "vendor" }
            .map { it["id"].toString() to (it["full_name"]?.toString() ?: "Unknown Vendor") }
        
        CreatePayoutDialog(
            vendors = vendors,
            onDismiss = { showCreatePayout = false },
            onConfirm = { vendorId, amount ->
                viewModel.createPayout(vendorId, amount)
                showCreatePayout = false
            }
        )
    }

    selectedPayoutForUpdate?.let { payout ->
        UpdatePayoutDialog(
            payout = payout,
            allUsers = allUsers,
            onDismiss = { selectedPayoutForUpdate = null },
            onUpdate = { id, status, ref ->
                viewModel.updatePayoutStatus(id, status, ref)
            },
            onProcessPaystack = { id ->
                viewModel.processPayout(id)
            }
        )
    }

    selectedOrderForDetail?.let { orderMap ->
        AdminOrderDetailDialog(
            orderMap = orderMap,
            allUsers = allUsers,
            uiState = uiState,
            viewModel = viewModel,
            onChat = { customerId ->
                onNavigateToMessages(customerId)
                selectedOrderForDetail = null
            },
            onDismiss = { selectedOrderForDetail = null }
        )
    }

    selectedOrderForStatus?.let { order ->
        UpdateOrderStatusDialog(
            order = order,
            onDismiss = { selectedOrderForStatus = null },
            onUpdate = { id, status ->
                viewModel.updateVendorOrderStatus(id, status)
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    datePickerState.setSelection(null, null)
                    showDatePicker = false
                }) {
                    Text("Clear")
                }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                modifier = Modifier.height(450.dp),
                title = { Text("Filter by Date", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = 1200.dp),
                    title = { Text("Admin Hub", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        onBackClick?.let { back ->
                            IconButton(onClick = back) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        when (selectedTab) {
                            0, 2, 3 -> {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        Icons.Default.DateRange, 
                                        contentDescription = "Date Filter", 
                                        tint = if (datePickerState.selectedStartDateMillis != null) Brand600 else Slate600
                                    )
                                }
                                if (selectedTab == 0) {
                                    IconButton(onClick = {
                                        val csvData = viewModel.exportOrdersToCSV(isAdmin = true)
                                        if (csvData.isNotEmpty()) {
                                            AppUtils.exportAndShareData(
                                                context,
                                                csvData,
                                                "Admin_Orders_${System.currentTimeMillis()}.csv"
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Default.Download, contentDescription = "Export Orders", tint = Slate600)
                                    }
                                }
                            }
                            1 -> {
                                IconButton(onClick = { viewModel.scheduleAutomatedPayouts() }) {
                                    Icon(Icons.Default.Autorenew, contentDescription = "Schedule Payouts", tint = Brand600)
                                }
                                IconButton(onClick = { showCreatePayout = true }) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "New Payout", tint = Brand600)
                                }
                            }
                        }
                        if (selectedTab == 2 || selectedTab == 3) {
                            IconButton(onClick = {
                                if (selectedTab == 2) {
                                    viewModel.exportFinancialReportPDF(context, "Sales")
                                } else {
                                    viewModel.exportLogsToCSV(context)
                                }
                            }) {
                                Icon(
                                    if (selectedTab == 2) Icons.Default.PictureAsPdf else Icons.Default.FileDownload,
                                    contentDescription = if (selectedTab == 3) "Export Logs" else "Export Report",
                                    tint = Slate600
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isGeneratingPdf) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Brand600)
            }
            // Tab Header Wrapper
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White, 
                shadowElevation = 1.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(modifier = Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
                        TabRow(
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
                                    text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium) }
                                )
                            }
                        }
                        
                        if (selectedTab == 0 || selectedTab == 1 || selectedTab == 3) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { 
                                        Text(when(selectedTab) {
                                            0 -> "Search Orders (ID, Customer)..."
                                            1 -> "Search Payouts (Vendor)..."
                                            else -> "Search Logs..."
                                        })
                                    },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = AppUtils.standardOutlinedTextFieldColors()
                                )
                                
                                if (selectedTab == 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LazyRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val filters = listOf("All", "Pending", "Processing", "Shipped", "Delivered", "Cancelled")
                                            items(filters) { filter ->
                                                FilterChip(
                                                    selected = statusFilter == filter,
                                                    onClick = { statusFilter = filter },
                                                    label = { Text(filter) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Brand100,
                                                        selectedLabelColor = Brand700
                                                    )
                                                )
                                            }
                                        }
                                        
                                        if (datePickerState.selectedStartDateMillis != null) {
                                            Surface(
                                                color = Brand50,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(dateFilterLabel, fontSize = 10.sp, color = Brand700, fontWeight = FontWeight.Bold)
                                                    Icon(
                                                        Icons.Default.Close, 
                                                        null, 
                                                        modifier = Modifier.size(12.dp).clickable { datePickerState.setSelection(null, null) },
                                                        tint = Brand700
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (datePickerState.selectedStartDateMillis != null) {
                                    Surface(
                                        color = Brand50,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(12.dp), tint = Brand700)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Date Range: $dateFilterLabel", fontSize = 12.sp, color = Brand700, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Default.Close, 
                                                null, 
                                                modifier = Modifier.size(14.dp).clickable { datePickerState.setSelection(null, null) },
                                                tint = Brand700
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).widthIn(max = 1200.dp).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> OrderList(
                        viewModel,
                        uiState, 
                        allUsers, 
                        searchQuery, 
                        statusFilter, 
                        dateRange = datePickerState.selectedStartDateMillis to datePickerState.selectedEndDateMillis,
                        onStatusClick = { selectedOrderForStatus = it }, 
                        onClick = { orderMap -> 
                            val orderId = orderMap["id"]?.toString() ?: ""
                            if (orderMap.containsKey("order_items")) {
                                selectedOrderForDetail = orderMap
                            } else {
                                viewModel.fetchOrderDetails(orderId) { fullOrder ->
                                    if (fullOrder != null) {
                                        selectedOrderForDetail = fullOrder
                                    }
                                }
                            }
                        }
                    )
                    1 -> PayoutList(viewModel, uiState, allUsers, searchQuery, onUpdateClick = { selectedPayoutForUpdate = it })
                    2 -> FinancialOverview(uiState, allUsers, dateRange = datePickerState.selectedStartDateMillis to datePickerState.selectedEndDateMillis)
                    3 -> SystemLogList(
                        viewModel,
                        uiState, 
                        searchQuery, 
                        dateRange = datePickerState.selectedStartDateMillis to datePickerState.selectedEndDateMillis,
                        onClearLogs = { viewModel.clearSystemLogs() }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderList(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    allUsers: List<Map<String, Any>>,
    searchQuery: String,
    statusFilter: String,
    dateRange: Pair<Long?, Long?>,
    onStatusClick: (AdminOrderItem) -> Unit,
    onClick: (Map<String, Any>) -> Unit
) {
    val filteredOrders = uiState.adminFilteredOrders

    if (filteredOrders.isEmpty()) {
        if (uiState.isAdminOrdersLoading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        } else {
            EmptyState(
                illustration = { AdminOrderIllustration() },
                title = "No Orders Found",
                description = if (searchQuery.isNotEmpty() || statusFilter != "All") "Try adjusting your filters" else "No orders have been placed yet."
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredOrders) { orderMap ->
                // Support both flat summary view and full nested order structure
                val profiles = orderMap["profiles"] as? Map<*, *>
                val customerName = orderMap["customer_name"]?.toString() ?: profiles?.get("full_name")?.toString() ?: "Unknown"
                
                val itemsCount = (orderMap["item_count"] as? Number)?.toInt() ?: 
                                 (orderMap["order_items"] as? List<*>)?.size ?: 0
                
                val vendorDisplay = orderMap["vendor_names"]?.toString() ?: run {
                    val orderItems = orderMap["order_items"] as? List<Map<String, Any>>
                    val vendorIds = orderItems?.mapNotNull { (it["products"] as? Map<*, *>)?.get("vendor_id")?.toString() }?.distinct() ?: emptyList()
                    if (vendorIds.size > 1) {
                        "Multiple Vendors (${vendorIds.size})"
                    } else if (vendorIds.isNotEmpty()) {
                        val vId = vendorIds.first()
                        allUsers.find { it["id"] == vId }?.get("full_name")?.toString() ?: "Vendor ($vId)"
                    } else {
                        "System"
                    }
                }

                val order = AdminOrderItem(
                    id = orderMap["id"]?.toString()?.take(8) ?: "",
                    fullId = orderMap["id"]?.toString() ?: "",
                    vendorName = vendorDisplay,
                    customerName = customerName,
                    amount = (orderMap["total_amount"] as? Number)?.toDouble() ?: 0.0,
                    status = orderMap["status"]?.toString() ?: "Pending",
                    date = orderMap["created_at"]?.toString()?.split("T")?.get(0) ?: "",
                    itemsCount = itemsCount
                )
                AdminOrderCard(order = order, onStatusClick = { onStatusClick(order) }, onClick = { onClick(orderMap) })
            }
            
            if (uiState.adminOrdersHasMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (uiState.isAdminOrdersLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Brand600)
                        } else {
                            TextButton(onClick = {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                val startDate = dateRange.first?.let { sdf.format(java.util.Date(it)) }
                                val endDate = dateRange.second?.let { sdf.format(java.util.Date(it)) }
                                viewModel.fetchAdminOrders(
                                    status = if (statusFilter == "All") null else statusFilter,
                                    startDate = startDate,
                                    endDate = endDate,
                                    searchQuery = if (searchQuery.length >= 3) searchQuery else null,
                                    page = uiState.adminOrdersPage + 1,
                                    append = true
                                )
                            }) {
                                Text("Load More", color = Brand600)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PayoutList(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    allUsers: List<Map<String, Any>>,
    searchQuery: String,
    onUpdateClick: (Map<String, Any>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val filteredPayouts = uiState.payouts.filter { payout ->
        val profiles = payout["profiles"] as? Map<*, *>
        val vendorId = payout["vendor_id"]?.toString()
        val vendorName = profiles?.get("full_name")?.toString() ?: 
                        allUsers.find { it["id"] == vendorId }?.get("full_name")?.toString() ?: ""
        
        vendorName.contains(searchQuery, ignoreCase = true)
    }

    if (filteredPayouts.isEmpty()) {
        EmptyState(
            illustration = { PayoutIllustration() },
            title = "No Payouts Found",
            description = if (searchQuery.isNotEmpty()) "No payouts matching '$searchQuery'" else "No payout history available."
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${filteredPayouts.size} Payout Records", fontWeight = FontWeight.Bold, color = Slate700)
                TextButton(onClick = { viewModel.exportFinancialReportPDF(context, "Payouts") }) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export PDF")
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredPayouts) { payout ->
                    PayoutCard(payout = payout, allUsers = allUsers, onUpdateClick = { onUpdateClick(payout) })
                }
            }
        }
    }
}

@Composable
fun FinancialOverview(uiState: HomeUiState, allUsers: List<Map<String, Any>>, dateRange: Pair<Long?, Long?>) {
    // Dynamic Commission Calculation based on vendor profile rates
    var platformRevenue = 0.0
    var totalGrossVolume = 0.0
    var readyForPayout = 0.0
    
    val filteredOrders = uiState.allOrders.filter { 
        com.example.nursewearconnect.utils.AppUtils.isDateInRange(it["created_at"]?.toString(), dateRange.first, dateRange.second)
    }

    // Prepare data for the chart
    val dailyRevenue = mutableMapOf<String, Double>()
    val dailyCommission = mutableMapOf<String, Double>()

    filteredOrders.forEach { order ->
        val date = order["created_at"]?.toString()?.split("T")?.firstOrNull() ?: "Other"
        val amount = (order["total_amount"] as? Number)?.toDouble() ?: 0.0
        val status = order["status"]?.toString() ?: "Pending"
        totalGrossVolume += amount
        
        dailyRevenue[date] = (dailyRevenue[date] ?: 0.0) + amount
        
        val orderItems = order["order_items"] as? List<Map<String, Any>> ?: emptyList()
        orderItems.forEach { item ->
            val itemPrice = (item["price_at_purchase"] as? Number)?.toDouble() ?: 0.0
            val qty = (item["quantity"] as? Number)?.toInt() ?: 1
            val itemTotal = itemPrice * qty
            
            val vendorId = (item["products"] as? Map<*, *>)?.get("vendor_id")?.toString()
            val vendorProfile = allUsers.find { it["id"] == vendorId }
            val commissionRate = (vendorProfile?.get("commission_rate") as? Number)?.toDouble() ?: 10.0
            
            val itemCommission = itemTotal * (commissionRate / 100.0)
            platformRevenue += itemCommission
            dailyCommission[date] = (dailyCommission[date] ?: 0.0) + itemCommission
            
            if (status.lowercase() == "delivered") {
                readyForPayout += (itemTotal - itemCommission)
            }
        }
    }

    val paidPayouts = uiState.payouts.filter { (it["status"] as? String) == "paid" }.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    val pendingPayouts = uiState.payouts.filter { (it["status"] as? String) == "pending" }.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    val netPlatformProfit = platformRevenue

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Revenue & Commission Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(Modifier.height(12.dp))
            RevenueTrendsChart(dailyRevenue, dailyCommission)
        }

        item {
            Text("Financial Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Slate100)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FinancialRow("Gross Sales", "KSh ${String.format("%,.0f", totalGrossVolume)}", Slate900)
                    FinancialRow("Platform Commission", "- KSh ${String.format("%,.0f", platformRevenue)}", Color(0xFFF43F5E))
                    HorizontalDivider(color = Slate50)
                    FinancialRow("Vendor Net Earnings", "KSh ${String.format("%,.0f", totalGrossVolume - platformRevenue)}", Brand700)
                    FinancialRow("Total Disbursed", "- KSh ${String.format("%,.0f", paidPayouts)}", Slate500)
                    FinancialRow("Pending Disbursal", "- KSh ${String.format("%,.0f", pendingPayouts)}", Color(0xFFD97706))
                    HorizontalDivider(color = Slate100, thickness = 2.dp)
                    FinancialRow("Net Platform Profit", "KSh ${String.format("%,.0f", netPlatformProfit)}", Color(0xFF10B981))
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Ready for Payout",
                    value = "KSh ${String.format("%,.0f", (readyForPayout - paidPayouts - pendingPayouts).coerceAtLeast(0.0))}",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = Brand600,
                    trend = "Unsettled"
                )
                AdminStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Avg. Order",
                    value = "KSh ${if (filteredOrders.isNotEmpty()) String.format("%,.0f", totalGrossVolume / filteredOrders.size) else "0"}",
                    icon = Icons.Default.Analytics,
                    color = Color(0xFF8B5CF6),
                    trend = "In Selected Period"
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detailed Sales Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate900)
                if (uiState.adminSalesReport.isNotEmpty()) {
                    Text("${uiState.adminSalesReport.size} Total", fontSize = 12.sp, color = Slate500)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (uiState.adminSalesReport.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Slate100)
                ) {
                    Text(
                        "No detailed sales records available. Ensure the 'admin_detailed_sales_report' view is populated.",
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = Slate400
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.adminSalesReport.take(15).forEach { item ->
                        SalesRecordCard(item)
                    }
                    if (uiState.adminSalesReport.size > 15) {
                        Text(
                            "Showing top 15 records. Use Export to view all ${uiState.adminSalesReport.size} entries.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Slate500,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SalesRecordCard(item: Map<String, Any>) {
    val orderId = item["order_id"]?.toString()?.takeLast(8) ?: "N/A"
    val productName = item["product_name"]?.toString() ?: "Unknown Product"
    val vendorName = item["vendor_name"]?.toString() ?: "System"
    val amount = (item["total_amount"] as? Number)?.toDouble() ?: 0.0
    val commission = (item["commission_earned"] as? Number)?.toDouble() ?: 0.0
    val mpesaReceipt = item["mpesa_receipt"]?.toString()
    val date = item["order_date"]?.toString()?.split("T")?.firstOrNull() ?: ""

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order #$orderId", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                Text(date, fontSize = 11.sp, color = Slate400)
            }
            Text(productName, fontSize = 14.sp, color = Slate700, maxLines = 1)
            Text("Vendor: $vendorName", fontSize = 12.sp, color = Slate500)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Slate50)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (mpesaReceipt != null && mpesaReceipt != "null") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, null, modifier = Modifier.size(12.dp), tint = Color(0xFF10B981))
                            Spacer(Modifier.width(4.dp))
                            Text(mpesaReceipt, fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Payment Pending", fontSize = 11.sp, color = Color(0xFFF59E0B))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("KES ${String.format("%,.0f", amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                    Text("Comm: KES ${String.format("%,.0f", commission)}", fontSize = 11.sp, color = Brand600, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun RevenueTrendsChart(revenueData: Map<String, Double>, commissionData: Map<String, Double>) {
    val sortedDates = revenueData.keys.sorted()
    val revenuePoints = sortedDates.map { revenueData[it] ?: 0.0 }
    val commissionPoints = sortedDates.map { commissionData[it] ?: 0.0 }
    
    val maxVal = (revenuePoints.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        if (sortedDates.isEmpty()) {
            Box(contentAlignment = Alignment.Center) {
                Text("Not enough data for trend analysis", color = Slate400, fontSize = 12.sp)
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Financial Performance", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                        Text("Gross Revenue vs Commission", fontSize = 11.sp, color = Slate400)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendItem("Revenue", Brand600)
                        Spacer(Modifier.width(12.dp))
                        LegendItem("Commission", Color(0xFFF43F5E))
                    }
                }
                
                Spacer(Modifier.height(24.dp))

                Canvas(modifier = Modifier.fillMaxSize().padding(start = 45.dp, bottom = 30.dp, end = 10.dp)) {
                    val width = size.width
                    val height = size.height
                    val spacing = width / (if (sortedDates.size > 1) sortedDates.size - 1 else 1)

                    // Y-Axis
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = height - (height * (i.toFloat() / gridLines))
                        val value = maxVal * (i.toFloat() / gridLines)
                        
                        drawLine(color = Slate100, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(width, y))
                        // Y-axis Label
                        val labelText = if (value >= 1000) "${(value/1000).toInt()}k" else value.toInt().toString()
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            -15.dp.toPx(),
                            y + 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#64748B")
                                textSize = 11.sp.toPx()
                                textAlign = android.graphics.Paint.Align.RIGHT
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            }
                        )
                    }

                    // Draw Axes
                    drawLine(color = Slate300, start = androidx.compose.ui.geometry.Offset(0f, height), end = androidx.compose.ui.geometry.Offset(width, height), strokeWidth = 2.dp.toPx())
                    drawLine(color = Slate300, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, height), strokeWidth = 2.dp.toPx())

                    // Draw Revenue Path
                    val revenuePath = Path()
                    val revenueFill = Path()
                    
                    revenuePoints.forEachIndexed { index, value ->
                        val x = index * spacing
                        val y = height - (value.toFloat() / maxVal.toFloat() * height)
                        if (index == 0) {
                            revenuePath.moveTo(x, y)
                            revenueFill.moveTo(x, height)
                            revenueFill.lineTo(x, y)
                        } else {
                            val prevX = (index - 1) * spacing
                            val prevY = height - (revenuePoints[index-1].toFloat() / maxVal.toFloat() * height)
                            val cx1 = prevX + (x - prevX) / 2
                            val cx2 = cx1
                            revenuePath.cubicTo(cx1, prevY, cx2, y, x, y)
                            revenueFill.cubicTo(cx1, prevY, cx2, y, x, y)
                        }
                        
                        if (index == sortedDates.size - 1) {
                            revenueFill.lineTo(x, height)
                            revenueFill.close()
                        }

                        // X-Axis
                        if (index % (sortedDates.size / 5).coerceAtLeast(1) == 0 || index == sortedDates.size - 1) {
                            drawContext.canvas.nativeCanvas.drawText(
                                sortedDates[index].takeLast(5),
                                x,
                                height + 22.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#94A3B8")
                                    textSize = 10.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                            )
                        }
                    }
                    
                    drawPath(path = revenueFill, brush = Brush.verticalGradient(listOf(Brand600.copy(alpha = 0.2f), Color.Transparent)))
                    drawPath(revenuePath, color = Brand600, style = Stroke(width = 3.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))

                    // Draw Commission Path
                    val commissionPath = Path()
                    commissionPoints.forEachIndexed { index, value ->
                        val x = index * spacing
                        val y = height - (value.toFloat() / maxVal.toFloat() * height)
                        if (index == 0) commissionPath.moveTo(x, y) else {
                            val prevX = (index - 1) * spacing
                            val prevY = height - (commissionPoints[index-1].toFloat() / maxVal.toFloat() * height)
                            commissionPath.cubicTo(prevX + (x - prevX) / 2, prevY, prevX + (x - prevX) / 2, y, x, y)
                        }
                    }
                    drawPath(commissionPath, color = Color(0xFFF43F5E), style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
    }
}

@Composable
fun SystemLogList(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    searchQuery: String,
    dateRange: Pair<Long?, Long?>,
    onClearLogs: () -> Unit
) {
    var severityFilter by remember { mutableStateOf("All") }

    // We'll use the server-side fetched logs from uiState.systemLogs
    // For now, let's keep the existing filtering but also use the pagination if available
    val filteredLogs = uiState.systemLogs.filter { log ->
        val action = log["action"]?.toString() ?: ""
        val details = log["details"]?.toString() ?: ""
        val createdAt = log["created_at"]?.toString()
        val severity = log["severity"]?.toString() ?: "info"
        
        val matchesSearch = action.contains(searchQuery, ignoreCase = true) || details.contains(searchQuery, ignoreCase = true)
        val matchesDate = com.example.nursewearconnect.utils.AppUtils.isDateInRange(createdAt, dateRange.first, dateRange.second)
        val matchesSeverity = severityFilter == "All" || severity.equals(severityFilter, ignoreCase = true)
        
        matchesSearch && matchesDate && matchesSeverity
    }.reversed()

    // Trigger server-side fetch for logs when filters change
    LaunchedEffect(searchQuery, dateRange) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val startDate = dateRange.first?.let { sdf.format(java.util.Date(it)) }
        val endDate = dateRange.second?.let { sdf.format(java.util.Date(it)) }
        viewModel.fetchSystemLogs(startDate, endDate, page = 0, append = false)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val severities = listOf("All", "Info", "Warning", "Error")
            items(severities) { severity ->
                FilterChip(
                    selected = severityFilter == severity,
                    onClick = { severityFilter = severity },
                    label = { Text(severity) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when(severity) {
                            "Error" -> Color(0xFFFEE2E2)
                            "Warning" -> Color(0xFFFEF3C7)
                            "Info" -> Brand100
                            else -> Brand100
                        },
                        selectedLabelColor = when(severity) {
                            "Error" -> Color(0xFFDC2626)
                            "Warning" -> Color(0xFFD97706)
                            "Info" -> Brand700
                            else -> Brand700
                        }
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filteredLogs.size} Audit Entries", fontWeight = FontWeight.Bold, color = Slate700)
            TextButton(onClick = onClearLogs, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF43F5E))) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear All")
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredLogs) { log ->
                LogItemCard(log)
            }
            
            // Add Load More for Logs if implemented in ViewModel/State
            // For now, let's assume it's just one page or handled similarly to orders
        }
    }
}

@Composable
fun LogItemCard(log: Map<String, Any>) {
    val action = log["action"]?.toString() ?: "UNKNOWN"
    val details = log["details"]?.toString() ?: ""
    val date = log["created_at"]?.toString()?.split("T")?.firstOrNull() ?: ""
    val time = log["created_at"]?.toString()?.split("T")?.get(1)?.take(5) ?: ""
    val severity = log["severity"]?.toString() ?: "info"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(8.dp).background(
                    when(severity) {
                        "error" -> Color(0xFFF43F5E)
                        "warning" -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }, CircleShape
                )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                Text(details, fontSize = 12.sp, color = Slate500)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(date, fontSize = 10.sp, color = Slate400)
                Text(time, fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdminStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    trend: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.1f)
                ) {
                    Icon(icon, null, modifier = Modifier.padding(8.dp).size(20.dp), tint = color)
                }
                if (trend.isNotEmpty()) {
                    Text(trend, color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = Slate500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(value, color = Slate900, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FinancialRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Slate500, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
    }
}

@Composable
fun AdminOrderDetailDialog(
    orderMap: Map<String, Any>,
    allUsers: List<Map<String, Any>>,
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    onChat: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val profiles = orderMap["profiles"] as? Map<*, *>
    val customerName = profiles?.get("full_name")?.toString() ?: "Unknown"
    val orderItems = orderMap["order_items"] as? List<Map<String, Any>> ?: emptyList()

    val context = androidx.compose.ui.platform.LocalContext.current
    val customerPhone = profiles?.get("phone_number")?.toString() ?: ""
    val customerId = orderMap["user_id"]?.toString() ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Order #${orderMap["id"]?.toString()?.take(8)}")
                Row {
                    if (customerPhone.isNotEmpty()) {
                        IconButton(onClick = { 
                            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$customerPhone") }
                            context.startActivity(intent)
                        }) { Icon(Icons.Default.Phone, null, tint = Brand600) }
                    }
                    IconButton(onClick = { 
                        if (customerId.isNotEmpty()) {
                            onChat(customerId)
                        } else if (customerPhone.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:$customerPhone") }
                            context.startActivity(intent)
                        }
                    }) { Icon(Icons.Default.ChatBubbleOutline, null, tint = Brand600) }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Customer: $customerName", fontWeight = FontWeight.Bold, color = Slate900)
                Text("Status: ${orderMap["status"]}", color = Brand600, fontWeight = FontWeight.SemiBold)
                
                OrderTimelineView(status = orderMap["status"]?.toString() ?: "Pending")

                HorizontalDivider(color = Slate100)
                
                Text("Order Breakdown (by Vendor)", fontWeight = FontWeight.SemiBold, color = Slate700)
                
                val groupedItems = orderItems.groupBy { (it["vendor_id"] ?: (it["products"] as? Map<*, *>)?.get("vendor_id"))?.toString() ?: "System" }
                
                groupedItems.forEach { (vendorId, items) ->
                    val vendorName = allUsers.find { it["id"] == vendorId }?.get("full_name")?.toString() ?: "Vendor $vendorId"
                    val allItemsDelivered = items.all { it["status"]?.toString()?.lowercase() == "delivered" }
                    val someItemsShipped = items.any { it["status"]?.toString()?.lowercase() == "shipped" }

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = Slate50,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (allItemsDelivered) Color(0xFF10B981).copy(alpha = 0.3f) else Slate100)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(vendorName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                Badge(
                                    containerColor = if (allItemsDelivered) Color(0xFFECFDF5) else if (someItemsShipped) Brand50 else Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        if (allItemsDelivered) "READY FOR PAYOUT" else if (someItemsShipped) "SHIPPED" else "PENDING",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (allItemsDelivered) Color(0xFF059669) else if (someItemsShipped) Brand600 else Color(0xFFD97706)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            items.forEach { item ->
                                val products = item["products"] as? Map<String, Any>
                                val name = products?.get("name")?.toString() ?: "Unknown Product"
                                val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                                val price = ((item["unit_price"] ?: item["price_at_purchase"]) as? Number)?.toDouble() ?: 0.0
                                val itemStatus = item["status"]?.toString() ?: "pending"
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, fontSize = 14.sp)
                                        Text("Status: $itemStatus", fontSize = 10.sp, color = Slate400)
                                    }
                                    Text("x$qty KSh ${String.format("%,.0f", price * qty)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                if (items.indexOf(item) < items.size - 1) Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Financial Governance: Refund to Wallet Logic
                val orderIdStr = orderMap["id"]?.toString() ?: ""
                val currentReturn = uiState.returnRequests.find { it["order_id"]?.toString() == orderIdStr }
                
                if (currentReturn != null) {
                    HorizontalDivider(color = Slate100, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Return Request", fontWeight = FontWeight.SemiBold, color = Color(0xFFF43F5E))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFF1F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECDD3))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reason: ${currentReturn["reason"]}", fontSize = 13.sp, color = Color(0xFF9F1239))
                                Text(currentReturn["status"]?.toString()?.uppercase() ?: "PENDING", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE11D48))
                            }
                            
                            if (currentReturn["status"]?.toString() == "pending") {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.processAdminRefund(currentReturn["id"].toString(), "Approved by Admin") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Refund to Wallet", fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            // Paystack Refund Logic
                            val paymentMethod = orderMap["payment_method"]?.toString()?.lowercase() ?: ""
                            if (paymentMethod == "paystack" && currentReturn["status"]?.toString() == "pending") {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { 
                                        viewModel.processPaystackRefund(
                                            orderId = orderIdStr,
                                            amount = null, // Full refund
                                            reason = "Approved by Admin (Paystack)"
                                        ) 
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand600),
                                    border = BorderStroke(1.dp, Brand600),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Paystack Full Refund", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun UpdateOrderStatusDialog(order: AdminOrderItem, onDismiss: () -> Unit, onUpdate: (String, String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Update Order Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select new status for Order #${order.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
                val statuses = listOf("Pending", "Processing", "Shipped", "Delivered", "Cancelled")
                statuses.forEach { status ->
                    val isSelected = order.status.equals(status, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdate(order.fullId, status)
                                onDismiss()
                            },
                        color = if (isSelected) Brand50 else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected, 
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Brand600)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                status, 
                                color = if (isSelected) Brand600 else Slate700,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = Slate500, fontWeight = FontWeight.SemiBold) 
            }
        }
    )
}

@Composable
fun UpdatePayoutDialog(
    payout: Map<String, Any>,
    allUsers: List<Map<String, Any>>,
    onDismiss: () -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onProcessPaystack: (String) -> Unit
) {
    var refNumber by remember { mutableStateOf("") }
    val profiles = payout["profiles"] as? Map<*, *>
    val vendorId = payout["vendor_id"]?.toString()
    val vendorProfile = allUsers.find { it["id"] == vendorId }
    val bankCode = vendorProfile?.get("bank_code")?.toString() ?: ""
    val accountNumber = vendorProfile?.get("bank_account_number")?.toString() ?: ""
    val hasBankDetails = bankCode.isNotEmpty() && accountNumber.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Update Payout Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(
                        "Payout to: ${profiles?.get("full_name") ?: vendorProfile?.get("full_name")}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate800
                    )
                    Text(
                        "Amount: KSh ${payout["amount"]}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Brand600
                    )
                }

                if (hasBankDetails) {
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("PAYSTACK READY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand600)
                            Text("Bank Code: $bankCode", fontSize = 12.sp, color = Slate600)
                            Text("Account: $accountNumber", fontSize = 12.sp, color = Slate600)
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Vendor has not provided bank details for Paystack automated payout.", fontSize = 11.sp, color = Color(0xFF9A3412))
                        }
                    }
                }
                
                OutlinedTextField(
                    value = refNumber,
                    onValueChange = { refNumber = it },
                    label = { Text("M-Pesa/Bank Reference (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasBankDetails) {
                        Button(
                            onClick = {
                                onProcessPaystack(payout["id"].toString())
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand600),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Process via Paystack", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                onUpdate(payout["id"].toString(), "paid", refNumber)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Mark Paid", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                onUpdate(payout["id"].toString(), "failed", refNumber)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF43F5E)),
                            border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Mark Failed", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = Slate500, fontWeight = FontWeight.SemiBold) 
            }
        }
    )
}

@Composable
fun CreatePayoutDialog(vendors: List<Pair<String, String>>, onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var selectedVendorId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Initiate Vendor Payout",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Select vendor and specify amount to disburse.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(vendors) { (id, name) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVendorId = id },
                            color = if (selectedVendorId == id) Brand50 else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVendorId == id, 
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Brand600)
                                )
                                Text(
                                    name, 
                                    modifier = Modifier.padding(start = 12.dp),
                                    fontWeight = if (selectedVendorId == id) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedVendorId == id) Brand600 else Slate700
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (KSh)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedVendorId.isNotEmpty() && amount.isNotEmpty()) onConfirm(selectedVendorId, amount.toInt()) },
                enabled = selectedVendorId.isNotEmpty() && amount.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) {
                Text("Confirm Payout", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = Slate500, fontWeight = FontWeight.SemiBold) 
            }
        }
    )
}

@Composable
fun AdminOrderCard(order: AdminOrderItem, onStatusClick: () -> Unit, onClick: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Order #${order.id}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(order.fullId)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy ID", modifier = Modifier.size(14.dp), tint = Slate400)
                        }
                    }
                    Text(order.date, fontSize = 12.sp, color = Slate500)
                }
                Surface(
                    onClick = onStatusClick,
                    color = when(order.status) {
                        "Pending" -> Color(0xFFFEF3C7)
                        "Shipped" -> Brand50
                        "Delivered" -> Color(0xFFECFDF5)
                        "Processing" -> Color(0xFFE0F2FE)
                        else -> Color(0xFFFEE2E2)
                    },
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            order.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when(order.status) {
                                "Pending" -> Color(0xFFD97706)
                                "Shipped" -> Brand600
                                "Delivered" -> Color(0xFF059669)
                                "Processing" -> Color(0xFF0284C7)
                                else -> Color(0xFFDC2626)
                            }
                        )
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(10.dp).padding(start = 4.dp), tint = Color.Unspecified)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Store, null, modifier = Modifier.size(14.dp), tint = Slate400)
                Spacer(Modifier.width(6.dp))
                Text("Fulfillment: ${order.vendorName}", fontSize = 13.sp, color = Slate700)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = Slate400)
                Spacer(Modifier.width(6.dp))
                Text("Customer: ${order.customerName}", fontSize = 13.sp, color = Slate700)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate50)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${order.itemsCount} Items", fontSize = 13.sp, color = Slate500)
                Text("KSh ${order.amount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Brand600)
            }
        }
    }
}

@Composable
fun PayoutCard(payout: Map<String, Any>, allUsers: List<Map<String, Any>>, onUpdateClick: () -> Unit) {
    val profiles = payout["profiles"] as? Map<*, *>
    val vendorId = payout["vendor_id"]?.toString()
    val vendorName = profiles?.get("full_name")?.toString() ?: 
                    allUsers.find { it["id"] == vendorId }?.get("full_name")?.toString() ?: "Unknown Vendor"
    val businessName = profiles?.get("business_name")?.toString() ?: 
                      allUsers.find { it["id"] == vendorId }?.get("business_name")?.toString() ?: ""
    val amount = payout["amount"]?.toString() ?: "0"
    val status = payout["status"]?.toString() ?: "pending"
    val date = payout["created_at"]?.toString()?.split("T")?.firstOrNull() ?: ""

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(vendorName, fontWeight = FontWeight.Bold, color = Slate900)
                if (businessName.isNotEmpty()) Text(businessName, fontSize = 12.sp, color = Slate500)
                Text(date, fontSize = 11.sp, color = Slate400)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("KSh $amount", fontWeight = FontWeight.ExtraBold, color = Brand600)
                Surface(
                    onClick = if (status == "pending") onUpdateClick else ({}),
                    color = when(status) {
                        "paid" -> Color(0xFFECFDF5)
                        "pending" -> Color(0xFFFEF3C7)
                        else -> Color(0xFFFEE2E2)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when(status) {
                            "paid" -> Color(0xFF059669)
                            "pending" -> Color(0xFFD97706)
                            else -> Color(0xFFDC2626)
                        }
                    )
                }
            }
        }
    }
}

data class AdminOrderItem(
    val id: String,
    val fullId: String,
    val vendorName: String,
    val customerName: String,
    val amount: Double,
    val status: String,
    val date: String,
    val itemsCount: Int
)

@Composable
fun AdminOrderIllustration() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Brand50,
                radius = size.minDimension / 2.2f
            )
            drawCircle(
                color = Brand100,
                radius = size.minDimension / 3.5f
            )
        }
        
        Icon(
            imageVector = Icons.Default.Assignment,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Brand600
        )
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val sparkleColor = Brand400
            
            drawCircle(sparkleColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x - 60.dp.toPx(), center.y - 40.dp.toPx()))
            drawCircle(sparkleColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x + 50.dp.toPx(), center.y - 60.dp.toPx()))
            drawCircle(sparkleColor, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x + 70.dp.toPx(), center.y + 30.dp.toPx()))
        }
    }
}

@Composable
fun PayoutIllustration() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFECFDF5), // Emerald 50
                radius = size.minDimension / 2.2f
            )
            drawCircle(
                color = Color(0xFFD1FAE5), // Emerald 100
                radius = size.minDimension / 3.5f
            )
        }
        
        Icon(
            imageVector = Icons.Default.Payments,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF10B981) // Emerald 600
        )
    }
}

