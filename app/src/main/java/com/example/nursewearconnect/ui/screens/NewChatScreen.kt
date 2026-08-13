package com.example.nursewearconnect.ui.screens

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.ui.viewmodel.MessagingViewModel
import com.example.nursewearconnect.utils.AppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onUserSelected: (String, String) -> Unit,
    homeViewModel: HomeViewModel
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val allUsers by homeViewModel.allUsers.collectAsState()
    val currentRole = uiState.userRole.lowercase()
    val currentUserId = uiState.userId

    var searchQuery by remember { mutableStateOf("") }

    // Logic for who can be messaged
    val targetUsers = remember(allUsers, currentRole, currentUserId, searchQuery) {
        allUsers.filter { user ->
            val userId = user["id"]?.toString() ?: ""
            val role = user["role"]?.toString()?.lowercase() ?: ""
            val name = user["full_name"]?.toString() ?: ""
            val email = user["email"]?.toString() ?: ""
            
            val isNotSelf = userId != currentUserId
            val matchesSearch = name.contains(searchQuery, ignoreCase = true) || email.contains(searchQuery, ignoreCase = true)
            
            val canMessage = when (currentRole) {
                "admin" -> true // Admin can message anyone
                "vendor" -> {
                    // Vendors can message Customers or Admins
                    role == "student" || role == "professional" || role == "admin"
                }
                else -> {
                    // Customers can message Vendors or Admins
                    role == "vendor" || role == "admin"
                }
            }
            
            isNotSelf && matchesSearch && canMessage
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Conversation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = Slate50
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by name or email...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = AppUtils.standardOutlinedTextFieldColors()
            )

            Text(
                "SUGGESTED CONTACTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                letterSpacing = 1.sp
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(targetUsers) { user ->
                    val id = user["id"]?.toString() ?: ""
                    val name = user["full_name"]?.toString() ?: "Unknown"
                    val role = user["role"]?.toString() ?: ""
                    
                    UserRow(
                        name = name,
                        role = role,
                        onClick = { onUserSelected(id, name) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Slate100, thickness = 0.5.dp)
                }

                if (targetUsers.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("No contacts found", color = Slate400)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserRow(name: String, role: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeterministicAvatar(name, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name, fontWeight = FontWeight.Bold, color = Slate900, fontSize = 15.sp)
            Text(
                role.replaceFirstChar { it.uppercase() }, 
                fontSize = 12.sp, 
                color = when(role.lowercase()) {
                    "admin" -> Color(0xFF6366F1)
                    "vendor" -> Brand600
                    else -> Color(0xFF10B981)
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
