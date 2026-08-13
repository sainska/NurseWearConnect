package com.example.nursewearconnect.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.model.ProductColor
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.utils.Constants
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import coil.compose.AsyncImage
import com.example.nursewearconnect.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorInventoryScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
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

    var showAddProduct by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("Name") }
    var filterStock by remember { mutableStateOf("All") }
    var showSupportDialog by remember { mutableStateOf(false) }

    if (showSupportDialog) {
        com.example.nursewearconnect.ui.components.SupportDialog(
            adminEmail = uiState.adminEmail,
            adminPhone = uiState.adminPhone,
            onDismiss = { showSupportDialog = false },
            subject = "Restricted Inventory Assistance"
        )
    }

    val isPending = uiState.userStatus == "pending"
    val isRejected = uiState.userStatus == "rejected"

    if (showAddProduct || productToEdit != null) {
        ProductDialog(
            product = productToEdit,
            categories = uiState.categories,
            onDismiss = {
                showAddProduct = false
                productToEdit = null
            },
            onConfirm = { product, imagesList ->
                if (productToEdit != null) {
                    viewModel.updateVendorProduct(product, imagesList)
                } else {
                    viewModel.addVendorProduct(product, imagesList)
                }
                showAddProduct = false
                productToEdit = null
            }
        )
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete Product") },
            text = { Text("Are you sure you want to delete '${productToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        productToDelete?.let { viewModel.deleteVendorProduct(it.id) }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(24.dp), tint = Brand600)
                        Spacer(Modifier.width(12.dp))
                        Text("My Inventory", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isPending && !isRejected) {
                        IconButton(onClick = {
                            val csvData = viewModel.exportInventoryToCSV(isAdmin = false)
                            if (csvData.isNotEmpty()) {
                                AppUtils.exportAndShareData(
                                    context,
                                    csvData,
                                    "My_Inventory_${System.currentTimeMillis()}.csv"
                                )
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Export Inventory", tint = Brand600)
                        }
                        IconButton(onClick = { viewModel.uiState.value.userId?.let { viewModel.loadVendorData(it) } }) {
                            Icon(
                                imageVector = Icons.Default.Refresh, 
                                contentDescription = "Refresh", 
                                tint = Slate900,
                                modifier = Modifier
                            )
                        }
                        IconButton(onClick = { showAddProduct = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Product", tint = Brand600)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900
                )
            )
        },
        floatingActionButton = {
            if (!isPending && !isRejected) {
                ExtendedFloatingActionButton(
                    onClick = { showAddProduct = true },
                    containerColor = Brand600,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add Product") }
                )
            }
        },
        containerColor = Slate50
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NursePullToRefresh(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.uiState.value.userId?.let { viewModel.loadVendorData(it) } },
                screenIcon = Icons.Default.Inventory2,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Search Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Search your inventory...") },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null, tint = Slate400)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = AppUtils.standardOutlinedTextFieldColors(),
                            singleLine = true
                        )
                    }

                    // Sorting and Filtering
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var sortExpanded by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = sortOrder != "Name",
                                onClick = { sortExpanded = true },
                                label = { Text("Sort: $sortOrder", fontSize = 12.sp) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                listOf("Name", "Price: Low to High", "Price: High to Low", "Stock: Low to High").forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order) },
                                        onClick = {
                                            sortOrder = order
                                            sortExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        var filterExpanded by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = filterStock != "All",
                                onClick = { filterExpanded = true },
                                label = { Text("Filter: $filterStock", fontSize = 12.sp) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                                listOf("All", "In Stock", "Out of Stock").forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter) },
                                        onClick = {
                                            filterStock = filter
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val products = uiState.vendorProducts
                    val filteredProducts = products.filter {
                        it.name.contains(searchQuery, ignoreCase = true) &&
                        (filterStock == "All" || 
                         (filterStock == "In Stock" && it.inStock && it.stockCount > 0) || 
                         (filterStock == "Out of Stock" && (!it.inStock || it.stockCount == 0)))
                    }.sortedWith { p1, p2 ->
                        when (sortOrder) {
                            "Price: Low to High" -> p1.priceKes.compareTo(p2.priceKes)
                            "Price: High to Low" -> p2.priceKes.compareTo(p1.priceKes)
                            "Stock: Low to High" -> p1.stockCount.compareTo(p2.stockCount)
                            else -> p1.name.compareTo(p2.name)
                        }
                    }

                    if (uiState.isLoading && products.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    color = Brand50
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory2,
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
                                Text("Updating Inventory...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        // Background loading - user doesn't see an intrusive progress bar
                        // Only the refresh icon in the TopBar will indicate activity if manually triggered

                        // Inventory Stats
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val totalItems = products.size
                            val lowStock = products.count { it.inStock && it.stockCount in 1..Constants.LOW_STOCK_THRESHOLD }
                            val walletBalance = uiState.walletBalance
                            
                            InventoryHealthCard("Total Items", totalItems.toString(), Brand50, Brand600, Modifier.weight(1f))
                            InventoryHealthCard("Low Stock", lowStock.toString(), Color(0xFFFFF7ED), Color(0xFFEA580C), Modifier.weight(1f))
                            
                            Surface(
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(16.dp),
                                color = Brand600,
                                onClick = { viewModel.requestWithdrawal(walletBalance) }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("KSh ${String.format("%,.0f", walletBalance)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Withdraw", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Text(
                            "Product List",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (filteredProducts.isEmpty()) {
                            SimpleEmptyState(
                                icon = Icons.Default.Inventory2,
                                title = if (searchQuery.isEmpty()) "No products yet" else "No matches found",
                                description = if (searchQuery.isEmpty()) 
                                    "Start building your inventory by adding your first product." 
                                    else "We couldn't find any products matching '$searchQuery'.",
                                actionLabel = if (searchQuery.isEmpty()) "Add Product" else "Clear Search",
                                onActionClick = {
                                    if (searchQuery.isEmpty()) showAddProduct = true else searchQuery = ""
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredProducts) { product ->
                                    VendorProductCard(
                                        product = product,
                                        onEdit = { if (!isPending && !isRejected) productToEdit = it },
                                        onDelete = { if (!isPending && !isRejected) productToDelete = it },
                                        onToggleActive = { if (!isPending && !isRejected) viewModel.updateVendorProduct(it.copy(isActive = !it.isActive)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Overlay for Pending/Rejected Status
            if (isPending || isRejected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isPending) Icons.Default.Pending else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isPending) Brand600 else Color(0xFFDC2626),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (isPending) "Account Pending Approval" else "Account Restricted",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isPending) 
                                    "Your vendor account is currently under review. You'll be able to manage inventory once approved." 
                                    else "Your vendor account has been restricted. Please contact support for more information.",
                                fontSize = 14.sp,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                            
                            if (uiState.statusNotes != null) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Notes: ${uiState.statusNotes}",
                                    fontSize = 14.sp,
                                    color = Slate900,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .background(Slate50, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                )
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { showSupportDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                                ) {
                                    Text("Contact Support")
                                }
                                Button(
                                    onClick = onBackClick,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                                ) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDialog(
    product: Product?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Product, List<ByteArray>) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(product?.name ?: "") }
    var price by remember { mutableStateOf(product?.priceKes?.toString() ?: "") }
    var description by remember { mutableStateOf(product?.description ?: "") }
    var category by remember { mutableStateOf(product?.category ?: if (categories.size > 1) categories[1] else "General") }
    var gender by remember { mutableStateOf(product?.gender ?: "Unisex") }
    var stockCount by remember { mutableStateOf(product?.stockCount?.toString() ?: "10") }
    var inStock by remember { mutableStateOf(product?.inStock ?: true) }
    var isActive by remember { mutableStateOf(product?.isActive ?: true) }
    var tag by remember { mutableStateOf(product?.tag ?: "") }
    var material by remember { mutableStateOf(product?.material ?: "") }
    var subCategory by remember { mutableStateOf(product?.subCategory ?: "") }
    var featured by remember { mutableStateOf(product?.featured ?: false) }
    var features by remember { mutableStateOf(product?.features?.joinToString(", ") ?: "") }
    
    var selectedImagesBytes by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var existingImagesToKeep by remember { mutableStateOf(product?.images ?: emptyList()) }
    
    // Variations state
    var availableSizes by remember { 
        mutableStateOf(product?.availableSizes ?: listOf("S", "M", "L", "XL")) 
    }
    var availableColors by remember { 
        mutableStateOf(product?.availableColors ?: emptyList()) 
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val newImages = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        selectedImagesBytes = selectedImagesBytes + newImages
    }
    
    // Measurement Guide state
    var measurementGuide by remember { 
        mutableStateOf(product?.measurementGuide?.toList() ?: emptyList()) 
    }

    // Validation state
    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (product == null) "New Product" else "Edit Details", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    // Multi-Image Picker Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Product Photos (Select 1 or more)", fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate100)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AddPhotoAlternate, null, tint = Brand600)
                                    }
                                }
                            }
                            
                            // Existing Images if editing
                            items(existingImagesToKeep) { imageUrl ->
                                Box(modifier = Modifier.size(100.dp)) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { existingImagesToKeep = existingImagesToKeep.filter { it != imageUrl } },
                                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            
                            // Newly selected images
                            items(selectedImagesBytes.size) { index ->
                                Box(modifier = Modifier.size(100.dp)) {
                                    val bitmap = BitmapFactory.decodeByteArray(selectedImagesBytes[index], 0, selectedImagesBytes[index].size)
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { selectedImagesBytes = selectedImagesBytes.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Text("Basic Information", style = MaterialTheme.typography.labelLarge, color = Brand600)
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            nameError = it.isBlank()
                        },
                        label = { Text("Product Name") },
                        placeholder = { Text("e.g. Premium Navy Jogger Scrubs") },
                        isError = nameError,
                        supportingText = { if (nameError) Text("Name is required") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }
                
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { 
                                price = it
                                priceError = it.toDoubleOrNull() == null
                            },
                            label = { Text("Price (KSh)") },
                            prefix = { Text("KSh ") },
                            isError = priceError,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = stockCount,
                            onValueChange = { stockCount = it },
                            label = { Text("Initial Stock") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Detailed Description") },
                        placeholder = { Text("Material, feel, features...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Category Dropdown
                        var expandedCat by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCat,
                            onExpandedChange = { expandedCat = !expandedCat },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = AppUtils.standardOutlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCat,
                                onDismissRequest = { expandedCat = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            category = cat
                                            expandedCat = false
                                        }
                                    )
                                }
                            }
                        }

                        // Gender Dropdown
                        var expandedGen by remember { mutableStateOf(false) }
                        val genders = listOf("Unisex", "Male", "Female")
                        ExposedDropdownMenuBox(
                            expanded = expandedGen,
                            onExpandedChange = { expandedGen = !expandedGen },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = gender,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Gender") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGen) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = AppUtils.standardOutlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedGen,
                                onDismissRequest = { expandedGen = false }
                            ) {
                                genders.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g) },
                                        onClick = {
                                            gender = g
                                            expandedGen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = tag,
                            onValueChange = { tag = it },
                            label = { Text("Tag (e.g. NEW, SALE)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = subCategory,
                            onValueChange = { subCategory = it },
                            label = { Text("Sub-category") },
                            placeholder = { Text("e.g. Clogs") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = material,
                        onValueChange = { material = it },
                        label = { Text("Material") },
                        placeholder = { Text("e.g. 100% Cotton, Breathable") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }

                item {
                    OutlinedTextField(
                        value = features,
                        onValueChange = { features = it },
                        label = { Text("Key Features (Comma separated)") },
                        placeholder = { Text("Waterproof, Antimicrobial...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate50,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate100)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, null, tint = if (featured) Color(0xFFF59E0B) else Slate400)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Featured Product", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Highlight this in the home screen", fontSize = 11.sp, color = Slate500)
                            }
                            Switch(
                                checked = featured, 
                                onCheckedChange = { featured = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFF59E0B))
                            )
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate50,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate100)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Visibility, null, tint = if (isActive) Color(0xFF10B981) else Slate400)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Catalog Visibility", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(if (isActive) "Visible to all customers" else "Hidden from public view", fontSize = 11.sp, color = Slate500)
                            }
                            Switch(
                                checked = isActive, 
                                onCheckedChange = { isActive = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Sizes & Colors", style = MaterialTheme.typography.labelLarge, color = Brand600)
                }

                item {
                    Text("Available Sizes", fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val allSizes = listOf("XXS", "XS", "S", "M", "L", "XL", "XXL", "3XL")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allSizes.forEach { size ->
                            FilterChip(
                                selected = availableSizes.contains(size),
                                onClick = {
                                    availableSizes = if (availableSizes.contains(size)) availableSizes - size else availableSizes + size
                                },
                                label = { Text(size) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Brand600,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Available Colors", fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { availableColors = availableColors + ProductColor("New Color", 0xFF000000) }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Custom", fontSize = 12.sp)
                        }
                    }
                    
                    val commonColors = listOf(
                        ProductColor("Navy Blue", 0xFF1E3A8A),
                        ProductColor("Royal Blue", 0xFF2563EB),
                        ProductColor("Hunter Green", 0xFF064E3B),
                        ProductColor("Burgundy", 0xFF7F1D1D),
                        ProductColor("Black", 0xFF000000),
                        ProductColor("White", 0xFFFFFFFF),
                        ProductColor("Grey", 0xFF4B5563)
                    )
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        commonColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(color.hex), CircleShape)
                                    .border(
                                        width = if (availableColors.any { it.hex == color.hex }) 3.dp else 1.dp,
                                        color = if (availableColors.any { it.hex == color.hex }) Brand600 else Slate200,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (!availableColors.any { it.hex == color.hex }) {
                                            availableColors = availableColors + color
                                        } else {
                                            availableColors = availableColors.filter { it.hex != color.hex }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (availableColors.any { it.hex == color.hex }) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = if(color.name == "White") Slate900 else Color.White)
                                }
                            }
                        }
                    }
                }

                items(availableColors.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate50, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(availableColors[index].hex), CircleShape)
                                .border(1.dp, Slate200, CircleShape)
                        )
                        OutlinedTextField(
                            value = availableColors[index].name,
                            onValueChange = { newName ->
                                availableColors = availableColors.toMutableList().apply {
                                    this[index] = this[index].copy(name = newName)
                                }
                            },
                            label = { Text("Name", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.5f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = availableColors[index].hex.toString(16).uppercase().removePrefix("FF"),
                            onValueChange = { newHexStr ->
                                val cleanHex = if (newHexStr.length > 6) newHexStr.take(6) else newHexStr
                                val newHex = try { 
                                    java.lang.Long.parseLong("FF$cleanHex", 16) 
                                } catch(e: Exception) { 0xFF000000 }
                                
                                availableColors = availableColors.toMutableList().apply {
                                    this[index] = this[index].copy(hex = newHex)
                                }
                            },
                            label = { Text("Hex Code", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            prefix = { Text("#", fontSize = 12.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        IconButton(onClick = {
                            availableColors = availableColors.toMutableList().apply { removeAt(index) }
                        }) {
                            Icon(Icons.Default.DeleteOutline, null, tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Size Guide (Measurements)", style = MaterialTheme.typography.labelLarge, color = Brand600)
                        TextButton(onClick = { measurementGuide = measurementGuide + Pair("", "") }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Row", fontSize = 12.sp)
                        }
                    }
                }

                items(measurementGuide.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate50.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = measurementGuide[index].first,
                            onValueChange = { newKey ->
                                val newList = measurementGuide.toMutableList()
                                newList[index] = Pair(newKey, newList[index].second)
                                measurementGuide = newList
                            },
                            label = { Text("Part", fontSize = 11.sp) },
                            placeholder = { Text("e.g. Chest", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = measurementGuide[index].second,
                            onValueChange = { newValue ->
                                val newList = measurementGuide.toMutableList()
                                newList[index] = Pair(newList[index].first, newValue)
                                measurementGuide = newList
                            },
                            label = { Text("Value", fontSize = 11.sp) },
                            placeholder = { Text("40-42 in", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        IconButton(
                            onClick = {
                                measurementGuide = measurementGuide.toMutableList().apply { removeAt(index) }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline, 
                                contentDescription = "Remove", 
                                tint = Color.Red.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isNameValid = name.isNotBlank()
                    val isPriceValid = price.toDoubleOrNull() != null
                    
                    nameError = !isNameValid
                    priceError = !isPriceValid

                    if (isNameValid && isPriceValid) {
                        val featureList = features.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val updatedProduct = product?.copy(
                            name = name,
                            priceKes = price.toDoubleOrNull() ?: 0.0,
                            stockCount = stockCount.toIntOrNull() ?: 0,
                            description = description,
                            inStock = inStock && (stockCount.toIntOrNull() ?: 0) > 0,
                            isActive = isActive,
                            category = category,
                            gender = gender,
                            tag = if (tag.isBlank()) null else tag,
                            material = material,
                            subCategory = if (subCategory.isBlank()) null else subCategory,
                            featured = featured,
                            features = featureList,
                            availableSizes = availableSizes,
                            availableColors = availableColors,
                            images = existingImagesToKeep,
                            measurementGuide = measurementGuide.filter { it.first.isNotBlank() }.toMap()
                        ) ?: Product(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            priceKes = price.toDoubleOrNull() ?: 0.0,
                            stockCount = stockCount.toIntOrNull() ?: 0,
                            description = description,
                            inStock = inStock && (stockCount.toIntOrNull() ?: 0) > 0,
                            isActive = isActive,
                            category = category,
                            gender = gender,
                            tag = if (tag.isBlank()) "NEW" else tag,
                            material = material,
                            subCategory = if (subCategory.isBlank()) null else subCategory,
                            featured = featured,
                            features = featureList,
                            rating = 5.0,
                            reviewsCount = 0,
                            images = emptyList(),
                            availableSizes = availableSizes,
                            availableColors = availableColors,
                            measurementGuide = measurementGuide.filter { it.first.isNotBlank() }.toMap(),
                            vendorId = null
                        )
                        onConfirm(updatedProduct, selectedImagesBytes)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
            ) {
                Text("Publish Product", fontWeight = FontWeight.Bold)
            }
        }
    )
}


@Composable
fun VendorProductCard(
    product: Product,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit,
    onToggleActive: (Product) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Slate50, RoundedCornerShape(12.dp)),
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
                    modifier = Modifier.size(24.dp)
                )
                if (!product.inStock || product.stockCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SOLD OUT",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("KSh ${product.priceKes}", fontSize = 13.sp, color = Brand600, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("•", color = Slate300)
                    Spacer(Modifier.width(8.dp))
                    
                    if (product.stockCount in 1..Constants.LOW_STOCK_THRESHOLD) {
                        Surface(
                            color = Color(0xFFFFF7ED),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFFED7AA))
                        ) {
                            Text(
                                "LOW STOCK: ${product.stockCount}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    } else {
                        Text("${product.stockCount} in stock", fontSize = 12.sp, color = if(product.stockCount == 0) Color(0xFFDC2626) else Slate500, fontWeight = if(product.stockCount == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(if (product.isActive) Color(0xFF10B981).copy(alpha = 0.1f) else Slate100, RoundedCornerShape(4.dp))
                        .clickable { onToggleActive(product) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (product.isActive) Color(0xFF10B981) else Color(0xFF94A3B8), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (product.isActive) "Active/Visible" else "Hidden/Inactive",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (product.isActive) Color(0xFF059669) else Slate600
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    IconButton(onClick = { onEdit(product) }) {
                        Icon(Icons.Default.Edit, null, tint = Slate400, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onDelete(product) }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }
                if (product.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(product.rating.toString(), fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
