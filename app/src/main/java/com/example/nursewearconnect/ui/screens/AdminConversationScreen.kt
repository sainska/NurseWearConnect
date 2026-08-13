package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConversationScreen(userName: String, onBack: () -> Unit) {
    var textState by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeterministicAvatar(userName, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(userName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("online", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand600),
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Videocam, null, tint = Color.White) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Call, null, tint = Color.White) }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                }
            )
        },
        bottomBar = {
            ChatComposer(
                text = textState,
                onTextChange = { textState = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate50)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { DateHeader("TODAY") }
                
                items(getMockConversation()) { msg ->
                    ChatBubble(
                        message = msg.text,
                        timestamp = msg.time,
                        isOutgoing = msg.isOutgoing,
                        status = msg.status
                    )
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = Color(0xFFD1E9F4),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = date,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Slate700
            )
        }
    }
}

@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.SentimentSatisfiedAlt, null, tint = Slate400) }
                
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    colors = AppUtils.standardTextFieldColors().copy(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
                
                IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, null, tint = Slate400) }
                if (text.isEmpty()) {
                    IconButton(onClick = {}) { Icon(Icons.Default.PhotoCamera, null, tint = Slate400) }
                }
            }
        }
        
        Spacer(Modifier.width(8.dp))
        
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = Brand600,
            shadowElevation = 2.dp
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = if (text.isEmpty()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Action",
                    tint = Color.White
                )
            }
        }
    }
}

data class MockMessage(val text: String, val time: String, val isOutgoing: Boolean, val status: MessageStatus = MessageStatus.READ)

fun getMockConversation() = listOf(
    MockMessage("Hello Admin, I've updated the logs for Ward A.", "10:15 AM", false),
    MockMessage("Thank you, Sarah. Did you include the vital signs data?", "10:16 AM", true, MessageStatus.READ),
    MockMessage("Yes, it's all in the PDF attachment.", "10:18 AM", false),
    MockMessage("Great. I'll review it shortly.", "10:20 AM", true, MessageStatus.DELIVERED)
)
