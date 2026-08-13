package com.example.nursewearconnect.ui.screens

import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.model.ProfileUpdateRequest
import com.example.nursewearconnect.utils.Constants
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun VendorPendingScreen(
    status: String,
    statusNotes: String?,
    onLogout: () -> Unit,
    viewModel: HomeViewModel? = null
) {
    val isRejected = status == "rejected"
    val isCorrection = status == "pending_corrections"
    var showEditDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val userRepository = viewModel?.getUserRepository()
    val uiState by if (viewModel != null) {
        viewModel.uiState.collectAsState()
    } else {
        remember { mutableStateOf(com.example.nursewearconnect.ui.viewmodel.HomeUiState()) }
    }
    
    val userProfile by if (userRepository != null) {
        userRepository.userProfile.collectAsState()
    } else {
        remember { mutableStateOf<Map<String, Any>?>(null) }
    }
    
    val profileName = userProfile?.get("full_name")?.toString() ?: "Vendor"

    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = uiState.adminEmail,
            adminPhone = uiState.adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = "Vendor Support - ${userProfile?.get("business_name") ?: profileName}"
        )
    }

    if (showEditDialog && viewModel != null) {
        val fullName = userProfile?.get("full_name") as? String ?: ""
        val phoneNumber = userProfile?.get("phone_number") as? String ?: ""
        val address = userProfile?.get("address") as? String ?: ""
        val businessName = userProfile?.get("business_name") as? String ?: ""
        val location = userProfile?.get("location") as? String ?: ""
        val bio = userProfile?.get("bio") as? String ?: userProfile?.get("business_description") as? String ?: ""
        val currentLicenseUrl = userProfile?.get("business_license_url") as? String

        var businessLicenseUrl by remember { mutableStateOf(currentLicenseUrl) }
        var isUploading by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                val contentResolver = context.contentResolver
                val extension = when (contentResolver.getType(it)) {
                    "application/pdf" -> "pdf"
                    "image/png" -> "png"
                    "image/jpeg" -> "jpeg"
                    else -> "jpg"
                }
                val inputStream = contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    isUploading = true
                    viewModel.viewModelScope.launch {
                        val result = userRepository?.uploadFile(
                            userRepository?.getUserId() ?: "",
                            bytes,
                            "licenses",
                            extension
                        )
                        isUploading = false
                        result?.onSuccess { url ->
                            businessLicenseUrl = url
                        }
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit & Resubmit", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Update your details and resubmit for approval.")
                    
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { /* handled by save */ },
                        label = { Text("Business Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false // For simplicity in this dialog, usually you'd have mutable states
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUploading) { 
                                launcher.launch("*/*")
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (businessLicenseUrl != null) Brand500 else Slate200)
                    ) {
                        Box {
                            if (isUploading) {
                                ShimmerPlaceholder(
                                    modifier = Modifier.matchParentSize(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .alpha(if (isUploading) 0.5f else 1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (businessLicenseUrl != null) Icons.Default.Edit else Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = if (businessLicenseUrl != null) Brand500 else Slate400
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (businessLicenseUrl != null) "New License Uploaded" else "Upload Updated License",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (businessLicenseUrl != null) Brand700 else Slate700
                                    )
                                }
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Brand600)
                                }
                            }
                        }
                    }
                    
                    Text("Note: Saving will reset your status to 'Pending' for review.", fontSize = 12.sp, color = Slate500)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val request = ProfileUpdateRequest(
                            status = "pending",
                            statusNotes = "Resubmitted with updated documents",
                            businessDescription = bio,
                            businessLicenseUrl = businessLicenseUrl
                        )
                        viewModel.updateProfile(request)
                        showEditDialog = false
                    },
                    enabled = !isUploading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Resubmit Application")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(if (isRejected || isCorrection) Color(0xFFFEF2F2) else Brand50),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isRejected -> Icons.Default.ErrorOutline
                        isCorrection -> Icons.Default.Warning
                        else -> Icons.Default.AccessTime
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .graphicsLayer {
                            if (!isRejected && !isCorrection) {
                                scaleX = scale
                                scaleY = scale
                            }
                        },
                    tint = if (isRejected || isCorrection) Color(0xFFEF4444) else Brand600
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when {
                    isRejected -> "Action Required"
                    isCorrection -> "Corrections Needed"
                    else -> "Application Pending"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    isRejected -> "Your application was rejected. Please see feedback below."
                    isCorrection -> "Your application needs some updates before you can start selling."
                    else -> "We're currently reviewing your business details. This usually takes 24-48 hours."
                },
                fontSize = 16.sp,
                color = Slate600,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            if (!statusNotes.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isRejected || isCorrection) Color(0xFFFFF7ED) else Brand50,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Feedback from Support:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isRejected || isCorrection) Color(0xFFC2410C) else Brand700
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            statusNotes,
                            fontSize = 14.sp,
                            color = Slate700,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (isRejected || isCorrection) {
                Button(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Review & Update Application")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = { showSupportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Contact Support")
                }
            } else {
                Button(
                    onClick = { showSupportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                ) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Contact Support")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }
}
