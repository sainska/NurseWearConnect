package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.components.SimpleEmptyState
import com.example.nursewearconnect.ui.viewmodel.MessagingViewModel
import com.example.nursewearconnect.ui.viewmodel.Notification
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.utils.AppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(onBackClick: () -> Unit, viewModel: MessagingViewModel) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        if (unreadCount > 0) {
                            Text("$unreadCount unread", style = MaterialTheme.typography.labelSmall, color = Slate500)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notifications.any { !it.is_read }) {
                        TextButton(onClick = { 
                            val unreadIds = notifications.filter { !it.is_read }.map { it.id }
                            viewModel.bulkAction(unreadIds, isRead = true)
                        }) {
                            Text("Mark all read", color = Brand600, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900,
                    navigationIconContentColor = Slate900
                )
            )
        },
        containerColor = Slate50
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier.padding(padding)
        ) {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SimpleEmptyState(
                        icon = Icons.Default.NotificationsNone,
                        title = "No Notifications",
                        description = "You're all caught up! Updates about your orders and account will appear here.",
                        actionLabel = "Refresh",
                        onActionClick = { viewModel.refresh() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            onMarkAsRead = { viewModel.markNotificationAsRead(notification.id) },
                            onArchive = { viewModel.archiveNotification(notification.id) }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: Notification,
    onMarkAsRead: () -> Unit,
    onArchive: () -> Unit
) {
    val categoryIcon = when (notification.category.lowercase()) {
        "order" -> Icons.Default.LocalMall
        "payment" -> Icons.Default.Payments
        "system" -> Icons.Default.SettingsSuggest
        "promo" -> Icons.Default.LocalOffer
        else -> Icons.Default.Info
    }
    
    val categoryColor = when (notification.category.lowercase()) {
        "order" -> Color(0xFF3B82F6) // Blue
        "payment" -> Color(0xFF10B981) // Emerald
        "system" -> Color(0xFF6366F1) // Indigo
        "promo" -> Color(0xFFF59E0B) // Amber
        else -> Slate500
    }

    Surface(
        onClick = { if (!notification.is_read) onMarkAsRead() },
        shape = RoundedCornerShape(16.dp),
        color = if (notification.is_read) Color.White else Color.White,
        border = BorderStroke(1.dp, if (notification.is_read) Slate100 else Brand600.copy(alpha = 0.2f)),
        shadowElevation = if (notification.is_read) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon with background
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = categoryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(categoryIcon, null, tint = categoryColor, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        notification.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        AppUtils.timeAgo(notification.created_at ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.is_read) FontWeight.Bold else FontWeight.ExtraBold,
                    color = if (notification.is_read) Slate700 else Slate900
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (!notification.is_read) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp)
                        .size(8.dp)
                        .background(Brand600, CircleShape)
                )
            }
            
            // Actions
            IconButton(onClick = onArchive, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                Icon(Icons.Default.Clear, "Dismiss", tint = Slate300, modifier = Modifier.size(16.dp))
            }
        }
    }
}
