package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.ui.viewmodel.MessagingViewModel
import com.example.nursewearconnect.ui.viewmodel.Conversation
import com.example.nursewearconnect.ui.viewmodel.Notification
import com.example.nursewearconnect.utils.AppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSmartMessagingScreen(
    viewModel: MessagingViewModel,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Chats", "In-App Notifications", "Snoozed")
    
    val conversations by viewModel.conversations.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    
    val unreadChats = conversations.count { it.unread_count > 0 }
    val needsReplyNotifs = notifications.count { it.priority_level.lowercase() == "needs_reply" && !it.is_read }
    val groupNotifs = notifications.count { it.category.lowercase() == "group" }

    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }

    BackHandler(enabled = selectedConversation != null) {
        selectedConversation = null
    }

    selectedConversation?.let { conversation ->
        ChatDetailScreen(
            conversation = conversation,
            viewModel = viewModel,
            onBack = { selectedConversation = null }
        )
    } ?: run {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text("Admin Console", style = MaterialTheme.typography.titleMedium, color = Color.White) 
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        if (selectedTab == 1) {
                            IconButton(onClick = {
                                val unreadIds = notifications.filter { !it.is_read && !it.is_archived }.map { it.id }
                                if (unreadIds.isNotEmpty()) {
                                    viewModel.bulkAction(unreadIds, isRead = true)
                                }
                            }) {
                                Icon(Icons.Default.DoneAll, "Mark all as read", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // Summary Chip Row (Gmail-inspired)
                SummaryChipRow(unreadChats, needsReplyNotifs, groupNotifs)

                // Custom Tab Bar
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 13.sp) }
                        )
                    }
                }

                // Content Switcher
                AnimatedContent(targetState = selectedTab, label = "TabSwitch") { target ->
                    when (target) {
                        0 -> ChatListTab(conversations) { 
                            selectedConversation = it
                            viewModel.loadMessages(it.other_user_id)
                        }
                        1 -> NotificationInboxTab(notifications, viewModel)
                        2 -> SnoozedNotificationsTab(viewModel)
                        else -> CenterText("No content")
                    }
                }
            }
        }
    }
}

@Composable
fun SnoozedNotificationsTab(viewModel: MessagingViewModel) {
    val notifications by viewModel.notifications.collectAsState()
    val snoozed = notifications.filter { it.is_archived }

    if (snoozed.isEmpty()) {
        CenterText("No snoozed alerts")
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    val snoozedIds = snoozed.map { it.id }
                    viewModel.bulkAction(snoozedIds, isArchived = false)
                }) {
                    Text("Restore All", color = SystemPrimary)
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(snoozed) { notification ->
                    SwipeableNotificationCard(
                        sender = notification.title,
                        snippet = notification.body,
                        category = notification.category,
                        priorityColor = Slate400,
                        timestamp = AppUtils.timeAgo(notification.created_at),
                        isUnread = false,
                        onArchive = { viewModel.bulkAction(listOf(notification.id), isArchived = false) },
                        onRead = { viewModel.markNotificationAsRead(notification.id) },
                        archiveIcon = Icons.Default.Unarchive,
                        archiveColor = SystemPrimary
                    )
                    HorizontalDivider(color = Slate200, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun SummaryChipRow(unreadCount: Int, needsReplyCount: Int, groupCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip("$unreadCount unread", MaterialTheme.colorScheme.primary)
        SummaryChip("$needsReplyCount needs reply", PriorityNeedsReply)
        SummaryChip("$groupCount groups", PriorityGroup)
    }
}

@Composable
fun SummaryChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ChatListTab(conversations: List<Conversation>, onChatClick: (Conversation) -> Unit) {
    if (conversations.isEmpty()) {
        CenterText("No active conversations")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(conversations) { chat ->
                ChatRow(chat, onChatClick)
                HorizontalDivider(color = Slate200, thickness = 0.5.dp, modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
fun NotificationInboxTab(notifications: List<Notification>, viewModel: MessagingViewModel) {
    val activeNotifications = notifications.filter { !it.is_archived }
    if (activeNotifications.isEmpty()) {
        CenterText("No notifications")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(activeNotifications) { notification ->
                SwipeableNotificationCard(
                    sender = notification.title,
                    snippet = notification.body,
                    category = notification.category,
                    priorityColor = when (notification.priority_level.lowercase()) {
                        "direct" -> PriorityDirect
                        "group" -> PriorityGroup
                        "needs_reply" -> PriorityNeedsReply
                        else -> PriorityLow
                    },
                    timestamp = AppUtils.timeAgo(notification.created_at),
                    isUnread = !notification.is_read,
                    onArchive = { viewModel.archiveNotification(notification.id) },
                    onRead = { viewModel.markNotificationAsRead(notification.id) }
                )
                HorizontalDivider(color = Slate200, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun ChatRow(chat: Conversation, onClick: (Conversation) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(chat) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeterministicAvatar(chat.other_user_name)
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.other_user_name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = AppUtils.timeAgo(chat.last_message_time),
                    fontSize = 12.sp,
                    color = if (chat.unread_count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.last_message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.unread_count > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unread_count.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
