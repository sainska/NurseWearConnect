package com.example.nursewearconnect.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.nursewearconnect.model.ProfileUpdateRequest
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.components.NursePullToRefresh
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.ui.viewmodel.UserType
import kotlinx.coroutines.delay
import java.io.InputStream

@Composable
fun ProfileScreen(
    innerPadding: PaddingValues, 
    viewModel: HomeViewModel,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToSystemLogs: () -> Unit = {},
    onNavigateToVendorApprovals: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToLogistics: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val userRepository = viewModel.getUserRepository()
    val userProfile by userRepository.userProfile.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.paystackAuthUrl) {
        uiState.paystackAuthUrl?.let { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.clearPaystackUrl()
        }
    }

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

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profile", "Security")

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                viewModel.uploadAvatar(bytes)
            }
        }
    }

    // Extracting data safely from the profile map
    val fullName = userProfile?.get("full_name") as? String ?: uiState.userName
    val email = userProfile?.get("email") as? String ?: ""
    val phoneNumber = userProfile?.get("phone_number") as? String ?: ""
    val businessName = userProfile?.get("business_name") as? String ?: ""
    val location = userProfile?.get("location") as? String ?: ""
    val address = userProfile?.get("address") as? String ?: ""
    val institution = userProfile?.get("institution") as? String ?: ""
    val bio = userProfile?.get("bio") as? String ?: userProfile?.get("business_description") as? String ?: ""
    val bankCode = userProfile?.get("bank_code") as? String ?: ""
    val bankAccountNumber = userProfile?.get("bank_account_number") as? String ?: ""
    val avatarUrl = userProfile?.get("avatar_url") as? String
    
    // Wallet & Loyalty Data
    val loyaltyPoints = (userProfile?.get("loyalty_points") as? Number)?.toInt() ?: 0
    val loyaltyTier = userProfile?.get("loyalty_tier") as? String ?: "bronze"
    val wallets = userProfile?.get("wallets") as? List<*>
    val wallet = wallets?.firstOrNull() as? Map<*, *>
    val walletBalance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0
    val currency = wallet?.get("currency") as? String ?: "KES"

    // Handle JSONB measurements safely
    val measurements = userProfile?.get("measurements") as? Map<*, *>
    val bust = measurements?.get("bust")?.toString() ?: "0\""
    val waist = measurements?.get("waist")?.toString() ?: "0\""
    val hips = measurements?.get("hips")?.toString() ?: "0\""

    val createdAt = userProfile?.get("created_at") as? String ?: ""

    var showEditDialog by remember { mutableStateOf(false) }
    var showMeasurementsDialog by remember { mutableStateOf(false) }
    var showPhotoPreview by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = uiState.adminEmail,
            adminPhone = uiState.adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = "Account Support - $fullName"
        )
    }

    // Dialogs
    if (showTopUpDialog) {
        TopUpWalletDialog(
            initialPhone = phoneNumber,
            onDismiss = { showTopUpDialog = false },
            onConfirm = { amount, phone, method ->
                viewModel.topUpWallet(amount, phone, method)
                showTopUpDialog = false
            }
        )
    }
    if (showPhotoPreview && !avatarUrl.isNullOrEmpty()) {
        PhotoPreviewDialog(
            avatarUrl = avatarUrl,
            onDismiss = { showPhotoPreview = false },
            onChangePhoto = {
                showPhotoPreview = false
                imagePickerLauncher.launch("image/*")
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out of your account? You will need to sign in again to access your profile.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                ) {
                    Text("Log Out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            userRole = uiState.userRole,
            initialName = fullName,
            initialEmail = email,
            initialPhone = phoneNumber,
            initialAddress = address,
            initialInstitution = institution,
            initialBusinessName = businessName,
            initialLocation = location,
            initialBio = bio,
            initialBankCode = bankCode,
            initialBankAccountNumber = bankAccountNumber,
            onDismiss = { showEditDialog = false },
            onSave = { data ->
                viewModel.updateProfile(data)
                showEditDialog = false
            }
        )
    }

    if (showMeasurementsDialog) {
        EditMeasurementsDialog(
            initialBust = bust,
            initialWaist = waist,
            initialHips = hips,
            onDismiss = { showMeasurementsDialog = false },
            onSave = { data ->
                viewModel.updateProfile(ProfileUpdateRequest(measurements = data))
                showMeasurementsDialog = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NursePullToRefresh(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadHomeData() },
            screenIcon = Icons.Default.Person,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate50)
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                ProfileHeader(
                    isLoading = uiState.isLoading,
                    rotation = rotation,
                    onRefresh = { viewModel.loadHomeData() },
                    onSupportClick = { showSupportDialog = true }
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Brand600,
                    divider = { HorizontalDivider(color = Slate100) },
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Brand600
                            )
                        }
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
                            },
                            selectedContentColor = Brand600,
                            unselectedContentColor = Slate500
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (selectedTab == 0) {
                        // --- PROFILE TAB ---
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            UserSummarySection(
                                userName = fullName,
                                userRole = uiState.userRole,
                                userType = uiState.userType,
                                avatarUrl = avatarUrl,
                                memberSince = createdAt,
                                onAvatarClick = {
                                    if (!avatarUrl.isNullOrEmpty()) {
                                        showPhotoPreview = true
                                    } else {
                                        imagePickerLauncher.launch("image/*")
                                    }
                                },
                                onUserTypeChange = { viewModel.setUserType(it) }
                            )

                            // Role-Specific Overview Section
                            when (uiState.userRole) {
                                "admin" -> {
                                    AdminStatsSummarySection(
                                        totalOrders = uiState.allOrders.size,
                                        pendingVendors = uiState.pendingVendors.size,
                                        totalRevenue = uiState.adminSalesReport.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 }
                                    )
                                    AdminQuickActionsSection(
                                        onApproveVendors = onNavigateToVendorApprovals,
                                        onViewLogs = onNavigateToSystemLogs
                                    )
                                }
                                "vendor" -> {
                                    VendorStatsSummarySection(
                                        revenue = uiState.vendorRevenue,
                                        orderCount = uiState.vendorOrderCount,
                                        stockHealth = uiState.vendorStockHealth,
                                        currency = currency
                                    )
                                    VendorBusinessSection(
                                        name = businessName,
                                        location = location,
                                        bio = bio,
                                        bankCode = bankCode,
                                        bankAccountNumber = bankAccountNumber,
                                        status = uiState.userStatus,
                                        statusNotes = uiState.statusNotes,
                                        onEditClick = { showEditDialog = true }
                                    )
                                }
                                else -> {
                                    WalletLoyaltySection(
                                        balance = walletBalance,
                                        currency = currency,
                                        points = loyaltyPoints,
                                        tier = loyaltyTier,
                                        onTopUpClick = { showTopUpDialog = true }
                                    )
                                    
                                    MeasurementsSection(
                                        bust = bust,
                                        waist = waist,
                                        hips = hips,
                                        recommendedSize = uiState.recommendedSize,
                                        fitNote = uiState.sizeFinderNote,
                                        onEditClick = { showMeasurementsDialog = true }
                                    )

                                    ReferralSection(
                                        referralCode = uiState.referralCode ?: "NWC-${fullName.take(3).uppercase()}${uiState.userId?.takeLast(4) ?: "0000"}",
                                        onShare = { code ->
                                            val sendIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                val apkLink = "https://trpsejzasbfqlshrbbae.supabase.co/storage/v1/object/public/deployments/nursewear-connect-latest.apk"
                                                putExtra(android.content.Intent.EXTRA_TEXT, 
                                                    "Join me on NurseWearConnect! Use my link to get KSh 500 off your first scrub order.\n\n" +
                                                    "1. Download App: $apkLink\n" +
                                                    "2. Register using link: nursewear://referral?code=$code")
                                                type = "text/plain"
                                            }
                                            val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                            context.startActivity(shareIntent)
                                        }
                                    )
                                }
                            }

                            PersonalInfoSection(
                                fullName = fullName,
                                email = email,
                                phoneNumber = phoneNumber,
                                address = address,
                                institution = institution,
                                userRole = uiState.userRole,
                                onEditClick = { showEditDialog = true }
                            )

                            AddressesAndFavoritesSection(
                                userRole = uiState.userRole,
                                reviewsCount = uiState.userReviews.size,
                                addressesCount = uiState.addresses.size,
                                favoritesCount = uiState.favoriteProductIds.size,
                                ordersCount = uiState.allOrders.filter { it["user_id"] == uiState.userId }.size,
                                onNavigateToSubscriptions = onNavigateToSubscriptions,
                                onNavigateToLogistics = onNavigateToLogistics,
                                onNavigateToOrders = onNavigateToOrders
                            )

                            Button(
                                onClick = { showLogoutConfirm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Slate200),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFF43F5E))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Log Out",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    } else {
                        // --- SECURITY TAB ---
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            SecuritySettingsSection(
                                biometricEnabled = uiState.biometricEnabled,
                                onBiometricToggle = { viewModel.setBiometricEnabled(it) },
                                activeSessions = uiState.activeSessions,
                                onRevokeSession = { viewModel.revokeSession(it) }
                            )
                            NotificationsSection(
                                enabled = uiState.notificationsEnabled,
                                onToggle = { viewModel.setNotificationsEnabled(it) }
                            )
                        }
                    }
                }
            }
        }

        // Error Feedback
        uiState.error?.let { err ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss", color = Color.White) } },
                containerColor = Color(0xFFF43F5E)
            ) { Text(err) }
        }

        // Loading Overlay
        if (uiState.isLoading && userProfile == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = Brand50
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
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
                    Text("Loading profile details...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun AdminStatsSummarySection(totalOrders: Int, pendingVendors: Int, totalRevenue: Double) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Brand100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFF6366F1), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Platform Insights", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Slate900, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatSmallCard("Orders", totalOrders.toString(), Icons.Default.Receipt, Color(0xFF6366F1), Color(0xFFEEF2FF), Modifier.weight(1f))
                StatSmallCard("Pending", pendingVendors.toString(), Icons.Default.VerifiedUser, Color(0xFFF59E0B), Color(0xFFFFFBEB), Modifier.weight(1f))
                StatSmallCard("Revenue", "KSh ${String.format("%,.0f", totalRevenue)}", Icons.Default.TrendingUp, Color(0xFF10B981), Color(0xFFECFDF5), Modifier.weight(1.3f))
            }
        }
    }
}

@Composable
fun VendorStatsSummarySection(revenue: Double, orderCount: Int, stockHealth: Map<String, Any>, currency: String) {
    val lowStock = (stockHealth["low_stock_count"] as? Number)?.toInt() ?: 0
    
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Brand100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Brand600, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Business Metrics", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Slate900, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatSmallCard("Revenue", "$currency ${String.format("%,.0f", revenue)}", Icons.Default.Payments, Brand600, Brand50, Modifier.weight(1.3f))
                StatSmallCard("Sales", orderCount.toString(), Icons.Default.ShoppingBag, Color(0xFF10B981), Color(0xFFECFDF5), Modifier.weight(0.9f))
                StatSmallCard("Alerts", lowStock.toString(), Icons.Default.Warning, Color(0xFFF43F5E), Color(0xFFFFF1F2), Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
fun StatSmallCard(label: String, value: String, icon: ImageVector, color: Color, bg: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(label, fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ReferralSection(referralCode: String, onShare: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, null, tint = Color(0xFF166534), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Referral Program", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
            }
            Text(
                "Invite a fellow nurse and you both get KSh 500 when they complete their first order!",
                fontSize = 12.sp,
                color = Color(0xFF166534).copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFDCFCE7))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        referralCode,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900,
                        letterSpacing = 2.sp
                    )
                    TextButton(
                        onClick = { onShare(referralCode) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share Code", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(isLoading: Boolean, rotation: Float = 0f, onRefresh: () -> Unit = {}, onSupportClick: () -> Unit = {}) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Account Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1f))
            
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh, 
                    contentDescription = "Refresh", 
                    tint = if (isLoading) Brand600 else Slate900,
                    modifier = if (isLoading) Modifier.rotate(rotation) else Modifier
                )
            }
            
            IconButton(onClick = onSupportClick) {
                Icon(Icons.Default.HeadsetMic, null, tint = Slate900)
            }
            
            IconButton(onClick = {}) {
                Icon(Icons.Default.Settings, null, tint = Slate900)
            }
        }
    }
}

@Composable
fun UserSummarySection(userName: String, userRole: String, userType: UserType, avatarUrl: String?, memberSince: String, onAvatarClick: () -> Unit, onUserTypeChange: (UserType) -> Unit) {
    val roleColor = when(userRole) {
        "admin" -> Color(0xFF6366F1) // Indigo
        "vendor" -> Brand600
        else -> Color(0xFF10B981) // Emerald
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Slate100),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header with Premium Gradient and Pattern
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(roleColor.copy(alpha = 0.8f), roleColor.copy(alpha = 0.4f))
                        )
                    )
            ) {
                // Subtle Decorative Circles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = 80.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, 0f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = 120.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.8f)
                    )
                }

                if (memberSince.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            "Member since ${AppUtils.timeAgo(memberSince)}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                // Role Icon Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(userRole) {
                            "admin" -> Icons.Default.Shield
                            "vendor" -> Icons.Default.Storefront
                            else -> Icons.Default.HealthAndSafety
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-40).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with premium border
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.clickable { onAvatarClick() }
                ) {
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(4.dp, Color.White),
                        shadowElevation = 12.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!avatarUrl.isNullOrEmpty()) {
                                SubcomposeAsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(Modifier.fillMaxSize().background(Slate50), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = roleColor)
                                        }
                                    },
                                    error = {
                                        Box(Modifier.fillMaxSize().background(roleColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                            Text(if (userRole == "vendor") "🏢" else if (userRole == "admin") "🛡️" else "👩‍⚕️", fontSize = 48.sp)
                                        }
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize().background(roleColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Text(if (userRole == "vendor") "🏢" else if (userRole == "admin") "🛡️" else "👩‍⚕️", fontSize = 48.sp)
                                }
                            }
                        }
                    }
                    
                    // Edit Badge
                    Surface(
                        modifier = Modifier.size(34.dp).offset(x = (-4).dp, y = (-4).dp),
                        shape = CircleShape,
                        color = roleColor,
                        border = BorderStroke(2.dp, Color.White),
                        shadowElevation = 4.dp
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera, 
                            null, 
                            tint = Color.White, 
                            modifier = Modifier.padding(7.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = userName, 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Black, 
                    color = Slate900,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Surface(
                    color = roleColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, roleColor.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(roleColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (userRole) {
                                "admin" -> "MASTER ADMINISTRATOR"
                                "vendor" -> "OFFICIAL VENDOR"
                                "professional" -> "PROFESSIONAL NURSE"
                                else -> "STUDENT NURSE"
                            }, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.ExtraBold,
                            color = roleColor,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            if (userRole == "student" || userRole == "professional") {
                HorizontalDivider(color = Slate50, modifier = Modifier.offset(y = (-20).dp).padding(horizontal = 20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .offset(y = (-20).dp)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("MEMBERSHIP TIER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 0.5.sp)
                        Text(
                            if (userType == UserType.STUDENT) "Student: 20% OFF" else "Pro: 10% OFF",
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = roleColor
                        )
                    }
                    Row(modifier = Modifier.background(Slate100, RoundedCornerShape(14.dp)).padding(4.dp)) {
                        AccountTypeButton("Pro", userType == UserType.PROFESSIONAL, roleColor) { onUserTypeChange(UserType.PROFESSIONAL) }
                        AccountTypeButton("Student", userType == UserType.STUDENT, roleColor) { onUserTypeChange(UserType.STUDENT) }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height((-20).dp))
            }
        }
    }
}

@Composable
fun AccountTypeButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (selected) color else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            label, 
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp, 
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Slate500
        )
    }
}

@Composable
fun PersonalInfoSection(fullName: String, email: String, phoneNumber: String, address: String, institution: String, userRole: String, onEditClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Slate100), shadowElevation = 1.dp) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Personal Information", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
                TextButton(onClick = onEditClick) { Text("Update", color = Brand600, fontWeight = FontWeight.Bold) }
            }
            HorizontalDivider(color = Slate50, modifier = Modifier.padding(horizontal = 16.dp))
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoRow("FULL NAME", fullName)
                InfoRow("EMAIL ADDRESS", email)
                InfoRow("PHONE NUMBER", phoneNumber)
                if (institution.isNotEmpty() || userRole != "vendor") {
                    InfoRow("INSTITUTION / HOSPITAL", institution.ifEmpty { "Not set" })
                }
                if (userRole != "admin") {
                    InfoRow("DEFAULT DELIVERY ADDRESS", address.ifEmpty { "Not set" })
                }
            }
        }
    }
}

@Composable
fun AdminQuickActionsSection(onApproveVendors: () -> Unit, onViewLogs: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Brand100)) {
        Column {
            Text("Admin Controls", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.padding(16.dp))
            HorizontalDivider(color = Slate100)
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminActionCard("Vendor Approvals", "✓", Brand50, Brand600, Modifier.weight(1f), onClick = onApproveVendors)
                AdminActionCard("System Audit Logs", "📋", Slate50, Slate600, Modifier.weight(1f), onClick = onViewLogs)
            }
        }
    }
}

@Composable
fun AdminActionCard(label: String, icon: String, bg: Color, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, bg.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value.ifEmpty { "Not Set" }, fontSize = 14.sp, color = Slate800, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VendorBusinessSection(
    name: String,
    location: String,
    bio: String,
    bankCode: String,
    bankAccountNumber: String,
    status: String,
    statusNotes: String?,
    onEditClick: () -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Brand100)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Business Profile", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = when(status.lowercase()) {
                            "active" -> Color(0xFFDCFCE7)
                            "pending" -> Color(0xFFFEF9C3)
                            else -> Color(0xFFFEE2E2)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            status.uppercase(), 
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when(status.lowercase()) {
                                "active" -> Color(0xFF166534)
                                "pending" -> Color(0xFF854D0E)
                                else -> Color(0xFF991B1B)
                            }
                        )
                    }
                }
                TextButton(onClick = onEditClick) { Text("Edit", color = Brand600) }
            }
            HorizontalDivider(color = Slate100)
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("LEGAL BUSINESS NAME", name)
                InfoRow("OPERATIONAL LOCATION", location)
                if (bio.isNotEmpty()) InfoRow("BUSINESS DESCRIPTION", bio)
                
                HorizontalDivider(color = Slate50, modifier = Modifier.padding(vertical = 4.dp))
                Text("SETTLEMENT ACCOUNT (PAYSTACK)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand600)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoRow("BANK CODE", bankCode)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        InfoRow("ACCOUNT NUMBER", bankAccountNumber)
                    }
                }

                if (!statusNotes.isNullOrEmpty()) {
                    Column(modifier = Modifier.background(Slate50, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text("VERIFICATION FEEDBACK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400)
                        Text(statusNotes, fontSize = 12.sp, color = Slate600)
                    }
                }
            }
        }
    }
}

@Composable
fun MeasurementsSection(bust: String, waist: String, hips: String, recommendedSize: String?, fitNote: String?, onEditClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Brand100)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Straighten, null, tint = Brand500, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tailoring Measurements", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
                TextButton(onClick = onEditClick) { Text("Update", color = Brand600) }
            }
            HorizontalDivider(color = Slate100)
            
            if (recommendedSize != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = Color(0xFFEFF6FF), // Blue 50
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = Color.White,
                            shape = CircleShape,
                            border = BorderStroke(2.dp, Brand600)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(recommendedSize, fontWeight = FontWeight.ExtraBold, color = Brand600)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Your Smart Fit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            Text(fitNote ?: "Based on your current measurements.", fontSize = 11.sp, color = Slate500)
                        }
                    }
                }
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MeasurementCard("Bust", bust, Modifier.weight(1f))
                MeasurementCard("Waist", waist, Modifier.weight(1f))
                MeasurementCard("Hips", hips, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MeasurementCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Slate50, border = BorderStroke(1.dp, Slate100)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
        }
    }
}


@Composable
fun AddressesAndFavoritesSection(
    userRole: String,
    reviewsCount: Int,
    addressesCount: Int,
    favoritesCount: Int,
    ordersCount: Int = 0,
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToLogistics: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {}
) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Slate100), shadowElevation = 1.dp) {
        Column {
            if (userRole != "admin") {
                ProfileLinkItem(
                    icon = Icons.Default.ShoppingBasket,
                    title = if (userRole == "vendor") "Sales History" else "My Orders",
                    subtitle = if (ordersCount > 0) "$ordersCount records found" else "View your transaction history",
                    iconBg = Color(0xFFF0FDF4),
                    iconTint = Color(0xFF166534),
                    onClick = onNavigateToOrders
                )
                HorizontalDivider(color = Slate50, modifier = Modifier.padding(horizontal = 12.dp))
            }
            
            if (userRole != "admin") {
                ProfileLinkItem(Icons.Default.LocationOn, "Delivery Addresses", if (addressesCount > 0) "$addressesCount locations saved" else "Manage shipping destinations", Slate50, Slate600)
                HorizontalDivider(color = Slate50, modifier = Modifier.padding(horizontal = 12.dp))
            }

            if (userRole == "student" || userRole == "professional") {
                ProfileLinkItem(
                    icon = Icons.Default.Loop,
                    title = "Scrub Subscriptions",
                    subtitle = "Recurring orders & delivery schedule",
                    iconBg = Brand50,
                    iconTint = Brand600,
                    onClick = onNavigateToSubscriptions
                )
                HorizontalDivider(color = Slate50, modifier = Modifier.padding(horizontal = 12.dp))
                
                ProfileLinkItem(
                    icon = Icons.Default.LocalShipping,
                    title = "Returns & Fitting Tracker",
                    subtitle = "Monitor returns and home fittings",
                    iconBg = Color(0xFFF1F5F9),
                    iconTint = Slate700,
                    onClick = onNavigateToLogistics
                )
                HorizontalDivider(color = Slate50, modifier = Modifier.padding(horizontal = 12.dp))
            }

            if (userRole != "vendor" && userRole != "admin") {
                ProfileLinkItem(Icons.Default.Favorite, "Wishlist", if (favoritesCount > 0) "$favoritesCount items saved" else "Your favorite uniforms", Color(0xFFFFF1F2), Color(0xFFF43F5E))
                HorizontalDivider(color = Slate50, modifier = Modifier.padding(horizontal = 12.dp))
            }

            ProfileLinkItem(
                icon = Icons.Default.Star, 
                title = "Ratings & Reviews", 
                subtitle = if (userRole == "vendor") "View customer feedback" else "$reviewsCount items reviewed", 
                iconBg = Color(0xFFFFFBEB), 
                iconTint = Color(0xFFF59E0B)
            )
        }
    }
}

@Composable
fun ProfileLinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = iconBg) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.padding(11.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate800)
            Text(subtitle, fontSize = 12.sp, color = Slate500)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Slate300, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SecuritySettingsSection(
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    activeSessions: List<Map<String, Any>>,
    onRevokeSession: (String) -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Slate100)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Privacy & Security", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric Authentication", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Use fingerprint or face ID for login", fontSize = 12.sp, color = Slate500)
                }
                Switch(checked = biometricEnabled, onCheckedChange = onBiometricToggle)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Slate50)

            Text("Device Login History", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
            if (activeSessions.isEmpty()) {
                Text("No other active sessions detected", fontSize = 12.sp, color = Slate400, modifier = Modifier.padding(top = 12.dp))
            } else {
                activeSessions.forEach { session ->
                    val sessionId = session["id"]?.toString() ?: ""
                    val device = session["device_name"]?.toString() ?: "Unknown Device"
                    val location = session["location"]?.toString() ?: "Unknown Location"
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Devices, null, tint = Slate400, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(device, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                Text(location, fontSize = 12.sp, color = Slate500)
                            }
                        }
                        IconButton(onClick = { onRevokeSession(sessionId) }) {
                            Icon(Icons.Default.Logout, "Revoke", tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Slate100)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notification Preferences", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Global Push Notifications", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Stay updated on orders, promos & alerts", fontSize = 12.sp, color = Slate500)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
fun WalletLoyaltySection(balance: Double, currency: String, points: Int, tier: String, onTopUpClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Wallet Card
        Surface(
            modifier = Modifier.weight(1.1f),
            shape = RoundedCornerShape(20.dp),
            color = Brand600,
            shadowElevation = 6.dp
        ) {
            Box {
                // Background decoration
                Icon(
                    Icons.Default.AccountBalanceWallet, 
                    null, 
                    tint = Color.White.copy(alpha = 0.1f), 
                    modifier = Modifier.size(100.dp).align(Alignment.CenterEnd).offset(x = 20.dp)
                )
                
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Wallet", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        }
                        
                        Surface(
                            onClick = onTopUpClick,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ Top Up", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$currency ${String.format("%,.2f", balance)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        // Loyalty Card
        Surface(
            modifier = Modifier.weight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Brand100),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star, 
                        null, 
                        tint = when(tier.lowercase()) {
                            "platinum" -> Color(0xFF6366F1)
                            "gold" -> Color(0xFFF59E0B)
                            "silver" -> Color(0xFF94A3B8)
                            else -> Color(0xFFB45309)
                        }, 
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(tier.uppercase(), fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${String.format("%,d", points)} pts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate900
                )
            }
        }
    }
}

@Composable
fun TopUpWalletDialog(initialPhone: String, onDismiss: () -> Unit, onConfirm: (Double, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(initialPhone) }
    var selectedMethod by remember { mutableStateOf("M-Pesa") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Top Up Wallet", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select payment method and enter amount.", fontSize = 12.sp, color = Slate500)
                
                // Payment Method Selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethodChip(
                        name = "M-Pesa",
                        isSelected = selectedMethod == "M-Pesa",
                        onClick = { selectedMethod = "M-Pesa" },
                        modifier = Modifier.weight(1f)
                    )
                    PaymentMethodChip(
                        name = "Card/Paystack",
                        isSelected = selectedMethod == "Card",
                        onClick = { selectedMethod = "Card" },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) amount = it },
                    label = { Text("Amount (KSh)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )

                if (selectedMethod == "M-Pesa") {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("M-Pesa Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Text("🇰🇪", modifier = Modifier.padding(start = 12.dp)) },
                        placeholder = { Text("0712345678") },
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (amount.isNotEmpty()) {
                        onConfirm(amount.toDouble(), phone, selectedMethod)
                    }
                },
                enabled = amount.isNotEmpty() && (selectedMethod == "Card" || phone.isNotEmpty()),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) {
                Text(if (selectedMethod == "M-Pesa") "Send STK Push" else "Pay with Paystack", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Slate500) }
        }
    )
}

@Composable
fun PaymentMethodChip(name: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Brand50.copy(alpha = 0.5f) else Color.Transparent,
        border = BorderStroke(1.dp, if (isSelected) Brand600 else Slate200),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = name,
                color = if (isSelected) Brand700 else Slate600,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun PhotoPreviewDialog(avatarUrl: String, onDismiss: () -> Unit, onChangePhoto: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Profile Picture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Slate900)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Slate500)
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Slate100)
                ) {
                    SubcomposeAsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Brand600)
                            }
                        }
                    )
                }
                
                Spacer(Modifier.height(28.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onChangePhoto,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Slate200)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(20.dp), tint = Slate700)
                        Spacer(Modifier.width(8.dp))
                        Text("Change", color = Slate700, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    userRole: String,
    initialName: String,
    initialEmail: String,
    initialPhone: String,
    initialAddress: String,
    initialInstitution: String,
    initialBusinessName: String,
    initialLocation: String,
    initialBio: String,
    initialBankCode: String = "",
    initialBankAccountNumber: String = "",
    onDismiss: () -> Unit,
    onSave: (ProfileUpdateRequest) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var institution by remember { mutableStateOf(initialInstitution) }
    var busName by remember { mutableStateOf(initialBusinessName) }
    var loc by remember { mutableStateOf(initialLocation) }
    var bioText by remember { mutableStateOf(initialBio) }
    var bCode by remember { mutableStateOf(initialBankCode) }
    var bAccNum by remember { mutableStateOf(initialBankAccountNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Account Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    "Keep your profile information up to date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedTextField(
                    value = initialEmail,
                    onValueChange = { },
                    label = { Text("Registered Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = false,
                    supportingText = { Text("Email is locked for security.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Slate600,
                        disabledBorderColor = Slate200,
                        disabledLabelColor = Slate400,
                        disabledContainerColor = Slate50
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Primary Delivery Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                
                if (userRole == "student" || userRole == "professional") {
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Medical Institution / School") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }

                if (userRole == "vendor") {
                    OutlinedTextField(
                        value = busName,
                        onValueChange = { busName = it },
                        label = { Text("Business Entity Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = loc,
                        onValueChange = { loc = it },
                        label = { Text("Physical Location / City") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = bioText,
                        onValueChange = { bioText = it },
                        label = { Text("Business Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Bank Settlement (Paystack Required)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Brand700)
                    Text("Automated payouts will be sent to this account.", fontSize = 11.sp, color = Slate500)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = bCode,
                            onValueChange = { if (it.length <= 5) bCode = it },
                            label = { Text("Code") },
                            modifier = Modifier.weight(0.35f),
                            placeholder = { Text("011") },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = bAccNum,
                            onValueChange = { if (it.all { char -> char.isDigit() }) bAccNum = it },
                            label = { Text("Account No.") },
                            modifier = Modifier.weight(0.65f),
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("1234567890") },
                            singleLine = true,
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = ProfileUpdateRequest(
                        fullName = name,
                        phoneNumber = phone,
                        address = address,
                        institution = institution,
                        businessName = if (userRole == "vendor") busName else null,
                        location = if (userRole == "vendor") loc else null,
                        bio = if (userRole == "vendor") bioText else null,
                        businessDescription = if (userRole == "vendor") bioText else null,
                        bankCode = if (userRole == "vendor") bCode else null,
                        bankAccountNumber = if (userRole == "vendor") bAccNum else null
                    )
                    onSave(request)
                },
                enabled = if (userRole == "vendor") name.isNotBlank() && bCode.isNotBlank() && bAccNum.isNotBlank() else name.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) {
                Text("Apply Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Discard", color = Slate500, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun EditMeasurementsDialog(initialBust: String, initialWaist: String, initialHips: String, onDismiss: () -> Unit, onSave: (Map<String, Any>) -> Unit) {
    var bust by remember { mutableStateOf(initialBust) }
    var waist by remember { mutableStateOf(initialWaist) }
    var hips by remember { mutableStateOf(initialHips) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Body Profile", fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Update your dimensions for better fitting recommendations.", fontSize = 12.sp, color = Slate500)
                OutlinedTextField(
                    value = bust, 
                    onValueChange = { bust = it }, 
                    label = { Text("Bust Size") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = waist, 
                    onValueChange = { waist = it }, 
                    label = { Text("Waist Size") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = hips, 
                    onValueChange = { hips = it }, 
                    label = { Text("Hips Size") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(mapOf("bust" to bust, "waist" to waist, "hips" to hips)) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) { 
                Text("Save Measurement", fontWeight = FontWeight.Bold) 
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Cancel", color = Slate500) } 
        }
    )
}
