package com.example.nursewearconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nursewearconnect.data.repository.*
import com.example.nursewearconnect.model.*
import com.example.nursewearconnect.model.CartItem
import com.example.nursewearconnect.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.nursewearconnect.data.repository.TransactionType
import java.io.File

data class HomeUiState(
    val userId: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    val userPhoneNumber: String = "",
    val greeting: String = "Good Morning",
    val unreadNotificationsCount: Int = 0,
    val unreadMessagesCount: Int = 0,
    val categories: List<String> = listOf("All", "Scrubs", "Jackets", "Shoes", "Accessories"),
    val activeCategory: String = "All",
    val searchQuery: String = "",
    val featuredProduct: Product? = null,
    val newArrivals: List<Product> = emptyList(),
    val recommendations: List<Product> = emptyList(),
    val favoriteProductIds: Set<String> = emptySet(),
    val reorderItems: List<Product> = emptyList(),
    val cartItems: List<CartItem> = emptyList(),
    val cartCount: Int = 0,
    val isLoading: Boolean = true,
    val isCartLoading: Boolean = true,
    val showQuickReorder: Boolean = false,
    val showFavorites: Boolean = false,
    val selectedProduct: Product? = null,
    val selectedSize: String? = null,
    val selectedColor: ProductColor? = null,
    val catalogSearchQuery: String = "",
    val catalogSelectedCategory: String = "All",
    val catalogSelectedSubCategory: String? = null,
    val catalogSelectedGender: String = "All",
    val catalogSortOption: CatalogSortOption = CatalogSortOption.NEWEST,
    val catalogMinPrice: Float = 0f,
    val catalogMaxPrice: Float = 20000f,
    val catalogSelectedSizes: Set<String> = emptySet(),
    val catalogSelectedMaterials: Set<String> = emptySet(),
    val userType: UserType = UserType.PROFESSIONAL,
    val userRole: String = "",
    val userStatus: String = "pending",
    val commissionRate: Double = 10.0,
    val rating: Double = 5.0,
    val monthlySalesTarget: Double = 100000.0,
    val statusNotes: String? = null,
    val products: List<Product> = emptyList(),
    val vendorProducts: List<Product> = emptyList(),
    val vendorOrders: List<Map<String, Any>> = emptyList(),
    val allOrders: List<Map<String, Any>> = emptyList(),
    val adminFilteredOrders: List<Map<String, Any>> = emptyList(),
    val isAdminOrdersLoading: Boolean = false,
    val adminOrdersPage: Int = 0,
    val adminOrdersHasMore: Boolean = true,
    val pendingVendors: List<Map<String, Any>> = emptyList(),
    val coupons: List<Map<String, Any>> = emptyList(),
    val banners: List<Map<String, Any>> = emptyList(),
    val systemLogs: List<Map<String, Any>> = emptyList(),
    val adminSalesReport: List<Map<String, Any>> = emptyList(),
    val payouts: List<Map<String, Any>> = emptyList(),
    val userReviews: List<Map<String, Any>> = emptyList(),
    val notifications: List<Map<String, Any>> = emptyList(),
    val messages: List<Map<String, Any>> = emptyList(),
    val productReviews: List<Map<String, Any>> = emptyList(),
    val isReviewsLoading: Boolean = false,
    val addresses: List<Map<String, Any>> = emptyList(),
    val favorites: List<Product> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val suggestedProducts: List<Product> = emptyList(),
    val activeSessions: List<Map<String, Any>> = emptyList(),
    val appliedCoupon: Map<String, Any>? = null,
    val biometricEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val selectedDocumentUrl: String? = null,
    val shippingMethod: String = "Standard",
    val transactionType: TransactionType = TransactionType.ORDER,
    val orderId: String? = null,
    val paystackAuthUrl: String? = null,
    val lastPaystackAuthUrl: String? = null,
    val paystackReference: String? = null,
    val checkoutError: String? = null,
    val showBiometricPrompt: Boolean = false,
    val checkoutLoading: Boolean = false,
    val paymentStatus: String? = null,
    val error: String? = null,
    val profilePicture: String? = null,
    val successMessage: String? = null,
    val isEmailConfirmed: Boolean = true,
    val isVendorVerified: Boolean = false,
    val isEligibleToReview: Boolean = false,
    val orderHistory: Map<String, List<Map<String, Any>>> = emptyMap(),
    val isHistoryLoading: Boolean = false,
    val isGeneratingPdf: Boolean = false,
    val receiptFile: File? = null,
    val digitalReceiptEnabled: Boolean = true,
    val adminEmail: String = Constants.ADMIN_EMAIL,
    val adminPhone: String = Constants.ADMIN_PHONE,
    val inventoryHealth: Map<String, Any> = emptyMap(),
    val demandForecast: Map<String, Any> = emptyMap(),
    val salesTrends: List<Map<String, Any>> = emptyList(),
    val couponPerformance: List<Map<String, Any>> = emptyList(),
    val couponUsageStats: List<Map<String, Any>> = emptyList(),
    val vendorRankings: List<Map<String, Any>> = emptyList(),
    val stockAlerts: List<Map<String, Any>> = emptyList(),
    val loyaltyHistory: List<Map<String, Any>> = emptyList(),
    val loyaltyTiers: List<Map<String, Any>> = emptyList(),
    val userPoints: Int = 0,
    val userTier: String = "bronze",
    val walletBalance: Double = 0.0,
    val isLoyaltyLoading: Boolean = false,
    val flashSales: List<Map<String, Any>> = emptyList(),
    val visualSearchResults: List<Product> = emptyList(),
    val isVisualSearching: Boolean = false,
    val returnRequests: List<Map<String, Any>> = emptyList(),
    val isFittingRequested: Boolean = false,
    val fittingDate: String? = null,
    val fittingSlot: String? = null,
    val fittingFee: Int = 150,
    val lastPurchasedItems: List<CartItem> = emptyList(),
    val subscriptions: List<Map<String, Any>> = emptyList(),
    val referralCode: String? = null,
    val bundles: List<com.example.nursewearconnect.model.Bundle> = emptyList(),
    val selectedCurrency: AppUtils.Currency = AppUtils.Currency.KES,
    val exchangeRates: Map<String, Double> = mapOf(),
    val recommendedSize: String? = null,
    val sizeFinderNote: String? = null,
    // Analytics State
    val vendorRevenue: Double = 0.0,
    val vendorOrderCount: Int = 0,
    val vendorBestSellers: List<Map<String, Any>> = emptyList(),
    val vendorSalesTrends: List<Map<String, Any>> = emptyList(),
    val vendorStockHealth: Map<String, Any> = emptyMap(),
    val prevVendorRevenue: Double = 0.0,
    val prevVendorOrderCount: Int = 0,
    val vendorRevenueBreakdown: List<Map<String, Any>> = emptyList()
)

enum class UserType { STUDENT, PROFESSIONAL }
enum class CatalogSortOption { PRICE_LOW_HIGH, PRICE_HIGH_LOW, RATING, NEWEST }

class HomeViewModel(
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val vendorRepository: VendorRepository,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null

    // Reactive filtered flows
    val filteredNewArrivals: StateFlow<List<Product>> = _uiState
        .map { state ->
            state.newArrivals.filter { product ->
                val matchesSearch = state.searchQuery.isEmpty() || 
                    product.name.contains(state.searchQuery, ignoreCase = true) ||
                    product.category.contains(state.searchQuery, ignoreCase = true)
                val matchesCategory = if (state.activeCategory == "All") true else product.category == state.activeCategory
                matchesSearch && matchesCategory
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredRecommendations: StateFlow<List<Product>> = _uiState
        .map { state ->
            state.recommendations.filter { product ->
                val matchesSearch = state.searchQuery.isEmpty() || product.name.contains(state.searchQuery, ignoreCase = true)
                val matchesCategory = if (state.activeCategory == "All") true else product.category == state.activeCategory
                matchesSearch && matchesCategory
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCatalogProducts: StateFlow<List<Product>> = _uiState
        .map { state ->
            state.products.filter { product ->
                val matchesSearch = state.catalogSearchQuery.isEmpty() || 
                    product.name.contains(state.catalogSearchQuery, ignoreCase = true) ||
                    product.description.contains(state.catalogSearchQuery, ignoreCase = true)
                val matchesCategory = if (state.catalogSelectedCategory == "All") true else product.category == state.catalogSelectedCategory
                val matchesPrice = product.priceKes.toFloat() in state.catalogMinPrice..state.catalogMaxPrice
                val matchesSize = if (state.catalogSelectedSizes.isEmpty()) true else product.availableSizes.any { it in state.catalogSelectedSizes }
                matchesSearch && matchesCategory && matchesPrice && matchesSize
            }.let { filtered ->
                when (state.catalogSortOption) {
                    CatalogSortOption.PRICE_LOW_HIGH -> filtered.sortedBy { it.priceKes }
                    CatalogSortOption.PRICE_HIGH_LOW -> filtered.sortedByDescending { it.priceKes }
                    CatalogSortOption.RATING -> filtered.sortedByDescending { it.rating }
                    CatalogSortOption.NEWEST -> filtered.sortedByDescending { it.id }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _allUsers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val allUsers: StateFlow<List<Map<String, Any>>> = _allUsers

    val filteredLogs: StateFlow<List<Map<String, Any>>> = _uiState
        .map { state ->
            state.systemLogs.filter { log ->
                val matchesSearch = state.searchQuery.isEmpty() || 
                    (log["action"]?.toString()?.contains(state.searchQuery, ignoreCase = true) ?: false) ||
                    (log["details"]?.toString()?.contains(state.searchQuery, ignoreCase = true) ?: false)
                matchesSearch
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeCartFlow()
        observeUserProfileFlow()
        observeProductsFlow()
        observeCategoriesFlow()
        observeAuthState()
        observeAuthUserFlow()
        observePaymentState()
        fetchAdminContact()
        checkBiometricPrompt()
        startRealtimeUpdates()
        
        // Initial load only if not handled by observeAuthState
        if (!authRepository.isLoggedIn.value) {
            loadHomeData(showLoading = false)
        }
    }

    private fun observeAuthState() = viewModelScope.launch { 
        authRepository.isLoggedIn.collect { loggedIn -> 
            if (loggedIn) {
                loadHomeData(showLoading = false)
                startRealtimeUpdates() // Re-setup realtime on login
            } else {
                _uiState.update { it.copy(userId = null, userRole = "", allOrders = emptyList(), vendorOrders = emptyList(), isEmailConfirmed = true) }
            }
        } 
    }

    private fun observeAuthUserFlow() = viewModelScope.launch {
        authRepository.currentUser.collect { user ->
            _uiState.update { it.copy(
                isEmailConfirmed = user?.emailConfirmedAt != null || user?.lastSignInAt != null // Fallback if confirmed_at is null but they are signed in (depends on Supabase config)
            ) }
        }
    }

    fun resendVerificationEmail(email: String? = null) {
        val targetEmail = email ?: _uiState.value.userEmail
        if (targetEmail.isEmpty()) return
        viewModelScope.launch {
            authRepository.resendVerificationEmail(targetEmail)
                .onSuccess { _uiState.update { it.copy(successMessage = "Verification email sent! Please check your inbox.") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    private var profileRealtimeJob: Job? = null
    private var notificationsRealtimeJob: Job? = null
    private var ordersRealtimeJob: Job? = null
    private var vendorOrdersRealtimeJob: Job? = null
    private var vendorProductsRealtimeJob: Job? = null
    private var systemLogsRealtimeJob: Job? = null

    private fun startRealtimeUpdates() {
        val userId = userRepository.getUserId() ?: _uiState.value.userId
        
        // Cancel existing jobs to prevent duplicate listeners
        profileRealtimeJob?.cancel()
        notificationsRealtimeJob?.cancel()
        ordersRealtimeJob?.cancel()
        vendorOrdersRealtimeJob?.cancel()
        vendorProductsRealtimeJob?.cancel()
        systemLogsRealtimeJob?.cancel()

        if (userId != null && userId != "demo_user") {
            profileRealtimeJob = viewModelScope.launch {
                userRepository.getProfileRealtime(userId)
                    .catch { e -> android.util.Log.e("HomeViewModel", "Profile realtime error: ${e.message}") }
                    .collect { userRepository.fetchProfile(userId) }
            }
            
            notificationsRealtimeJob = viewModelScope.launch {
                userRepository.getNotificationsRealtime(userId)
                    .catch { e -> android.util.Log.e("HomeViewModel", "Notifications realtime error: ${e.message}") }
                    .collect { 
                        val notifications = userRepository.getNotifications(userId)
                        _uiState.update { it.copy(notifications = notifications, unreadNotificationsCount = notifications.count { n -> !(n["is_read"] as? Boolean ?: true) }) }
                    }
            }

            ordersRealtimeJob = viewModelScope.launch {
                orderRepository.getUserOrdersRealtime(userId)
                    .catch { e -> android.util.Log.e("HomeViewModel", "Orders realtime error: ${e.message}") }
                    .collect { 
                        orderRepository.getUserOrders("eq.$userId").onSuccess { orders ->
                            _uiState.update { it.copy(allOrders = orders) }
                        }
                    }
            }

            // Role-based Realtime
            val role = _uiState.value.userRole
            if (role == "admin") {
                ordersRealtimeJob = viewModelScope.launch {
                    adminRepository.getOrdersRealtime()
                        .catch { e -> android.util.Log.e("HomeViewModel", "Admin orders realtime error: ${e.message}") }
                        .collect { 
                            fetchAdminOrders(append = false)
                            loadReportsData() // Keep Business Intelligence charts in sync
                        }
                }
                systemLogsRealtimeJob = viewModelScope.launch {
                    adminRepository.getSystemLogsRealtime()
                        .catch { e -> android.util.Log.e("HomeViewModel", "Admin logs realtime error: ${e.message}") }
                        .collect { fetchSystemLogs(append = false) }
                }
            } else if (role == "vendor") {
                vendorOrdersRealtimeJob = viewModelScope.launch {
                    vendorRepository.getVendorOrdersRealtime(userId)
                        .catch { e -> android.util.Log.e("HomeViewModel", "Vendor orders realtime error: ${e.message}") }
                        .collect { 
                            loadVendorData(userId, showLoading = false)
                            loadVendorAnalytics(userId, 30) // Update charts in realtime
                        }
                }
                vendorProductsRealtimeJob = viewModelScope.launch {
                    vendorRepository.getVendorProductsRealtime(userId)
                        .catch { e -> android.util.Log.e("HomeViewModel", "Vendor products realtime error: ${e.message}") }
                        .collect { loadVendorData(userId, showLoading = false) }
                }
            }
        }

        systemLogsRealtimeJob = viewModelScope.launch {
            adminRepository.getSystemLogsRealtime()
                .catch { e -> android.util.Log.e("HomeViewModel", "Logs realtime error: ${e.message}") }
                .collect { 
                    if (_uiState.value.userRole == "admin") loadAdminData(showLoading = false)
                }
        }
    }

    // --- Admin Reports ---

    fun loadAdminReports() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val jobs = listOf(
                launch { adminRepository.getDetailedSalesReport().onSuccess { data -> _uiState.update { it.copy(adminSalesReport = data) } } },
                launch { adminRepository.getSalesTrends().onSuccess { data -> _uiState.update { it.copy(salesTrends = data) } } },
                launch { adminRepository.getInventoryHealth().onSuccess { data -> _uiState.update { it.copy(inventoryHealth = data) } } },
                launch { adminRepository.getDemandForecasting().onSuccess { data -> 
                    val forecastMap = data.associate { it["product_id"].toString() to it }
                    _uiState.update { it.copy(demandForecast = forecastMap) } 
                } },
                launch { adminRepository.getVendorRankings().onSuccess { data -> _uiState.update { it.copy(vendorRankings = data) } } },
                launch { adminRepository.getCouponPerformance().onSuccess { data -> _uiState.update { it.copy(couponPerformance = data) } } }
            )
            jobs.joinAll()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun exportReport(context: android.content.Context, reportName: String, format: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true) }
            val reportData = _uiState.value.adminSalesReport
            val summary = mapOf(
                "Total Revenue" to reportData.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 },
                "Total Commission" to reportData.sumOf { (it["commission_earned"] as? Number)?.toDouble() ?: 0.0 }
            )
            val verificationCode = "NW-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
            val file = if (format.lowercase() == "excel") ExcelGenerator.generateFinancialReport(context, reportName, reportData, summary)
                       else PdfGenerator.generateFinancialReport(context, reportName, reportData, summary, verificationCode)
            
            if (file != null) {
                adminRepository.saveGeneratedReport(reportName.uppercase(), format.uppercase(), file, _uiState.value.userId ?: "system", summary + mapOf("verification_code" to verificationCode))
                    .onSuccess { _uiState.update { it.copy(isGeneratingPdf = false, receiptFile = file, successMessage = "Report saved: ${file.name}") } }
            } else {
                _uiState.update { it.copy(isGeneratingPdf = false, error = "Failed to generate report") }
            }
        }
    }

    fun printCurrentReport(context: android.content.Context, reportName: String) {
        viewModelScope.launch {
            val reportData = _uiState.value.adminSalesReport
            val summary = mapOf("Total Revenue" to reportData.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 })
            val pdfFile = PdfGenerator.generateFinancialReport(context, reportName, reportData, summary)
            if (pdfFile != null) AppUtils.printPdf(context, pdfFile, "NurseWear $reportName")
        }
    }

    // --- Global Actions ---

    fun updateStockLevel(productId: String, newStock: Int) {
        viewModelScope.launch {
            vendorRepository.updateProductStock(productId, newStock).onSuccess {
                loadAdminReports()
                launch { productRepository.refreshProducts() }
            }
        }
    }

    fun exportInventoryToCSV(isAdmin: Boolean = true): String {
        val products = if (isAdmin) _uiState.value.products else _uiState.value.vendorProducts
        if (products.isEmpty()) return ""
        val builder = StringBuilder("ID,Name,Category,Gender,Price (KES),Stock Count,Status,Vendor\n")
        products.forEach { p ->
            val s = if (p.inStock && p.stockCount > 0) "In Stock" else "Out of Stock"
            builder.append("${p.id},\"${p.name}\",\"${p.category}\",${p.gender},${p.priceKes},${p.stockCount},$s,\"${p.vendorName ?: ""}\"\n")
        }
        return builder.toString()
    }

    fun exportOrdersToCSV(isAdmin: Boolean = true): String {
        val orders = if (isAdmin) _uiState.value.allOrders else _uiState.value.vendorOrders
        if (orders.isEmpty()) return ""
        val builder = StringBuilder("Order ID,Date,Customer,Product,Quantity,Total,Status\n")
        orders.forEach { o ->
            val id = o["order_id"]?.toString() ?: o["id"]?.toString() ?: ""
            val date = o["created_at"]?.toString()?.take(10) ?: ""
            val customer = o["customer_name"]?.toString() ?: ""
            val product = o["product_name"]?.toString() ?: ""
            val qty = o["quantity"]?.toString() ?: "1"
            val total = o["unit_price"]?.toString() ?: o["total_amount"]?.toString() ?: "0"
            val status = o["item_status"]?.toString() ?: o["status"]?.toString() ?: "Pending"
            builder.append("\"$id\",$date,\"$customer\",\"$product\",$qty,$total,\"$status\"\n")
        }
        return builder.toString()
    }

    fun loadHomeData(showLoading: Boolean = true) {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            val currentRole = _uiState.value.userRole
            if (showLoading) _uiState.update { it.copy(isLoading = true, greeting = getGreeting()) }
            else _uiState.update { it.copy(greeting = getGreeting()) }
            
            // Background tasks that don't block profile loading
            launch { productRepository.refreshProducts() }
            launch { productRepository.getCategories() }
            launch { loadMarketingData() }
            
            userRepository.getUserId()?.let { userId ->
                if (userId != "demo_user") {
                    userRepository.fetchProfile(userId)
                    
                    val jobs = listOf(
                        launch { 
                            try {
                                val favs = userRepository.getUserFavorites(userId)
                                _uiState.update { it.copy(favoriteProductIds = favs.mapNotNull { f -> f["product_id"]?.toString() }.toSet()) }
                            } catch (e: Exception) {}
                        },
                        launch { 
                            try {
                                val addresses = userRepository.getUserAddresses(userId)
                                _uiState.update { it.copy(addresses = addresses) }
                            } catch (e: Exception) {}
                        },
                        launch { 
                            try {
                                val sessions = userRepository.getActiveSessions(userId)
                                _uiState.update { it.copy(activeSessions = sessions) }
                            } catch (e: Exception) {}
                        },
                        launch { 
                            try {
                                val reviews = userRepository.getUserReviews(userId)
                                _uiState.update { it.copy(userReviews = reviews) }
                            } catch (e: Exception) {}
                        },
                        launch {
                            try {
                                orderRepository.getUserOrders("eq.$userId").onSuccess { orders ->
                                    _uiState.update { it.copy(allOrders = orders) }
                                }
                            } catch (e: Exception) {}
                        }
                    )
                    
                    // Essential role check
                    val rawRole = userRepository.getUserRole() ?: ""
                    val role = rawRole.replace("\"", "").lowercase()
                    _uiState.update { it.copy(userId = userId, userRole = role) }
                    
                    // Only load admin/vendor data if role changed or was empty
                    if (role != currentRole || role.isNotEmpty()) {
                        when (role) {
                            "admin" -> loadAdminData(showLoading)
                            "vendor" -> loadVendorData(userId, showLoading)
                        }
                    }
                    
                    jobs.joinAll()
                }
            }
            if (showLoading) _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadVendorData(vendorId: String, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
            vendorRepository.getVendorProducts(vendorId).onSuccess { p -> _uiState.update { it.copy(vendorProducts = p) } }
            vendorRepository.getVendorOrders(vendorId).onSuccess { o -> 
                // Sort by newest first for "Live Feed"
                val sorted = o.sortedByDescending { it["created_at"]?.toString() ?: "" }
                _uiState.update { it.copy(vendorOrders = sorted) } 
            }
            // Load vendor specific payouts
            adminRepository.getPayouts().onSuccess { p -> 
                val vendorPayouts = p.filter { it["vendor_id"] == vendorId }
                _uiState.update { it.copy(payouts = vendorPayouts) }
            }
            loadVendorAnalytics(vendorId)
            if (showLoading) _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadVendorAnalytics(vendorId: String, days: Int = 30) {
        viewModelScope.launch {
            vendorRepository.getVendorAnalytics(vendorId, days).onSuccess { data ->
                _uiState.update { it.copy(
                    vendorRevenue = (data["revenue"] as? Number)?.toDouble() ?: 0.0,
                    vendorOrderCount = (data["order_count"] as? Number)?.toInt() ?: 0,
                    vendorBestSellers = (data["best_sellers"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList(),
                    vendorSalesTrends = (data["sales_trends"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList(),
                    vendorStockHealth = (data["stock_health"] as? Map<*, *>)?.filterKeys { it is String }?.mapKeys { it.key as String }?.filterValues { it != null }?.mapValues { it.value as Any } ?: emptyMap(),
                    prevVendorRevenue = (data["prev_revenue"] as? Number)?.toDouble() ?: 0.0,
                    prevVendorOrderCount = (data["prev_order_count"] as? Number)?.toInt() ?: 0
                ) }
            }
            vendorRepository.getVendorRevenueBreakdown(vendorId).onSuccess { breakdown ->
                _uiState.update { it.copy(vendorRevenueBreakdown = breakdown) }
            }
        }
    }

    fun loadAdminData(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
            adminRepository.getPendingVendors().onSuccess { v -> _uiState.update { it.copy(pendingVendors = v) } }
            adminRepository.getAllUsers().onSuccess { u -> _allUsers.value = u }
            adminRepository.getPayouts().onSuccess { p -> _uiState.update { it.copy(payouts = p) } }
            adminRepository.getSalesTrends().onSuccess { t -> _uiState.update { it.copy(salesTrends = t) } }
            adminRepository.getInventoryHealth().onSuccess { h -> _uiState.update { it.copy(inventoryHealth = h) } }
            fetchAdminOrders()
            fetchSystemLogs()
            loadAdminReports()
            loadMarketingData()
            if (showLoading) _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun fetchAdminOrders(status: String? = null, startDate: String? = null, endDate: String? = null, searchQuery: String? = null, page: Int = 0, append: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAdminOrdersLoading = true) }
            adminRepository.getAllOrders(status, startDate, endDate, searchQuery, limit = 20, offset = page * 20).onSuccess { o -> 
                _uiState.update { it.copy(
                    allOrders = if (append) it.allOrders + o else o, 
                    adminFilteredOrders = if (append) it.adminFilteredOrders + o else o,
                    isAdminOrdersLoading = false,
                    adminOrdersPage = page,
                    adminOrdersHasMore = o.size >= 20
                ) }
            }.onFailure { e ->
                _uiState.update { it.copy(isAdminOrdersLoading = false, error = e.message ?: "Failed to fetch admin orders") }
            }
        }
    }

    fun fetchOrderDetails(orderId: String, onComplete: (Map<String, Any>?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            orderRepository.getOrderDetails(orderId).onSuccess { order ->
                _uiState.update { it.copy(isLoading = false) }
                onComplete(order)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
                onComplete(null)
            }
        }
    }

    fun fetchSystemLogs(startDate: String? = null, endDate: String? = null, page: Int = 0, append: Boolean = false) {
        viewModelScope.launch { adminRepository.getSystemLogs(startDate, endDate).onSuccess { l -> 
            _uiState.update { it.copy(systemLogs = if (append) it.systemLogs + l else l) }
        } }
    }

    fun clearSystemLogs() {
        viewModelScope.launch { adminRepository.clearSystemLogs().onSuccess { _uiState.update { it.copy(systemLogs = emptyList()) } } }
    }

    fun loadMarketingData(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
            productRepository.getCoupons().onSuccess { c -> _uiState.update { it.copy(coupons = c) } }
            productRepository.getBanners().onSuccess { b -> _uiState.update { it.copy(banners = b) } }
            if (showLoading) _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadAdminMarketingData() = loadMarketingData()

    fun approveVendor(vendorId: String) {
        viewModelScope.launch { 
            // Optimistic update
            _uiState.update { state ->
                state.copy(pendingVendors = state.pendingVendors.filter { it["id"]?.toString() != vendorId })
            }
            adminRepository.approveVendor(vendorId, _uiState.value.userId ?: "system").onSuccess { 
                loadAdminData() 
            }.onFailure { 
                loadAdminData() // Revert on failure
            }
        }
    }

    fun rejectVendor(vendorId: String, reason: String? = null) {
        viewModelScope.launch { 
            // Optimistic update
            _uiState.update { state ->
                state.copy(pendingVendors = state.pendingVendors.filter { it["id"]?.toString() != vendorId })
            }
            adminRepository.rejectVendor(vendorId, _uiState.value.userId ?: "system", reason).onSuccess { 
                loadAdminData() 
            }.onFailure {
                loadAdminData() // Revert on failure
            }
        }
    }

    fun requestVendorCorrections(vendorId: String, notes: String) {
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { state ->
                state.copy(pendingVendors = state.pendingVendors.filter { it["id"]?.toString() != vendorId })
            }
            adminRepository.requestVendorCorrections(vendorId, _uiState.value.userId ?: "system", notes).onSuccess {
                loadAdminData()
            }.onFailure {
                loadAdminData()
            }
        }
    }

    fun updateUserStatus(userId: String, status: String) {
        viewModelScope.launch { adminRepository.updateUserStatus(userId, status, _uiState.value.userId ?: "system").onSuccess { loadAdminData() } }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch { adminRepository.deleteUser(userId).onSuccess { loadAdminData() } }
    }

    fun bulkUpdateOrderStatus(orderIds: List<String>, status: String) {
        viewModelScope.launch { 
            // Optimistic update
            _uiState.update { state ->
                val updatedOrders = state.allOrders.map { order ->
                    if (orderIds.contains(order["id"]?.toString())) {
                        order.toMutableMap().apply { this["status"] = status }
                    } else order
                }
                state.copy(allOrders = updatedOrders)
            }
            adminRepository.bulkUpdateOrderStatus(orderIds, status, _uiState.value.userId ?: "system").onSuccess { 
                fetchAdminOrders() 
            }.onFailure {
                fetchAdminOrders() // Revert on failure
            }
        }
    }

    fun createPayout(vendorId: String, amount: Int) {
        viewModelScope.launch { adminRepository.createPayout(vendorId, amount, _uiState.value.userId ?: "system").onSuccess { loadAdminData() } }
    }

    fun updatePayoutStatus(payoutId: String, status: String, reference: String? = null) {
        viewModelScope.launch { adminRepository.updatePayoutStatus(payoutId, status, reference, _uiState.value.userId ?: "system").onSuccess { loadAdminData() } }
    }

    fun processPayout(payoutId: String) {
        viewModelScope.launch { adminRepository.processPaystackPayout(payoutId, _uiState.value.userId ?: "system").onSuccess { loadAdminData() } }
    }

    fun processAdminRefund(orderId: String, amount: Any?, reason: String = "Admin Refund") {
        viewModelScope.launch { 
            val amountDouble = when(amount) {
                is Number -> amount.toDouble()
                is String -> amount.toDoubleOrNull()
                else -> null
            }
            adminRepository.processPaystackRefund(orderId, amountDouble, reason, _uiState.value.userId ?: "system").onSuccess { fetchAdminOrders() } 
        }
    }

    fun processPaystackRefund(orderId: String, amount: Any?, reason: String = "Admin Refund") = processAdminRefund(orderId, amount, reason)

    fun toggleVendorStatus() {
        val currentStatus = _uiState.value.userStatus
        val newStatus = if (currentStatus == "active") "vacation" else "active"
        updateProfile(ProfileUpdateRequest(status = newStatus))
    }

    fun updateProfile(request: ProfileUpdateRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.value.userId?.let { userId ->
                userRepository.updateProfile(userId, request)
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, successMessage = "Profile updated successfully") }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
            } ?: run {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.value.userId?.let { userId ->
                userRepository.uploadImage(userId, bytes, "avatars")
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, successMessage = "Avatar updated successfully") }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
            } ?: run {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logout() { viewModelScope.launch { authRepository.logout(); userRepository.logout(); _uiState.update { it.copy(userId = null, userRole = "") } } }

    fun addToCart(product: Product, quantity: Int, size: String?): Boolean {
        if (size == null && product.availableSizes.isNotEmpty()) { _uiState.update { it.copy(error = "Size required") }; return false }
        cartRepository.addToCart(CartItem(product, size ?: "One Size", product.availableColors.firstOrNull(), quantity))
        return true
    }

    fun removeFromCart(item: CartItem) = cartRepository.removeFromCart(item)
    fun updateCartItemQuantity(item: CartItem, q: Int) = cartRepository.updateQuantity(item, q)
    fun clearCart() = cartRepository.clearCart()

    fun applyCoupon(code: String) {
        viewModelScope.launch { productRepository.getCoupons(code).onSuccess { c -> if (c.isNotEmpty()) _uiState.update { it.copy(appliedCoupon = c.first()) } } }
    }

    fun removeCoupon() { _uiState.update { it.copy(appliedCoupon = null) } }

    fun checkout(
        userId: String? = null, 
        totalAmount: Double? = null, 
        address: String? = null, 
        addressId: String? = null, 
        shippingMethod: String = "Standard",
        paymentMethod: String = "Paystack",
        phoneNumber: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(checkoutLoading = true, checkoutError = null) }
            val breakdown = getPriceBreakdown()
            val finalUserId = userId ?: _uiState.value.userId ?: return@launch
            val finalAmount = totalAmount ?: breakdown.finalTotal.toDouble()
            val finalAddress = address ?: "Default Address"
            
            val currentItems = _uiState.value.cartItems.toList()
            _uiState.update { it.copy(lastPurchasedItems = currentItems) }

            // Level 42 Fix: Deferred Order Creation (Place order ONLY after payment verification)
            when (paymentMethod) {
                "Paystack", "M-Pesa" -> {
                    val vendorId = currentItems.firstOrNull()?.product?.vendorId ?: "admin"
                    @Suppress("UNCHECKED_CAST")
                    val checkoutPayload = mapOf(
                        "cart_items" to currentItems.map { 
                            mapOf(
                                "product_id" to it.product.id,
                                "quantity" to it.quantity,
                                "unit_price" to it.product.priceKes,
                                "size" to it.size,
                                "color" to it.color?.name,
                                "embroidery_name" to it.embroideryName
                            )
                        },
                        "shipping_address" to finalAddress,
                        "shipping_method" to _uiState.value.shippingMethod,
                        "discount_amount" to breakdown.discountAmount.toDouble(),
                        "total_amount" to breakdown.subtotal.toDouble() + breakdown.embroideryTotal.toDouble(),
                        "final_amount" to finalAmount,
                        "vendor_id" to (if (vendorId == "admin" || vendorId.isBlank()) null else vendorId),
                        "is_fitting_service" to _uiState.value.isFittingRequested,
                        "digital_receipt_enabled" to _uiState.value.digitalReceiptEnabled
                    ).filterValues { it != null } as Map<String, Any>

                    if (paymentMethod == "Paystack") {
                        setPaymentStatus("Initializing Secure Payment...")
                        _uiState.update { it.copy(transactionType = TransactionType.ORDER) }
                        paymentRepository.initializePaystackPayment(
                            orderId = null, // Deferred creation
                            email = _uiState.value.userEmail,
                            amount = finalAmount,
                            userId = finalUserId,
                            checkoutPayload = checkoutPayload
                        )
                    } else {
                        setPaymentStatus("Requesting M-Pesa STK Push...")
                        _uiState.update { it.copy(transactionType = TransactionType.ORDER) }
                        paymentRepository.initiateMpesaPayment(
                            orderId = null, // Deferred creation
                            phoneNumber = phoneNumber ?: _uiState.value.userPhoneNumber,
                            amount = finalAmount,
                            email = _uiState.value.userEmail,
                            userId = finalUserId,
                            checkoutPayload = checkoutPayload
                        )
                    }
                }
                "Wallet" -> {
                    // Wallet still needs the order created first to have something to pay for
                    val result = orderRepository.placeOrder(
                        userId = finalUserId, 
                        cartItems = currentItems, 
                        totalAmount = finalAmount, 
                        shippingAddress = finalAddress, 
                        addressId = addressId,
                        discountAmount = breakdown.discountAmount.toDouble(),
                        digitalReceiptEnabled = _uiState.value.digitalReceiptEnabled,
                        fittingRequested = _uiState.value.isFittingRequested,
                        fittingDate = _uiState.value.fittingDate,
                        fittingSlot = _uiState.value.fittingSlot
                    )

                    if (result is OrderResult.Success) {
                        _uiState.update { it.copy(orderId = result.orderId, transactionType = TransactionType.ORDER) }
                        setPaymentStatus("Processing Wallet Payment...")
                        paymentRepository.processWalletPayment(
                            orderId = result.orderId,
                            userId = finalUserId,
                            amount = finalAmount
                        )
                    } else if (result is OrderResult.Error) {
                        _uiState.update { it.copy(checkoutError = result.message, error = result.message, checkoutLoading = false) }
                    }
                }
            }
            
            _uiState.update { it.copy(checkoutLoading = false) }
        }
    }

    fun loadOrderStatusHistory(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoryLoading = true) }
            orderRepository.getOrderStatusHistory(orderId).onSuccess { h ->
                val m = _uiState.value.orderHistory.toMutableMap()
                m[orderId] = h
                _uiState.update { it.copy(orderHistory = m, isHistoryLoading = false) }
            }
        }
    }

    fun getPriceBreakdown(): PriceBreakdown {
        val s = _uiState.value
        val itemsSubtotal = s.cartItems.sumOf { it.product.priceKes * it.quantity }
        
        // Embroidery logic: KES 250 per item with embroidery
        val embroideryTotal = s.cartItems.filter { it.embroideryName != null }.sumOf { 250 * it.quantity }
        
        val totalBeforeDiscount = itemsSubtotal + embroideryTotal
        
        val dRate = if (s.userType == UserType.STUDENT) 0.2 else 0.1
        val discount = (itemsSubtotal * dRate).toInt()
        
        val taxableAmount = itemsSubtotal + embroideryTotal - discount
        val tax = (taxableAmount * 0.16).toInt()
        
        val shipping = if (s.shippingMethod == "Express") 200 else 0
        
        val fittingFee = if (s.isFittingRequested) s.fittingFee else 0
        
        val finalTotal = taxableAmount + tax + shipping + fittingFee
        
        return PriceBreakdown(
            subtotal = totalBeforeDiscount.toInt(),
            itemsSubtotal = itemsSubtotal.toInt(),
            embroideryTotal = embroideryTotal,
            discountAmount = discount,
            discountLabel = if (s.userType == UserType.STUDENT) "Student Discount (20%)" else "Professional Discount (10%)",
            tax = tax,
            shippingCost = shipping,
            fittingFee = fittingFee,
            finalTotal = finalTotal.toInt()
        )
    }

    fun setCatalogSearchQuery(q: String) = _uiState.update { it.copy(catalogSearchQuery = q) }
    fun onCategorySelected(c: String) = _uiState.update { it.copy(activeCategory = c) }
    fun onSearchQueryChanged(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun clearError() = _uiState.update { it.copy(error = null, successMessage = null, checkoutError = null) }
    private fun getGreeting() = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) { in 0..11 -> "Good Morning"; in 12..16 -> "Good Afternoon"; else -> "Good Evening" }

    fun setSelectedDocumentUrl(u: String?) = _uiState.update { it.copy(selectedDocumentUrl = u) }
    fun initiatePaystackPayment(orderId: String, email: String, amount: Double) { 
        viewModelScope.launch { 
            _uiState.update { it.copy(transactionType = TransactionType.ORDER) }
            paymentRepository.initializePaystackPayment(orderId, email, amount) 
        } 
    }
    fun clearPaystackUrl() = _uiState.update { it.copy(paystackAuthUrl = null) }
    fun resetCheckoutState() = _uiState.update { it.copy(orderId = null, paystackAuthUrl = null, lastPaystackAuthUrl = null, checkoutError = null, paymentStatus = null) }
    fun setDigitalReceiptEnabled(e: Boolean) = _uiState.update { it.copy(digitalReceiptEnabled = e) }
    fun setPaymentStatus(s: String?) = _uiState.update { it.copy(paymentStatus = s) }
    fun setFittingRequested(r: Boolean) = _uiState.update { it.copy(isFittingRequested = r) }
    fun setShippingMethod(m: String) = _uiState.update { it.copy(shippingMethod = m) }

    fun topUpWallet(amount: Double, phoneNumber: String, method: String = "M-Pesa") {
        viewModelScope.launch {
            val userId = _uiState.value.userId ?: return@launch
            val email = _uiState.value.userEmail
            
            _uiState.update { it.copy(transactionType = TransactionType.WALLET_TOPUP) }
            
            if (method == "M-Pesa") {
                setPaymentStatus("Requesting M-Pesa STK Push...")
                paymentRepository.initiateWalletTopUp(
                    userId = userId,
                    phoneNumber = phoneNumber,
                    amount = amount,
                    email = email
                )
            } else {
                setPaymentStatus("Initializing Paystack Payment...")
                paymentRepository.initializePaystackWalletTopUp(
                    userId = userId,
                    email = email,
                    amount = amount
                )
            }
            loadHomeData(showLoading = false)
        }
    }

    fun requestWithdrawal(amount: Double) {
        viewModelScope.launch {
            val vendorId = _uiState.value.userId ?: return@launch
            val mpesaNumber = _uiState.value.userPhoneNumber
            
            if (mpesaNumber.isEmpty()) {
                _uiState.update { it.copy(error = "M-Pesa number not found in profile. Please update your profile.") }
                return@launch
            }

            if (amount <= 0 || amount > _uiState.value.walletBalance) {
                _uiState.update { it.copy(error = "Invalid withdrawal amount or insufficient balance") }
                return@launch
            }
            
            _uiState.update { it.copy(isLoading = true) }
            
            // Create payout record. The admin will later process this via processPaystackPayout
            adminRepository.createPayout(vendorId, amount.toInt(), vendorId, mpesaNumber).onSuccess {
                loadVendorData(vendorId)
                _uiState.update { it.copy(
                    successMessage = "Withdrawal of KSh $amount to $mpesaNumber initiated successfully!", 
                    isLoading = false 
                ) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Withdrawal failed: ${e.message}", isLoading = false) }
            }
        }
    }
    fun quickReorder(product: Product) = addToCart(product, 1, product.availableSizes.firstOrNull())

    fun loadLiveUsers() { viewModelScope.launch { adminRepository.getLiveUsers() } }
    fun sendMessageToUser(id: String, msg: String) { /* impl */ }
    fun addUser(profile: Map<String, Any>) { viewModelScope.launch { adminRepository.createUser(profile) } }
    fun scheduleAutomatedPayouts() { /* impl */ }
    fun exportFinancialReportPDF(c: android.content.Context, reportName: String = "Financial", format: String = "PDF") {
        if (_uiState.value.userRole == "vendor") {
            exportVendorPerformanceReport(c, format)
            return
        }
        
        if (format.uppercase() == "CSV" || format.uppercase() == "EXCEL") {
            val reportData = if (reportName == "Sales") _uiState.value.adminSalesReport else _uiState.value.systemLogs
            val summary = if (reportName == "Sales") mapOf("Total" to reportData.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 }) else emptyMap()
            
            val file = ExcelGenerator.generateFinancialReport(c, reportName, reportData, summary)
            if (file != null) {
                viewModelScope.launch {
                    productRepository.uploadImage("reports", "excel/${file.name}", file.readBytes()).onSuccess { url ->
                        _uiState.update { it.copy(successMessage = "Report Ready") }
                        // Use exportAndShareData for CSV/Excel instead of printPdf
                        AppUtils.exportAndShareData(c, file.readText(), file.name)
                    }
                }
            }
        } else {
            viewModelScope.launch {
                _uiState.update { it.copy(isGeneratingPdf = true) }
                val reportData = _uiState.value.adminSalesReport
                val summary = mapOf("Total Revenue" to reportData.sumOf { (it["total_amount"] as? Number)?.toDouble() ?: 0.0 })
                val verificationUrl = "https://trpsejzasbfqlshrbbae.supabase.co/storage/v1/object/public/reports/verify_${System.currentTimeMillis()}.pdf"
                val file = PdfGenerator.generateFinancialReport(c, reportName, reportData, summary, verificationUrl)
                if (file != null) {
                    productRepository.uploadImage("reports", "pdf/${file.name}", file.readBytes()).onSuccess { url ->
                        _uiState.update { it.copy(isGeneratingPdf = false, successMessage = "Report Ready") }
                        AppUtils.printPdf(c, file, reportName)
                    }
                } else {
                    _uiState.update { it.copy(isGeneratingPdf = false, error = "Failed to generate report") }
                }
            }
        }
    }

    fun exportVendorPerformanceReport(c: android.content.Context, format: String = "PDF") {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true) }
            val state = _uiState.value
            val reportName = "Vendor_Performance"
            
            // Prepare data for the report
            val reportData = state.vendorOrders.map { order ->
                mapOf(
                    "created_at" to (order["created_at"] ?: ""),
                    "id" to (order["order_id"] ?: order["id"] ?: "N/A"),
                    "vendor_name" to state.userName,
                    "amount" to (order["unit_price"] ?: order["total_amount"] ?: 0.0)
                )
            }
            
            val netEarnings = state.vendorRevenue * (1 - (state.commissionRate / 100.0))
            val summary = mapOf(
                "Gross Revenue" to state.vendorRevenue,
                "Commission (${state.commissionRate}%)" to (state.vendorRevenue * (state.commissionRate / 100.0)),
                "Net Earnings" to netEarnings,
                "Total Orders" to state.vendorOrderCount.toDouble()
            )
            
            val verificationCode = "VN-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
            
            val isExcel = format.uppercase() == "CSV" || format.uppercase() == "EXCEL"
            val file = if (isExcel) {
                ExcelGenerator.generateFinancialReport(c, reportName, reportData, summary)
            } else {
                PdfGenerator.generateFinancialReport(c, reportName, reportData, summary, "https://nursewear.connect/verify/$verificationCode")
            }
            
            if (file != null) {
                // Log report generation
                vendorRepository.getVendorAnalytics(state.userId ?: "system").onSuccess {
                    adminRepository.saveGeneratedReport(reportName.uppercase(), format.uppercase(), file, state.userId ?: "system", summary + mapOf("verification_code" to verificationCode))
                }
                
                _uiState.update { it.copy(isGeneratingPdf = false, successMessage = "Report Generated") }
                
                if (isExcel) {
                    AppUtils.exportAndShareData(c, file.readText(), file.name)
                } else {
                    AppUtils.printPdf(c, file, reportName)
                }
            } else {
                _uiState.update { it.copy(isGeneratingPdf = false, error = "Failed to generate report") }
            }
        }
    }

    fun exportLogsToCSV(c: android.content.Context) = exportFinancialReportPDF(c, "SystemLogs", "CSV")

    fun generateDigitalReceipt(id: String, context: android.content.Context? = null) {
        val c = context ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true) }
            val order = _uiState.value.allOrders.find { it["id"] == id } ?: return@launch
            val orderItemsRaw = order["order_items"] as? List<Map<String, Any>> ?: emptyList()
            
            // Map raw order items to CartItem for the generator
            val cartItems = orderItemsRaw.map { item ->
                CartItem(
                    product = Product(
                        id = item["product_id"]?.toString() ?: "",
                        name = item["product_name"]?.toString() ?: "Product",
                        priceKes = (item["unit_price"] as? Number)?.toDouble() ?: 0.0,
                        images = listOf(item["product_image"]?.toString() ?: ""),
                        category = "", gender = "", description = "",
                        rating = 0.0, reviewsCount = 0, tag = ""
                    ),
                    size = item["size"]?.toString() ?: "M",
                    color = null,
                    quantity = (item["quantity"] as? Number)?.toInt() ?: 1,
                    embroideryName = item["embroidery_name"]?.toString()
                )
            }
            
            val total = (order["total_amount"] as? Number)?.toInt() ?: 0
            val breakdown = PriceBreakdown(
                subtotal = total,
                itemsSubtotal = total,
                embroideryTotal = 0,
                discountAmount = 0,
                discountLabel = "N/A",
                tax = (total * 0.16).toInt(),
                shippingCost = 0,
                fittingFee = 0,
                finalTotal = total
            )
            
            val verificationUrl = "https://trpsejzasbfqlshrbbae.supabase.co/storage/v1/object/public/receipts/verify_$id.pdf"
            
            val file = PdfGenerator.generateReceipt(
                c, id, cartItems, breakdown, 
                _uiState.value.userName, 
                order["shipping_address"]?.toString() ?: "N/A",
                verificationUrl
            )
            
            if (file != null) {
                productRepository.uploadImage("receipts", "order_$id.pdf", file.readBytes()).onSuccess { url ->
                    _uiState.update { it.copy(isGeneratingPdf = false, receiptFile = file, successMessage = "Receipt Generated & Verified") }
                    // Trigger Email after PDF is in storage
                    val userEmail = _uiState.value.userEmail
                    if (userEmail.isNotEmpty()) {
                        viewModelScope.launch {
                            orderRepository.sendReceiptEmail(id, userEmail)
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isGeneratingPdf = false, error = "Failed to generate receipt") }
            }
        }
    }
    fun reorder(id: String) { /* impl */ }
    fun updateSuggestedProducts(q: String? = null) { /* impl */ }
    fun addToRecentSearches(q: String) { /* impl */ }
    fun setCatalogCategory(c: String) = _uiState.update { it.copy(catalogSelectedCategory = c) }
    fun setCatalogSubCategory(s: String?) = _uiState.update { it.copy(catalogSelectedSubCategory = s) }
    fun resetFilters() { /* impl */ }
    fun clearRecentSearches() { /* impl */ }
    fun setCatalogPriceRange(r: ClosedFloatingPointRange<Float>) = _uiState.update { it.copy(catalogMinPrice = r.start, catalogMaxPrice = r.endInclusive) }
    fun setCatalogGender(g: String) = _uiState.update { it.copy(catalogSelectedGender = g) }
    fun toggleCatalogSize(s: String) { val c = _uiState.value.catalogSelectedSizes; _uiState.update { it.copy(catalogSelectedSizes = if (c.contains(s)) c - s else c + s) } }
    fun toggleCatalogMaterial(m: String) { val c = _uiState.value.catalogSelectedMaterials; _uiState.update { it.copy(catalogSelectedMaterials = if (c.contains(m)) c - m else c + m) } }
    fun trackBannerImpression(id: String) { /* impl */ }
    fun trackBannerClick(id: String) { /* impl */ }
    fun applySizeQuizResult(res: Any?) {
        val data = res as? Map<*, *> ?: return
        val bust = SizeFinder.parseMeasurement(data["bust"])
        val waist = SizeFinder.parseMeasurement(data["waist"])
        val hips = SizeFinder.parseMeasurement(data["hips"])
        val gender = _uiState.value.userType.name // Or get from profile

        val result = SizeFinder.calculateRecommendedSize(gender, bust, waist, hips)
        _uiState.update { it.copy(
            recommendedSize = result.recommendedSize,
            sizeFinderNote = result.fitNote
        ) }
        
        // Persist to profile
        viewModelScope.launch {
            userRepository.updateProfile(
                _uiState.value.userId ?: return@launch,
                ProfileUpdateRequest(measurements = mapOf("bust" to "$bust\"", "waist" to "$waist\"", "hips" to "$hips\""))
            )
        }
    }
    fun setShowQuickReorder(s: Boolean) = _uiState.update { it.copy(showQuickReorder = s) }
    fun setShowFavorites(s: Boolean) = _uiState.update { it.copy(showFavorites = s) }
    fun joinProductWaitlist(id: String) = viewModelScope.launch { productRepository.joinWaitlist(id) }
    fun setFittingAppointment(date: String, slot: String) = _uiState.update { it.copy(fittingDate = date, fittingSlot = slot) }
    fun updateVendorOrderStatus(id: String, s: String) = bulkUpdateOrderStatus(listOf(id), s)
    fun initiateReturnRequest(id: String, r: String) { /* impl */ }
    fun setUserType(t: UserType) {
        _uiState.update { it.copy(userType = t) }
        viewModelScope.launch {
            _uiState.value.userId?.let { userId ->
                val newRole = if (t == UserType.STUDENT) "student" else "professional"
                userRepository.updateProfile(userId, ProfileUpdateRequest(role = newRole))
            }
        }
    }
    fun revokeSession(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.revokeSession(id)
                .onSuccess {
                    _uiState.value.userId?.let { userId ->
                        val sessions = userRepository.getActiveSessions(userId)
                        _uiState.update { it.copy(activeSessions = sessions, isLoading = false, successMessage = "Session revoked") }
                    } ?: _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
    fun setNotificationsEnabled(e: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = e) }
        viewModelScope.launch {
            _uiState.value.userId?.let { userRepository.setNotificationsEnabled(it, e) }
        }
    }
    fun updateSubscriptionStatus(id: String, s: String) = viewModelScope.launch { userRepository.updateSubscription(id, s) }
    fun updateVendorProduct(p: Product, images: List<ByteArray> = emptyList()) {
        viewModelScope.launch {
            val userId = _uiState.value.userId ?: return@launch
            
            // Optimistic Update (for non-image fields)
            _uiState.update { state ->
                val updatedProducts = state.vendorProducts.map { 
                    if (it.id == p.id) p.copy(vendorId = userId) else it 
                }
                state.copy(vendorProducts = updatedProducts)
            }

            _uiState.update { it.copy(isLoading = true) }
            var updatedProduct = p.copy(vendorId = userId)
            
            if (images.isNotEmpty()) {
                val urls = mutableListOf<String>()
                images.forEachIndexed { index, bytes ->
                    val fileName = "product_${userId}_${System.currentTimeMillis()}_$index.jpg"
                    productRepository.uploadImage("product-images", fileName, bytes).onSuccess { url ->
                        urls.add(url)
                    }.onFailure { e ->
                        _uiState.update { it.copy(error = "Image $index upload failed: ${e.message}", isLoading = false) }
                        return@launch
                    }
                }
                updatedProduct = updatedProduct.copy(images = updatedProduct.images + urls)
            }
            
            vendorRepository.updateProduct(updatedProduct)
                .onSuccess {
                    loadVendorData(userId, showLoading = false)
                    productRepository.refreshProducts()
                    _uiState.update { it.copy(successMessage = "Product updated successfully", isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Update failed: ${e.message}", isLoading = false) }
                    loadVendorData(userId, showLoading = false) // Revert
                }
        }
    }

    fun addVendorProduct(p: Product, images: List<ByteArray> = emptyList()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userId = _uiState.value.userId ?: return@launch
            
            var newProduct = p.copy(vendorId = userId)
            
            if (images.isNotEmpty()) {
                val urls = mutableListOf<String>()
                images.forEachIndexed { index, bytes ->
                    val fileName = "product_${userId}_${System.currentTimeMillis()}_$index.jpg"
                    productRepository.uploadImage("product-images", fileName, bytes).onSuccess { url ->
                        urls.add(url)
                    }.onFailure { e ->
                        _uiState.update { it.copy(error = "Image $index upload failed: ${e.message}", isLoading = false) }
                        return@launch
                    }
                }
                newProduct = newProduct.copy(images = urls)
            }
            
            vendorRepository.addProduct(newProduct)
                .onSuccess {
                    loadVendorData(userId, showLoading = false)
                    productRepository.refreshProducts()
                    _uiState.update { it.copy(successMessage = "Product added successfully", isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Add failed: ${e.message}", isLoading = false) }
                }
        }
    }

    fun deleteVendorProduct(id: String) {
        viewModelScope.launch {
            val userId = _uiState.value.userId ?: return@launch
            
            // Optimistic Update
            _uiState.update { state ->
                state.copy(vendorProducts = state.vendorProducts.filter { it.id != id })
            }

            _uiState.update { it.copy(isLoading = true) }
            vendorRepository.deleteProduct(id, userId)
                .onSuccess {
                    loadVendorData(userId, showLoading = false)
                    productRepository.refreshProducts()
                    _uiState.update { it.copy(successMessage = "Product deleted", isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Delete failed: ${e.message}", isLoading = false) }
                    loadVendorData(userId, showLoading = false) // Revert
                }
        }
    }
    fun setCurrency(c: AppUtils.Currency) = _uiState.update { it.copy(selectedCurrency = c) }
    fun toggleEmbroidery(item: CartItem, name: String?) = cartRepository.updateCartItem(item, item.copy(embroideryName = name))
    fun getCurrentUserId(): String? = _uiState.value.userId
    fun toggleFavorite(id: String) { viewModelScope.launch { val userId = _uiState.value.userId ?: return@launch; val current = _uiState.value.favoriteProductIds; val isFav = current.contains(id); userRepository.toggleFavorite(userId, id, !isFav).onSuccess { _uiState.update { it.copy(favoriteProductIds = if (isFav) current - id else current + id) } } } }
    fun toggleFavorite(product: Product) = toggleFavorite(product.id)
    fun setSelectedProduct(p: Product?) = _uiState.update { it.copy(selectedProduct = p, selectedSize = null, selectedColor = null) }
    fun setSelectedSize(s: String?) = _uiState.update { it.copy(selectedSize = s) }
    fun setSelectedColor(c: ProductColor?) = _uiState.update { it.copy(selectedColor = c) }
    fun submitReview(id: String, rating: Int, comment: String) { viewModelScope.launch { userRepository.getUserId()?.let { uid -> productRepository.addReview(id, uid, rating, comment).onSuccess { fetchProductReviews(id) } } } }
    fun dismissBiometricPrompt() = _uiState.update { it.copy(showBiometricPrompt = false) }
    fun setBiometricEnabled(e: Boolean) { userRepository.setBiometricEnabled(e); _uiState.update { it.copy(biometricEnabled = e) } }
    fun verifyPaystackPayment(ref: String) { viewModelScope.launch { paymentRepository.verifyPaystackPayment(ref) } }
    fun verifyEmailFromDeepLink(email: String, token: String, type: String) { viewModelScope.launch { authRepository.verifyEmailToken(email, token, type) } }
    fun updateVendorOrderItemStatus(item_id: String, order_id: String, status: String) { 
        viewModelScope.launch { 
            val userId = _uiState.value.userId
            
            // Optimistic UI Update
            _uiState.update { state ->
                val updatedOrders = state.vendorOrders.map { order ->
                    if (order["order_item_id"]?.toString() == item_id) {
                        order.toMutableMap().apply { this["item_status"] = status }
                    } else order
                }
                state.copy(vendorOrders = updatedOrders)
            }

            vendorRepository.updateOrderItemStatus(item_id, order_id, status, userId)
                .onSuccess { 
                    userId?.let { loadVendorData(it) } 
                    _uiState.update { it.copy(successMessage = "Order status updated to ${status.uppercase()}") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Update failed: ${e.message}") }
                    // Revert on failure
                    userId?.let { loadVendorData(it) }
                }
        } 
    }
    fun updateOrderItemStatus(item_id: String, order_id: String, status: String) = updateVendorOrderItemStatus(item_id, order_id, status)
    fun addCoupon(coupon: Map<String, Any>) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { it.copy(coupons = it.coupons + coupon) }
            productRepository.addCoupon(coupon).onSuccess { 
                loadMarketingData() 
            }.onFailure {
                loadMarketingData() // Revert
            }
        } 
    }
    fun deleteCoupon(id: String) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { it.copy(coupons = it.coupons.filter { it["id"]?.toString() != id }) }
            productRepository.deleteCoupon(id).onSuccess { 
                loadMarketingData() 
            }.onFailure {
                loadMarketingData() // Revert
            }
        } 
    }
    fun addBanner(banner: Map<String, Any>) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { it.copy(banners = it.banners + banner) }
            productRepository.addBanner(banner).onSuccess { 
                loadMarketingData() 
            }.onFailure {
                loadMarketingData() // Revert
            }
        } 
    }
    fun updateBanner(id: String, updates: Map<String, Any>) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { state ->
                val updatedBanners = state.banners.map { 
                    if (it["id"]?.toString() == id) it + updates else it 
                }
                state.copy(banners = updatedBanners)
            }
            productRepository.updateBanner(id, updates).onSuccess { 
                loadMarketingData() 
            }.onFailure {
                loadMarketingData() // Revert
            }
        } 
    }
    fun deleteBanner(id: String) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { it.copy(banners = it.banners.filter { it["id"]?.toString() != id }) }
            productRepository.deleteBanner(id).onSuccess { 
                loadMarketingData() 
            }.onFailure {
                loadMarketingData() // Revert
            }
        } 
    }
    fun approveBanner(id: String) = updateBanner(id, mapOf("status" to "approved", "active" to true))
    fun rejectBanner(id: String, reason: String) = updateBanner(id, mapOf("status" to "rejected", "active" to false, "rejection_reason" to reason))
    fun uploadBannerImage(bytes: ByteArray, onComplete: (String) -> Unit) { viewModelScope.launch { productRepository.uploadImage("banners", "banner_${System.currentTimeMillis()}.jpg", bytes).onSuccess { onComplete(it) } } }
    fun addCategory(name: String, desc: String?) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { it.copy(categories = it.categories + name) }
            productRepository.addCategory(name, desc).onSuccess { 
                loadHomeData(showLoading = false) 
            }.onFailure {
                loadHomeData(showLoading = false) // Revert
            }
        } 
    }
    fun deleteCategory(name: String) { 
        viewModelScope.launch { 
            // Optimistic Update
            _uiState.update { it.copy(categories = it.categories.filter { it != name }) }
            productRepository.deleteCategory(name).onSuccess { 
                loadHomeData(showLoading = false) 
            }.onFailure {
                loadHomeData(showLoading = false) // Revert
            }
        } 
    }
    fun setCatalogSortOption(opt: CatalogSortOption) = _uiState.update { it.copy(catalogSortOption = opt) }

    fun fetchProductReviews(id: String) = viewModelScope.launch { productRepository.getProductReviews(id).onSuccess { r -> _uiState.update { it.copy(productReviews = r) } } }
    fun initiatePayment(orderId: String, phoneNumber: String, amount: Double, email: String? = null) = viewModelScope.launch { paymentRepository.initiateMpesaPayment(orderId, phoneNumber, amount, email) }
    fun checkPaymentStatus(id: String) = viewModelScope.launch { paymentRepository.checkStatus(id) }
    fun clearPaystackAccessCode() { /* impl */ }
    fun exportSalesReportToCSV() = ""
    fun loadProductById(id: String, res: (Product?) -> Unit) { viewModelScope.launch { productRepository.products.first().find { it.id == id }.let(res) } }
    fun loadReportsData() = loadAdminReports()
    fun getUserRepository() = userRepository

    val formattedTotal: String get() = "KSh ${getPriceBreakdown().finalTotal}"

    private fun observeCartFlow() = viewModelScope.launch { cartRepository.cartItems.collect { i -> _uiState.update { it.copy(cartItems = i, cartCount = i.sumOf { it.quantity }, isCartLoading = false) } } }
    
    private fun observeProductsFlow() = viewModelScope.launch {
        productRepository.products.collect { products ->
            _uiState.update { it.copy(
                products = products,
                newArrivals = products.take(10),
                recommendations = if (products.size > 10) products.shuffled().take(10) else products
            ) }
        }
    }

    private fun observeCategoriesFlow() = viewModelScope.launch {
        productRepository.categories.collect { categories ->
            if (categories.isNotEmpty()) {
                _uiState.update { it.copy(
                    categories = listOf("All") + categories.map { it.name }
                ) }
            }
        }
    }

    private fun observeUserProfileFlow() = viewModelScope.launch { 
        userRepository.userProfile.collect { profile -> 
            profile?.let {
                val role = (it["role"]?.toString() ?: "student").replace("\"", "").lowercase()
                
                // Safe wallet balance extraction
                val wallets = it["wallets"] as? List<*>
                val wallet = if (wallets != null && wallets.isNotEmpty()) wallets.firstOrNull() as? Map<*, *> else it["wallets"] as? Map<*, *>
                val balance = try {
                    (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0
                } catch (e: Exception) { 0.0 }
                
                    _uiState.update { s -> s.copy(
                        userName = it["full_name"]?.toString()?.replace("\"", "") ?: "",
                        userEmail = it["email"]?.toString()?.replace("\"", "") ?: "",
                        userPhoneNumber = it["phone_number"]?.toString()?.replace("\"", "") ?: it["mpesa_number"]?.toString()?.replace("\"", "") ?: "",
                        profilePicture = it["avatar_url"]?.toString(),
                        userRole = role,
                        userStatus = (it["status"]?.toString() ?: "active").replace("\"", "").lowercase(),
                        isVendorVerified = (it["is_verified_vendor"] as? Boolean) ?: false,
                        walletBalance = balance,
                        commissionRate = (it["commission_rate"] as? Number)?.toDouble() ?: 10.0,
                    rating = (it["rating"] as? Number)?.toDouble() ?: 5.0,
                    monthlySalesTarget = (it["monthly_sales_target"] as? Number)?.toDouble() ?: 100000.0,
                    userType = if (role == "student") UserType.STUDENT else UserType.PROFESSIONAL,
                    biometricEnabled = (it["biometric_enabled"] as? Boolean) ?: userRepository.isBiometricEnabled(),
                    notificationsEnabled = (it["notifications_enabled"] as? Boolean) ?: true
                ) }

                // Auto-calculate recommended size if measurements exist
                val measurements = it["measurements"] as? Map<*, *>
                if (measurements != null) {
                    val bust = SizeFinder.parseMeasurement(measurements["bust"])
                    val waist = SizeFinder.parseMeasurement(measurements["waist"])
                    val hips = SizeFinder.parseMeasurement(measurements["hips"])
                    if (bust > 0) {
                        val result = SizeFinder.calculateRecommendedSize(role, bust, waist, hips)
                        _uiState.update { s -> s.copy(recommendedSize = result.recommendedSize, sizeFinderNote = result.fitNote) }
                    }
                }
            } 
        } 
    }
    private fun observePaymentState() = viewModelScope.launch {
        paymentRepository.paymentState.collect { s ->
            when (s) {
                is PaymentStatus.Completed -> {
                    _uiState.update { it.copy(
                        successMessage = "Payment successful!",
                        paymentStatus = "Paid: ${s.transactionId}",
                        checkoutLoading = false,
                        paystackAuthUrl = null
                    ) }
                    clearCart()
                    loadHomeData(showLoading = false)
                }
                is PaymentStatus.Success -> {
                    _uiState.update { it.copy(
                        paymentStatus = "M-Pesa STK Sent. Please enter PIN on your phone.",
                        checkoutLoading = false,
                        paystackAuthUrl = null
                    ) }
                    // Background polling for STK Push completion
                    viewModelScope.launch {
                        paymentRepository.pollPaymentStatus(s.checkoutId, type = _uiState.value.transactionType)
                    }
                }
                is PaymentStatus.PaystackInitialized -> {
                    _uiState.update { it.copy(
                        paystackAuthUrl = s.authorizationUrl,
                        lastPaystackAuthUrl = s.authorizationUrl,
                        paystackReference = s.reference,
                        paymentStatus = if (s.isStk) "M-Pesa STK Sent. Please enter PIN on your phone." else "Awaiting Payment...",
                        checkoutLoading = false
                    ) }
                    
                    // Start polling for status if it's an STK push or just to be safe
                    viewModelScope.launch {
                        paymentRepository.pollPaystackStatus(s.reference, type = _uiState.value.transactionType)
                    }
                }
                is PaymentStatus.Error -> {
                    _uiState.update { it.copy(
                        error = s.message,
                        checkoutError = s.message,
                        checkoutLoading = false,
                        paymentStatus = "Failed: ${s.message}",
                        transactionType = s.type
                    ) }
                }
                is PaymentStatus.Loading -> {
                    _uiState.update { it.copy(checkoutLoading = true) }
                }
                else -> {}
            }
        }
    }
    private fun fetchAdminContact() = viewModelScope.launch { userRepository.getAdminContact().onSuccess { c -> _uiState.update { it.copy(adminEmail = c["email"] ?: Constants.ADMIN_EMAIL, adminPhone = c["phone_number"] ?: Constants.ADMIN_PHONE) } } }
    private fun checkBiometricPrompt() { if (!userRepository.isBiometricEnabled() && !userRepository.hasPromptedBiometrics()) _uiState.update { it.copy(showBiometricPrompt = true) } }

    private inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> { if (isSuccess) action(getOrThrow()); return this }
    private inline fun <T> Result<T>.onFailure(action: (Throwable) -> Unit): Result<T> { if (isFailure) action(exceptionOrNull()!!); return this }
}

data class PriceBreakdown(
    val subtotal: Int,
    val itemsSubtotal: Int = 0,
    val embroideryTotal: Int = 0,
    val discountAmount: Int,
    val discountLabel: String,
    val tax: Int,
    val shippingCost: Int,
    val fittingFee: Int = 0,
    val finalTotal: Int
)
