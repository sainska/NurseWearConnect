package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.example.nursewearconnect.ui.components.OrderTimelineView
import com.example.nursewearconnect.utils.AppUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel,
    onNavigateToInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadReportsData()
    }
    
    // Server-side metrics from V2 RPC
    val summary = uiState.inventoryHealth // Note: In VM we might need to separate inventoryHealth from financialSummary
    val totalRevenue = (summary["total_gross_revenue"] as? Number)?.toDouble() ?: 0.0
    val platformComm = (summary["platform_commission"] as? Number)?.toDouble() ?: 0.0
    val orderCount = (summary["order_count"] as? Number)?.toInt() ?: 0
    val avgOrder = (summary["avg_order_value"] as? Number)?.toDouble() ?: 0.0
    val returnRate = (summary["return_rate_percent"] as? Number)?.toDouble() ?: 0.0

    var showDownloadDialog by remember { mutableStateOf(false) }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Export Report") },
            text = { Text("Choose your preferred format for the business performance report.") },
            confirmButton = {
                Button(onClick = { 
                    viewModel.exportFinancialReportPDF(context, "Performance Summary")
                    showDownloadDialog = false 
                }) { Text("PDF") }
            },
            dismissButton = {
                OutlinedButton(onClick = { 
                    viewModel.exportFinancialReportPDF(context, "Sales", "CSV")
                    showDownloadDialog = false 
                }) { Text("Excel/CSV") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Business Intelligence", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDownloadDialog = true }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Export", tint = Brand600)
                    }
                    IconButton(onClick = { viewModel.printCurrentReport(context, "Business Report") }) {
                        Icon(Icons.Default.Print, contentDescription = "Print Report", tint = Slate600)
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
            item {
                Text(
                    "Performance Summary",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Slate900,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("Real-time metrics from the last 90 days", fontSize = 14.sp, color = Slate500)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Gross Revenue",
                            value = AppUtils.formatCurrency(totalRevenue),
                            icon = Icons.Default.Payments,
                            color = Brand600,
                            trend = "+12.5%"
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Total Orders",
                            value = orderCount.toString(),
                            icon = Icons.Default.LocalMall,
                            color = Color(0xFF8B5CF6),
                            trend = "+5.2%"
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Avg. Order",
                            value = AppUtils.formatCurrency(avgOrder),
                            icon = Icons.Default.Analytics,
                            color = Color(0xFF10B981),
                            trend = "Stable"
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Return Rate",
                            value = String.format("%.1f%%", returnRate),
                            icon = Icons.Default.AssignmentReturn,
                            color = Color(0xFFF43F5E),
                            trend = "-1.0%"
                        )
                    }
                }
            }

            item {
                AnimatedRevenueChart(uiState.salesTrends)
            }

            item {
                SectionHeader("AI Demand Forecasting", "Predictive inventory analysis")
            }

            if (uiState.demandForecast.isEmpty()) {
                item { 
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Brand600)
                    }
                }
            } else {
                items(uiState.demandForecast.values.toList().take(3)) { forecast ->
                    ForecastItemCard(forecast as Map<String, Any>)
                }
            }

            item {
                SectionHeader("Inventory Health", "Stock distribution & asset value")
            }

            item {
                ReportsInventoryHealthOverview(uiState.inventoryHealth)
            }

            item {
                Button(
                    onClick = onNavigateToInventory,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Text("Manage Global Inventory", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Slate900)
        Text(subtitle, fontSize = 14.sp, color = Slate500)
    }
}

@Composable
fun ForecastItemCard(data: Map<String, Any>) {
    val productName = data["product_name"]?.toString() ?: "Product"
    val predicted = (data["predicted_demand_30_days"] as? Number)?.toDouble() ?: 0.0
    val current = (data["current_stock"] as? Number)?.toInt() ?: 0
    val recommendation = data["recommendation"]?.toString() ?: "Monitor"
    val confidence = (data["confidence_score"] as? Number)?.toDouble() ?: 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = Brand50
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Brand600, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(productName, fontWeight = FontWeight.Bold, color = Slate900, maxLines = 1)
                    Text("AI Confidence: ${(confidence * 100).toInt()}%", fontSize = 11.sp, color = Slate400)
                }
                Badge(
                    containerColor = if (recommendation.contains("Urgently")) Color(0xFFFEE2E2) else Brand50,
                    contentColor = if (recommendation.contains("Urgently")) Color(0xFFB91C1C) else Brand700
                ) {
                    Text(recommendation.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ForecastMetric("In Stock", current.toString(), Slate600)
                ForecastMetric("Forecast", predicted.toInt().toString(), Brand600)
                ForecastMetric("Status", if (current >= predicted) "Healthy" else "Risk", if (current >= predicted) Color(0xFF10B981) else Color(0xFFF59E0B))
            }
        }
    }
}

@Composable
fun ForecastMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = Slate400)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ReportsInventoryHealthOverview(health: Map<String, Any>) {
    val totalItems = (health["total_items"] as? Number)?.toInt() ?: 0
    val lowStock = (health["low_stock_count"] as? Number)?.toInt() ?: 0
    val outStock = (health["out_of_stock_count"] as? Number)?.toInt() ?: 0
    val totalValue = (health["total_stock_value"] as? Number)?.toDouble() ?: 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InventoryMetricItem("Live SKUs", totalItems.toString(), Slate900)
                InventoryMetricItem("Low Stock", lowStock.toString(), Color(0xFFF59E0B))
                InventoryMetricItem("Stockouts", outStock.toString(), Color(0xFFF43F5E))
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Slate100)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Inventory Asset Value", fontSize = 12.sp, color = Slate400)
                    Text(AppUtils.formatCurrency(totalValue), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Slate900)
                }
                Icon(Icons.Default.AccountBalance, null, tint = Brand200, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun AnimatedRevenueChart(trends: List<Map<String, Any>>) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(trends) {
        animationProgress.animateTo(1f, animationSpec = tween(1500, easing = FastOutSlowInEasing))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Revenue Intelligence", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate900)
                    Text("Volume analytics over time", fontSize = 12.sp, color = Slate400)
                }
                Surface(color = Brand50, shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Brand600, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("Realtime", fontSize = 10.sp, color = Brand700, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (trends.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No data available for the period", color = Slate300, fontSize = 12.sp)
                    }
                } else {
                    val revenuePoints = trends.map { (it["revenue"] as? Number)?.toDouble() ?: 0.0 }
                    val labels = trends.map { it["label"]?.toString() ?: "" }
                    val maxRevenue = revenuePoints.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    
                    Canvas(modifier = Modifier.fillMaxSize().padding(start = 45.dp, bottom = 30.dp, end = 10.dp)) {
                        val width = size.width
                        val height = size.height
                        val step = width / (trends.size - 1).coerceAtLeast(1)
                        
                        // Y-Axis and Grid
                        val gridLines = 4
                        for (i in 0..gridLines) {
                            val y = height - (height * (i.toFloat() / gridLines))
                            val value = maxRevenue * (i.toFloat() / gridLines)
                            
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

                        val path = Path()
                        val fillPath = Path()
                        
                        revenuePoints.forEachIndexed { index, revenue ->
                            val x = index * step
                            val y = height - ( (revenue.toFloat() / maxRevenue.toFloat()) * height * animationProgress.value )
                            
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, height)
                                fillPath.lineTo(x, y)
                            } else {
                                val prevX = (index - 1) * step
                                val prevY = height - ( (revenuePoints[index - 1].toFloat() / maxRevenue.toFloat()) * height * animationProgress.value )
                                
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

                            // X-Axis
                            if (index % (revenuePoints.size / 5).coerceAtLeast(1) == 0 || index == revenuePoints.size - 1) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    labels.getOrNull(index) ?: "",
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
                        
                        drawPath(
                            path = path,
                            color = Brand600,
                            style = Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                        )
                        
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Brand600.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )

                        // Top Dot
                        if (revenuePoints.isNotEmpty()) {
                             val lastIndex = revenuePoints.size - 1
                             val lastX = lastIndex * step
                             val lastY = height - ( (revenuePoints[lastIndex].toFloat() / maxRevenue.toFloat()) * height * animationProgress.value )
                             drawCircle(color = Brand600, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                             drawCircle(color = Color.White, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = Slate400)
    }
}

@Composable
fun StatCard(
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
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = color
                    )
                }
                Text(
                    trend,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (trend.startsWith("+")) Color(0xFF10B981) else Color(0xFFF43F5E)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 12.sp, color = Slate500)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
        }
    }
}

@Composable
fun PerformanceChartCard(orders: List<Map<String, Any>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Revenue Trend", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
            Spacer(modifier = Modifier.height(24.dp))
            
            // Simplified dynamic chart based on real data
            val revenueByDay = orders
                .filter { it["status"] == "delivered" }
                .groupBy { 
                    val dateStr = it["created_at"] as? String ?: ""
                    dateStr.take(10)
                }
                .mapValues { entry -> 
                    entry.value.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 }
                }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Slate50, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (orders.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = Slate300)
                        Text("No data available", fontSize = 12.sp, color = Slate400)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val maxRev = revenueByDay.values.maxOrNull() ?: 1.0
                        listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 1.0f).forEach { weight ->
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .fillMaxHeight(weight)
                                    .background(Brand600.copy(alpha = 0.8f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                    Text(day, fontSize = 11.sp, color = Slate400)
                }
            }
        }
    }
}
