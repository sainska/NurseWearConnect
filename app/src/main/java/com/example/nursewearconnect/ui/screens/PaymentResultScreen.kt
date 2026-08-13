package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.data.repository.TransactionType
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.utils.AppUtils
import kotlinx.coroutines.delay

@Composable
fun PaymentResultScreen(
    orderId: String,
    transactionType: TransactionType = TransactionType.ORDER,
    viewModel: HomeViewModel,
    onNavigateToOrders: () -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSuccess = uiState.paymentStatus?.contains("Paid", ignoreCase = true) == true
    val isError = (uiState.error != null && !uiState.checkoutLoading) || uiState.paymentStatus?.contains("Failed", ignoreCase = true) == true
    val isProcessing = uiState.checkoutLoading || 
                      uiState.paymentStatus?.contains("Verifying", ignoreCase = true) == true || 
                      uiState.paymentStatus?.contains("Initializing", ignoreCase = true) == true ||
                      uiState.paymentStatus?.contains("Sent", ignoreCase = true) == true ||
                      uiState.paymentStatus?.contains("Awaiting", ignoreCase = true) == true
    
    val context = androidx.compose.ui.platform.LocalContext.current

    // Trigger receipt generation on success
    LaunchedEffect(isSuccess) {
        if (isSuccess && orderId.isNotEmpty() && transactionType == TransactionType.ORDER) {
            viewModel.generateDigitalReceipt(orderId, context)
        }
    }

    // Animation states
    var showContent by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (showContent) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSuccess) Color(0xFFDCFCE7) else if (isError) Color(0xFFFEE2E2) else Brand50
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Brand600, modifier = Modifier.size(60.dp))
                } else {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = if (isSuccess) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            val title = when {
                isProcessing -> if (transactionType == TransactionType.WALLET_TOPUP) "Processing Top-up..." else "Processing Order..."
                isSuccess -> if (transactionType == TransactionType.WALLET_TOPUP) "Top-up Successful!" else "Payment Successful!"
                else -> if (transactionType == TransactionType.WALLET_TOPUP) "Top-up Failed" else "Payment Failed"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle / Description
            val subtitle = when {
                isProcessing -> "We are verifying your transaction with Paystack. Please don't close the app."
                isSuccess -> if (transactionType == TransactionType.WALLET_TOPUP) "Your wallet balance has been updated successfully." else "Thank you for your purchase! Your uniform is now being prepared."
                else -> (uiState.error ?: "Something went wrong.") + " Please try again."
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Slate500,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Order Details Card (Only for orders)
            if (transactionType == TransactionType.ORDER && orderId.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Order ID", color = Slate500, fontSize = 14.sp)
                            Text("#${orderId.takeLast(8)}", fontWeight = FontWeight.Bold, color = Slate900)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Status", color = Slate500, fontSize = 14.sp)
                            Surface(
                                color = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEF9C3),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isSuccess) "PAID" else "PENDING",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSuccess) Color(0xFF166534) else Color(0xFF854D0E)
                                )
                            }
                        }
                    }
                }
            } else if (transactionType == TransactionType.WALLET_TOPUP) {
                // Wallet specific info could go here
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Brand50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Brand200)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Transaction Type", color = Slate500, fontSize = 14.sp)
                        Text("Wallet Top-up", fontWeight = FontWeight.Bold, color = Brand700, fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Actions
            if (!isProcessing) {
                if (isSuccess) {
                    val primaryActionText = if (transactionType == TransactionType.WALLET_TOPUP) "View Wallet" else "Track My Order"
                    val onPrimaryClick = if (transactionType == TransactionType.WALLET_TOPUP) onNavigateHome else onNavigateToOrders

                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                    ) {
                        Text(primaryActionText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onNavigateHome) {
                        Text("Return to Store", color = Slate500, fontWeight = FontWeight.SemiBold)
                    }
                } else if (isError) {
                    val retryText = if (transactionType == TransactionType.WALLET_TOPUP) "Try Again" else "Try Another Method"
                    val onRetryClick = if (transactionType == TransactionType.WALLET_TOPUP) onNavigateHome else onNavigateToCart

                    Button(
                        onClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                    ) {
                        Text(retryText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onNavigateHome) {
                        Text("Return to Store", color = Slate500, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp)) // Maintain space while loading
            }
        }
    }
}
