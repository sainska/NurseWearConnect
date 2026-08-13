package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.utils.AppUtils
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.activity.compose.BackHandler
import com.example.nursewearconnect.ui.components.EmptyState
import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.CatalogSortOption
import com.example.nursewearconnect.ui.components.NursePullToRefresh
import com.example.nursewearconnect.ui.components.NursePullToRefresh
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@Composable
fun SearchNotFoundIllustration() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Slate100,
                radius = size.minDimension / 2.5f
            )
            drawCircle(
                color = Slate200,
                radius = size.minDimension / 4f
            )
        }
        
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Slate300
        )
        
        // Decorative floating elements
        val transition = rememberInfiniteTransition(label = "floating")
        val floatAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floating"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = floatAnim.dp, x = (-40).dp)
                .size(24.dp)
                .background(Brand50, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QuestionMark, null, modifier = Modifier.size(14.dp), tint = Brand300)
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    isFavorite: Boolean = false,
    isPreviewMode: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onAddToCart: () -> Unit = {},
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Slate50, RoundedCornerShape(12.dp)),
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
                    Icon(
                        imageVector = when(product.category) {
                            "Equipment" -> Icons.Default.MedicalServices
                            "Theatre Shoes" -> Icons.Default.IceSkating
                            else -> Icons.Default.Checkroom
                        },
                        contentDescription = null,
                        tint = Brand600,
                        modifier = Modifier.size(48.dp)
                    )
                }

                if (product.tag != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = RoundedCornerShape(99.dp),
                        color = Brand500
                    ) {
                        Text(product.tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (!isPreviewMode) {
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFF43F5E) else Slate300,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!product.inStock) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "OUT OF STOCK",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("${product.gender} • ${product.category}".uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand600, letterSpacing = 0.5.sp)
            Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate900, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, modifier = Modifier.heightIn(min = 36.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                repeat(4) { Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(10.dp)) }
                Icon(Icons.Filled.Star, null, tint = Slate200, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(4.dp))
                Text("${product.rating}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate600)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        AppUtils.formatCurrency(product.priceKes.toDouble(), currency, exchangeRates),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }
                
                if (!isPreviewMode) {
                    IconButton(
                        onClick = onAddToCart,
                        modifier = Modifier.size(32.dp).background(if (product.inStock) Slate900 else Slate200, CircleShape),
                        enabled = product.inStock
                    ) {
                        Text("+", color = if (product.inStock) Color.White else Slate400, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel? = null,
    onBack: () -> Unit = {},
    isPreviewMode: Boolean = false
) {
    val uiState by viewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    
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

    val searchQuery = uiState?.catalogSearchQuery ?: ""
    val selectedFilter = uiState?.catalogSelectedCategory ?: "All"
    val selectedSubFilter = uiState?.catalogSelectedSubCategory
    val selectedGender = uiState?.catalogSelectedGender ?: "All"
    val sortOption = uiState?.catalogSortOption ?: CatalogSortOption.NEWEST
    
    var isGridView by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    
    // Search Interaction State
    val interactionSource = remember { MutableInteractionSource() }
    val isSearchFocused by interactionSource.collectIsFocusedAsState()
    val searchBarHeight by animateDpAsState(targetValue = if (isSearchFocused) 56.dp else 44.dp)
    
    val minPrice = uiState?.catalogMinPrice ?: 0f
    val maxPrice = uiState?.catalogMaxPrice ?: 20000f
    val selectedSizes = uiState?.catalogSelectedSizes ?: emptySet()
    val selectedMaterials = uiState?.catalogSelectedMaterials ?: emptySet()

    val filteredProducts by viewModel?.filteredCatalogProducts?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = uiState?.isLoading ?: false
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = isSearchFocused || showFilterSheet || showSortSheet || searchQuery.isNotEmpty()) {
        when {
            showFilterSheet -> showFilterSheet = false
            showSortSheet -> showSortSheet = false
            isSearchFocused -> keyboardController?.hide()
            searchQuery.isNotEmpty() -> viewModel?.setCatalogSearchQuery("")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sticky Compact Header
        Surface(
            color = Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .widthIn(max = 1200.dp)
                        .fillMaxWidth()
                ) {
                    // Row 1: Back + Search + Filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        this@Row.AnimatedVisibility(visible = !isSearchFocused) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Slate900)
                            }
                        }
                        
                        val keyboardController = LocalSoftwareKeyboardController.current
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel?.setCatalogSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = searchBarHeight, max = searchBarHeight)
                                .onFocusChanged { if (it.isFocused) viewModel?.updateSuggestedProducts() },
                            interactionSource = interactionSource,
                            placeholder = { Text("Search products...", fontSize = 13.sp, color = Slate400) },
                            leadingIcon = { 
                                if (isSearchFocused) {
                                    IconButton(onClick = { keyboardController?.hide() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search", modifier = Modifier.size(18.dp), tint = Slate400)
                                    }
                                } else {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Slate400)
                                }
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel?.setCatalogSearchQuery("") }) {
                                        Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp), tint = Slate400)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Slate900,
                                unfocusedTextColor = Slate900,
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White,
                                unfocusedBorderColor = Slate400,
                                focusedBorderColor = Brand600,
                                cursorColor = Brand600
                            ),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search,
                                capitalization = KeyboardCapitalization.Words
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { 
                                    viewModel?.addToRecentSearches(searchQuery)
                                    keyboardController?.hide() 
                                }
                            )
                        )
                        
                        this@Row.AnimatedVisibility(visible = !isSearchFocused) {
                            IconButton(onClick = { viewModel?.loadHomeData() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh, 
                                    contentDescription = "Refresh", 
                                    tint = if (isLoading) Brand600 else Slate900,
                                    modifier = if (isLoading) Modifier.rotate(rotation) else Modifier
                                )
                            }
                        }

                        this@Row.AnimatedVisibility(visible = !isSearchFocused) {
                            Surface(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Brand600
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Tune, "Filter", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    // Row 2: Category Filters (Horizontal Scroll)
                    val categories = uiState?.categories ?: listOf("All", "Uniform", "Top", "Pants", "Set", "Jacket", "Equipment", "Theatre Shoes")
                    LazyRow(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedFilter == category
                            Surface(
                                onClick = { viewModel?.setCatalogCategory(category) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Slate900 else Slate50,
                                border = if (isSelected) null else BorderStroke(1.dp, Slate100)
                            ) {
                                Text(
                                    text = category,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Slate600
                                )
                            }
                        }
                    }
                    
                    // Row 3: Sub-category navigation (if Theatre Shoes selected)
                    if (selectedFilter == "Theatre Shoes") {
                        val subCategories = listOf("Theatre Clogs", "Surgical Crocs", "Slip-on Theatre Shoes", "Closed Sterile Shoes")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            HorizontalDivider(color = Slate100, thickness = 1.dp)
                            Text(
                                "Specialized footwear for clinical & surgical environments.",
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                fontSize = 11.sp,
                                color = Slate500
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedSubFilter == null,
                                        onClick = { viewModel?.setCatalogSubCategory(null) },
                                        label = { Text("All Shoes", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Brand50,
                                            selectedLabelColor = Brand700
                                        )
                                    )
                                }
                                items(subCategories) { sub ->
                                    FilterChip(
                                        selected = selectedSubFilter == sub,
                                        onClick = { viewModel?.setCatalogSubCategory(sub) },
                                        label = { Text(sub, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Brand50,
                                            selectedLabelColor = Brand700
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub-Header: Results count, Sort, and View Toggle
        Row(
            modifier = Modifier
                .widthIn(max = 1200.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredProducts.size} items in ${if(selectedFilter=="All") "Catalog" else selectedFilter}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500
            )
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // View Toggle
                Row(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    val iconSize = 28.dp
                    IconButton(onClick = { isGridView = true }, modifier = Modifier.size(iconSize)) {
                        Icon(Icons.Default.GridView, null, tint = if (isGridView) Brand600 else Slate300, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { isGridView = false }, modifier = Modifier.size(iconSize)) {
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = if (!isGridView) Brand600 else Slate300, modifier = Modifier.size(16.dp))
                    }
                }

                Surface(
                    onClick = { showSortSheet = true },
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sort", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = Slate400)
                    }
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 1200.dp)
                .fillMaxWidth()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NursePullToRefresh(
                isRefreshing = isLoading,
                onRefresh = { viewModel?.loadHomeData() },
                screenIcon = Icons.Default.Layers,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading && (uiState?.products?.isEmpty() == true)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = Brand50
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
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
                            Text("Exploring catalog...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                        }
                    }
                } else if (filteredProducts.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState?.error != null) {
                            EmptyState(
                                illustration = {
                                    Box(
                                        modifier = Modifier.size(160.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(80.dp),
                                            tint = Slate300
                                        )
                                    }
                                },
                                title = "Network Error",
                                description = uiState?.error ?: "Unable to connect to server. Please check your internet connection.",
                                actionLabel = "Retry Connection",
                                onActionClick = { viewModel?.loadHomeData() }
                            )
                        } else {
                            EmptyState(
                                illustration = { SearchNotFoundIllustration() },
                                title = "No products found",
                                description = "We couldn't find any products matching your search or filters. Try adjusting them.",
                                actionLabel = "Reset All Filters",
                                onActionClick = { viewModel?.resetFilters(); viewModel?.setCatalogSearchQuery("") }
                            )
                        }
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts) { product ->
                                ProductCard(
                                    product = product,
                                    isFavorite = uiState?.favoriteProductIds?.contains(product.id) ?: false,
                                    onFavoriteClick = { if (!isPreviewMode) viewModel?.toggleFavorite(product.id) },
                                    onClick = { viewModel?.setSelectedProduct(product) },
                                    onAddToCart = { if (!isPreviewMode) viewModel?.addToCart(product, 1, null) },
                                    isPreviewMode = isPreviewMode,
                                    currency = uiState?.selectedCurrency ?: AppUtils.Currency.KES,
                                    exchangeRates = uiState?.exchangeRates ?: emptyMap()
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts) { product ->
                                ProductListItem(
                                    product = product,
                                    isFavorite = uiState?.favoriteProductIds?.contains(product.id) ?: false,
                                    onFavoriteToggle = { if (!isPreviewMode) viewModel?.toggleFavorite(product.id) },
                                    onClick = { viewModel?.setSelectedProduct(product) },
                                    onAddToCart = { if (!isPreviewMode) viewModel?.addToCart(product, 1, null) },
                                    isPreviewMode = isPreviewMode,
                                    currency = uiState?.selectedCurrency ?: AppUtils.Currency.KES,
                                    exchangeRates = uiState?.exchangeRates ?: emptyMap()
                                )
                            }
                        }
                    }
                }
            }

            // Search Suggestions Overlay
            this@Column.AnimatedVisibility(
                visible = isSearchFocused,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Recent Searches
                        val recentSearches = uiState?.recentSearches ?: emptyList()
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Recent Searches",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Slate900
                                )
                                TextButton(onClick = { viewModel?.clearRecentSearches() }) {
                                    Text("Clear", color = Brand600, fontSize = 12.sp)
                                }
                            }
                            
                            FlowRow(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentSearches.forEach { search ->
                                    SuggestionChip(
                                        onClick = { viewModel?.setCatalogSearchQuery(search) },
                                        label = { Text(search) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            labelColor = Slate600,
                                            containerColor = Slate50
                                        ),
                                        border = BorderStroke(1.dp, Slate200)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Suggested Products
                        Text(
                            "Suggested for You",
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate900,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val suggestedProducts = uiState?.suggestedProducts ?: emptyList()
                        suggestedProducts.forEach { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel?.setSelectedProduct(product) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = product.images.firstOrNull(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        product.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        AppUtils.formatCurrency(product.priceKes.toDouble(), uiState?.selectedCurrency ?: AppUtils.Currency.KES, uiState?.exchangeRates ?: emptyMap()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Brand600,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Slate300
                                )
                            }
                            HorizontalDivider(color = Slate50)
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            selectedOption = sortOption,
            onOptionSelected = {
                viewModel?.setCatalogSortOption(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            minPrice = minPrice,
            maxPrice = maxPrice,
            selectedGender = selectedGender,
            selectedSizes = selectedSizes,
            selectedMaterials = selectedMaterials,
            onPriceChange = { viewModel?.setCatalogPriceRange(it) },
            onGenderSelected = { viewModel?.setCatalogGender(it) },
            onSizeToggle = { viewModel?.toggleCatalogSize(it) },
            onMaterialToggle = { viewModel?.toggleCatalogMaterial(it) },
            onReset = { viewModel?.resetFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}





@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    minPrice: Float,
    maxPrice: Float,
    selectedGender: String,
    selectedSizes: Set<String>,
    selectedMaterials: Set<String>,
    onPriceChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onGenderSelected: (String) -> Unit,
    onSizeToggle: (String) -> Unit,
    onMaterialToggle: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate200) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
                TextButton(onClick = onReset) {
                    Text("Reset All", color = Brand600, fontWeight = FontWeight.Bold)
                }
            }

            // Price Range
            FilterSection(title = "Price Range (${if (selectedGender == "All") "KSh" else "KSh"})") { // Default to KSh for filter context
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    RangeSlider(
                        value = minPrice..maxPrice,
                        onValueChange = onPriceChange,
                        valueRange = 0f..20000f,
                        colors = SliderDefaults.colors(
                            thumbColor = Brand600,
                            activeTrackColor = Brand600,
                            inactiveTrackColor = Slate100
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppUtils.formatCurrency(minPrice.toDouble()), fontSize = 13.sp, color = Slate600)
                        Text(AppUtils.formatCurrency(maxPrice.toDouble()), fontSize = 13.sp, color = Slate600)
                    }
                }
            }

            // Gender
            FilterSection(title = "Gender") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Men", "Women", "Unisex").forEach { gender ->
                        FilterChip(
                            selected = selectedGender == gender,
                            onClick = { onGenderSelected(gender) },
                            label = { Text(gender) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Brand50,
                                selectedLabelColor = Brand700
                            )
                        )
                    }
                }
            }

            // Sizes
            FilterSection(title = "Sizes") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("XS", "S", "M", "L", "XL", "XXL", "36", "37", "38", "39", "40", "41", "42").forEach { size ->
                        FilterChip(
                            selected = selectedSizes.contains(size),
                            onClick = { onSizeToggle(size) },
                            label = { Text(size) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Brand50,
                                selectedLabelColor = Brand700
                            )
                        )
                    }
                }
            }

            // Material Features
            FilterSection(title = "Features / Materials") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Autoclavable", "Antimicrobial", "4-Way Stretch", "Waterproof", "Breathable", "Non-slip").forEach { material ->
                        FilterChip(
                            selected = selectedMaterials.contains(material),
                            onClick = { onMaterialToggle(material) },
                            label = { Text(material) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Brand50,
                                selectedLabelColor = Brand700
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Show Results", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}





@Composable
fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    selectedOption: CatalogSortOption,
    onOptionSelected: (CatalogSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate200) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Sort By",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            
            val options = listOf(
                CatalogSortOption.NEWEST to "Newest Arrivals",
                CatalogSortOption.PRICE_LOW_HIGH to "Price: Low to High",
                CatalogSortOption.PRICE_HIGH_LOW to "Price: High to Low",
                CatalogSortOption.RATING to "Customer Rating"
            )
            
            options.forEach { (option, label) ->
                val isSelected = option == selectedOption
                Surface(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSelected) Slate50 else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Brand600 else Slate700
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Brand600, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}





@Composable
fun ProductListItem(
    product: Product,
    isFavorite: Boolean = false,
    isPreviewMode: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit = {},
    onAddToCart: () -> Unit = {},
    currency: AppUtils.Currency = AppUtils.Currency.KES,
    exchangeRates: Map<String, Double> = emptyMap()
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        // ... (rest of the Box code)
        // Update price text
        // Text(AppUtils.formatCurrency(product.priceKes.toDouble(), currency, exchangeRates), ...)
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 130.dp)
                    .background(Slate50, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (product.images.isNotEmpty()) {
                    AsyncImage(
                        model = product.images.first(),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 130.dp)
                            .background(Brand50, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when(product.category) {
                                "Equipment" -> Icons.Default.MedicalServices
                                "Theatre Shoes" -> Icons.Default.IceSkating
                                else -> Icons.Default.Checkroom
                            },
                            contentDescription = null,
                            tint = Brand600,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                if (product.tag != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = RoundedCornerShape(99.dp),
                        color = Brand500
                    ) {
                        Text(product.tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (!product.inStock) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "OUT OF STOCK",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${product.gender} • ${product.category}".uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand600, letterSpacing = 0.5.sp)
                        Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate900, lineHeight = 18.sp)
                    }
                    
                    if (!isPreviewMode) {
                        IconButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFF43F5E) else Slate300,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    repeat(4) { Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(10.dp)) }
                    Icon(Icons.Filled.Star, null, tint = Slate200, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${product.rating}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate600)
                    Text(" (${product.reviewsCount})", fontSize = 11.sp, color = Slate400)
                }

                Row(modifier = Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val attrs = if (product.category == "Equipment") {
                        listOf("Professional", "Durable")
                    } else if (product.category == "Theatre Shoes") {
                        listOf("Non-slip", "Waterproof")
                    } else {
                        listOf("4-Way Stretch", "Antimicrobial")
                    }
                    attrs.forEach { attr ->
                        Surface(shape = RoundedCornerShape(4.dp), color = if (attr == "Antimicrobial" || attr == "Professional" || attr == "Non-slip") Brand50 else Slate100) {
                            Text(attr, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = if (attr == "Antimicrobial" || attr == "Professional" || attr == "Non-slip") Brand600 else Slate600)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            AppUtils.formatCurrency(product.priceKes.toDouble(), currency, exchangeRates),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        
                        // Vendor Info
                        product.vendorName?.let { vName ->
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Storefront, null, modifier = Modifier.size(10.dp), tint = Slate400)
                                Text(
                                    vName,
                                    fontSize = 10.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                product.vendorRating?.let { vRating ->
                                    Text("•", fontSize = 10.sp, color = Slate300)
                                    Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(8.dp))
                                    Text("%.1f".format(vRating), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                }
                            }
                        }

                        if (product.availableSizes.isNotEmpty()) {
                            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFF1E3A8A), CircleShape).border(1.dp, Slate200, CircleShape))
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFF0F766E), CircleShape).border(1.dp, Slate200, CircleShape))
                                Box(modifier = Modifier.size(12.dp).background(Color.Black, CircleShape).border(1.dp, Slate200, CircleShape))
                            }
                        }
                    }
                    if (!isPreviewMode) {
                        IconButton(
                            onClick = onAddToCart,
                            modifier = Modifier.size(36.dp).background(if (product.inStock) Slate900 else Slate200, CircleShape),
                            enabled = product.inStock
                        ) {
                            Text("+", color = if (product.inStock) Color.White else Slate400, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}




