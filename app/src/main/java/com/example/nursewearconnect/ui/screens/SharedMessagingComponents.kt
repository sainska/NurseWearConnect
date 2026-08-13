package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nursewearconnect.ui.theme.*
import kotlin.math.absoluteValue

// System-inspired Colors (Replacing WhatsApp colors with standard Material-like palette)
val SystemOutgoingBubble @Composable get() = MaterialTheme.colorScheme.primaryContainer
val SystemIncomingBubble @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val SystemTicksBlue = Color(0xFF2196F3)
val SystemPrimary = Color(0xFF0D9488) // Brand600
val SystemChatBg @Composable get() = MaterialTheme.colorScheme.background

// Gmail-inspired Priority Colors
val PriorityDirect = Color(0xFF22C55E)
val PriorityGroup = Color(0xFFF59E0B)
val PriorityNeedsReply = Color(0xFFEF4444)
val PriorityLow = Color(0xFF94A3B8)

@Composable
fun DeterministicAvatar(
    name: String,
    modifier: Modifier = Modifier.size(44.dp)
) {
    val seedColors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65)
    )
    
    val backgroundColor = remember(name) {
        seedColors[name.hashCode().absoluteValue % seedColors.size]
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = backgroundColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: String,
    timestamp: String,
    isOutgoing: Boolean,
    imageUrl: String? = null,
    status: MessageStatus = MessageStatus.READ
) {
    val shape = if (isOutgoing) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isOutgoing) SystemOutgoingBubble else SystemIncomingBubble,
            shape = shape,
            shadowElevation = 1.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Column {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Image message",
                            modifier = Modifier
                                .sizeIn(maxWidth = 240.dp, maxHeight = 320.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (message.isNotEmpty()) {
                        Text(
                            text = message,
                            color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp, end = 48.dp)
                        )
                    } else if (imageUrl != null) {
                        Spacer(Modifier.width(80.dp)) // Min width if only image
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timestamp,
                        color = if (imageUrl != null && message.isEmpty()) Color.White.copy(alpha = 0.9f) else if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        style = if (imageUrl != null && message.isEmpty()) androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(blurRadius = 4f, color = Color.Black)
                        ) else androidx.compose.ui.text.TextStyle.Default
                    )
                    if (isOutgoing) {
                        Spacer(Modifier.width(4.dp))
                        MessageStatusTicks(status, isOnImage = imageUrl != null && message.isEmpty())
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusTicks(status: MessageStatus, isOnImage: Boolean = false) {
    val color = when (status) {
        MessageStatus.SENT -> if (isOnImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
        MessageStatus.DELIVERED -> if (isOnImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
        MessageStatus.READ -> if (isOnImage) SystemTicksBlue else SystemTicksBlue
    }
    when (status) {
        MessageStatus.SENT -> Icon(Icons.Default.Done, null, modifier = Modifier.size(14.dp), tint = color)
        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp), tint = color)
        MessageStatus.READ -> Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp), tint = color)
    }
}

enum class MessageStatus { SENT, DELIVERED, READ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNotificationCard(
    sender: String,
    snippet: String,
    category: String,
    priorityColor: Color,
    timestamp: String,
    isUnread: Boolean,
    onArchive: () -> Unit,
    onRead: () -> Unit,
    archiveIcon: ImageVector = Icons.Default.Archive,
    archiveColor: Color = Color.Red
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> { onRead(); false }
                SwipeToDismissBoxValue.EndToStart -> { onArchive(); true }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> archiveColor
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) 
                    Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) 
                        Icons.Default.Email else archiveIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        NotificationCard(sender, snippet, category, priorityColor, timestamp, isUnread)
    }
}

@Composable
fun NotificationCard(
    sender: String,
    snippet: String,
    category: String,
    priorityColor: Color,
    timestamp: String,
    isUnread: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        color = if (isUnread) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp)).background(priorityColor))
            Spacer(Modifier.width(12.dp))
            DeterministicAvatar(sender)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sender, fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(timestamp, fontSize = 12.sp, color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(category.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = priorityColor)
                Text(snippet, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isUnread) {
                Box(modifier = Modifier.padding(end = 16.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

fun formatTimestamp(isoString: String): String {
    if (isoString.isEmpty()) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoString.split(".")[0]) ?: return ""
        
        val now = java.util.Calendar.getInstance()
        val msgTime = java.util.Calendar.getInstance().apply { time = date }
        
        if (now.get(java.util.Calendar.DATE) == msgTime.get(java.util.Calendar.DATE)) {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(date)
        } else {
            java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(date)
        }
    } catch (e: Exception) {
        isoString.take(10)
    }
}
