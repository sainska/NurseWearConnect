package com.example.nursewearconnect.ui.viewmodel

import com.example.nursewearconnect.data.repository.*
import com.example.nursewearconnect.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val productRepository = mockk<ProductRepository>(relaxed = true)
    private val cartRepository = mockk<CartRepository>(relaxed = true)
    private val orderRepository = mockk<OrderRepository>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val vendorRepository = mockk<VendorRepository>(relaxed = true)
    private val adminRepository = mockk<AdminRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock default flows
        every { productRepository.products } returns MutableStateFlow<List<Product>>(emptyList()).asStateFlow()
        every { productRepository.categories } returns MutableStateFlow<List<Category>>(emptyList()).asStateFlow()
        every { cartRepository.cartItems } returns MutableStateFlow<List<CartItem>>(emptyList()).asStateFlow()
        every { userRepository.userProfile } returns MutableStateFlow<Map<String, Any>?>(null).asStateFlow()
        every { authRepository.isLoggedIn } returns MutableStateFlow(false).asStateFlow()
        every { paymentRepository.paymentState } returns MutableStateFlow(PaymentStatus.Idle).asStateFlow()

        viewModel = HomeViewModel(
            productRepository,
            cartRepository,
            orderRepository,
            paymentRepository,
            userRepository,
            vendorRepository,
            adminRepository,
            authRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getPriceBreakdown calculates student discount correctly`() = runTest {
        // Given
        val product = Product(
            id = "1",
            name = "Test Scrub",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 5000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 10,
            tag = "NEW",
            images = emptyList(),
            description = "Test Description",
            inStock = true,
            isActive = true
        )
        val cartItems = listOf(CartItem(product, "M", ProductColor("Blue", 0), 2))
        
        val cartItemsFlow = MutableStateFlow(cartItems)
        every { cartRepository.cartItems } returns cartItemsFlow.asStateFlow()
        
        // Ensure userProfile flow is mocked
        every { userRepository.userProfile } returns MutableStateFlow<Map<String, Any>?>(null).asStateFlow()
        every { authRepository.isLoggedIn } returns MutableStateFlow(false).asStateFlow()
        every { paymentRepository.paymentState } returns MutableStateFlow<PaymentStatus>(PaymentStatus.Idle).asStateFlow()
        every { productRepository.products } returns MutableStateFlow<List<Product>>(emptyList()).asStateFlow()
        every { productRepository.categories } returns MutableStateFlow<List<Category>>(emptyList()).asStateFlow()

        viewModel = HomeViewModel(
            productRepository, cartRepository, orderRepository,
            paymentRepository, userRepository, vendorRepository,
            adminRepository, authRepository
        )
        
        viewModel.setUserType(UserType.STUDENT)
        viewModel.setShippingMethod("Standard")
        
        testScheduler.advanceUntilIdle()

        // When
        val breakdown = viewModel.getPriceBreakdown()

        // Then
        assertEquals(10000, breakdown.subtotal)
        assertEquals(2000, breakdown.discountAmount)
        assertEquals(1280, breakdown.tax)
        assertEquals(9280, breakdown.finalTotal)
    }

    @Test
    fun `getPriceBreakdown calculates professional discount correctly`() = runTest {
        // Given
        val product = Product(
            id = "1",
            name = "Test Scrub",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 5000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 10,
            tag = "NEW",
            images = emptyList(),
            description = "Test Description",
            inStock = true,
            isActive = true
        )
        val cartItems = listOf(CartItem(product, "M", ProductColor("Blue", 0), 1))
        
        val cartItemsFlow = MutableStateFlow(cartItems)
        every { cartRepository.cartItems } returns cartItemsFlow.asStateFlow()
        
        every { userRepository.userProfile } returns MutableStateFlow<Map<String, Any>?>(null).asStateFlow()
        every { authRepository.isLoggedIn } returns MutableStateFlow(false).asStateFlow()
        every { paymentRepository.paymentState } returns MutableStateFlow<PaymentStatus>(PaymentStatus.Idle).asStateFlow()
        every { productRepository.products } returns MutableStateFlow<List<Product>>(emptyList()).asStateFlow()
        every { productRepository.categories } returns MutableStateFlow<List<Category>>(emptyList()).asStateFlow()

        viewModel = HomeViewModel(
            productRepository, cartRepository, orderRepository,
            paymentRepository, userRepository, vendorRepository,
            adminRepository, authRepository
        )
        
        viewModel.setUserType(UserType.PROFESSIONAL)
        viewModel.setShippingMethod("Express")
        
        testScheduler.advanceUntilIdle()

        // When
        val breakdown = viewModel.getPriceBreakdown()

        // Then
        assertEquals(5000, breakdown.subtotal)
        assertEquals(500, breakdown.discountAmount)
        assertEquals(720, breakdown.tax)
        assertEquals(5720, breakdown.finalTotal)
    }

    @Test
    fun `addToCart prevents adding if out of stock`() = runTest {
        // Given
        val outOfStockProduct = Product(
            id = "1",
            name = "Test Scrub",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 5000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 0,
            tag = "NEW",
            images = emptyList(),
            description = "Test Description",
            inStock = false,
            isActive = true
        )

        // When
        viewModel.addToCart(outOfStockProduct)

        // Then
        verify(exactly = 0) { cartRepository.addToCart(any()) }
        assertEquals("Sorry, Test Scrub is currently out of stock.", viewModel.uiState.value.error)
    }

    @Test
    fun `addToCart prevents adding if size not selected`() = runTest {
        // Given
        val product = Product(
            id = "1",
            name = "Test Scrub",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 5000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 10,
            tag = "NEW",
            images = emptyList(),
            description = "Test Description",
            inStock = true,
            isActive = true,
            availableSizes = listOf("S", "M", "L")
        )

        // When
        // Ensure selectedSize is null in state
        viewModel.setSelectedSize("") // This sets it to "", which is "selected" but maybe not valid?
        // Actually HomeViewModel check is _uiState.value.selectedSize == null
        // Let's reset the viewModel to ensure fresh state or find a way to set it to null.
        // Looking at HomeViewModel, selectedSize is null by default in HomeUiState.
        
        viewModel.addToCart(product)

        // Then
        verify(exactly = 0) { cartRepository.addToCart(any()) }
        assertEquals("Please select a size for your Test Scrub before adding to cart", viewModel.uiState.value.error)
    }

    @Test
    fun `addToCart prevents adding if exceeds stock`() = runTest {
        // Given
        val product = Product(
            id = "1",
            name = "Test Scrub",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 5000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 2,
            tag = "NEW",
            images = emptyList(),
            description = "Test Description",
            inStock = true,
            isActive = true,
            availableSizes = emptyList() // No size needed
        )
        // Already 2 in cart
        val cartItems = listOf(CartItem(product, "One Size", ProductColor("Blue", 0), 2))
        every { cartRepository.cartItems } returns MutableStateFlow(cartItems).asStateFlow()
        
        every { userRepository.userProfile } returns MutableStateFlow<Map<String, Any>?>(null).asStateFlow()
        every { authRepository.isLoggedIn } returns MutableStateFlow(false).asStateFlow()
        every { paymentRepository.paymentState } returns MutableStateFlow<PaymentStatus>(PaymentStatus.Idle).asStateFlow()
        every { productRepository.products } returns MutableStateFlow<List<Product>>(emptyList()).asStateFlow()
        every { productRepository.categories } returns MutableStateFlow<List<Category>>(emptyList()).asStateFlow()

        viewModel = HomeViewModel(
            productRepository, cartRepository, orderRepository,
            paymentRepository, userRepository, vendorRepository,
            adminRepository, authRepository
        )
        
        testScheduler.advanceUntilIdle()

        // When
        viewModel.addToCart(product)

        // Then
        verify(exactly = 0) { cartRepository.addToCart(any()) }
        assertEquals("Only 2 units available in stock.", viewModel.uiState.value.error)
    }

    @Test
    fun `getPriceBreakdown handles embroidery and coupons correctly`() = runTest {
        // Given
        val product = Product(
            id = "1",
            name = "S1",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 10000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 10,
            tag = null,
            images = emptyList(),
            description = "Test Description",
            inStock = true,
            isActive = true
        )
        val cartItem = CartItem(product, "M", null, 1, embroideryName = "Nurse Jane")
        
        every { cartRepository.cartItems } returns MutableStateFlow(listOf(cartItem)).asStateFlow()
        
        // Mock a 10% coupon
        val coupon = mapOf(
            "code" to "SAVE10",
            "discount_type" to "percentage",
            "discount_value" to 10.0,
            "active" to true,
            "min_spend_kes" to 0.0
        )
        
        coEvery { productRepository.getCoupons() } returns Result.success(listOf(coupon))
        
        every { userRepository.userProfile } returns MutableStateFlow<Map<String, Any>?>(null).asStateFlow()
        every { authRepository.isLoggedIn } returns MutableStateFlow(false).asStateFlow()
        every { paymentRepository.paymentState } returns MutableStateFlow<PaymentStatus>(PaymentStatus.Idle).asStateFlow()
        every { productRepository.products } returns MutableStateFlow<List<Product>>(emptyList()).asStateFlow()
        every { productRepository.categories } returns MutableStateFlow<List<Category>>(emptyList()).asStateFlow()

        viewModel = HomeViewModel(
            productRepository, cartRepository, orderRepository,
            paymentRepository, userRepository, vendorRepository,
            adminRepository, authRepository
        )

        viewModel.setUserType(UserType.PROFESSIONAL) // 10% base discount
        viewModel.applyCoupon("SAVE10")
        testScheduler.advanceUntilIdle()

        // When
        val breakdown = viewModel.getPriceBreakdown()

        // Then
        assertEquals(11000, breakdown.subtotal)
        assertEquals(2000, breakdown.discountAmount)
        assertEquals(1440, breakdown.tax)
        assertEquals(10440, breakdown.finalTotal)
    }

    @Test
    fun `updateCartItemQuantity prevents exceeding stock`() = runTest {
        // Given
        val product = Product(
            id = "1",
            name = "Test Scrub",
            category = "Scrubs",
            gender = "Unisex",
            priceKes = 5000,
            rating = 4.5,
            reviewsCount = 10,
            stockCount = 5,
            tag = "NEW",
            images = emptyList(),
            description = "Test Description",
            inStock = true,
            isActive = true
        )
        val cartItem = CartItem(product, "M", ProductColor("Blue", 0), 1)

        // When
        viewModel.updateCartItemQuantity(cartItem, 6)

        // Then
        verify(exactly = 0) { cartRepository.updateQuantity(any(), any()) }
        assertEquals("Only 5 items available in stock.", viewModel.uiState.value.error)
    }
}
