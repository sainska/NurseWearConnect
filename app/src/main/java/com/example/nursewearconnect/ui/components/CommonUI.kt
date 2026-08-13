package com.example.nursewearconnect.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.example.nursewearconnect.ui.theme.*

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(10f, 10f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim)
    )

    Box(modifier = modifier.clip(shape).background(brush))
}

@Composable
fun EmptyState(
    icon: ImageVector? = null,
    illustration: (@Composable () -> Unit)? = null,
    title: String,
    description: String,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
    onActionClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustration != null) {
            illustration()
        } else if (icon != null) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = Slate50
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Slate400, modifier = Modifier.size(40.dp))
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Slate900, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(description, fontSize = 14.sp, color = Slate500, textAlign = TextAlign.Center, lineHeight = 20.sp)
        
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SimpleEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: () -> Unit = {}
) {
    EmptyState(
        icon = icon,
        title = title,
        description = description,
        actionLabel = actionLabel,
        onActionClick = onActionClick
    )
}

@Composable
fun InventoryHealthCard(
    label: String,
    value: String,
    bgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Column(Modifier.padding(12.dp)) {
            if (icon != null) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
            }
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = iconColor)
            Text(label, fontSize = 11.sp, color = iconColor.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun OrderIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(120.dp)
            .background(Brand50, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Brand600
        )
    }
}

@Composable
fun SupportDialog(
    adminEmail: String,
    adminPhone: String,
    onDismiss: () -> Unit,
    subject: String = "Support Request"
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contact Support", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Need assistance? Reach out to our support team directly via email or phone.")
                
                OutlinedCard(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:$adminEmail")
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback if no email client
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, null, tint = Brand600)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Email Support", fontWeight = FontWeight.Bold)
                            Text(adminEmail, fontSize = 12.sp, color = Slate500)
                        }
                    }
                }

                OutlinedCard(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = android.net.Uri.parse("tel:$adminPhone")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = Brand600)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Call Support", fontWeight = FontWeight.Bold)
                            Text(adminPhone, fontSize = 12.sp, color = Slate500)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Brand600, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordStrengthSection(
    hasMinLength: Boolean,
    hasUppercase: Boolean,
    hasNumber: Boolean,
    hasSpecialChar: Boolean
) {
    val strength = listOf(hasMinLength, hasUppercase, hasNumber, hasSpecialChar).count { it }
    val strengthColor = when (strength) {
        0, 1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF59E0B)
        3 -> Color(0xFF10B981)
        else -> Brand600
    }
    
    val strengthLabel = when (strength) {
        0, 1 -> "Weak"
        2 -> "Fair"
        3 -> "Good"
        else -> "Strong"
    }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Security Strength", fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Medium)
            Text(strengthLabel, fontSize = 12.sp, color = strengthColor, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index < strength) strengthColor else Slate100)
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StrengthRequirement("8+ chars", hasMinLength)
            StrengthRequirement("Uppercase", hasUppercase)
            StrengthRequirement("Number", hasNumber)
            StrengthRequirement("Special", hasSpecialChar)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrengthRequirement(label: String, isMet: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (isMet) Color(0xFF10B981) else Slate300,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = if (isMet) Slate700 else Slate400)
    }
}

@Composable
fun AnalyticCard(label: String, value: String, icon: ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(label, fontSize = 11.sp, color = Slate500, maxLines = 1)
        }
    }
}

@Composable
fun NurseMessageOverlay(
    error: String? = null,
    success: String? = null,
    onDismissError: () -> Unit = {},
    onDismissSuccess: () -> Unit = {}
) {
    val message = error ?: success
    val isError = error != null

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        message?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(if (isError) 7000 else 4000) 
                if (isError) onDismissError() else onDismissSuccess()
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { if (isError) onDismissError() else onDismissSuccess() },
                color = if (isError) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, if (isError) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isError) Color(0xFFFEE2E2) else Color(0xFFBBF7D0)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) Color(0xFFDC2626) else Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isError) "Notice" else "Success!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (isError) Color(0xFF7F1D1D) else Color(0xFF064E3B)
                        )
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = if (isError) Color(0xFF991B1B) else Color(0xFF14532D),
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    IconButton(
                        onClick = { if (isError) onDismissError() else onDismissSuccess() },
                        modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.03f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = if (isError) Color(0xFFB91C1C) else Color(0xFF15803D),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
