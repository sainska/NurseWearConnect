package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.Locale
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.model.ProductColor
import com.example.nursewearconnect.ui.theme.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import com.example.nursewearconnect.ui.components.NursePullToRefresh
import com.example.nursewearconnect.ui.components.InventoryHealthCard
import com.example.nursewearconnect.ui.components.FittingServiceSection
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.utils.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    innerPadding: PaddingValues, 
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToUserLogs: () -> Unit = {},
    onNavigateToAdminUsers: () -> Unit = {},
    onNavigateToAdminVendors: () -> Unit = {},
    onNavigateToAdminInventory: () -> Unit = {},
    onNavigateToAdminOrders: () -> Unit = {},
    onNavigateToAdminMarketing: () -> Unit = {},
    onNavigateToVendorMarketing: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToVendorInventory: () -> Unit = {},
    onNavigateToVendorOrders: () -> Unit = {},
    onNavigateToVendorAnalytics: () -> Unit = {},
    onNavigateToVendorCatalog: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    messagingViewModel: com.example.nursewearconnect.ui.viewmodel.MessagingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userRole = uiState.userRole
    val filteredNewArrivals by viewModel.filteredNewArrivals.collectAsState()
    val filteredRecommendations by viewModel.filteredRecommendations.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val isLoading = uiState.isLoading
    
    var showSizeQuiz by rememberSaveable { mutableStateOf(false) }

    val unreadNotifs by messagingViewModel.unreadNotificationCount.collectAsState()
    val convos by messagingViewModel.conversations.collectAsState()
    val unreadMessages = remember(convos) { convos.sumOf { it.unread_count } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Decorative Background Gradients
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Brand100.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        NursePullToRefresh(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadHomeData(); messagingViewModel.refresh() },
            screenIcon = Icons.Default.Home,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Content Wrapper to limit width on large screens
                Column(
                    modifier = Modifier
                        .widthIn(max = 1200.dp)
                        .fillMaxWidth()
                ) {
                    HomeHeader(
                        userRole = userRole,
                        userName = uiState.userName,
                        greeting = uiState.greeting,
                        profilePicture = uiState.profilePicture,
                        unreadNotificationsCount = unreadNotifs,
                        unreadMessagesCount = unreadMessages,
                        onNotificationsClick = onNavigateToNotifications,
                        onMessagesClick = onNavigateToMessages,
                        onProfileClick = onNavigateToProfile,
                        modifier = Modifier.statusBarsPadding()
                    )
                    
                    SearchBar(
                        query = uiState.searchQuery,
                        userRole = userRole,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onSearchAction = { onNavigateToCatalog() },
                        onFilterClick = { onNavigateToCatalog() }
                    )
                    
                    if (userRole != "vendor" && userRole != "admin") {
                        CategorySelector(
                            categories = uiState.categories, 
                            activeCat = uiState.activeCategory,
                            onCategorySelected = { viewModel.onCategorySelected(it) }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (userRole == "vendor") {
                        val commissionRate = uiState.commissionRate.takeIf { it > 0.0 } ?: 10.0
                        val dbRevenue = uiState.vendorRevenue
                        val netEarnings = dbRevenue * (1 - (commissionRate / 100.0))
                        val dbOrderCount = uiState.vendorOrderCount
                        val dbLowStockCount = (uiState.vendorStockHealth["low_stock_count"] as? Number)?.toInt() ?: 0
                        val pendingOrderCount = uiState.vendorOrders.count { it["item_status"]?.toString()?.lowercase() == "pending" }
                        
                        // LEVEL 1: Command Center (High Level Stats)
                        VendorCommandCenter(
                            revenue = netEarnings,
                            pendingOrders = pendingOrderCount,
                            lowStock = dbLowStockCount,
                            onAnalyticsClick = onNavigateToVendorAnalytics
                        )

                        VendorFinancialsCard(
                            balance = uiState.walletBalance,
                            nextPayout = uiState.payouts.filter { it["status"]?.toString()?.lowercase() == "pending" }.firstOrNull()?.get("scheduled_date")?.toString() ?: "TBD",
                            onWithdrawClick = { viewModel.requestWithdrawal(uiState.walletBalance) }
                        )

                        // LEVEL 2: Operations Hub (Quick Actions)
                        SectionHeader(title = "Operations Hub", subtitle = "Manage your business daily tasks", onSeeAllClick = {})
                        VendorQuickActions(
                            onInventoryClick = onNavigateToVendorInventory,
                            onOrdersClick = onNavigateToVendorOrders,
                            onMarketingClick = onNavigateToVendorMarketing,
                            onMessagesClick = onNavigateToMessages,
                            onCatalogClick = onNavigateToVendorCatalog
                        )

                        // LEVEL 3: Shop Status & Critical Alerts
                        VendorStoreStatusBanner(
                            status = uiState.userStatus,
                            onToggleStatus = { viewModel.toggleVendorStatus() }
                        )

                        if (uiState.userStatus == "pending" || uiState.userStatus == "rejected") {
                            VendorRestrictionAlert(
                                status = uiState.userStatus,
                                notes = uiState.statusNotes,
                                adminEmail = uiState.adminEmail,
                                adminPhone = uiState.adminPhone
                            )
                        }

                        // LEVEL 4: Inventory & Sales Insights
                        if (uiState.vendorBestSellers.isNotEmpty()) {
                            SectionHeader(
                                title = "Performance Insights",
                                subtitle = "Your top performing uniforms",
                                onSeeAllClick = onNavigateToVendorAnalytics
                            )
                            VendorTopSellingRow(
                                bestSellers = uiState.vendorBestSellers,
                                onClick = { productId ->
                                    viewModel.loadProductById(productId) { product ->
                                        if (product != null) {
                                            viewModel.setSelectedProduct(product)
                                        }
                                    }
                                }
                            )
                        }

                        // LEVEL 5: Live Order Feed
                        SectionHeader(
                            title = "Recent Activity",
                            subtitle = "Real-time updates on your sales",
                            onSeeAllClick = onNavigateToVendorOrders
                        )
                        
                        VendorLiveFeed(
                            orders = uiState.vendorOrders,
                            onOrderClick = { onNavigateToVendorOrders() }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                    } else if (userRole == "admin") {
                        val allUsers by viewModel.allUsers.collectAsState()
                        val totalRevenue = uiState.allOrders.filter { it["status"] == "delivered" }
                            .sumOf { (it["total_amount"] as? Number)?.toDouble() ?: it["total_amount"]?.toString()?.toDoubleOrNull() ?: 0.0 }
                        
                        LaunchedEffect(Unit) {
                            viewModel.loadAdminMarketingData()
                        }

                        AdminStats(
                            userCount = allUsers.size,
                            pendingVendors = uiState.pendingVendors.size,
                            totalRevenue = totalRevenue,
                            orderCount = uiState.allOrders.size,
                            onClick = onNavigateToReports
                        )

                        QuickActions(
                            userRole = userRole,
                            onQuickReorderClick = onNavigateToMessages,
                            onFavoritesClick = onNavigateToAdminInventory,
                            onUserLogsClick = onNavigateToUserLogs,
                            onAdminUsersClick = onNavigateToAdminUsers,
                            onAdminVendorsClick = onNavigateToAdminVendors,
                            onAdminMarketingClick = onNavigateToAdminMarketing,
                            onReportsClick = onNavigateToReports
                        )

                        if (uiState.salesTrends.isNotEmpty()) {
                            SectionHeader(
                                title = "Sales Trends",
                                subtitle = "System-wide revenue performance",
                                onSeeAllClick = onNavigateToReports
                            )
                            AdminSalesTrendsRow(trends = uiState.salesTrends)
                        }

                        SectionHeader(
                            title = "Inventory Overview",
                            subtitle = "Stock health across all vendors",
                            onSeeAllClick = onNavigateToAdminInventory
                        )
                        
                        AdminInventoryHealthRow(
                            health = uiState.inventoryHealth,
                            onInventoryClick = onNavigateToAdminInventory
                        )

                        SectionHeader(
                            title = "Priority Tasks",
                            subtitle = "Items requiring immediate attention",
                            onSeeAllClick = { onNavigateToAdminVendors() }
                        )
                        
                        AdminPendingTasks(
                            pendingVendors = uiState.pendingVendors,
                            lowStockProducts = uiState.products.filter { it.stockCount > 0 && it.stockCount <= Constants.LOW_STOCK_THRESHOLD },
                            pendingBanners = uiState.banners.filter { it["status"] == "pending" },
                            onApproveVendor = { viewModel.approveVendor(it) },
                            onApproveBanner = { viewModel.approveBanner(it) },
                            onNavigateToInventory = onNavigateToAdminInventory,
                            onNavigateToBanners = onNavigateToAdminMarketing
                        )

                        Spacer(Modifier.height(24.dp))

                        SectionHeader(
                            title = "Marketing & Catalog",
                            subtitle = "Promotions and active coupons",
                            onSeeAllClick = { onNavigateToAdminMarketing() }
                        )
                        
                        MarketingOverview(
                            coupons = uiState.coupons,
                            banners = uiState.banners,
                            onCouponClick = onNavigateToAdminMarketing,
                            onBannerClick = onNavigateToAdminMarketing
                        )

                        Spacer(Modifier.height(24.dp))

                        SectionHeader(
                            title = "System Activity",
                            subtitle = "Recent audit logs and events",
                            onSeeAllClick = onNavigateToUserLogs
                        )
                        
                        AdminActivityList(
                            logs = uiState.systemLogs,
                            onSeeAllOrders = onNavigateToAdminOrders
                        )

                    } else {
                        HeroBanner(
                            featuredProduct = uiState.featuredProduct,
                            banners = uiState.banners,
                            userRole = uiState.userRole,
                            onShopNowClick = { banner ->
                                val productId = banner?.get("product_id") as? String
                                if (productId != null) {
                                    viewModel.loadProductById(productId) { product ->
                                        if (product != null) {
                                            viewModel.setSelectedProduct(product)
                                        } else {
                                            onNavigateToCatalog()
                                        }
                                    }
                                } else {
                                    onNavigateToCatalog()
                                }
                            },
                            onActionLinkClick = { link ->
                                if (link.startsWith("category:")) {
                                    val category = link.removePrefix("category:")
                                    viewModel.onCategorySelected(category)
                                    onNavigateToCatalog()
                                }
                            },
                            onBannerImpression = { viewModel.trackBannerImpression(it) },
                            onBannerClick = { viewModel.trackBannerClick(it) }
                        )

                        if (uiState.banners.isNotEmpty()) {
                            SectionHeader(
                                title = "Banner Cards",
                                subtitle = "Special promotions for you",
                                onSeeAllClick = { onNavigateToCatalog() }
                            )
                            BannerCardsRow(
                                banners = uiState.banners.filter { it["active"] == true },
                                onBannerClick = { id -> 
                                    val banner = uiState.banners.find { it["id"] == id }
                                    val link = banner?.get("action_link")?.toString()
                                    if (link != null) {
                                        if (link.startsWith("category:")) {
                                            viewModel.onCategorySelected(link.removePrefix("category:"))
                                            onNavigateToCatalog()
                                        }
                                    }
                                }
                            )
                        }
                        
                        SizeFinderCard(onStartQuiz = { showSizeQuiz = true })
                    
                        if (showSizeQuiz) {
                            SizeQuizBottomSheet(
                                onDismiss = { showSizeQuiz = false },
                                onComplete = { results ->
                                    viewModel.applySizeQuizResult(results)
                                    showSizeQuiz = false
                                },
                                gender = uiState.selectedProduct?.gender ?: "Unisex"
                            )
                        }
                    }
                    

                    // New Arrivals Section
                    if ((userRole == "student" || userRole == "professional") && filteredNewArrivals.isNotEmpty()) {
                        SectionHeader(
                            title = "New Arrivals",
                            subtitle = "Fresh styles for your shift",
                            onSeeAllClick = { onNavigateToCatalog() }
                        )
                        NewArrivalsRow(
                            products = filteredNewArrivals,
                            onProductClick = { viewModel.setSelectedProduct(it) },
                            onAddToCart = { viewModel.addToCart(it, 1, null) }
                        )
                    }

                    // Bundle Deals Section
                    if (uiState.bundles.isNotEmpty() && (userRole != "vendor" && userRole != "admin")) {
                        SectionHeader(
                            title = "Bundle Deals",
                            subtitle = "Save more with medical sets",
                            onSeeAllClick = { /* Navigate to Bundles if applicable */ }
                        )
                        BundleDealsRow(
                            bundles = uiState.bundles,
                            onBundleClick = { bundle ->
                                bundle.items.firstOrNull()?.products?.let { viewModel.setSelectedProduct(it) }
                            }
                        )
                    }
                    
                    if (userRole != "vendor" && userRole != "admin") {
                        QuickActions(
                            userRole = userRole,
                            onQuickReorderClick = { viewModel.setShowQuickReorder(true) },
                            onFavoritesClick = { viewModel.setShowFavorites(true) }
                        )
                    }

                    if (userRole == "student" || userRole == "professional") {
                        SectionHeader(
                            title = "Recommended for You",
                            subtitle = "Based on your sizing profile",
                            onSeeAllClick = { onNavigateToCatalog() }
                        )
                        ProductGrid(
                            products = filteredRecommendations,
                            favoriteProductIds = uiState.favoriteProductIds,
                            onFavoriteToggle = { viewModel.toggleFavorite(it.id) },
                            onAddToCart = { viewModel.addToCart(it, 1, null) },
                            onProductClick = { viewModel.setSelectedProduct(it) },
                            isLoading = isLoading,
                            currency = uiState.selectedCurrency,
                            exchangeRates = uiState.exchangeRates
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Favorites Bottom Sheet
        if (uiState.showFavorites) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowFavorites(false) },
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("Your Favorites", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Spacer(Modifier.height(16.dp))
                    
                    val favoriteProducts = uiState.products.filter { uiState.favoriteProductIds.contains(it.id) }
                    if (favoriteProducts.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No favorites yet.", color = Slate500)
                        }
                    } else {
                        favoriteProducts.forEach { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = Slate50) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (product.images.isNotEmpty()) {
                                            AsyncImage(
                                                model = product.images.first(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Inventory, contentDescription = null, tint = Slate300)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(AppUtils.formatCurrency(product.priceKes.toDouble(), uiState.selectedCurrency, uiState.exchangeRates), fontSize = 12.sp, color = Slate500)
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(product.id) }) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF43F5E))
                                }
                            }
                            HorizontalDivider(color = Slate100)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        // Quick Re-order Bottom Sheet
        if (uiState.showQuickReorder) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowQuickReorder(false) },
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("Quick Re-order", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text("From your past successful purchases", fontSize = 12.sp, color = Slate500)
                    Spacer(Modifier.height(16.dp))
                    
                    if (uiState.reorderItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No previous orders found.", color = Slate500)
                        }
                    } else {
                        uiState.reorderItems.forEach { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(12.dp), color = Slate50) {
                                    AsyncImage(
                                        model = product.images.firstOrNull(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(AppUtils.formatCurrency(product.priceKes.toDouble(), uiState.selectedCurrency, uiState.exchangeRates), fontSize = 13.sp, color = Brand600, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { viewModel.quickReorder(product); viewModel.setShowQuickReorder(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Brand600),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    Text("Reorder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider(color = Slate100)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        // Product Detail Bottom Sheet
        uiState.selectedProduct?.let { product ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.setSelectedProduct(null) },
                containerColor = Color.White,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ProductDetailContent(
                    product = product,
                    isFavorite = uiState.favoriteProductIds.contains(product.id),
                    onFavoriteToggle = { viewModel.toggleFavorite(product.id) },
                    selectedSize = uiState.selectedSize,
                    onSizeSelected = { viewModel.setSelectedSize(it) },
                    selectedColor = uiState.selectedColor,
                    onColorSelected = { viewModel.setSelectedColor(it) },
                    onAddToCart = { 
                        if (viewModel.addToCart(product, 1, uiState.selectedSize)) {
                            viewModel.setSelectedProduct(null)
                        }
                    },
                    error = uiState.error,
                    reviews = uiState.productReviews,
                    isReviewsLoading = uiState.isReviewsLoading,
                    onSubmitReview = { rating, comment ->
                        viewModel.submitReview(product.id, rating, comment)
                    },
                    isAdmin = userRole == "admin",
                    onEditProduct = { editedProduct ->
                        viewModel.setSelectedProduct(null)
                        onNavigateToAdminInventory()
                    },
                    onOpenSizeFinder = { showSizeQuiz = true },
                    onJoinWaitlist = { viewModel.joinProductWaitlist(product.id) },
                    isFittingRequested = uiState.isFittingRequested,
                    onFittingRequestedChange = { viewModel.setFittingRequested(it) },
                    fittingDate = uiState.fittingDate,
                    fittingSlot = uiState.fittingSlot,
                    onFittingAppointmentSelected = { date, slot -> viewModel.setFittingAppointment(date, slot) },
                    currency = uiState.selectedCurrency,
                    exchangeRates = uiState.exchangeRates,
                    recommendedSize = uiState.recommendedSize
                )
            }
        }
    }
}

@Composable
fun ProductDetailContent(
    product: Product,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    selectedSize: String?,
    onSizeSelected: (String) -> Unit,
    selectedColor: ProductColor?,
    onColorSelected: (ProductColor) -> Unit,
    onAddToCart: () -> Unit,
    error: String? = null,
    reviews: List<Map<String, Any>> = emptyList(),
    isReviewsLoading: Boolean = false,
    isEligibleToReview: Boolean = false,
    onSubmitReview: (Int, String) -> Unit = { _, _ -> },
    isAdmin: Boolean = false,
    onEditProduct: (Product) -> Unit = {},
    onOpenSizeFinder: () -> Unit = {},
    onJoinWaitlist: () -> Unit = {},
    onChatWithVendor: (String) -> Unit = {},
    isFittingRequested: Boolean = false,
    onFittingRequestedChange: (Boolean) -> Unit = {},
    fittingDate: String? = null,
    fittingSlot: String? = null,
    onFittingAppointmentSelected: (String, String) -> Unit = { _, _ -> },
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap(),
    recommendedSize: String? = null
) {
    var showReviewDialog by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(Slate50, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (product.images.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { product.images.size })
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        AsyncImage(
                            model = product.images[page],
                            contentDescription = "${product.name} - Image ${page + 1}",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    if (product.images.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(product.images.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Brand600 else Slate300
                                Box(
                                    modifier = Modifier
                                        .size(if (pagerState.currentPage == iteration) 12.dp else 6.dp, 6.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.size(80.dp).background(Brand50, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(imageVector = when(product.category) { "Equipment" -> Icons.Default.MedicalServices; "Theatre Shoes" -> Icons.Default.IceSkating; else -> Icons.Default.Checkroom }, contentDescription = null, tint = Brand600, modifier = Modifier.size(40.dp))
                }
            }
            
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)) {
                Icon(imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (isFavorite) Color(0xFFF43F5E) else Slate300)
            }

            if (isAdmin) {
                IconButton(onClick = { onEditProduct(product) }, modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Product", tint = Brand600)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${product.gender} • ${product.category}".uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand600, letterSpacing = 1.sp)
                Text(product.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Slate900, lineHeight = 30.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(AppUtils.formatCurrency(product.priceKes.toDouble(), currency, exchangeRates), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Brand600)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Text(product.description, fontSize = 14.sp, color = Slate600, lineHeight = 22.sp)
        
        Spacer(Modifier.height(24.dp))
        if (product.availableSizes.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Select Size", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                if (recommendedSize == null) {
                    TextButton(onClick = onOpenSizeFinder) {
                        Icon(Icons.Default.Straighten, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Size Finder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            if (recommendedSize != null) {
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Brand600, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recommended for you: $recommendedSize", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand700)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                product.availableSizes.forEach { size ->
                    val isSelected = size == selectedSize
                    val isRecommended = size == recommendedSize
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Brand600 else if (isRecommended) Color(0xFFEFF6FF) else Slate50)
                            .border(1.dp, if (isSelected) Brand600 else if (isRecommended) Brand200 else Slate200, RoundedCornerShape(8.dp))
                            .clickable { onSizeSelected(size) }, 
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = size, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Slate700)
                            if (isRecommended && !isSelected) {
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Brand600))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onAddToCart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Brand600)) {
            Icon(Icons.Default.ShoppingCart, null)
            Spacer(Modifier.width(12.dp))
            Text("Add to Cart", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        if (!isAdmin && product.vendorId != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onChatWithVendor(product.vendorId) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Brand600),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand600)
            ) {
                Icon(Icons.Default.ChatBubbleOutline, null)
                Spacer(Modifier.width(12.dp))
                Text("Chat with Vendor", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun HomeHeader(
    userRole: String,
    userName: String,
    greeting: String,
    profilePicture: String? = null,
    unreadNotificationsCount: Int,
    unreadMessagesCount: Int,
    onNotificationsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = greeting, fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
            Text(text = when(userRole) { "vendor" -> "$userName (Vendor)"; "admin" -> "$userName (Admin)"; "professional" -> "$userName (Pro)"; else -> userName }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderIconButton(icon = Icons.Outlined.Notifications, badgeCount = unreadNotificationsCount, onClick = onNotificationsClick)
            HeaderIconButton(icon = Icons.Default.ChatBubbleOutline, badgeCount = unreadMessagesCount, onClick = onMessagesClick)
            Surface(onClick = onProfileClick, modifier = Modifier.size(46.dp), shape = CircleShape, color = Brand100, border = BorderStroke(2.dp, Color.White), shadowElevation = 2.dp) {
                Box(contentAlignment = Alignment.Center) {
                    if (!profilePicture.isNullOrEmpty()) { AsyncImage(model = profilePicture, contentDescription = "Profile Picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } else { Text(text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "U", color = Brand600, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
fun HeaderIconButton(icon: ImageVector, badgeCount: Int, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(top = 2.dp, end = 4.dp)) {
        Surface(onClick = onClick, modifier = Modifier.size(46.dp), shape = CircleShape, color = Color.White, border = BorderStroke(1.2.dp, Slate100), shadowElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = Slate700) }
        }
        if (badgeCount > 0) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).sizeIn(minWidth = 18.dp, minHeight = 18.dp), color = Color(0xFFEF4444), shape = CircleShape, border = BorderStroke(1.5.dp, Color.White)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) { Text(text = if (badgeCount > 99) "99+" else badgeCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp) }
            }
        }
    }
}

@Composable
fun SearchBar(query: String, userRole: String = "student", onQueryChange: (String) -> Unit, onSearchAction: () -> Unit = {}, onFilterClick: () -> Unit = {}) {
    OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), placeholder = { Text("Search products...", fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, unfocusedBorderColor = Slate100, focusedBorderColor = Brand600), singleLine = true)
}

@Composable
fun CategorySelector(categories: List<String>, activeCat: String, onCategorySelected: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            val isSelected = category == activeCat
            Surface(onClick = { onCategorySelected(category) }, shape = RoundedCornerShape(12.dp), color = if (isSelected) Brand600 else Color.White, border = BorderStroke(1.dp, if (isSelected) Brand600 else Slate200)) {
                Text(text = category, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = if (isSelected) Color.White else Slate600, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VendorCommandCenter(revenue: Double, pendingOrders: Int, lowStock: Int, onAnalyticsClick: () -> Unit) {
    Surface(
        onClick = onAnalyticsClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Brand600,
        shadowElevation = 8.dp
    ) {
        Box {
            // Decoration
            Icon(Icons.Default.TrendingUp, null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(150.dp).align(Alignment.CenterEnd).offset(x = 30.dp))
            
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Total Net Earnings", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("KSh ${String.format(Locale.US, "%,.0f", revenue)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VendorStatMiniCard(
                        label = "Pending",
                        value = pendingOrders.toString(),
                        icon = Icons.Default.Inventory,
                        modifier = Modifier.weight(1f)
                    )
                    VendorStatMiniCard(
                        label = "Low Stock",
                        value = lowStock.toString(),
                        icon = Icons.Default.ErrorOutline,
                        modifier = Modifier.weight(1f)
                    )
                    VendorStatMiniCard(
                        label = "Live",
                        value = "Shop",
                        icon = Icons.Default.Storefront,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun VendorStatMiniCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VendorQuickActions(onInventoryClick: () -> Unit, onOrdersClick: () -> Unit, onMarketingClick: () -> Unit, onMessagesClick: () -> Unit, onCatalogClick: () -> Unit) {
    val actions = listOf(
        Triple("Inventory", Icons.Default.Layers, Color(0xFFEFF6FF) to Color(0xFF3B82F6)),
        Triple("Orders", Icons.Default.ShoppingBag, Color(0xFFF0FDF4) to Color(0xFF22C55E)),
        Triple("Marketing", Icons.Default.Campaign, Color(0xFFFEF3C7) to Color(0xFFD97706)),
        Triple("Messages", Icons.Default.Chat, Color(0xFFF5F3FF) to Color(0xFF8B5CF6)),
        Triple("Catalog", Icons.Default.Visibility, Color(0xFFFDF2F8) to Color(0xFFEC4899))
    )
    
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(actions) { (title, icon, colors) ->
            Surface(
                onClick = { when(title) { "Inventory" -> onInventoryClick(); "Orders" -> onOrdersClick(); "Marketing" -> onMarketingClick(); "Messages" -> onMessagesClick(); else -> onCatalogClick() } },
                modifier = Modifier.width(100.dp).height(100.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                shadowElevation = 2.dp
            ) {
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = colors.first) {
                        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = colors.second, modifier = Modifier.size(20.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
            }
        }
    }
}

@Composable
fun VendorRestrictionAlert(status: String, notes: String?, adminEmail: String, adminPhone: String) {
    val isPending = status == "pending"
    var showSupportDialog by remember { mutableStateOf(false) }

    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = adminEmail,
            adminPhone = adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = if (isPending) "Vendor Verification Inquiry" else "Vendor Account Restriction Appeal"
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable { showSupportDialog = true },
        shape = RoundedCornerShape(20.dp),
        color = if (isPending) Color(0xFFFFFBEB) else Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, if (isPending) Color(0xFFFDE68A) else Color(0xFFFECACA))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isPending) Icons.Default.GppMaybe else Icons.Default.GppBad,
                contentDescription = null,
                tint = if (isPending) Color(0xFFB45309) else Color(0xFFB91C1C)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isPending) "Account Verification Pending" else "Account Restricted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isPending) Color(0xFF92400E) else Color(0xFF991B1B)
                )
                Text(
                    text = notes ?: if (isPending) "We're verifying your business documents. This usually takes 24-48 hours." else "Access limited. Please update your business documents or contact support.",
                    fontSize = 12.sp,
                    color = if (isPending) Color(0xFFB45309) else Color(0xFFB91C1C)
                )
            }
        }
    }
}

@Composable
fun VendorLiveFeed(orders: List<Map<String, Any>>, onOrderClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (orders.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp).background(Slate50, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("No recent orders to show.", color = Slate500, fontSize = 13.sp)
            }
        } else {
            orders.take(5).forEach { order ->
                val orderId = order["order_id"]?.toString() ?: order["id"]?.toString() ?: ""
                val status = order["item_status"]?.toString() ?: order["status"]?.toString() ?: "pending"
                val customerName = order["customer_name"]?.toString() ?: "New Customer"
                val amount = (order["unit_price"] as? Number)?.toDouble() ?: (order["total_amount"] as? Number)?.toDouble() ?: 0.0
                
                Surface(
                    onClick = { onOrderClick(orderId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate100)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Slate50) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingBasket, null, tint = Slate400, modifier = Modifier.size(20.dp)) }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(customerName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text("Order #$orderId", fontSize = 11.sp, color = Slate500)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("KSh ${String.format(Locale.US, "%,.0f", amount)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Brand600)
                            Surface(
                                color = when(status.lowercase()) { "pending" -> Color(0xFFFEF9C3); "delivered" -> Color(0xFFDCFCE7); else -> Color(0xFFEFF6FF) },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(status.uppercase(), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = when(status.lowercase()) { "pending" -> Color(0xFF854D0E); "delivered" -> Color(0xFF166534); else -> Color(0xFF1D4ED8) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VendorStoreStatusBanner(status: String, onToggleStatus: () -> Unit) {
    val isOpen = status != "vacation"
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp), color = if (isOpen) Color(0xFFF0FDF4) else Color(0xFFFEF2F2), border = BorderStroke(1.dp, if (isOpen) Color(0xFFBBF7D0) else Color(0xFFFECACA))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(if (isOpen) Color(0xFF22C55E) else Color(0xFFEF4444), CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(text = if (isOpen) "Your shop is LIVE" else "Your shop is in VACATION MODE", fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = isOpen, onCheckedChange = { onToggleStatus() })
        }
    }
}

@Composable
fun VendorTopSellingRow(bestSellers: List<Map<String, Any>>, onClick: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        items(bestSellers) { item ->
            Surface(modifier = Modifier.width(160.dp).clickable { onClick(item["product_id"].toString()) }, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Slate100), shadowElevation = 2.dp) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AsyncImage(model = item["product_image"], contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(8.dp))
                    Text(item["product_name"].toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${item["sales_count"]} sales", fontSize = 11.sp, color = Slate500)
                }
            }
        }
    }
}

@Composable
fun VendorFinancialsCard(balance: Double, nextPayout: String, onWithdrawClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Slate100), shadowElevation = 2.dp) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Withdrawable Balance", fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Medium)
                Text("KSh ${String.format(Locale.US, "%,.0f", balance)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text("Next Payout: $nextPayout", fontSize = 11.sp, color = Brand600, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onWithdrawClick, colors = ButtonDefaults.buttonColors(containerColor = Brand600), shape = RoundedCornerShape(12.dp)) { Text("Withdraw") }
        }
    }
}





@Composable
fun AdminStats(userCount: Int, pendingVendors: Int, totalRevenue: Double, orderCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Slate900,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "System Overview",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "KSh ${String.format(Locale.US, "%,.0f", totalRevenue)}",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.TrendingUp,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                AdminMasterStatItem(
                    label = "Total Orders",
                    value = orderCount.toString(),
                    icon = Icons.Default.ShoppingBag,
                    modifier = Modifier.weight(1f)
                )
                AdminMasterStatItem(
                    label = "Users",
                    value = userCount.toString(),
                    icon = Icons.Default.People,
                    modifier = Modifier.weight(1f)
                )
                AdminMasterStatItem(
                    label = "Pending",
                    value = pendingVendors.toString(),
                    icon = Icons.Default.Storefront,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "View detailed analytics",
                    color = Brand200,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, tint = Brand200, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun AdminMasterStatItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
fun AdminStatCard(label: String, value: String, icon: ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
            }
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(label, fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AdminSalesTrendsRow(trends: List<Map<String, Any>>) {
    if (trends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(80.dp)
                .background(Slate50, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Gathering sales trend data...", color = Slate500, fontSize = 13.sp)
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(trends) { trend ->
            val dateStr = trend["period"]?.toString() ?: ""
            val revenue = (trend["revenue"] as? Number)?.toDouble() ?: 0.0
            val count = (trend["order_count"] as? Number)?.toInt() ?: 0
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (revenue > 0) Brand100 else Slate100),
                shadowElevation = if (revenue > 0) 2.dp else 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = AppUtils.formatDateShort(dateStr), 
                        fontSize = 10.sp, 
                        color = Slate500,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "KSh ${String.format(Locale.US, "%,.0f", revenue)}", 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = if (revenue > 0) Brand600 else Slate400
                    )
                    Text(
                        text = "$count orders", 
                        fontSize = 9.sp, 
                        color = Slate400,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun AdminInventoryHealthRow(health: Map<String, Any>, onInventoryClick: () -> Unit) {
    val lowStock = (health["low_stock_count"] as? Number)?.toInt() ?: 0
    val totalItems = (health["total_items_count"] as? Number)?.toInt() ?: 0
    val outOfStock = (health["out_of_stock_count"] as? Number)?.toInt() ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InventoryHealthCard(
            label = "Total Items",
            value = totalItems.toString(),
            bgColor = Brand50,
            iconColor = Brand600,
            icon = Icons.Default.Inventory2,
            modifier = Modifier.weight(1f)
        )
        InventoryHealthCard(
            label = "Low Stock",
            value = lowStock.toString(),
            bgColor = Color(0xFFFFF7ED),
            iconColor = Color(0xFFEA580C),
            icon = Icons.Default.Warning,
            modifier = Modifier.weight(1f)
        )
        InventoryHealthCard(
            label = "Out of Stock",
            value = outOfStock.toString(),
            bgColor = Color(0xFFFEF2F2),
            iconColor = Color(0xFFDC2626),
            icon = Icons.Default.Block,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AdminPendingTasks(
    pendingVendors: List<Map<String, Any>>,
    lowStockProducts: List<Product>,
    pendingBanners: List<Map<String, Any>>,
    onApproveVendor: (String) -> Unit,
    onApproveBanner: (String) -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToBanners: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pendingVendors.take(3).forEach { vendor ->
            AdminTaskCard(
                title = "Vendor Approval: ${vendor["full_name"]}",
                subtitle = "New vendor application pending review",
                icon = Icons.Default.Store,
                iconColor = Color(0xFFF59E0B),
                actionLabel = "Review",
                onAction = { onApproveVendor(vendor["id"].toString()) }
            )
        }
        
        pendingBanners.take(2).forEach { banner ->
            AdminTaskCard(
                title = "Banner Approval: ${banner["title"]}",
                subtitle = "Vendor promotion needs verification",
                icon = Icons.Default.Campaign,
                iconColor = Color(0xFF8B5CF6),
                actionLabel = "Review",
                onAction = onNavigateToBanners
            )
        }

        if (lowStockProducts.isNotEmpty()) {
            AdminTaskCard(
                title = "${lowStockProducts.size} Products Low on Stock",
                subtitle = "Inventory levels below threshold",
                icon = Icons.Default.Warning,
                iconColor = Color(0xFFEF4444),
                actionLabel = "Manage",
                onAction = onNavigateToInventory
            )
        }
    }
}

@Composable
fun AdminTaskCard(title: String, subtitle: String, icon: ImageVector, iconColor: Color, actionLabel: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconColor)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text(subtitle, fontSize = 12.sp, color = Slate500)
            }
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Brand600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun MarketingOverview(coupons: List<Map<String, Any>>, banners: List<Map<String, Any>>, onCouponClick: () -> Unit, onBannerClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MarketingSummaryCard(
            title = "Coupons",
            count = coupons.size,
            icon = Icons.Default.ConfirmationNumber,
            color = Color(0xFF10B981),
            modifier = Modifier.weight(1f),
            onClick = onCouponClick
        )
        MarketingSummaryCard(
            title = "Banners",
            count = banners.size,
            icon = Icons.Default.AdUnits,
            color = Color(0xFF3B82F6),
            modifier = Modifier.weight(1f),
            onClick = onBannerClick
        )
    }
}

@Composable
fun MarketingSummaryCard(title: String, count: Int, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("$count", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text(title, fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun AdminActivityList(logs: List<Map<String, Any>>, onSeeAllOrders: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        logs.take(5).forEach { log ->
            val severity = log["severity"]?.toString() ?: "info"
            val icon = when(severity) {
                "error" -> Icons.Default.Error
                "warning" -> Icons.Default.Warning
                else -> Icons.Default.Info
            }
            val iconColor = when(severity) {
                "error" -> Color(0xFFEF4444)
                "warning" -> Color(0xFFF59E0B)
                else -> Color(0xFF3B82F6)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Slate50
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = iconColor)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(log["action"]?.toString() ?: "System Event", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text(log["details"]?.toString() ?: "", fontSize = 11.sp, color = Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun BannerCardsRow(banners: List<Map<String, Any>>, onBannerClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        items(banners) { banner ->
            BannerCard(banner = banner, onClick = { onBannerClick(banner["id"]?.toString() ?: "") })
        }
    }
}

@Composable
fun BannerCard(banner: Map<String, Any>, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(280.dp).height(140.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Brand600,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imageUrl = banner["image_url"]?.toString() ?: banner["image"]?.toString()
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.7f
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )

            Column(modifier = Modifier.padding(16.dp).align(Alignment.BottomStart)) {
                Text(
                    text = banner["tag"]?.toString()?.uppercase() ?: "PROMO",
                    color = Brand200,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = banner["title"]?.toString() ?: "Featured Offer",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = banner["subtitle"]?.toString() ?: "Limited time only",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HeroBanner(
    featuredProduct: Product?,
    banners: List<Map<String, Any>>,
    userRole: String,
    onShopNowClick: (Map<String, Any>?) -> Unit,
    onActionLinkClick: (String) -> Unit,
    onBannerImpression: (String) -> Unit,
    onBannerClick: (String) -> Unit
) {
    val activeBanners = banners.filter { it["active"] == true }
    val pagerState = rememberPagerState(pageCount = { if (activeBanners.isEmpty()) 1 else activeBanners.size })
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brand600)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val banner = activeBanners.getOrNull(page)
            val bannerId = banner?.get("id")?.toString() ?: "default"
            
            LaunchedEffect(bannerId) {
                onBannerImpression(bannerId)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { 
                        onBannerClick(bannerId)
                        val link = banner?.get("action_link")?.toString()
                        if (link != null) onActionLinkClick(link) else onShopNowClick(banner)
                    }
            ) {
                // Background Image
                val imageUrl = banner?.get("image_url")?.toString() ?: banner?.get("image")?.toString()
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.8f
                    )
                }
                
                // Gradient Overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 100f
                            )
                        )
                )

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = banner?.get("tag")?.toString()?.uppercase() ?: "NEW ARRIVAL",
                        color = Brand200,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = banner?.get("title")?.toString() ?: "Premium Nursing Wear",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = banner?.get("subtitle")?.toString() ?: "Experience ultimate comfort and style",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        onClick = { 
                            onBannerClick(bannerId)
                            val link = banner?.get("action_link")?.toString()
                            if (link != null) onActionLinkClick(link) else onShopNowClick(banner)
                        }
                    ) {
                        Text(
                            text = banner?.get("button_text")?.toString() ?: "Shop Now",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Brand600,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // Pager Indicator
        if (activeBanners.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(activeBanners.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == iteration) 12.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
fun SizeFinderCard(onStartQuiz: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(24.dp), color = Brand50, onClick = onStartQuiz) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Straighten, null, tint = Brand600)
            Spacer(Modifier.width(16.dp))
            Column { Text("Size Finder", fontWeight = FontWeight.Bold); Text("Find your perfect fit", fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeQuizBottomSheet(onDismiss: () -> Unit, onComplete: (Map<String, Any>) -> Unit, gender: String) {
    var step by remember { mutableIntStateOf(0) }
    var bust by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Straighten, null, tint = Brand600, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "Find Your Perfect Fit",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            Text(
                "Enter your measurements in inches for a personalized recommendation.",
                fontSize = 14.sp,
                color = Slate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SizeInputField("Bust / Chest", bust, "Measure around the fullest part") { bust = it }
                SizeInputField("Waist", waist, "Measure around your natural waistline") { waist = it }
                SizeInputField("Hips", hips, "Measure around the fullest part of your hips") { hips = it }
            }
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = {
                    onComplete(mapOf("bust" to bust, "waist" to waist, "hips" to hips))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = bust.isNotBlank() && waist.isNotBlank() && hips.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) {
                Text("Get My Size", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SizeInputField(label: String, value: String, hint: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate700, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 4) onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. 36.0") },
            suffix = { Text("in", color = Slate400) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { Text(hint, fontSize = 11.sp) },
            colors = AppUtils.standardOutlinedTextFieldColors()
        )
    }
}

@Composable
private fun FitPreferenceStep(selected: String, onSelect: (String) -> Unit) {}

@Composable
fun NewArrivalsRow(products: List<Product>, onProductClick: (Product) -> Unit, onAddToCart: (Product) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(products) { product ->
            Surface(modifier = Modifier.width(140.dp).clickable { onProductClick(product) }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Slate100)) {
                Column(Modifier.padding(8.dp)) {
                    AsyncImage(model = product.images.firstOrNull(), contentDescription = null, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Text(product.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun BundleDealsRow(bundles: List<com.example.nursewearconnect.model.Bundle>, onBundleClick: (com.example.nursewearconnect.model.Bundle) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(bundles) { bundle ->
            Surface(modifier = Modifier.width(200.dp).clickable { onBundleClick(bundle) }, shape = RoundedCornerShape(12.dp), color = Brand50) {
                Text(bundle.name, Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProductGrid(products: List<Product>, favoriteProductIds: Set<String>, onFavoriteToggle: (Product) -> Unit, onAddToCart: (Product) -> Unit, onProductClick: (Product) -> Unit, isLoading: Boolean, currency: AppUtils.Currency, exchangeRates: Map<String, Double>) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { product ->
                    ProductCard(product, favoriteProductIds.contains(product.id), Modifier.weight(1f), onFavoriteClick = { onFavoriteToggle(product) }, onClick = { onProductClick(product) })
                }
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, isFavorite: Boolean, modifier: Modifier = Modifier, onFavoriteClick: () -> Unit = {}, onClick: () -> Unit = {}) {
    Surface(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Slate100)) {
        Column(Modifier.padding(12.dp)) {
            AsyncImage(model = product.images.firstOrNull(), contentDescription = null, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(8.dp))
            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("KSh ${product.priceKes}", color = Brand600, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: ImageVector, bgColor: Color, iconColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(90.dp), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Slate100)) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Icon(icon, null, tint = iconColor)
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier = modifier) { Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)); Text(value, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(subtitle, fontSize = 12.sp, color = Slate500)
        }
        TextButton(onClick = onSeeAllClick) {
            Text("See All", color = Brand600, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickActions(
    userRole: String,
    onQuickReorderClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onVendorAnalyticsClick: () -> Unit = {},
    onUserLogsClick: () -> Unit = {},
    onAdminUsersClick: () -> Unit = {},
    onAdminVendorsClick: () -> Unit = {},
    onAdminMarketingClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onVendorCatalogClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (userRole == "vendor") {
            QuickActionCard(title = "Analytics", icon = Icons.Default.BarChart, bgColor = Brand50, iconColor = Brand600, modifier = Modifier.width(100.dp), onClick = onVendorAnalyticsClick)
            QuickActionCard(title = "Inventory", icon = Icons.Default.Inventory2, bgColor = Brand50, iconColor = Brand600, modifier = Modifier.width(100.dp), onClick = onFavoritesClick)
            QuickActionCard(title = "Shop Preview", icon = Icons.Default.Visibility, bgColor = Brand50, iconColor = Brand600, modifier = Modifier.width(100.dp), onClick = onVendorCatalogClick)
            QuickActionCard(title = "Marketing", icon = Icons.Default.Campaign, bgColor = Brand50, iconColor = Brand600, modifier = Modifier.width(100.dp), onClick = onAdminMarketingClick)
            QuickActionCard(title = "Messages", icon = Icons.Default.Message, bgColor = Brand50, iconColor = Brand600, modifier = Modifier.width(100.dp), onClick = onQuickReorderClick)
        } else if (userRole == "admin") {
            QuickActionCard(title = "Users", icon = Icons.Default.People, bgColor = Color(0xFFEFF6FF), iconColor = Color(0xFF3B82F6), modifier = Modifier.width(100.dp), onClick = onAdminUsersClick)
            QuickActionCard(title = "Vendors", icon = Icons.Default.Store, bgColor = Color(0xFFF0FDF4), iconColor = Color(0xFF22C55E), modifier = Modifier.width(100.dp), onClick = onAdminVendorsClick)
            QuickActionCard(title = "Marketing", icon = Icons.Default.Campaign, bgColor = Color(0xFFFEF3C7), iconColor = Color(0xFFD97706), modifier = Modifier.width(100.dp), onClick = onAdminMarketingClick)
            QuickActionCard(title = "Reports", icon = Icons.Default.BarChart, bgColor = Color(0xFFF5F3FF), iconColor = Color(0xFF8B5CF6), modifier = Modifier.width(100.dp), onClick = onReportsClick)
            QuickActionCard(title = "Logs", icon = Icons.Default.History, bgColor = Color(0xFFF3F4F6), iconColor = Color(0xFF4B5563), modifier = Modifier.width(100.dp), onClick = onUserLogsClick)
        } else {
            QuickActionCard(title = "Favorites", icon = Icons.Default.Favorite, bgColor = Color(0xFFFDF2F8), iconColor = Color(0xFFF472B6), modifier = Modifier.weight(1f), onClick = onFavoritesClick)
            QuickActionCard(title = "Quick Reorder", icon = Icons.Default.Autorenew, bgColor = Color(0xFFEFF6FF), iconColor = Color(0xFF60A5FA), modifier = Modifier.weight(1f), onClick = onQuickReorderClick)
        }
    }
}
