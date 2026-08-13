package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*

import androidx.compose.runtime.*

@Composable
fun UserBannedScreen(
    onLogout: () -> Unit,
    adminEmail: String,
    adminPhone: String,
    reason: String? = null
) {
    var showSupportDialog by remember { mutableStateOf(false) }
    
    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = adminEmail,
            adminPhone = adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = "Account Suspension Appeal"
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Banned",
                modifier = Modifier.size(120.dp),
                tint = Color(0xFFDC2626)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Account Suspended",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Your account has been suspended for violating our terms of service and community guidelines.",
                textAlign = TextAlign.Center,
                color = Slate600,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            
            if (!reason.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = Color(0xFFFEF2F2),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Reason:",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            fontSize = 14.sp
                        )
                        Text(
                            text = reason,
                            color = Color(0xFFB91C1C),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
            
            Button(
                onClick = { showSupportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Contact Support", modifier = Modifier.padding(8.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onLogout) {
                Text("Log Out", color = Slate600)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserBannedScreenPreview() {
    com.example.nursewearconnect.ui.theme.NurseWearConnectTheme {
        UserBannedScreen(
            adminEmail = "support@nursewear.com",
            adminPhone = "+254700000000",
            onLogout = {},
            reason = "Violation of community guidelines."
        )
    }
}
