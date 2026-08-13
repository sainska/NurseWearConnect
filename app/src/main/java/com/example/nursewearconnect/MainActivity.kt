package com.example.nursewearconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.nursewearconnect.ui.components.*
import com.example.nursewearconnect.ui.screens.*
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.utils.AppUtils

import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.ui.viewmodel.LoginViewModel
import com.example.nursewearconnect.ui.viewmodel.MessagingViewModel
import com.example.nursewearconnect.ui.viewmodel.RegistrationViewModel
import com.example.nursewearconnect.ui.viewmodel.ViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

class MainActivity : FragmentActivity() {
    private val _deepLinkIntent = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle initial intent
        _deepLinkIntent.value = intent
        
        setContent {
            NurseWearConnectTheme {
                val adaptiveInfo = currentWindowAdaptiveInfo()
                val useNavRail = adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
                
                val deepLinkIntent by _deepLinkIntent
                
                NurseWearConnectApp(
                    showBiometricPrompt = ::showBiometricPrompt,
                    isColdStart = savedInstanceState == null,
                    useNavRail = useNavRail,
                    deepLinkIntent = deepLinkIntent,
                    onDeepLinkHandled = { _deepLinkIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _deepLinkIntent.value = intent
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NurseWearConnectApp(
    showBiometricPrompt: (() -> Unit) -> Unit,
    isColdStart: Boolean = true,
    useNavRail: Boolean = false,
    deepLinkIntent: Intent? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as? NurseWearApplication ?: return
    val viewModelFactory = remember { ViewModelFactory(app) }
    val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val messagingViewModel: MessagingViewModel = viewModel(factory = viewModelFactory)
    val loginViewModel: LoginViewModel = viewModel(factory = viewModelFactory)
    val registrationViewModel: RegistrationViewModel = viewModel(factory = viewModelFactory)
    
    val homeUiState by homeViewModel.uiState.collectAsState()
    val loginError by loginViewModel.error.collectAsState()
    val regError by registrationViewModel.error.collectAsState()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentScreen by rememberSaveable { 
        mutableStateOf(
            if (app.securityManager.getToken() != null) Screen.MAIN 
            else if (app.securityManager.isOnboardingCompleted()) Screen.LOGIN 
            else Screen.ONBOARDING
        ) 
    }
    var userRole by rememberSaveable { mutableStateOf(app.securityManager.getUserRole()?.lowercase() ?: "") }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingReferralCode by rememberSaveable { mutableStateOf<String?>(null) }

    val isMainHome = currentScreen == Screen.MAIN && currentDestination == AppDestinations.HOME && homeUiState.selectedProduct == null
    val isAtStart = currentScreen == Screen.ONBOARDING || (currentScreen == Screen.LOGIN && app.securityManager.getToken() == null && !app.securityManager.isOnboardingCompleted())

    // FCM Token Update handling
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                val token = intent?.getStringExtra("token")
                if (token != null) {
                    homeViewModel.updateProfile(com.example.nursewearconnect.model.ProfileUpdateRequest(fcmToken = token))
                }
            }
        }
        val filter = android.content.IntentFilter("com.example.nursewearconnect.FCM_TOKEN_UPDATE")
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(deepLinkIntent) {
        deepLinkIntent?.let { intent ->
            val data = intent.data
            if (data != null && data.scheme == "nursewear") {
                when(data.host) {
                    "checkout" -> {
                        val reference = data.getQueryParameter("reference") ?: data.getQueryParameter("trxref")
                        if (reference != null) {
                            homeViewModel.verifyPaystackPayment(reference)
                            onDeepLinkHandled()
                        }
                    }
                    "verify" -> {
                        val token = data.getQueryParameter("token")
                        val type = data.getQueryParameter("type") ?: "signup"
                        val email = data.getQueryParameter("email") ?: ""
                        if (token != null) {
                            homeViewModel.verifyEmailFromDeepLink(email, token, type)
                            if (currentScreen != Screen.LOGIN) {
                                currentScreen = Screen.LOGIN
                            }
                        }
                        onDeepLinkHandled()
                    }
                    "referral" -> {
                        val code = data.getQueryParameter("code")
                        if (code != null) {
                            pendingReferralCode = code
                            currentScreen = Screen.REGISTER
                        }
                        onDeepLinkHandled()
                    }
                }
            }
        }
    }

    BackHandler(enabled = !isMainHome && !isAtStart) {
        when {
            homeUiState.selectedProduct != null -> homeViewModel.setSelectedProduct(null)
            currentScreen == Screen.LOGIN -> currentScreen = Screen.ONBOARDING
            currentScreen == Screen.REGISTER || currentScreen == Screen.RECOVERY -> currentScreen = Screen.LOGIN
            currentScreen == Screen.ORDER_DETAILS -> currentScreen = Screen.MAIN
            currentScreen == Screen.MAIN -> {
                if (currentDestination != AppDestinations.HOME) {
                    currentDestination = AppDestinations.HOME
                }
            }
            else -> currentScreen = Screen.MAIN
        }
    }

    LaunchedEffect(Unit) {
        if (isColdStart && app.securityManager.getToken() != null && userRole == "admin") {
            currentDestination = AppDestinations.HOME
        }
        
        // Background refresh every 5 minutes for critical data
        while(true) {
            kotlinx.coroutines.delay(5 * 60 * 1000L)
            if (app.securityManager.getToken() != null) {
                homeViewModel.loadHomeData(showLoading = false)
            }
        }
    }

    LaunchedEffect(homeUiState.userRole) {
        if (homeUiState.userRole.isNotEmpty()) {
            userRole = homeUiState.userRole.lowercase()
        }
    }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val inactivityTimeout = 5 * 60 * 1000L

    LaunchedEffect(lastInteractionTime, currentScreen) {
        if (currentScreen != Screen.LOGIN && currentScreen != Screen.ONBOARDING && currentScreen != Screen.REGISTER) {
            kotlinx.coroutines.delay(inactivityTimeout)
            if (System.currentTimeMillis() - lastInteractionTime >= inactivityTimeout) {
                homeViewModel.logout()
                currentScreen = Screen.LOGIN
            }
        }
    }

    LaunchedEffect(homeUiState.paymentStatus, homeUiState.error) {
        val status = homeUiState.paymentStatus
        if (status != null && (status.contains("Paid") || status.contains("Failed"))) {
            if (currentScreen != Screen.PAYMENT_RESULT) {
                currentScreen = Screen.PAYMENT_RESULT
            }
        }
        if (homeUiState.error != null && homeUiState.checkoutLoading) {
            if (currentScreen != Screen.PAYMENT_RESULT) {
                currentScreen = Screen.PAYMENT_RESULT
            }
        }
    }

    if (showSplash) {
        SplashScreen(onAnimationFinished = { showSplash = false })
    } else {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isAuthFlow = targetState in listOf(Screen.LOGIN, Screen.REGISTER, Screen.RECOVERY) && 
                               initialState in listOf(Screen.LOGIN, Screen.REGISTER, Screen.RECOVERY)
                
                val isToMain = targetState == Screen.MAIN
                val isFromMain = initialState == Screen.MAIN

                when {
                    isToMain || isFromMain -> {
                        // Vertical "pop" effect for main app transitions
                        (fadeIn(animationSpec = tween(300, easing = LinearOutSlowInEasing)) + 
                         scaleIn(initialScale = 0.95f, animationSpec = tween(300)))
                            .togetherWith(fadeOut(animationSpec = tween(250)))
                    }
                    isAuthFlow -> {
                        // Faster horizontal slides for auth sub-screens
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn())
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it / 2 } + fadeOut())
                        } else {
                            (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn())
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { it / 2 } + fadeOut())
                        }
                    }
                    else -> {
                        // Standard smooth slide for everything else
                        (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn())
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { -it } + fadeOut())
                    }
                }
            },
            label = "premium_screen_transition"
        ) { targetScreen ->
            when (targetScreen) {
                Screen.ONBOARDING -> {
                    OnboardingScreen(
                        onSkip = { 
                            app.securityManager.setOnboardingCompleted(true)
                            currentScreen = Screen.LOGIN 
                        },
                        onFinish = { 
                            app.securityManager.setOnboardingCompleted(true)
                            currentScreen = Screen.LOGIN 
                        }
                    )
                }
                Screen.LOGIN -> {
                    val loginSuccess by loginViewModel.loginSuccess.collectAsState()
                    val loginLoading by loginViewModel.isLoading.collectAsState()

                    LaunchedEffect(loginSuccess) {
                        loginSuccess?.let { rawRole ->
                            val role = rawRole.lowercase()
                            userRole = role
                            homeViewModel.clearError() // Prevent jumping to PAYMENT_RESULT if previous error exists
                            app.userRepository.initFromCache()
                            
                            // Level-based redirection
                            currentDestination = when (role) {
                                "admin" -> AppDestinations.HOME
                                "vendor" -> AppDestinations.HOME // Dashboard or Catalog? Home has Dashboard
                                else -> AppDestinations.HOME
                            }
                            
                            currentScreen = Screen.MAIN
                            if (role == "admin") {
                                homeViewModel.loadAdminData()
                            } else if (role == "vendor") {
                                homeViewModel.loadVendorData(app.authRepository.getUserId() ?: "")
                            } else {
                                homeViewModel.loadHomeData()
                            }
                            loginViewModel.resetLoginState()
                        }
                    }

                    LoginScreen(
                        onBack = { currentScreen = Screen.ONBOARDING },
                        onLoginSuccess = { email, password -> loginViewModel.login(email, password) },
                        onNavigateToRegister = { currentScreen = Screen.REGISTER },
                        onNavigateToRecovery = { currentScreen = Screen.RECOVERY },
                        onBiometricLogin = {
                            showBiometricPrompt {
                                val role = app.securityManager.getUserRole()?.lowercase() ?: ""
                                userRole = role
                                app.userRepository.initFromCache()
                                currentDestination = AppDestinations.HOME
                                currentScreen = Screen.MAIN
                                if (role == "admin") homeViewModel.loadAdminData()
                            }
                        },
                        onGoogleLogin = { loginViewModel.loginWithGoogle() },
                        onAppleLogin = { loginViewModel.loginWithApple() },
                        isLoading = loginLoading,
                        externalError = loginError
                    )
                }
                Screen.REGISTER -> {
                    val registrationSuccess by registrationViewModel.registrationSuccess.collectAsState()
                    val registrationLoading by registrationViewModel.isLoading.collectAsState()
                    val verificationSent by registrationViewModel.verificationSent.collectAsState()
                    val lastEmail by registrationViewModel.lastEmail.collectAsState()

                    LaunchedEffect(verificationSent) {
                        if (verificationSent) {
                            homeViewModel.resendVerificationEmail(lastEmail)
                        }
                    }

                    LaunchedEffect(registrationSuccess) {
                        registrationSuccess?.let { role ->
                            if (role.lowercase() == "vendor") {
                                // Vendors always wait for admin approval, handled by VendorPendingScreen
                            } else {
                                if (app.authRepository.getUserId() == null) {
                                    // Not logged in -> likely needs email verification
                                    homeViewModel.updateProfile(com.example.nursewearconnect.model.ProfileUpdateRequest()) // Dummy update to trigger success message if needed
                                    // Better: just set the success message directly if we could.
                                    // For now, let's assume LoginViewModel or HomeViewModel can handle it.
                                    currentScreen = Screen.LOGIN
                                    // We'll rely on the Login screen or a global overlay to show "Verification email sent"
                                } else {
                                    userRole = role.lowercase()
                                    currentDestination = AppDestinations.HOME
                                    currentScreen = Screen.MAIN
                                }
                                registrationViewModel.resetRegistrationState()
                            }
                        }
                    }

                    RegisterScreen(
                        onBack = { currentScreen = Screen.LOGIN },
                        onRegisterSuccess = { role, fullName, email, phone, password, businessName, location, description, licenseUrl ->
                            registrationViewModel.register(email, password, fullName, phone, role, businessName, location, description, licenseUrl, pendingReferralCode)
                            // Clear referral code after attempt
                            pendingReferralCode = null
                        },
                        onNavigateToLogin = { currentScreen = Screen.LOGIN },
                        onResendVerification = { email -> homeViewModel.resendVerificationEmail(email) },
                        isLoading = registrationLoading,
                        externalError = regError,
                        isExternalSuccess = registrationSuccess != null,
                        onUploadDocument = { bytes, extension, onComplete ->
                            registrationViewModel.uploadLicense(bytes, extension, onComplete)
                        }
                    )
                }
                Screen.RECOVERY -> {
                    PasswordRecoveryScreen(
                        onBack = { currentScreen = Screen.LOGIN },
                        onSuccess = { currentScreen = Screen.LOGIN }
                    )
                }
                Screen.MAIN -> {
                    val snackhostState = remember { SnackbarHostState() }
                    val isLoggedIn by app.authRepository.isLoggedIn.collectAsState()
                    val activeNotification by messagingViewModel.newInAppNotification.collectAsState()

                    LaunchedEffect(activeNotification) {
                        activeNotification?.let {
                            // Automatically show system notification in the bar
                            AppUtils.showSystemNotification(context, it.title, it.body)
                        }
                    }

                    LaunchedEffect(isLoggedIn) {
                        if (!isLoggedIn) currentScreen = Screen.LOGIN
                    }

                    if (homeUiState.userRole.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Brand600)
                        }
                    } else {
                        var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }
                        
                        val nestedScrollConnection = remember {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                    val delta = available.y
                                    if (delta < -10) { isBottomBarVisible = false } 
                                    else if (delta > 10) { isBottomBarVisible = true }
                                    return androidx.compose.ui.geometry.Offset.Zero
                                }
                            }
                        }

                        val destinations = remember(homeUiState.userRole) {
                            AppDestinations.entries.filter { destination ->
                                when (destination) {
                                    AppDestinations.CART -> homeUiState.userRole == "student" || homeUiState.userRole == "professional"
                                    else -> true
                                }
                            }
                        }
                        
                        val pagerState = rememberPagerState(
                            initialPage = destinations.indexOf(currentDestination).coerceAtLeast(0),
                            pageCount = { destinations.size }
                        )

                        val isVendorPending = (homeUiState.userRole == "vendor") && homeUiState.userStatus != "active"
                        val isUserBanned = homeUiState.userStatus == "banned"
                        
                        if (isUserBanned) {
                            UserBannedScreen(
                                onLogout = { homeViewModel.logout(); currentScreen = Screen.LOGIN },
                                adminEmail = homeUiState.adminEmail,
                                adminPhone = homeUiState.adminPhone,
                                reason = homeUiState.statusNotes
                            )
                        } else if (isVendorPending) {
                            VendorPendingScreen(
                                status = homeUiState.userStatus,
                                statusNotes = homeUiState.statusNotes,
                                onLogout = { homeViewModel.logout(); currentScreen = Screen.LOGIN },
                                viewModel = homeViewModel
                            )
                        } else {
                            LaunchedEffect(currentDestination, destinations) {
                                val targetPage = destinations.indexOf(currentDestination)
                                if (targetPage != -1 && targetPage != pagerState.currentPage) {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }

                            LaunchedEffect(pagerState.currentPage) {
                                if (pagerState.currentPage < destinations.size) {
                                    currentDestination = destinations[pagerState.currentPage]
                                }
                            }

                            Row(modifier = Modifier.fillMaxSize()) {
                                if (useNavRail) {
                                    NurseNavigationRail(
                                        userRole = homeUiState.userRole,
                                        currentDestination = currentDestination,
                                        onDestinationSelected = { currentDestination = it },
                                        cartCount = homeUiState.cartCount
                                    )
                                }
                                
                                Scaffold(
                                    modifier = Modifier
                                        .weight(1f)
                                        .nestedScroll(nestedScrollConnection)
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent()
                                                    lastInteractionTime = System.currentTimeMillis()
                                                }
                                            }
                                        },
                                    snackbarHost = { SnackbarHost(snackhostState) },
                                    bottomBar = {
                                        AnimatedVisibility(
                                            visible = isBottomBarVisible && !useNavRail,
                                            enter = slideInVertically(initialOffsetY = { it }),
                                            exit = slideOutVertically(targetOffsetY = { it })
                                        ) {
                                            NurseBottomNavigation(
                                                userRole = homeUiState.userRole,
                                                currentDestination = currentDestination,
                                                onDestinationSelected = { currentDestination = it },
                                                cartCount = homeUiState.cartCount
                                            )
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.background
                                ) { innerPadding ->
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondViewportPageCount = 1,
                                        userScrollEnabled = !useNavRail
                                    ) { page ->
                                        val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                                        when (destination) {
                                            AppDestinations.HOME -> HomeScreen(
                                                innerPadding = innerPadding,
                                                onNavigateToNotifications = { currentScreen = Screen.NOTIFICATIONS },
                                                onNavigateToMessages = { currentScreen = Screen.MESSAGES },
                                                onNavigateToProfile = { currentDestination = AppDestinations.PROFILE },
                                                onNavigateToUserLogs = { currentScreen = Screen.USER_LOGS },
                                                onNavigateToAdminUsers = { currentScreen = Screen.ADMIN_USERS },
                                                onNavigateToAdminVendors = { currentScreen = Screen.ADMIN_VENDORS },
                                                onNavigateToAdminInventory = { currentScreen = Screen.ADMIN_INVENTORY },
                                                onNavigateToAdminOrders = { currentScreen = Screen.ADMIN_ORDERS },
                                                onNavigateToAdminMarketing = { currentScreen = Screen.ADMIN_MARKETING },
                                                onNavigateToVendorMarketing = { currentScreen = Screen.VENDOR_MARKETING },
                                                onNavigateToReports = { currentScreen = Screen.ADMIN_REPORTS },
                                                onNavigateToVendorInventory = { currentScreen = Screen.VENDOR_INVENTORY },
                                                onNavigateToVendorOrders = { currentScreen = Screen.VENDOR_ORDERS },
                                                onNavigateToVendorAnalytics = { currentScreen = Screen.VENDOR_ANALYTICS },
                                                onNavigateToVendorCatalog = { currentScreen = Screen.VENDOR_CATALOG },
                                                viewModel = homeViewModel,
                                                messagingViewModel = messagingViewModel
                                            )
                                            AppDestinations.CATALOG -> {
                                                if (homeUiState.userRole == "admin") {
                                                    ReportsScreen({ currentDestination = AppDestinations.HOME }, homeViewModel, { currentScreen = Screen.ADMIN_INVENTORY })
                                                } else if (homeUiState.userRole == "vendor") {
                                                    VendorInventoryScreen({ currentDestination = AppDestinations.HOME }, homeViewModel)
                                                } else {
                                                    CatalogScreen(innerPadding, homeViewModel, { currentDestination = AppDestinations.HOME })
                                                }
                                            }
                                            AppDestinations.CART -> CartScreen(innerPadding, homeViewModel, { currentDestination = AppDestinations.CATALOG })
                                            AppDestinations.ORDERS -> {
                                                if (homeUiState.userRole == "admin") AdminOrderManagementScreen(
                                                    onBackClick = { currentDestination = AppDestinations.HOME },
                                                    onNavigateToMessages = { userId ->
                                                        messagingViewModel.openConversation(userId)
                                                        currentScreen = Screen.MESSAGES
                                                    },
                                                    viewModel = homeViewModel
                                                )
                                                else if (homeUiState.userRole == "vendor") VendorOrdersScreen(
                                                    onBackClick = { currentDestination = AppDestinations.HOME },
                                                    onNavigateToMessages = { userId ->
                                                        messagingViewModel.openConversation(userId)
                                                        currentScreen = Screen.MESSAGES
                                                    },
                                                    viewModel = homeViewModel
                                                )
                                                else OrdersScreen(innerPadding, homeViewModel, { currentScreen = Screen.NOTIFICATIONS }, { }, { id ->
                                                    selectedOrderId = id
                                                    currentScreen = Screen.ORDER_DETAILS
                                                })
                                            }
                                            AppDestinations.PROFILE -> ProfileScreen(
                                                innerPadding = innerPadding, 
                                                viewModel = homeViewModel, 
                                                onNavigateToCatalog = { currentDestination = AppDestinations.CATALOG }, 
                                                onNavigateToSystemLogs = { currentScreen = Screen.USER_LOGS }, 
                                                onNavigateToVendorApprovals = { currentScreen = Screen.ADMIN_VENDORS }, 
                                                onNavigateToSubscriptions = { currentScreen = Screen.SUBSCRIPTIONS }, 
                                                onNavigateToLogistics = { currentScreen = Screen.LOGISTICS },
                                                onNavigateToOrders = { currentDestination = AppDestinations.ORDERS }
                                            )
                                        }
                                    }
                                }
                            }
                                
                            NotificationOverlay(
                                notification = activeNotification, 
                                onDismiss = { messagingViewModel.dismissNotification() }
                            )

                            if (homeUiState.showBiometricPrompt) {
                                AlertDialog(
                                    onDismissRequest = { homeViewModel.dismissBiometricPrompt() },
                                    title = { Text("Enable Biometric Login?") },
                                    text = { Text("Would you like to use fingerprint or face ID for faster login next time?") },
                                    confirmButton = {
                                        TextButton(onClick = { 
                                            homeViewModel.setBiometricEnabled(true)
                                            homeViewModel.dismissBiometricPrompt()
                                        }) { Text("Enable") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { homeViewModel.dismissBiometricPrompt() }) { Text("Later") }
                                    }
                                )
                            }

                            homeUiState.selectedProduct?.let { product ->
                                ModalBottomSheet(onDismissRequest = { homeViewModel.setSelectedProduct(null) }) {
                                    ProductDetailContent(
                                        product = product,
                                        isFavorite = homeUiState.favoriteProductIds.contains(product.id),
                                        onFavoriteToggle = { homeViewModel.toggleFavorite(product.id) },
                                        selectedSize = homeUiState.selectedSize,
                                        onSizeSelected = { homeViewModel.setSelectedSize(it) },
                                        selectedColor = homeUiState.selectedColor,
                                        onColorSelected = { homeViewModel.setSelectedColor(it) },
                                        onAddToCart = { if (homeViewModel.addToCart(product, 1, homeUiState.selectedSize)) homeViewModel.setSelectedProduct(null) },
                                        error = homeUiState.error,
                                        reviews = homeUiState.productReviews,
                                        isReviewsLoading = homeUiState.isReviewsLoading,
                                        isEligibleToReview = homeUiState.isEligibleToReview,
                                        onSubmitReview = { r, c -> homeViewModel.submitReview(product.id, r, c) },
                                        isAdmin = homeUiState.userRole == "admin",
                                        onEditProduct = { homeViewModel.setSelectedProduct(null); currentScreen = Screen.ADMIN_INVENTORY },
                                        onChatWithVendor = { vendorId ->
                                            messagingViewModel.openConversation(vendorId)
                                            homeViewModel.setSelectedProduct(null)
                                            currentScreen = Screen.MESSAGES
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Screen.NOTIFICATIONS -> NotificationScreen({ currentScreen = Screen.MAIN }, messagingViewModel)
                Screen.MESSAGES -> {
                    if (userRole == "admin") {
                        AdminSmartMessagingScreen(
                            viewModel = messagingViewModel,
                            homeViewModel = homeViewModel,
                            onBack = { currentScreen = Screen.MAIN }
                        )
                    } else {
                        MessagesScreen(
                            onBackClick = { currentScreen = Screen.MAIN },
                            homeViewModel = homeViewModel,
                            viewModel = messagingViewModel
                        )
                    }
                }
                Screen.USER_LOGS -> UserLogsScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ADMIN_USERS -> AdminUserManagementScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ADMIN_VENDORS -> AdminVendorApprovalsScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ADMIN_INVENTORY -> AdminInventoryScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ADMIN_ORDERS -> AdminOrderManagementScreen(
                    onBackClick = { currentScreen = Screen.MAIN },
                    onNavigateToMessages = { userId ->
                        messagingViewModel.openConversation(userId)
                        currentScreen = Screen.MESSAGES
                    },
                    viewModel = homeViewModel
                )
                Screen.VENDOR_INVENTORY -> VendorInventoryScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.VENDOR_ANALYTICS -> VendorAnalyticsScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ADMIN_MARKETING -> AdminMarketingScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.VENDOR_MARKETING -> VendorMarketingScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.VENDOR_CATALOG -> VendorCatalogScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ORDER_DETAILS -> OrderDetailsScreen(selectedOrderId ?: "", homeViewModel) { currentScreen = Screen.MAIN }
                Screen.SUBSCRIPTIONS -> SubscriptionManagementScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.LOGISTICS -> LogisticsTrackingScreen({ currentScreen = Screen.MAIN }, homeViewModel)
                Screen.ADMIN_REPORTS -> ReportsScreen({ currentScreen = Screen.MAIN }, homeViewModel, { currentScreen = Screen.ADMIN_INVENTORY })
                Screen.VENDOR_ORDERS -> VendorOrdersScreen(
                    onBackClick = { currentScreen = Screen.MAIN },
                    onNavigateToMessages = { userId ->
                        messagingViewModel.openConversation(userId)
                        currentScreen = Screen.MESSAGES
                    },
                    viewModel = homeViewModel
                )
                Screen.PAYMENT_RESULT -> PaymentResultScreen(
                    orderId = homeUiState.orderId ?: "",
                    transactionType = homeUiState.transactionType,
                    viewModel = homeViewModel,
                    onNavigateToOrders = {
                        currentDestination = AppDestinations.ORDERS
                        currentScreen = Screen.MAIN
                        homeViewModel.resetCheckoutState()
                    },
                    onNavigateToCart = {
                        currentDestination = AppDestinations.CART
                        currentScreen = Screen.MAIN
                        homeViewModel.resetCheckoutState()
                    },
                    onNavigateHome = {
                        currentDestination = AppDestinations.HOME
                        currentScreen = Screen.MAIN
                        homeViewModel.resetCheckoutState()
                    }
                )
            }
        }

        // Global Overlay System for Errors and Success Messages
        NurseMessageOverlay(
            error = homeUiState.error ?: loginError ?: regError,
            success = homeUiState.successMessage,
            onDismissError = {
                homeViewModel.clearError()
                loginViewModel.resetLoginState()
                registrationViewModel.resetRegistrationState()
            },
            onDismissSuccess = {
                homeViewModel.clearError()
            }
        )
    }
}

enum class Screen {
    ONBOARDING, LOGIN, REGISTER, RECOVERY, MAIN, NOTIFICATIONS, MESSAGES, USER_LOGS, ADMIN_USERS, ADMIN_VENDORS, ADMIN_INVENTORY, ADMIN_ORDERS, VENDOR_INVENTORY, VENDOR_ORDERS, ADMIN_MARKETING, VENDOR_MARKETING, ADMIN_REPORTS, VENDOR_ANALYTICS, VENDOR_CATALOG, ORDER_DETAILS, SUBSCRIPTIONS, LOGISTICS, PAYMENT_RESULT
}

@Composable
fun NurseNavigationRail(
    userRole: String,
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
    cartCount: Int
) {
    NavigationRail(
        containerColor = Color.White,
        header = {
            IconButton(onClick = { /* Handle brand click */ }) {
                Icon(Icons.Default.MedicalServices, contentDescription = "NurseWear", tint = Brand600, modifier = Modifier.size(32.dp))
            }
        },
        modifier = Modifier.width(80.dp)
    ) {
        AppDestinations.entries.forEach { destination ->
            val isSpecialRole = userRole == "student" || userRole == "professional"
            
            NavigationRailItem(
                selected = destination == currentDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (destination == AppDestinations.CART && cartCount > 0) {
                                Badge(containerColor = Color(0xFFF43F5E), contentColor = Color.White) {
                                    Text(cartCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (destination == currentDestination) destination.filledIcon else destination.outlinedIcon,
                            contentDescription = destination.label
                        )
                    }
                },
                label = { Text(destination.label, fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Brand600,
                    selectedTextColor = Brand600,
                    unselectedIconColor = Slate400,
                    unselectedTextColor = Slate400,
                    indicatorColor = Brand100
                )
            )
        }
    }
}

@Composable
fun NurseBottomNavigation(
    userRole: String,
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
    cartCount: Int
) {
    // Container Box that allows components to protrude without being cut
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The actual Navigation Bar Surface
        Surface(
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp),
            shadowElevation = 16.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppDestinations.entries.forEach { destination ->
                    val isSpecialRole = userRole == "student" || userRole == "professional"
                    if (destination == AppDestinations.CART) {
                        if (isSpecialRole) {
                            // Empty space reserved for the protruding FAB
                            Box(modifier = Modifier.size(64.dp))
                        }
                    } else {
                        NavItem(
                            label = when {
                                destination == AppDestinations.CATALOG && userRole == "admin" -> "Reports"
                                destination == AppDestinations.CATALOG && userRole == "vendor" -> "Inventory"
                                else -> destination.label
                            },
                            filledIcon = when {
                                destination == AppDestinations.CATALOG && userRole == "admin" -> Icons.Filled.Assessment
                                destination == AppDestinations.CATALOG && userRole == "vendor" -> Icons.Filled.Inventory
                                else -> destination.filledIcon
                            },
                            outlinedIcon = when {
                                destination == AppDestinations.CATALOG && userRole == "admin" -> Icons.Outlined.Assessment
                                destination == AppDestinations.CATALOG && userRole == "vendor" -> Icons.Outlined.Inventory
                                else -> destination.outlinedIcon
                            },
                            selected = destination == currentDestination,
                            onClick = { onDestinationSelected(destination) }
                        )
                    }
                }
            }
        }

        // The Protruding Cart FAB
        if (userRole == "student" || userRole == "professional") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Offset sits halfway off the 85dp bar
                    .offset(y = (-32).dp) 
            ) {
                CartFab(cartCount) { onDestinationSelected(AppDestinations.CART) }
            }
        }
    }
}

@Composable
fun NavItem(label: String, filledIcon: ImageVector, outlinedIcon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Brand600 else Slate400, label = "nav_tint")
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "nav_scale")

    Column(
        modifier = Modifier.width(64.dp).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(if (selected) filledIcon else outlinedIcon, label, tint = tint, modifier = Modifier.size(26.dp).graphicsLayer(scaleX = scale, scaleY = scale))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center)
        AnimatedVisibility(visible = selected, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(modifier = Modifier.padding(top = 4.dp).size(4.dp).background(Brand600, CircleShape))
        }
    }
}

@Composable
fun CartFab(count: Int, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "cart_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (count > 0) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                if (count > 0) {
                    scaleX = scale
                    scaleY = scale
                }
            },
        shape = CircleShape,
        color = Brand600,
        shadowElevation = 12.dp,
        border = BorderStroke(4.dp, Color.White)
    ) {
        Box(contentAlignment = Alignment.Center) {
            BadgedBox(
                badge = {
                    if (count > 0) {
                        Badge(
                            containerColor = Color(0xFFF43F5E), // Rose 500
                            contentColor = Color.White,
                            modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                        ) {
                            Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.LocalMall,
                    contentDescription = "Cart",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

enum class AppDestinations(val label: String, val filledIcon: ImageVector, val outlinedIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CATALOG("Catalog", Icons.Filled.Layers, Icons.Outlined.Layers),
    CART("Cart", Icons.Filled.LocalMall, Icons.Outlined.LocalMall),
    ORDERS("Orders", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person),
}
