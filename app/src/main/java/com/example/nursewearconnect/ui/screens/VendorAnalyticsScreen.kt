package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorAnalyticsScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedDays by remember { mutableIntStateOf(30) }
    
    // Refresh data when period changes
    LaunchedEffect(selectedDays) {
        uiState.userId?.let { viewModel.loadVendorAnalytics(it, selectedDays) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showPeriodMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showPeriodMenu = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Period", tint = Brand600)
                        }
                        DropdownMenu(expanded = showPeriodMenu, onDismissRequest = { showPeriodMenu = false }) {
                            listOf(7 to "Last 7 Days", 30 to "Last 30 Days", 90 to "Last 90 Days").forEach { (days, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        selectedDays = days
                                        showPeriodMenu = false
                                    },
                                    trailingIcon = { if (selectedDays == days) Icon(Icons.Default.Check, null, tint = Brand600) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        viewModel.exportFinancialReportPDF(context, "Vendor_Performance", "PDF")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = Brand600)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SalesSummaryCards(uiState, selectedDays)
            }
            
            item {
                Text(
                    "Revenue Trend (Last $selectedDays Days)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Spacer(Modifier.height(12.dp))
                RevenueChart(uiState.vendorSalesTrends)
            }
            
            item {
                Text(
                    "Top Selling Products",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            }
            
            if (uiState.vendorBestSellers.isEmpty()) {
                item {
                    Text("No sales data available for this period.", fontSize = 14.sp, color = Slate500, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(uiState.vendorBestSellers) { item ->
                    TopProductItem(
                        name = item["name"]?.toString() ?: "Unknown", 
                        sold = (item["units_sold"] as? Number)?.toInt() ?: 0, 
                        revenue = (item["revenue"] as? Number)?.toInt() ?: 0
                    )
                }
            }
            
            item {
                Text(
                    "Inventory Health",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            }
            
            item {
                VendorInventoryHealthOverview(uiState.vendorStockHealth)
            }
        }
    }
}

@Composable
fun SalesSummaryCards(uiState: com.example.nursewearconnect.ui.viewmodel.HomeUiState, days: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val commissionRate = uiState.commissionRate
            val totalRevenue = uiState.vendorRevenue
            val commission = totalRevenue * (commissionRate / 100.0)
            val netEarning = totalRevenue - commission
            
            val prevRevenue = uiState.prevVendorRevenue
            val prevNetEarning = prevRevenue * (1 - (commissionRate / 100.0))
            val revGrowth = if (prevNetEarning > 0) ((netEarning - prevNetEarning) / prevNetEarning) * 100 else 0.0

            AnalyticsCard(
                label = "Net Earnings",
                value = "KSh ${String.format(Locale.US, "%,.0f", netEarning)}",
                trend = "${if (revGrowth >= 0) "+" else ""}${String.format("%.1f", revGrowth)}% vs prev.",
                trendColor = if (revGrowth >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
            
            val orderCount = uiState.vendorOrderCount
            val prevOrderCount = uiState.prevVendorOrderCount
            val orderGrowth = if (prevOrderCount > 0) ((orderCount - prevOrderCount).toDouble() / prevOrderCount) * 100 else 0.0

            AnalyticsCard(
                label = "Orders",
                value = orderCount.toString(),
                trend = "${if (orderGrowth >= 0) "+" else ""}${String.format("%.1f", orderGrowth)}% vs prev.",
                trendColor = if (orderGrowth >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val stock = uiState.vendorStockHealth
            val lowStock = (stock["low_stock"] as? Number)?.toInt() ?: 0
            val outOfStock = (stock["out_of_stock"] as? Number)?.toInt() ?: 0
            
            AnalyticsCard(
                label = "Low/Out Stock",
                value = "$lowStock / $outOfStock",
                trend = if (lowStock + outOfStock > 0) "Needs Attention" else "All Good",
                trendColor = if (lowStock + outOfStock > 5) Color(0xFFEF4444) else if (lowStock + outOfStock > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            
            val stockValue = (stock["total_stock_value"] as? Number)?.toDouble() ?: 0.0
            AnalyticsCard(
                label = "Stock Value",
                value = "KSh ${String.format(Locale.US, "%,.0f", stockValue)}",
                trend = "Total Assets",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AnalyticsCard(
    label: String,
    value: String,
    trend: String,
    modifier: Modifier = Modifier,
    trendColor: Color = Color(0xFF10B981)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trendColor == Color(0xFF10B981)) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = trendColor, modifier = Modifier.size(14.dp))
                } else {
                    Icon(Icons.Default.ErrorOutline, null, tint = trendColor, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(trend, fontSize = 12.sp, color = trendColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VendorInventoryHealthOverview(health: Map<String, Any>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val total = (health["total_products"] as? Number)?.toInt() ?: 0
            val low = (health["low_stock"] as? Number)?.toInt() ?: 0
            val out = (health["out_of_stock"] as? Number)?.toInt() ?: 0
            
            InventoryProgressRow("In Stock", total - low - out, total, Color(0xFF10B981))
            InventoryProgressRow("Low Stock", low, total, Color(0xFFF59E0B))
            InventoryProgressRow("Out of Stock", out, total, Color(0xFFEF4444))
        }
    }
}

@Composable
fun InventoryProgressRow(label: String, count: Int, total: Int, color: Color) {
    val progress = if (total > 0) count.toFloat() / total else 0f
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = Slate600)
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color,
            trackColor = Slate100
        )
    }
}

@Composable
fun RevenueChart(trends: List<Map<String, Any>>) {
    val revenuePoints = trends.map { (it["revenue"] as? Number)?.toDouble() ?: 0.0 }
    val labels = trends.map { it["label"]?.toString() ?: "" }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        if (revenuePoints.isEmpty() || revenuePoints.all { it == 0.0 }) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BarChart, null, tint = Slate300, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No revenue data for this period", color = Slate400, fontSize = 12.sp)
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val maxRev = revenuePoints.maxOrNull() ?: 0.0
                    Column {
                        Text("Revenue Over Time", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text("Peak: KSh ${String.format(Locale.US, "%,.0f", maxRev)}", fontSize = 10.sp, color = Brand600, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = Brand50, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "Live Updates", 
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp, 
                            color = Brand700, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(Modifier.height(20.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(start = 40.dp, bottom = 30.dp, end = 10.dp)) {
                        val maxRev = (revenuePoints.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                        val width = size.width
                        val height = size.height
                        val stepX = if (revenuePoints.size > 1) width / (revenuePoints.size - 1) else width
                        
                        // Y-Axis Labels & Grid Lines
                        val gridLines = 4
                        for (i in 0..gridLines) {
                            val y = height - (height * (i.toFloat() / gridLines))
                            val value = maxRev * (i.toFloat() / gridLines)
                            
                            // Grid line
                            drawLine(
                                color = Slate100,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            
                            // Y-axis Label
                            val labelText = if (value >= 1000) "${(value/1000).toInt()}k" else value.toInt().toString()
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                -12.dp.toPx(),
                                y + 4.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#64748B")
                                    textSize = 11.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                }
                            )
                        }

                        // Draw Axes Lines
                        drawLine(
                            color = Slate300,
                            start = androidx.compose.ui.geometry.Offset(0f, height),
                            end = androidx.compose.ui.geometry.Offset(width, height),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = Slate300,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, height),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Drawing Smooth Bézier Curve
                        val path = Path()
                        val fillPath = Path()
                        
                        revenuePoints.forEachIndexed { index, rev ->
                            val x = stepX * index
                            val y = height * (1 - (rev / maxRev).toFloat())
                            
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, height)
                                fillPath.lineTo(x, y)
                            } else {
                                val prevX = stepX * (index - 1)
                                val prevY = height * (1 - (revenuePoints[index - 1] / maxRev).toFloat())
                                
                                val conX1 = prevX + (x - prevX) / 2
                                val conY1 = prevY
                                val conX2 = prevX + (x - prevX) / 2
                                val conY2 = y
                                
                                path.cubicTo(conX1, conY1, conX2, conY2, x, y)
                                fillPath.cubicTo(conX1, conY1, conX2, conY2, x, y)
                            }
                            
                            if (index == revenuePoints.size - 1) {
                                fillPath.lineTo(x, height)
                                fillPath.close()
                            }

                            // X-Axis Labels (Date)
                            if (index % (revenuePoints.size / 5).coerceAtLeast(1) == 0 || index == revenuePoints.size - 1) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    labels.getOrNull(index)?.takeLast(5) ?: "",
                                    x,
                                    height + 20.dp.toPx(),
                                    android.graphics.Paint().apply {
                                        color = android.graphics.Color.parseColor("#94A3B8")
                                        textSize = 10.sp.toPx()
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                    }
                                )
                            }
                        }
                        
                        // Draw Gradient Fill
                        drawPath(
                            path = fillPath,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Brand500.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                        
                        // Draw Main Line
                        drawPath(
                            path = path,
                            color = Brand600,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                        
                        // Draw Data Points
                        revenuePoints.forEachIndexed { index, rev ->
                            val x = stepX * index
                            val y = height * (1 - (rev / maxRev).toFloat())
                            drawCircle(
                                color = Brand600,
                                radius = 4.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(x, y)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(x, y)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopProductItem(name: String, sold: Int, revenue: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(name, fontWeight = FontWeight.Medium, color = Slate900)
                Text("$sold units sold", fontSize = 12.sp, color = Slate500)
            }
            Text("KSh $revenue", fontWeight = FontWeight.Bold, color = Brand600)
        }
    }
}

@Composable
fun RecentOrderCard(order: Map<String, Any>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Brand50, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("#${order["id"].toString().takeLast(3)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand600)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Order ${order["id"].toString().take(8)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(order["status"].toString(), fontSize = 12.sp, color = Slate400)
            }
            Text("KSh ${order["total_amount"]}", fontWeight = FontWeight.Bold)
        }
    }
}
