package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminInventoryScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

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

    androidx.activity.compose.BackHandler(enabled = searchQuery.isNotEmpty()) {
        searchQuery = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(24.dp), tint = Brand600)
                        Spacer(Modifier.width(12.dp))
                        Text("Global Inventory", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAdminData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh, 
                            contentDescription = "Refresh", 
                            tint = if (uiState.isLoading) Brand600 else Slate400,
                            modifier = if (uiState.isLoading) Modifier.rotate(rotation) else Modifier
                        )
                    }
                    IconButton(onClick = { 
                        val csvData = viewModel.exportInventoryToCSV(isAdmin = true)
                        if (csvData.isNotEmpty()) {
                            AppUtils.exportAndShareData(
                                context,
                                csvData,
                                "Global_Inventory_${System.currentTimeMillis()}.csv"
                            )
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        .padding(16.dp),
                    placeholder = { Text("Search products by name or SKU...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate400) },
                    shape = RoundedCornerShape(12.dp),
                    colors = AppUtils.standardOutlinedTextFieldColors(),
                    singleLine = true
                )
            }

            // Product List
            if (uiState.isLoading && uiState.products.isEmpty()) {
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
                        Text("Loading global catalog...", fontSize = 14.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val products = uiState.products
                    val filteredProducts = products.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }

                    items(filteredProducts) { product ->
                        AdminProductCard(product)
                    }
                    
                    if (filteredProducts.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("No products found", color = Slate400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminProductCard(product: Product) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = Slate50
            ) {
                Box(contentAlignment = Alignment.Center) {
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
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, color = Slate900, fontSize = 15.sp)
                Text("KSh ${product.priceKes}", fontSize = 12.sp, color = Slate500)
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stock Status
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (product.inStock && product.stockCount > 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                    ) {
                        Text(
                            if (product.inStock && product.stockCount > 0) "In Stock" else "Out of Stock",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (product.inStock && product.stockCount > 0) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                    }
                    
                    Text("Stock: ${product.stockCount} units", fontSize = 11.sp, color = Slate400)
                }
            }

            IconButton(onClick = { /* Edit Product */ }) {
                Icon(Icons.Default.Edit, null, tint = Brand600, modifier = Modifier.size(20.dp))
            }
        }
    }
}
