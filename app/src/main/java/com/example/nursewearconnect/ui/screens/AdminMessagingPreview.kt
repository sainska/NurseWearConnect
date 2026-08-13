package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nursewearconnect.ui.theme.*

@Preview(showBackground = true)
@Composable
fun PreviewNotificationCards() {
    NurseWearConnectTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notification Item (Unread)", style = MaterialTheme.typography.labelMedium, color = Slate500)
            NotificationCard(
                sender = "System Alert",
                snippet = "New vendor application received from MediSupply Ltd.",
                category = "Urgent",
                priorityColor = Color.Red,
                timestamp = "2m ago",
                isUnread = true
            )
            
            Spacer(Modifier.height(16.dp))
            Text("Notification Item (Read)", style = MaterialTheme.typography.labelMedium, color = Slate500)
            NotificationCard(
                sender = "Dr. Sarah Miller",
                snippet = "The inventory for Ward B has been updated.",
                category = "Inventory",
                priorityColor = Brand600,
                timestamp = "1h ago",
                isUnread = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSummaryChips() {
    NurseWearConnectTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SummaryChipRow(unreadCount = 5, needsReplyCount = 2, groupCount = 12)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSnoozedTab() {
    NurseWearConnectTheme {
        // Since we can't easily mock the ViewModel here without more setup, 
        // we'd usually use a stateless version of the component for previews.
        // But for now, we'll just preview the card.
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Snoozed Notification Card", style = MaterialTheme.typography.labelMedium, color = Slate500)
            SwipeableNotificationCard(
                sender = "Abandoned Cart",
                snippet = "User 'John Doe' left items in their cart.",
                category = "Sales",
                priorityColor = Slate400,
                timestamp = "3h ago",
                isUnread = false,
                onArchive = {},
                onRead = {},
                archiveIcon = Icons.Default.Unarchive,
                archiveColor = Brand600
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PreviewAdminSmartMessagingLayout() {
    NurseWearConnectTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Admin Console", color = Color.White) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Brand600)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                SummaryChipRow(3, 1, 5)
                Spacer(Modifier.height(20.dp))
                CenterText("Select a tab to view notifications")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAdminConversation() {
    NurseWearConnectTheme {
        AdminConversationScreen(userName = "Sarah Miller", onBack = {})
    }
}
