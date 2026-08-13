package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.components.SimpleEmptyState
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.ui.viewmodel.MessagingViewModel
import com.example.nursewearconnect.ui.viewmodel.Conversation
import com.example.nursewearconnect.ui.viewmodel.Message
import com.example.nursewearconnect.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onBackClick: () -> Unit,
    homeViewModel: HomeViewModel,
    viewModel: MessagingViewModel
) {
    val conversations by viewModel.conversations.collectAsState()
    val allUsers by homeViewModel.allUsers.collectAsState()
    val activeTargetId by viewModel.activeTargetUserId.collectAsState()
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var showNewChat by remember { mutableStateOf(false) }
    
    // Auto-select conversation if activeTargetId is set from outside
    LaunchedEffect(activeTargetId, conversations) {
        if (activeTargetId != null) {
            val convo = conversations.find { it.other_user_id == activeTargetId }
            if (convo != null) {
                selectedConversation = convo
                viewModel.loadMessages(convo.other_user_id)
            }
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    val pullToRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = selectedConversation != null || activeTargetId != null || showNewChat) {
        if (showNewChat) {
            showNewChat = false
        } else {
            selectedConversation = null
            viewModel.clearActiveTarget()
        }
    }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isEmpty()) conversations
        else conversations.filter { 
            it.other_user_name.contains(searchQuery, ignoreCase = true) || 
            it.last_message.contains(searchQuery, ignoreCase = true)
        }
    }
    
    AnimatedContent(
        targetState = if (showNewChat) "new" else if (selectedConversation != null || activeTargetId != null) "detail" else "list",
        transitionSpec = {
            if (targetState == "detail" || targetState == "new") {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "chat_navigation"
    ) { screen ->
        when (screen) {
            "new" -> {
                NewChatScreen(
                    onBack = { showNewChat = false },
                    onUserSelected = { id, name ->
                        viewModel.openConversation(id)
                        showNewChat = false
                    },
                    homeViewModel = homeViewModel
                )
            }
            "detail" -> {
                val targetName = remember(activeTargetId, selectedConversation, allUsers) {
                    selectedConversation?.other_user_name ?: allUsers.find { u -> u["id"] == activeTargetId }?.get("full_name")?.toString() ?: "Chat"
                }

                ChatDetailScreen(
                    conversation = selectedConversation ?: Conversation(
                        last_message_id = "",
                        other_user_id = activeTargetId ?: "",
                        other_user_name = targetName,
                        last_message = "",
                        last_message_time = "",
                        last_message_priority = "normal",
                        unread_count = 0
                    ),
                    viewModel = viewModel,
                    onBack = { 
                        selectedConversation = null 
                        viewModel.clearActiveTarget()
                    }
                )
            }
            "list" -> {
                Scaffold(
                    topBar = {
                        if (isSearching) {
                            CenterAlignedTopAppBar(
                                title = {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search messages...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        singleLine = true
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { 
                                        isSearching = false
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Stop Search")
                                    }
                                }
                            )
                        } else {
                            CenterAlignedTopAppBar(
                                title = { Text("Messages", fontWeight = FontWeight.Black) },
                                navigationIcon = {
                                    IconButton(onClick = onBackClick) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { isSearching = true }) {
                                        Icon(Icons.Default.Search, "Search")
                                    }
                                    IconButton(onClick = { showNewChat = true }) {
                                        Icon(Icons.Default.EditNote, "New Chat")
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Color.White,
                                    titleContentColor = Slate900
                                )
                            )
                        }
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
                        if (filteredConversations.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                SimpleEmptyState(
                                    icon = Icons.Default.ChatBubbleOutline,
                                    title = if (searchQuery.isNotEmpty()) "No Results" else "Inbox Empty",
                                    description = if (searchQuery.isNotEmpty()) "Try searching for someone else." else "Start a conversation with a vendor or nurse.",
                                    actionLabel = if (searchQuery.isNotEmpty()) "Clear Search" else "Start Chat",
                                    onActionClick = { 
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = ""
                                            isSearching = false
                                        } else {
                                            showNewChat = true
                                        }
                                    }
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredConversations, key = { it.other_user_id }) { convo ->
                                    ConversationCard(convo) { 
                                        selectedConversation = convo
                                        viewModel.loadMessages(convo.other_user_id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation, 
    viewModel: MessagingViewModel, 
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.currentMessages.collectAsState()
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadChatImage(it, conversation.other_user_id, context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeterministicAvatar(conversation.other_user_name, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(conversation.other_user_name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Active now", fontSize = 11.sp, color = Brand600)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { /* Call logic */ }) { Icon(Icons.Default.Phone, null, tint = Brand600) }
                    IconButton(onClick = { /* More options */ }) { Icon(Icons.Default.MoreVert, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp, color = Color.White, shadowElevation = 16.dp) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .imePadding()
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Add, null, tint = Brand600)
                    }
                    
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Write a message...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Slate100,
                            unfocusedContainerColor = Slate100,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(conversation.other_user_id, messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        containerColor = Brand600,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Slate50)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(
                        message = msg.message,
                        imageUrl = msg.image_url,
                        timestamp = formatTimestamp(msg.created_at ?: ""),
                        isOutgoing = msg.sender_id != conversation.other_user_id,
                        status = when {
                            msg.is_read -> MessageStatus.READ
                            msg.is_delivered -> MessageStatus.DELIVERED
                            else -> MessageStatus.SENT
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationCard(convo: Conversation, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (convo.unread_count > 0) Brand100 else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                DeterministicAvatar(convo.other_user_name, modifier = Modifier.size(52.dp))
                if (convo.unread_count > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                            .background(Brand600, CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = convo.other_user_name,
                        fontSize = 15.sp,
                        fontWeight = if (convo.unread_count > 0) FontWeight.ExtraBold else FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = formatTimestamp(convo.last_message_time),
                        fontSize = 11.sp,
                        color = if (convo.unread_count > 0) Brand600 else Slate400,
                        fontWeight = if (convo.unread_count > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                Spacer(Modifier.height(2.dp))
                
                Text(
                    text = convo.last_message,
                    fontSize = 13.sp,
                    color = if (convo.unread_count > 0) Slate800 else Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (convo.unread_count > 0) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
