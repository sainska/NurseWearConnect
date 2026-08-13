package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
fun UserLogsScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var showExportDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = selectedFilter != "All") {
        selectedFilter = "All"
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadAdminData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System User Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = { viewModel.exportLogsToCSV(context) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = { viewModel.clearSystemLogs() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = Color.Red.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf("All", "Security", "Transactions", "System")
                    filters.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Brand600,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val logs: List<Map<String, Any>> = uiState.systemLogs.filter { log ->
                    when (selectedFilter) {
                        "All" -> true
                        "Security" -> log["action"]?.toString()?.contains("Login", ignoreCase = true) == true || 
                                     log["action"]?.toString()?.contains("Auth", ignoreCase = true) == true ||
                                     log["action"]?.toString()?.contains("Security", ignoreCase = true) == true
                        "Transactions" -> log["action"]?.toString()?.contains("Order", ignoreCase = true) == true || 
                                         log["action"]?.toString()?.contains("Payment", ignoreCase = true) == true
                        "System" -> log["action"]?.toString()?.contains("Update", ignoreCase = true) == true ||
                                   log["action"]?.toString()?.contains("Config", ignoreCase = true) == true
                        else -> true
                    }
                }
                
                if (logs.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No logs found", color = Slate400)
                        }
                    }
                }

                items(logs) { logMap ->
                    val profiles = logMap["profiles"] as? Map<*, *>
                    val userName = profiles?.get("full_name")?.toString() ?: "System"
                    
                    val log = SystemLog(
                        user = userName,
                        action = logMap["action"]?.toString() ?: "Action",
                        time = logMap["created_at"]?.toString()?.split("T")?.get(0) ?: "N/A",
                        icon = when {
                            logMap["action"]?.toString()?.contains("Approved", ignoreCase = true) == true -> Icons.Default.VerifiedUser
                            logMap["action"]?.toString()?.contains("Order", ignoreCase = true) == true -> Icons.Default.ShoppingCart
                            logMap["action"]?.toString()?.contains("Security", ignoreCase = true) == true -> Icons.Default.Security
                            logMap["action"]?.toString()?.contains("Failed", ignoreCase = true) == true -> Icons.Default.Report
                            logMap["action"]?.toString()?.contains("Updated", ignoreCase = true) == true -> Icons.Default.Edit
                            else -> Icons.Default.History
                        },
                        categoryColor = when {
                            logMap["action"]?.toString()?.contains("Approved", ignoreCase = true) == true -> Color(0xFF10B981)
                            logMap["action"]?.toString()?.contains("Failed", ignoreCase = true) == true -> Color(0xFFEF4444)
                            else -> Brand600
                        },
                        details = logMap["details"]?.toString(),
                        severity = logMap["severity"]?.toString() ?: "info"
                    )
                    LogItemCard(log)
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Logs Exported") },
            text = { Text("System logs have been exported to CSV format and are ready for download.") },
            confirmButton = {
                Button(onClick = { showExportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Brand600)) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun LogItemCard(log: SystemLog) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(log.categoryColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = log.icon,
                    contentDescription = null,
                    tint = log.categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(log.user, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text(log.time, fontSize = 11.sp, color = Slate400)
                }
                
                Text(log.action, fontSize = 13.sp, color = Slate700, modifier = Modifier.padding(top = 2.dp))
                
                if (log.details != null) {
                    Surface(
                        modifier = Modifier.padding(top = 8.dp),
                        color = Slate50,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = log.details,
                            fontSize = 11.sp,
                            color = Slate500,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

data class SystemLog(
    val user: String,
    val action: String,
    val time: String,
    val icon: ImageVector,
    val categoryColor: Color,
    val details: String? = null,
    val severity: String = "info"
)
