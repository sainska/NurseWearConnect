package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nursewearconnect.utils.CheckoutLauncher
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.components.EmptyState
import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import com.example.nursewearconnect.ui.components.FittingServiceSection
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.model.CartItem
import com.example.nursewearconnect.model.*
import com.example.nursewearconnect.ui.viewmodel.PriceBreakdown
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.ui.viewmodel.UserType

@Composable
fun CartScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onNavigateToCatalog: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val cartItems = uiState.cartItems
    var checkoutStep by remember { mutableIntStateOf(1) } // 1: Review, 2: Address, 3: Payment, 4: Success

    // Address State
    var address by remember { mutableStateOf("Argwings Kodhek Rd, Nairobi\nWard 4B, Staff Quarters") }
    var locationName by remember { mutableStateOf("Nairobi Hospital") }
    var phoneNumber by remember { mutableStateOf("+254 712 345 678") }
    var locationType by remember { mutableStateOf("WORK") }
    var addressId by remember { mutableStateOf<String?>(null) }
    var showAddressDialog by remember { mutableStateOf(false) }

    // Shipping State
    var selectedShippingMethod by remember { mutableStateOf("Standard") }
    
    // Payment Method Selection
    var selectedPaymentMethod by remember { mutableStateOf("Paystack") }

    // Currency Selector State
    var showCurrencyMenu by remember { mutableStateOf(false) }

    // Calculation logic for total including discounts
    val priceBreakdown = viewModel.getPriceBreakdown()
    val finalTotal = priceBreakdown.finalTotal

    // Coupon logic
    var promoCode by remember { mutableStateOf("") }
    val appliedCoupon = uiState.appliedCoupon

    // Address logic - check database for user addresses
    LaunchedEffect(uiState.addresses) {
        if (uiState.addresses.isNotEmpty()) {
            val primary = uiState.addresses.first()
            locationName = primary["name"]?.toString() ?: locationName
            address = primary["address"]?.toString() ?: address
            phoneNumber = primary["phone"]?.toString() ?: phoneNumber
            locationType = primary["type"]?.toString() ?: locationType
            addressId = primary["id"]?.toString()
        }
    }

    // Paystack Custom Tab Redirection
    LaunchedEffect(uiState.paystackAuthUrl) {
        uiState.paystackAuthUrl?.let { url ->
            if (url.isNotEmpty()) {
                android.util.Log.d("CartScreen", "Triggering Paystack Checkout for URL: $url")
                com.example.nursewearconnect.utils.CheckoutLauncher.openPaystackCheckout(context, url)
                viewModel.clearPaystackUrl()
            }
        }
    }

    // Effect to auto-scroll or react to payment completion
    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus?.startsWith("Paid:") == true && checkoutStep == 4) {
            // Extra logic for successful payment can be added here
            android.util.Log.d("CartScreen", "Payment successful: ${uiState.paymentStatus}")
        }
    }

    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("Edit Delivery Address", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("Location Name (e.g. Home, Work)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Full Address") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = locationType == "WORK",
                            onClick = { locationType = "WORK" },
                            label = { Text("Work") }
                        )
                        FilterChip(
                            selected = locationType == "HOME",
                            onClick = { locationType = "HOME" },
                            label = { Text("Home") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAddressDialog = false }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(bottom = innerPadding.calculateBottomPadding())
    ) {
        if (cartItems.isEmpty() && uiState.orderId == null) {
            EmptyCartState(onNavigateToCatalog)
        } else {
            val displayItems = cartItems.ifEmpty { uiState.lastPurchasedItems }
            if (uiState.isLoading && cartItems.isEmpty() && uiState.orderId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Brand50
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
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
                        Text("Preparing checkout...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    CheckoutHeader(
                        onBackClick = {
                            if (checkoutStep > 1) checkoutStep--
                            else {
                                viewModel.resetCheckoutState()
                                onNavigateToCatalog()
                            }
                        },
                        step = checkoutStep,
                        currency = uiState.selectedCurrency,
                        onCurrencyClick = { showCurrencyMenu = true }
                    )

                    // Currency Dropdown Menu
                    Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopEnd).padding(end = 24.dp)) {
                        DropdownMenu(
                            expanded = showCurrencyMenu,
                            onDismissRequest = { showCurrencyMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            AppUtils.Currency.entries.forEach { currency ->
                                DropdownMenuItem(
                                    text = { 
                                        Row {
                                            Text(currency.code, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Text(currency.name, color = Slate500)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setCurrency(currency)
                                        showCurrencyMenu = false
                                    },
                                    trailingIcon = {
                                        if (uiState.selectedCurrency == currency) {
                                            Icon(Icons.Default.Check, null, tint = Brand600)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        item { CheckoutStepper(currentStep = checkoutStep) }

                        when (checkoutStep) {
                            1 -> {
                                item {
                                    Text(
                                        text = "Order Review",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                    )
                                }

                                if (uiState.isCartLoading && displayItems.isEmpty()) {
                                    items(3) {
                                        ShimmerPlaceholder(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    }
                                } else {
                                    items(displayItems) { item ->
                                        CheckoutItemCard(
                                            cartItem = item,
                                            onIncrease = { viewModel.updateCartItemQuantity(item, item.quantity + 1) },
                                            onDecrease = { viewModel.updateCartItemQuantity(item, item.quantity - 1) },
                                            onRemove = { viewModel.removeFromCart(item) },
                                            onToggleEmbroidery = { name -> viewModel.toggleEmbroidery(item, name) },
                                            currency = uiState.selectedCurrency,
                                            exchangeRates = uiState.exchangeRates
                                        )
                                    }
                                }

                                item { 
                                    ReorderHistorySection(
                                        reorderItems = uiState.reorderItems,
                                        onAddToCart = { viewModel.quickReorder(it) }
                                    ) 
                                }
                            }
                            2 -> {
                                item { 
                                    DeliverySection(
                                        locationName = locationName,
                                        address = address,
                                        phone = phoneNumber,
                                        locationType = locationType,
                                        onEditClick = { showAddressDialog = true }
                                    ) 
                                }
                                item {
                                    FittingServiceSection(
                                        isRequested = uiState.isFittingRequested,
                                        onRequestedChange = { viewModel.setFittingRequested(it) },
                                        fittingDate = uiState.fittingDate,
                                        fittingSlot = uiState.fittingSlot,
                                        onAppointmentSelected = { date, slot -> viewModel.setFittingAppointment(date, slot) }
                                    )
                                }
                                item { 
                                    ShippingMethodSection(
                                        selectedMethod = uiState.shippingMethod,
                                        onMethodSelected = { viewModel.setShippingMethod(it) },
                                        currency = uiState.selectedCurrency,
                                        exchangeRates = uiState.exchangeRates
                                    ) 
                                }
                            }
                            3 -> {
                                item {
                                    PromoCodeSection(
                                        promoCode = promoCode,
                                        onCodeChange = { promoCode = it },
                                        onApply = { viewModel.applyCoupon(promoCode) },
                                        appliedCoupon = appliedCoupon,
                                        onRemove = { viewModel.removeCoupon() }
                                    ) 
                                }
                                item { 
                                    PaymentMethodSection(
                                        phoneNumber = phoneNumber,
                                        onPhoneChange = { phoneNumber = it },
                                        walletBalance = uiState.walletBalance,
                                        selectedMethod = selectedPaymentMethod,
                                        onMethodSelected = { selectedPaymentMethod = it }
                                    ) 
                                }
                                
                                item {
                                    OrderItemsSummary(
                                        items = displayItems,
                                        currency = uiState.selectedCurrency,
                                        exchangeRates = uiState.exchangeRates
                                    )
                                }

                                item { PaymentSummarySection(priceBreakdown, uiState) }
                                item { 
                                    ReceiptToggleSection(
                                        checked = uiState.digitalReceiptEnabled,
                                        onCheckedChange = { viewModel.setDigitalReceiptEnabled(it) }
                                    ) 
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }

                // Sticky Bottom CTA
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Slate100)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                if (checkoutStep < 3) {
                                    checkoutStep++
                                } else {
                                    val totalToCharge = finalTotal.toDouble()
                                    val currentUserId = uiState.userId
                                    
                                    if (currentUserId == null || currentUserId == "demo_user") {
                                        viewModel.setPaymentStatus("Error: Please log in to complete your order.")
                                        return@Button
                                    }

                                    if (selectedPaymentMethod == "Wallet") {
                                        if (uiState.biometricEnabled && context is androidx.fragment.app.FragmentActivity) {
                                            com.example.nursewearconnect.utils.BiometricHelper.showBiometricPrompt(
                                                activity = context,
                                                title = "Confirm Payment",
                                                subtitle = "Authorize KSh ${"%,d".format(finalTotal)} from your wallet",
                                                onSuccess = {
                                                    viewModel.checkout(
                                                        userId = uiState.userId ?: "demo_user",
                                                        totalAmount = totalToCharge,
                                                        address = address,
                                                        addressId = addressId,
                                                        shippingMethod = uiState.shippingMethod,
                                                        paymentMethod = "Wallet"
                                                    )
                                                },
                                                onError = { _, err ->
                                                    viewModel.setPaymentStatus("Authentication failed: $err")
                                                }
                                            )
                                        } else {
                                            if (uiState.walletBalance >= totalToCharge) {
                                                viewModel.checkout(
                                                    userId = uiState.userId ?: "demo_user",
                                                    totalAmount = totalToCharge,
                                                    address = address,
                                                    addressId = addressId,
                                                    shippingMethod = uiState.shippingMethod,
                                                    paymentMethod = "Wallet"
                                                )
                                            } else {
                                                viewModel.setPaymentStatus("Error: Insufficient wallet balance.")
                                            }
                                        }
                                    } else {
                                        viewModel.checkout(
                                            userId = currentUserId,
                                            totalAmount = totalToCharge,
                                            address = address,
                                            addressId = addressId,
                                            shippingMethod = uiState.shippingMethod,
                                            paymentMethod = selectedPaymentMethod,
                                            phoneNumber = phoneNumber.replace("+", "").replace(" ", "")
                                        )
                                    }
                                }
                            },
                            enabled = !uiState.checkoutLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                        ) {
                            if (uiState.checkoutLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                val buttonText = when (checkoutStep) {
                                    1 -> "Review Shipping Address"
                                    2 -> "Continue to Payment"
                                    3 -> {
                                        val amountStr = AppUtils.formatCurrency(priceBreakdown.finalTotal.toDouble(), uiState.selectedCurrency, uiState.exchangeRates)
                                        if (selectedPaymentMethod == "Wallet" && uiState.biometricEnabled) 
                                            "Pay with TouchID - $amountStr" 
                                        else "Complete Order - $amountStr"
                                    }
                                    else -> ""
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = Slate400)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Secure checkout powered by M-Pesa & Paystack",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartIllustration() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Brand50,
                radius = size.minDimension / 2.2f
            )
            drawCircle(
                color = Brand100,
                radius = size.minDimension / 3.5f
            )
        }
        
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(90.dp),
            tint = Brand200
        )
        
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 35.dp, y = (-25).dp)
                .size(32.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, Brand600, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Brand600
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Brand300, radius = 5.dp.toPx(), center = Offset(center.x - 70.dp.toPx(), center.y + 20.dp.toPx()))
            drawCircle(Brand400, radius = 3.dp.toPx(), center = Offset(center.x + 60.dp.toPx(), center.y - 50.dp.toPx()))
        }
    }
}

@Composable
fun EmptyCartState(onNavigateToCatalog: () -> Unit) {
    EmptyState(
        illustration = { CartIllustration() },
        title = "Your cart is empty",
        description = "Discover the best medical apparel and gear curated just for you.",
        actionLabel = "Start Shopping",
        onActionClick = onNavigateToCatalog
    )
}

@Composable
fun CheckoutHeader(
    onBackClick: () -> Unit = {}, 
    step: Int,
    currency: AppUtils.Currency? = null,
    isLoading: Boolean = false,
    rotation: Float = 0f,
    onRefresh: () -> Unit = {},
    onCurrencyClick: () -> Unit = {}
) {
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Slate50, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate600)
                }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(24.dp), tint = Brand600)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (step == 3) "Payment" else if (step == 2) "Delivery" else "Checkout",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Slate900
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh, 
                        contentDescription = "Refresh", 
                        tint = if (isLoading) Brand600 else Slate900,
                        modifier = if (isLoading) Modifier.rotate(rotation) else Modifier
                    )
                }

                if (currency != null) {
                    Surface(
                        onClick = onCurrencyClick,
                        shape = RoundedCornerShape(100.dp),
                        color = Brand50,
                        border = BorderStroke(1.dp, Brand100)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currency.code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand700)
                            Icon(Icons.Default.ArrowDropDown, null, tint = Brand700, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutStepper(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepItem(
            icon = if (currentStep > 1) Icons.Default.Check else null,
            text = if (currentStep <= 1) "1" else null,
            label = "Review",
            isActive = currentStep >= 1,
            isCompleted = currentStep > 1
        )
        StepperLine(isActive = currentStep > 1)
        StepItem(
            icon = if (currentStep > 2) Icons.Default.Check else null,
            text = if (currentStep <= 2) "2" else null,
            label = "Address",
            isActive = currentStep >= 2,
            isCompleted = currentStep > 2
        )
        StepperLine(isActive = currentStep > 2)
        StepItem(
            text = "3",
            label = "Payment",
            isActive = currentStep >= 3
        )
    }
}

@Composable
fun StepItem(
    icon: ImageVector? = null,
    text: String? = null,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (isActive) Brand500 else Color.White,
                    CircleShape
                )
                .then(
                    if (!isActive) Modifier.background(Color.White, CircleShape).border(2.dp, Slate200, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted && icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else if (text != null) {
                Text(
                    text = text,
                    color = if (isActive) Color.White else Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Brand500 else Slate400,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun RowScope.StepperLine(isActive: Boolean) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(2.dp)
            .padding(horizontal = 8.dp)
            .background(if (isActive) Brand500 else Slate200)
    )
}

@Composable
fun CheckoutItemCard(
    cartItem: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onToggleEmbroidery: (String?) -> Unit,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    val product = cartItem.product
    var showEmbroideryDialog by remember { mutableStateOf(false) }
    var embroideryText by remember { mutableStateOf(cartItem.embroideryName ?: "") }

    if (showEmbroideryDialog) {
        AlertDialog(
            onDismissRequest = { showEmbroideryDialog = false },
            title = { Text("Name Embroidery") },
            text = {
                Column {
                    Text("Add a custom name & logo to your apparel (KSh 250 extra per item)", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = embroideryText,
                        onValueChange = { if (it.length <= 15) embroideryText = it },
                        label = { Text("Name (Max 15 chars)") },
                        placeholder = { Text("e.g. Dr. Jane Doe") },
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onToggleEmbroidery(if (embroideryText.isBlank()) null else embroideryText)
                    showEmbroideryDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onToggleEmbroidery(null)
                    embroideryText = ""
                    showEmbroideryDialog = false
                }) { Text("Remove") }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 96.dp)
                    .background(Slate50, RoundedCornerShape(12.dp))
                    .border(1.dp, Slate100, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (product.images.isNotEmpty()) {
                    AsyncImage(
                        model = product.images.first(),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Slate300, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Slate300, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = "${cartItem.color?.name ?: "Default"} • Size ${cartItem.size}",
                    fontSize = 11.sp,
                    color = Slate500
                )
                
                TextButton(
                    onClick = { showEmbroideryDialog = true },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (cartItem.embroideryName != null) Icons.Default.CheckCircle else Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (cartItem.embroideryName != null) Color(0xFF059669) else Brand500,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (cartItem.embroideryName != null) "Embroidery: ${cartItem.embroideryName}" else "Add Name & Logo (+250)",
                            fontSize = 10.sp,
                            color = if (cartItem.embroideryName != null) Color(0xFF059669) else Brand600,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = AppUtils.formatCurrency(product.priceKes.toDouble(), currency, exchangeRates),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .background(Slate50, RoundedCornerShape(8.dp))
                            .border(1.dp, Slate100, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        IconButton(onClick = onDecrease, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp), tint = Slate600)
                        }
                        Text("${cartItem.quantity}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        IconButton(onClick = onIncrease, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = Slate600)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReorderHistorySection(
    reorderItems: List<Product>,
    onAddToCart: (Product) -> Unit
) {
    if (reorderItems.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 16.dp).background(Color.White).padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Buy it again", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text("View All", fontSize = 12.sp, color = Brand600, fontWeight = FontWeight.Medium)
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(reorderItems) { product ->
                Surface(
                    modifier = Modifier.width(128.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate100)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(Color.White, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (product.images.isNotEmpty()) {
                                AsyncImage(
                                    model = product.images.first(),
                                    contentDescription = product.name,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Slate200, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text(
                            text = product.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(text = "KSh ${"%,d".format(product.priceKes)}", fontSize = 10.sp, color = Slate500)
                        
                        Button(
                            onClick = { onAddToCart(product) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(28.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Brand200)
                        ) {
                            Text("+ Add", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand600)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeliverySection(
    locationName: String,
    address: String,
    phone: String,
    locationType: String,
    onEditClick: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Delivery Address", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
            TextButton(onClick = onEditClick) {
                Text("Change", fontSize = 12.sp, color = Brand600, fontWeight = FontWeight.Medium)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Brand200),
            shadowElevation = 2.dp,
            onClick = onEditClick
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .offset(x = 100.dp, y = (-20).dp)
                        .background(Brand50, CircleShape)
                        .align(Alignment.TopEnd)
                )
                
                Row(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = Brand50
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Brand600,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(locationName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Slate100
                            ) {
                                Text(
                                    locationType,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate600,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            address,
                            fontSize = 12.sp,
                            color = Slate600,
                            lineHeight = 18.sp
                        )
                        Text(phone, fontSize = 11.sp, color = Slate500, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ShippingMethodSection(
    selectedMethod: String,
    onMethodSelected: (String) -> Unit,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text("Shipping Method", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Spacer(modifier = Modifier.height(12.dp))
        
        ShippingOption(
            title = "Standard Delivery",
            duration = "2-3 Business Days",
            price = "Free",
            isSelected = selectedMethod == "Standard",
            onClick = { onMethodSelected("Standard") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        ShippingOption(
            title = "Express Delivery",
            duration = "Same Day (Order before 2PM)",
            price = AppUtils.formatCurrency(200.0, currency, exchangeRates),
            isSelected = selectedMethod == "Express",
            onClick = { onMethodSelected("Express") }
        )
    }
}

@Composable
fun ShippingOption(title: String, duration: String, price: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (isSelected) Brand500 else Slate200),
        color = if (isSelected) Brand50.copy(alpha = 0.3f) else Color.White
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(2.dp, if (isSelected) Brand500 else Slate300, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(modifier = Modifier.size(10.dp).background(Brand500, CircleShape))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text(duration, fontSize = 11.sp, color = Slate500)
            }
            Text(price, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
        }
    }
}

@Composable
fun PromoCodeSection(
    promoCode: String,
    onCodeChange: (String) -> Unit,
    onApply: () -> Unit,
    appliedCoupon: Map<String, Any>?,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(24.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (appliedCoupon != null) Color(0xFF059669) else Slate200),
        color = if (appliedCoupon != null) Color(0xFFF0FDF4) else Color.White
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (appliedCoupon != null) Icons.Default.CheckCircle else Icons.Default.ConfirmationNumber,
                contentDescription = null,
                tint = if (appliedCoupon != null) Color(0xFF059669) else Brand500,
                modifier = Modifier.padding(start = 12.dp).size(16.dp)
            )
            TextField(
                value = if (appliedCoupon != null) appliedCoupon["code"]?.toString() ?: "" else promoCode,
                onValueChange = onCodeChange,
                enabled = appliedCoupon == null,
                placeholder = { Text("Enter promo code", fontSize = 13.sp, color = Slate400) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = Slate900,
                    unfocusedTextColor = Slate900,
                    disabledTextColor = Slate900
                ),
                modifier = Modifier.weight(1f).height(40.dp)
            )
            if (appliedCoupon != null) {
                TextButton(onClick = onRemove) {
                    Text("Remove", fontSize = 12.sp, color = Color.Red)
                }
            } else {
                val isCodeEmpty = promoCode.isBlank()
                Button(
                    onClick = onApply,
                    enabled = !isCodeEmpty,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand600,
                        disabledContainerColor = Slate300
                    ),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MpesaLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFFF1F8E9), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "M-",
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Black,
            fontSize = 14.sp
        )
        Text(
            text = "PESA",
            color = Color(0xFFE91E63),
            fontWeight = FontWeight.Black,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PaystackLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(width = 100.dp, height = 20.dp)) {
            val bluePath = PathParser().parsePathString(
                "m-45.8 232.2h-80.4c-2.7 0-5 2.3-5 5 .1v9.1c0 2.8 2.3 5.1 5 5.1h80.4c2.8 0 5-2.3 5.1-5.1v-9c0-2.9-2.3-5.2-5.1-5.2zm0 50.5h-80.4c-1.3 0-2.6.5-3.5 1.5-1 1-1.5 2.2-1.5 3.6v9.1c0 2.8 2.3 5.1 5 5.1h80.4c2.8 0 5-2.2 5.1-5.1v-9.1c-.1-2.9-2.3-5.1-5.1-5.1zm-35.1 25.2h-45.3c-1.3 0-2.6.5-3.5 1.5s-1.5 2.2-1.5 3.6v9.1c0 2.8 2.3 5.1 5 5.1h45.2c2.8 0 5-2.3 5-5v-9.1c.1-3-2.1-5.3-4.9-5.2zm40.2-50.5h-85.5c-1.3 0-2.6.5-3.5 1.5s-1.5 2.2-1.5 3.6v9.1c0 2.8 2.3 5.1 5 5.1h85.4c2.8 0 5-2.3 5-5.1v-9.1c.1-2.8-2.2-5-4.9-5.1zm0 0"
            ).toPath()
            
            val darkPath = PathParser().parsePathString(
                "m52.8 252.6c-2.5-2.6-5.4-4.6-8.7-6s-6.8-2.1-10.4-2.1c-3.5-.1-6.9.7-10.1 2.2-2.1 1-4 2.4-5.6 4.1v-1.6c0-.8-.3-1.6-.8-2.2s-1.3-1-2.2-1h-11.1c-.8 0-1.6.3-2.1 1-.6.6-.9 1.4-.8 2.2v74.8c0 .8.3 1.6.8 2.2.6.6 1.3.9 2.1.9h11.4c.8 0 1.5-.3 2.1-.9.6-.5 1-1.3.9-2.2v-25.6c1.6 1.8 3.7 3.1 6 3.9 3 1.1 6.1 1.7 9.3 1.7 3.6 0 7.2-.7 10.5-2.1s6.3-3.4 8.8-6c2.6-2.7 4.6-5.9 6-9.4 1.6-3.9 2.3-8.1 2.2-12.3.1-4.2-.7-8.4-2.2-12.4-1.5-3.3-3.5-6.5-6.1-9.2zm-10.2 27.1c-.6 1.6-1.5 3-2.7 4.3-2.3 2.5-5.6 3.9-9 3.9-1.7 0-3.4-.3-5-1.1-1.5-.7-2.9-1.6-4.1-2.8s-2.1-2.7-2.7-4.3c-1.3-3.4-1.3-7.1 0-10.5.6-1.6 1.6-3 2.7-4.2 1.2-1.2 2.6-2.2 4.1-2.9 1.6-.7 3.3-1.1 5-1.1 1.8 0 3.4.3 5.1 1.1 1.5.7 2.9 1.6 4 2.8 1.2 1.2 2 2.6 2.7 4.2 1.2 3.5 1.1 7.2-.1 10.6zm79.6-33.6h-11.3c-.8 0-1.6.3-2.1.9-.6.6-.9 1.4-.9 2.3v1.4c-1.4-1.7-3.2-3-5.1-3.9-3.1-1.5-6.5-2.2-9.9-2.2-7.3 0-14.2 2.9-19.4 8-2.7 2.7-4.8 5.9-6.2 9.4-1.6 3.9-2.4 8.1-2.3 12.4-.1 4.2.7 8.4 2.3 12.4 1.5 3.5 3.5 6.7 6.2 9.4 5.1 5.2 12.1 8.1 19.3 8.1 3.4.1 6.8-.7 9.9-2.2 1.9-1 3.8-2.3 5.2-3.9v1.5c0 .8.3 1.6.9 2.2.6.5 1.3.9 2.1.9h11.3c.8 0 1.6-.3 2.1-.9.6-.6.9-1.4.9-2.2v-50.3c0-.8-.3-1.6-.8-2.2-.6-.7-1.4-1.1-2.2-1.1zm-15.3 33.6c-.6 1.6-1.5 3-2.7 4.3-1.2 1.2-2.5 2.2-4 2.9-3.2 1.5-6.9 1.5-10.1 0-1.5-.7-2.9-1.7-4.1-2.9s-2.1-2.7-2.7-4.3c-1.2-3.4-1.2-7.1 0-10.5.6-1.6 1.5-2.9 2.7-4.2 1.2-1.2 2.5-2.2 4.1-2.9 3.2-1.5 6.9-1.5 10 0 1.5.7 2.9 1.6 4 2.8s2 2.6 2.7 4.2c1.4 3.5 1.4 7.2.1 10.6zm127.9-6.8c-1.6-1.4-3.5-2.6-5.5-3.4-2.1-.9-4.4-1.5-6.6-2l-8.6-1.7c-2.2-.4-3.8-1-4.6-1.7-.7-.5-1.2-1.3-1.2-2.2s.5-1.7 1.6-2.4c1.5-.8 3.1-1.2 4.8-1.1 2.2 0 4.4.5 6.4 1.3 2 .9 3.9 1.8 5.7 3 2.5 1.6 4.7 1.3 6.2-.5l4.1-4.7c.8-.8 1.2-1.8 1.3-2.9-.1-1.2-.7-2.2-1.6-3-1.7-1.5-4.5-3.1-8.2-4.7s-8.4-2.4-13.9-2.4c-3.4-.1-6.7.4-9.9 1.4-2.7.9-5.3 2.2-7.6 3.9-2.1 1.6-3.7 3.6-4.9 6-1.1 2.3-1.7 4.8-1.7 7.3 0 4.7 1.4 8.5 4.2 11.3s6.5 4.7 11.1 5.6l9 2c1.9.3 3.9.9 5.7 1.8 1 .4 1.6 1.4 1.6 2.5 0 1-.5 1.9-1.6 2.7s-2.9 1.3-5.3 1.3-4.9-.5-7.1-1.6c-2.1-1-4-2.3-5.8-3.8-.8-.6-1.6-1.1-2.6-1.5-1-.3-2.3 0-3.6 1.1l-4.9 3.7c-1.4 1-2.1 2.7-1.7 4.3.3 1.7 1.6 3.3 4.1 5.2 6.2 4.2 13.6 6.4 21.1 6.2 3.5 0 7-.4 10.3-1.4 2.9-.9 5.6-2.2 8-4 2.2-1.6 4-3.7 5.2-6.2 1.2-2.4 1.8-5 1.8-7.7.1-2.4-.4-4.8-1.4-7-1-1.6-2.3-3.3-3.9-4.7zm49.4 13.7c-.5-.9-1.4-1.5-2.5-1.7-1 0-2.1.3-2.9.9-1.4.9-3 1.4-4.6 1.5-.5 0-1.1-.1-1.6-.2-.6-.1-1.1-.4-1.5-.8-.5-.5-.9-1.1-1.2-1.7-.4-1-.6-2-.5-3v-20.5h14.6c.9 0 1.7-.4 2.3-1s1-1.3 1-2.2v-8.7c0-.9-.3-1.7-1-2.2-.6-.6-1.4-.9-2.2-.9h-14.7v-14c0-.8-.3-1.7-.9-2.2s-1.3-.8-2.1-.9h-11.4c-.8 0-1.6.3-2.2.9s-1 1.4-1 2.2v14h-6.5c-.8 0-1.6.3-2.2 1-.5.6-.8 1.4-.8 2.2v8.7c0 .8.3 1.6.8 2.2.5.7 1.3 1 2.2 1h6.5v24.4c-.1 2.9.5 5.8 1.7 8.4 1.1 2.2 2.5 4.1 4.4 5.7 1.8 1.5 3.9 2.6 6.2 3.2 2.3.7 4.7 1.1 7.1 1.1 3.1 0 6.3-.5 9.3-1.5 2.8-.9 5.3-2.5 7.3-4.6 1.3-1.3 1.4-3.4.4-4.9zm61.8-40.5h-11.3c-.8 0-1.5.3-2.1.9s-.9 1.4-.9 2.3v1.4c-1.4-1.7-3.1-3-5.1-3.9-3.1-1.5-6.5-2.2-9.9-2.2-7.3 0-14.2 2.9-19.4 8-2.7 2.7-4.8 5.9-6.2 9.4-1.6 3.9-2.4 8.1-2.3 12.3-.1 4.2.7 8.4 2.3 12.4 1.4 3.5 3.6 6.7 6.2 9.4 5.1 5.2 12 8.1 19.3 8.1 3.4.1 6.8-.7 9.9-2.1 2-1 3.8-2.3 5.2-3.9v1.5c0 .8.3 1.6.9 2.1.6.6 1.3.9 2.1.9h11.3c1.7 0 3-1.3 3-3v-50.3c0-.8-.3-1.6-.8-2.2-.5-.7-1.3-1.1-2.2-1.1zm-15.2 33.6c-.6 1.6-1.5 3-2.7 4.3-1.2 1.2-2.5 2.2-4 2.9-1.6.7-3.3 1.1-5.1 1.1s-3.4-.4-5-1.1c-1.5-.7-2.9-1.7-4.1-2.9s-2.1-2.7-2.6-4.3c-1.2-3.4-1.2-7.1 0-10.5.6-1.6 1.5-3 2.6-4.2 1.2-1.2 2.6-2.2 4.1-2.9 1.6-.7 3.3-1.1 5-1.1s3.4.3 5.1 1.1c1.5.7 2.8 1.6 4 2.8s2.1 2.6 2.7 4.2c1.3 3.4 1.3 7.2 0 10.6zm77.2 6.1-6.5-5c-1.2-1-2.4-1.3-3.4-.9-.9.4-1.7 1-2.4 1.7-1.4 1.7-3.1 3.2-4.9 4.5-2 1.1-4.1 1.7-6.3 1.5-2.6 0-5-.7-7.1-2.2s-3.7-3.5-4.5-6c-.6-1.7-.9-3.4-.9-5.1 0-1.8.3-3.5.9-5.3.6-1.6 1.4-3 2.6-4.2s2.5-2.2 4-2.8c1.6-.7 3.3-1.1 5-1.1 2.2-.1 4.4.5 6.3 1.6 1.9 1.2 3.5 2.7 4.9 4.5.6.7 1.4 1.3 2.3 1.7 1 .4 2.2.1 3.4-.9l6.5-4.9c.8-.5 1.4-1.3 1.7-2.2.4-1 .3-2.1-.3-3-2.5-3.9-5.9-7.1-10-9.4-4.3-2.4-9.4-3.7-15.1-3.7-4 0-8 .8-11.8 2.3-3.6 1.5-6.8 3.6-9.5 6.3s-4.9 5.9-6.4 9.5c-3.1 7.5-3.1 15.9 0 23.4 1.5 3.5 3.6 6.8 6.4 9.4 5.7 5.6 13.3 8.6 21.3 8.6 5.7 0 10.8-1.3 15.1-3.7 4.1-2.3 7.6-5.5 10.1-9.5.5-.9.6-2 .3-2.9-.4-.8-1-1.6-1.8-2.2zm60.2 11.7-17.9-26.2 15.3-20.2c.7-.9 1-2.2.6-3.3-.3-.8-1-1.6-2.9-1.6h-12.1c-.7 0-1.4.2-2 .5-.8.4-1.4 1-1.8 1.7l-12.2 17.1h-2.9v-40.4c0-.8-.3-1.6-.9-2.2s-1.3-.9-2.1-.9h-11.3c-.8 0-1.6.3-2.2.9s-.9 1.3-.9 2.2v74.5c0 .9.3 1.6.9 2.2s1.4.9 2.2.9h11.3c.8 0 1.6-.3 2.1-.9.6-.6.9-1.4.9-2.2v-19.7h3.2l13.3 20.4c.8 1.5 2.3 2.4 3.9 2.4h12.7c1.9 0 2.7-.9 3.1-1.7.5-1.2.4-2.5-.3-3.5zm-281.8-51.4h-12.7c-1 0-1.9.3-2.6 1-.6.6-1 1.3-1.2 2.1l-9.4 34.8h-2.3l-10-34.8c-.2-.7-.5-1.4-1-2.1-.6-.7-1.4-1.1-2.3-1.1h-12.9c-1.7 0-2.7.5-3.2 1.7-.3 1-.3 2.1 0 3.1l16 49c.3.7.6 1.5 1.2 2 .6.6 1.5.9 2.4.9h6.8l-.6 1.6-1.5 4.5c-.5 1.4-1.3 2.6-2.5 3.5-1.1.8-2.4 1.3-3.8 1.2-1.2 0-2.3-.3-3.4-.7-1.1-.5-2.1-1.1-3-1.8-.8-.6-1.8-.9-2.9-.9h-.1c-1.2.1-2.3.7-2.9 1.8l-4 5.9c-1.6 2.6-.7 4.2.3 5.1 2.2 2 4.7 3.5 7.5 4.4 3.1 1.1 6.3 1.6 9.5 1.6 5.8 0 10.6-1.6 14.3-4.7 3.8-3.4 6.7-7.8 8.1-12.8l18.6-60.6c.4-1.1.5-2.2.1-3.2-.1-.7-.8-1.5-2.5-1.5zm0 0"
            ).toPath()

            val scaleX = size.width / 600f
            val scaleY = size.height / 100f
            
            translate(left = 131.2f * scaleX, top = -222f * scaleY) {
                scale(scaleX, scaleY, Offset.Zero) {
                    drawPath(bluePath, color = Color(0xFF00C3F7))
                    drawPath(darkPath, color = Color(0xFF011B33))
                }
            }
        }
    }
}

@Composable
fun PaymentMethodSection(
    phoneNumber: String, 
    onPhoneChange: (String) -> Unit,
    walletBalance: Double,
    selectedMethod: String, 
    onMethodSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Payment Method", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaymentMethodCard(
                name = "Wallet",
                content = { 
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = if (selectedMethod == "Wallet") Brand600 else Slate400) 
                },
                isSelected = selectedMethod == "Wallet",
                onClick = { onMethodSelected("Wallet") },
                modifier = Modifier.weight(1f)
            )
            PaymentMethodCard(
                name = "M-Pesa",
                content = { MpesaLogo() },
                isSelected = selectedMethod == "M-Pesa",
                onClick = { onMethodSelected("M-Pesa") },
                modifier = Modifier.weight(1f)
            )
            PaymentMethodCard(
                name = "Paystack",
                content = { PaystackLogo() },
                isSelected = selectedMethod == "Paystack",
                onClick = { onMethodSelected("Paystack") },
                modifier = Modifier.weight(1f)
            )
        }
        
        if (selectedMethod == "Wallet") {
            WalletForm(walletBalance)
        } else if (selectedMethod == "M-Pesa") {
            MpesaForm(phoneNumber, onPhoneChange)
        } else {
            PaystackForm()
        }
    }
}

@Composable
fun WalletForm(balance: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .background(Brand50, RoundedCornerShape(12.dp))
            .border(1.dp, Brand100, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = Brand600, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("NurseWear Wallet", fontWeight = FontWeight.Bold, color = Brand700)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Current Balance: KSh ${"%,.2f".format(balance)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Slate900
        )
        Text(
            "Quick & secure payment. No transaction fees.",
            fontSize = 11.sp,
            color = Slate500,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun PaystackForm() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .background(Color(0xFFF0F9FF), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = Color(0xFF0369A1), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Secured by Paystack",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0C4A6E)
                )
            }
            PaystackLogo()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Pay with Card, Bank Transfer, or Apple Pay. Your payment info is encrypted and never stored on our servers.",
            fontSize = 12.sp,
            color = Color(0xFF075985),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFF0369A1), modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "PCI-DSS Certified Level 1 Service Provider",
                fontSize = 10.sp,
                color = Color(0xFF0369A1),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PaymentMethodCard(
    name: String,
    content: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (isSelected) Brand500 else Slate200),
        color = if (isSelected) Brand50.copy(alpha = 0.3f) else Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Brand600 else Slate700)
        }
    }
}

@Composable
fun MpesaForm(phoneNumber: String, onPhoneChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("M-Pesa Number", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate500)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("0712345678", fontSize = 14.sp) },
            leadingIcon = { Text("🇰🇪", modifier = Modifier.padding(start = 12.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Brand600,
                unfocusedBorderColor = Slate200
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
            )
        )
        Text(
            "Supports 07..., 01..., or 254... formats. STK Push will be sent instantly.",
            fontSize = 11.sp,
            color = Slate500,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun StripeForm() {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Card Information", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate500)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = "**** **** **** 4242",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = Slate400) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Brand600,
                unfocusedBorderColor = Slate200
            )
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = "12/26",
                onValueChange = {},
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("MM/YY") }
            )
            OutlinedTextField(
                value = "***",
                onValueChange = {},
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("CVC") }
            )
        }
    }
}

@Composable
fun OrderItemsSummary(
    items: List<CartItem>,
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Order Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Slate100)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Slate50
                        ) {
                            if (item.product.images.isNotEmpty()) {
                                AsyncImage(
                                    model = item.product.images.first(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Checkroom, null, tint = Brand600, modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.product.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate900
                            )
                            Text(
                                "Size: ${item.size}${if (item.color != null) " • Color: ${item.color.name}" else ""}",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                            if (!item.embroideryName.isNullOrBlank()) {
                                Text(
                                    "Embroidery: ${item.embroideryName}",
                                    fontSize = 11.sp,
                                    color = Brand600,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                AppUtils.formatCurrency((item.product.priceKes * item.quantity).toDouble(), currency, exchangeRates),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                "Qty: ${item.quantity}",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    if (items.indexOf(item) < items.size - 1) {
                        HorizontalDivider(color = Slate50, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentSummarySection(
    priceBreakdown: PriceBreakdown,
    uiState: com.example.nursewearconnect.ui.viewmodel.HomeUiState
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        HorizontalDivider(color = Slate100, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Payment Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Spacer(modifier = Modifier.height(16.dp))
        
        SummaryRow("Subtotal Items", AppUtils.formatCurrency(priceBreakdown.itemsSubtotal.toDouble(), uiState.selectedCurrency, uiState.exchangeRates))
        
        if (priceBreakdown.embroideryTotal > 0) {
            SummaryRow("Name & Logo Embroidery", AppUtils.formatCurrency(priceBreakdown.embroideryTotal.toDouble(), uiState.selectedCurrency, uiState.exchangeRates))
        }
        
        SummaryRow(priceBreakdown.discountLabel, "- ${AppUtils.formatCurrency(priceBreakdown.discountAmount.toDouble(), uiState.selectedCurrency, uiState.exchangeRates)}", isDiscount = true)
        
        SummaryRow(
            label = "Shipping",
            value = if (priceBreakdown.shippingCost == 0) "Free" else AppUtils.formatCurrency(priceBreakdown.shippingCost.toDouble(), uiState.selectedCurrency, uiState.exchangeRates),
            isFree = priceBreakdown.shippingCost == 0
        )
        
        if (priceBreakdown.fittingFee > 0) {
            SummaryRow("Home Fitting Service", AppUtils.formatCurrency(priceBreakdown.fittingFee.toDouble(), uiState.selectedCurrency, uiState.exchangeRates))
        }

        SummaryRow("Tax (16% VAT)", AppUtils.formatCurrency(priceBreakdown.tax.toDouble(), uiState.selectedCurrency, uiState.exchangeRates))
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Slate200, thickness = 1.dp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total Amount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(AppUtils.formatCurrency(priceBreakdown.finalTotal.toDouble(), uiState.selectedCurrency, uiState.exchangeRates), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand600)
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, isFree: Boolean = false, isDiscount: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = if (isDiscount) Color(0xFF059669) else Slate500)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isFree || isDiscount) Color(0xFF059669) else Slate900
        )
    }
}

@Composable
fun ReceiptToggleSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.padding(horizontal = 24.dp),
        shape = RoundedCornerShape(12.dp),
        color = Brand50,
        border = BorderStroke(1.dp, Brand100)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null, tint = Brand500, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Digital Receipt", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text("Send to email for tax purposes", fontSize = 11.sp, color = Slate600)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Brand500,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Slate200
                )
            )
        }
    }
}
