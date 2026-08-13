package com.example.nursewearconnect.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.utils.AppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserManagementScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Students", "Vendors", "Admins", "Live")
    val allUsers by viewModel.allUsers.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    var showAddUserDialog by remember { mutableStateOf(false) }
    var userToMessage by remember { mutableStateOf<AdminUserItem?>(null) }
    var userToDelete by remember { mutableStateOf<AdminUserItem?>(null) }
    var adminMessageText by remember { mutableStateOf("") }

    BackHandler(enabled = showAddUserDialog || userToMessage != null || userToDelete != null || (selectedTab != 0)) {
        when {
            showAddUserDialog -> showAddUserDialog = false
            userToMessage != null -> userToMessage = null
            userToDelete != null -> userToDelete = null
            selectedTab != 0 -> selectedTab = 0
            else -> onBackClick()
        }
    }

    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onAdd = { newUser ->
                viewModel.addUser(newUser)
                showAddUserDialog = false
            }
        )
    }

    userToMessage?.let { user ->
        AlertDialog(
            onDismissRequest = { userToMessage = null },
            title = { Text("Direct Admin Message to ${user.name}") },
            text = {
                OutlinedTextField(
                    value = adminMessageText,
                    onValueChange = { adminMessageText = it },
                    placeholder = { Text("Type an official admin message...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendMessageToUser(user.id, adminMessageText)
                        userToMessage = null
                        adminMessageText = ""
                    },
                    enabled = adminMessageText.isNotBlank()
                ) { Text("Send Message") }
            },
            dismissButton = {
                TextButton(onClick = { userToMessage = null }) { Text("Cancel") }
            }
        )
    }

    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to permanently delete ${user.name}'s account? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUser(user.id)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadAdminData()
    }

    LaunchedEffect(selectedTab) {
        if (tabs[selectedTab] == "Live") {
            viewModel.loadLiveUsers()
        }
    }

    // Periodically refresh live users if on that tab
    LaunchedEffect(selectedTab) {
        if (tabs[selectedTab] == "Live") {
            while(true) {
                kotlinx.coroutines.delay(10000) // 10 seconds for more real-time feel
                viewModel.loadLiveUsers()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(24.dp), tint = Brand600)
                        Spacer(Modifier.width(12.dp))
                        Text("User Management", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAdminData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh, 
                            contentDescription = "Refresh", 
                            tint = if (uiState.isLoading) Brand600 else Slate400,
                            modifier = if (uiState.isLoading) Modifier.rotate(rotation) else Modifier
                        )
                    }
                    IconButton(onClick = { showAddUserDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add User", tint = Brand600)
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
            // Search and Filter
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("Search users by name or email...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) },
                        shape = RoundedCornerShape(12.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors(),
                        singleLine = true
                    )
                    
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = Brand600,
                        edgePadding = 16.dp,
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
                                text = { 
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // User List
            if (uiState.isLoading && allUsers.isEmpty() && uiState.activeSessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Brand50
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = Brand600,
                                    modifier = Modifier.size(40.dp)
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(80.dp),
                                    color = Brand600,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Loading user directory...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val usersToDisplay = if (tabs[selectedTab] == "Live") {
                        uiState.activeSessions.filter { 
                            val name = it["full_name"]?.toString() ?: ""
                            name.contains(searchQuery, ignoreCase = true)
                        }
                    } else {
                        allUsers.filter { 
                            val role = it["role"]?.toString() ?: ""
                            val name = it["full_name"]?.toString() ?: ""
                            val email = it["email"]?.toString() ?: ""
                            
                            role.lowercase() == tabs[selectedTab].lowercase().removeSuffix("s") &&
                            (name.contains(searchQuery, ignoreCase = true) || email.contains(searchQuery, ignoreCase = true))
                        }
                    }

                    items(usersToDisplay) { userMap ->
                        val user = AdminUserItem(
                            id = userMap["id"]?.toString() ?: userMap["user_id"]?.toString() ?: "",
                            name = userMap["full_name"]?.toString() ?: "Unknown",
                            email = userMap["email"]?.toString() ?: "",
                            phone = userMap["phone_number"]?.toString() ?: "",
                            role = userMap["role"]?.toString() ?: "",
                            status = userMap["status"]?.toString() ?: "active",
                            institution = userMap["institution"]?.toString(),
                            lastSignIn = userMap["last_sign_in_at"]?.toString(),
                            lastSignOut = userMap["last_sign_out_at"]?.toString(),
                            sessionStart = userMap["session_start"]?.toString()
                        )
                        UserManagementCard(
                            user = user,
                            isLive = tabs[selectedTab] == "Live",
                            onMessage = { 
                                userToMessage = user
                            },
                            onDelete = { userToDelete = user },
                            onBan = { viewModel.updateUserStatus(user.id, "banned") },
                            onApprove = { viewModel.approveVendor(user.id) },
                            onUnban = { viewModel.updateUserStatus(user.id, "active") }
                        )
                    }
                    
                    if (usersToDisplay.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("No users found", color = Slate400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserManagementCard(
    user: AdminUserItem,
    isLive: Boolean = false,
    onMessage: () -> Unit,
    onDelete: () -> Unit,
    onBan: () -> Unit,
    onApprove: () -> Unit,
    onUnban: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = when {
                            user.status.lowercase() == "banned" || user.status.lowercase() == "rejected" -> Color(0xFFFEE2E2)
                            user.status.lowercase() == "pending" -> Color(0xFFFEF3C7)
                            user.status.lowercase() == "active" -> Color(0xFFDCFCE7)
                            else -> Brand50
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                user.name.take(1).uppercase(),
                                color = when {
                                    user.status.lowercase() == "banned" || user.status.lowercase() == "rejected" -> Color(0xFFDC2626)
                                    user.status.lowercase() == "pending" -> Color(0xFFD97706)
                                    user.status.lowercase() == "active" -> Color(0xFF16A34A)
                                    else -> Brand600
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(user.name, fontWeight = FontWeight.Bold, color = Slate900, fontSize = 15.sp)
                    Text(user.email, fontSize = 12.sp, color = Slate500)
                    if (user.phone.isNotEmpty()) {
                        Text(user.phone, fontSize = 12.sp, color = Slate400)
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = Slate400)
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Send Message") },
                            onClick = { onMessage(); showMenu = false },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }
                        )
                        if (user.status == "pending" && user.role == "vendor") {
                            DropdownMenuItem(
                                text = { Text("Approve Vendor") },
                                onClick = { onApprove(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Check, null, tint = Color(0xFF22C55E)) }
                            )
                        }
                        if (user.status != "banned") {
                            DropdownMenuItem(
                                text = { Text("Ban User", color = Color.Red) },
                                onClick = { onBan(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = Color.Red) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Unban User") },
                                onClick = { onUnban(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Undo, null) }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Account", color = Color.Red) },
                            onClick = { 
                                showMenu = false
                                onDelete() 
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when(user.status.lowercase()) {
                        "active" -> Color(0xFFDCFCE7)
                        "pending" -> Color(0xFFFEF3C7)
                        "rejected" -> Color(0xFFFEE2E2)
                        "banned" -> Color(0xFFFEF2F2)
                        else -> Slate100
                    }
                ) {
                    val statusText = when(user.status.lowercase()) {
                        "pending" -> "PENDING VERIFICATION"
                        "active" -> "VERIFIED"
                        else -> user.status.uppercase()
                    }
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when(user.status.lowercase()) {
                            "active" -> Color(0xFF16A34A)
                            "pending" -> Color(0xFFD97706)
                            "rejected" -> Color(0xFFDC2626)
                            "banned" -> Color(0xFFB91C1C)
                            else -> Slate600
                        }
                    )
                }
                
                if (user.institution != null) {
                    Text("•", color = Slate300)
                    Text(user.institution, fontSize = 11.sp, color = Slate400)
                }

                if (isLive) {
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("Online Now", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                        }
                        
                        val duration = remember(user.sessionStart) {
                            if (user.sessionStart != null) {
                                try {
                                    val start = java.time.ZonedDateTime.parse(user.sessionStart)
                                    val now = java.time.ZonedDateTime.now()
                                    java.time.Duration.between(start, now).toMinutes()
                                } catch (e: Exception) { null }
                            } else null
                        }
                        
                        if (duration != null) {
                            Text("${duration}m in app", fontSize = 11.sp, color = Slate400)
                        }
                    }
                } else if (user.lastSignIn != null) {
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Last Login: ${AppUtils.timeAgo(user.lastSignIn)}", fontSize = 11.sp, color = Slate400)
                        if (user.lastSignOut != null) {
                            val sessionMinutes = remember(user.lastSignIn, user.lastSignOut) {
                                try {
                                    val start = java.time.ZonedDateTime.parse(user.lastSignIn)
                                    val end = java.time.ZonedDateTime.parse(user.lastSignOut)
                                    java.time.Duration.between(start, end).toMinutes()
                                } catch (e: Exception) { null }
                            }
                            if (sessionMinutes != null && sessionMinutes > 0) {
                                Text("Stayed: ${sessionMinutes}m", fontSize = 11.sp, color = Slate400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("student") }
    val roles = listOf("student", "vendor", "admin")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                Text("Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    roles.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(mapOf(
                        "full_name" to name,
                        "email" to email,
                        "phone_number" to phone,
                        "role" to role,
                        "status" to "active",
                        "id" to java.util.UUID.randomUUID().toString()
                    ))
                },
                enabled = name.isNotBlank() && email.isNotBlank()
            ) {
                Text("Add User")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MessageUserDialog(
    userName: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message $userName") },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Enter message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = AppUtils.standardOutlinedTextFieldColors()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSend(message) },
                enabled = message.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class AdminUserItem(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val role: String,
    val status: String,
    val institution: String? = null,
    val lastSignIn: String? = null,
    val lastSignOut: String? = null,
    val sessionStart: String? = null
)

@Preview(showBackground = true)
@Composable
fun AdminUserManagementScreenPreview() {
    NurseWearConnectTheme {
        AdminUserManagementScreen(
            onBackClick = {},
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        )
    }
}
