package com.example.nursewearconnect.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@Composable
fun CouponManagerContent(
    viewModel: HomeViewModel,
    isAdmin: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredCoupons = remember(uiState.coupons, isAdmin) {
        if (isAdmin) uiState.coupons else uiState.coupons.filter { it["vendor_id"] == viewModel.getCurrentUserId() }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // States for new coupon
    var code by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var discountPercent by remember { mutableStateOf("") }
    var maxDiscount by remember { mutableStateOf("") }
    var minSpend by remember { mutableStateOf("") }
    var usageLimit by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    // Validation states
    var codeError by remember { mutableStateOf(false) }
    var discountError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAdminMarketingData()
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                codeError = false
                discountError = false
            },
            title = { Text("Create New Coupon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = code, 
                        onValueChange = { 
                            code = it.uppercase()
                            codeError = it.isBlank()
                        }, 
                        label = { Text("Coupon Code (e.g. NURSE20)") },
                        isError = codeError,
                        supportingText = { if (codeError) Text("Code cannot be empty") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = description, 
                        onValueChange = { description = it }, 
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discountPercent, 
                            onValueChange = { 
                                discountPercent = it
                                discountError = it.isBlank()
                            }, 
                            label = { Text("Discount %") },
                            isError = discountError,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = maxDiscount, 
                            onValueChange = { maxDiscount = it }, 
                            label = { Text("Max KES") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minSpend,
                            onValueChange = { minSpend = it },
                            label = { Text("Min Spend (KES)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = usageLimit,
                            onValueChange = { usageLimit = it },
                            label = { Text("Usage Limit") },
                            placeholder = { Text("e.g. 100") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("Start (YYYY-MM-DD)") },
                            placeholder = { Text("2024-01-01") },
                            modifier = Modifier.weight(1f),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("End (YYYY-MM-DD)") },
                            placeholder = { Text("2024-12-31") },
                            modifier = Modifier.weight(1f),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isCodeValid = code.isNotBlank()
                        val isDiscountValid = discountPercent.isNotBlank()
                        
                        codeError = !isCodeValid
                        discountError = !isDiscountValid

                        if (isCodeValid && isDiscountValid) {
                            viewModel.addCoupon(mapOf(
                                "code" to code,
                                "description" to description,
                                "discount_percent" to (discountPercent.toIntOrNull() ?: 0),
                                "max_discount_kes" to (maxDiscount.toIntOrNull() ?: 0),
                                "min_spend_kes" to (minSpend.toIntOrNull() ?: 0),
                                "usage_limit" to (usageLimit.toIntOrNull() ?: 0),
                                "start_date" to startDate,
                                "end_date" to endDate,
                                "active" to true,
                                "vendor_id" to (viewModel.getCurrentUserId() ?: "")
                            ))
                            showAddDialog = false
                            // Reset fields
                            code = ""; description = ""; discountPercent = ""; maxDiscount = ""
                            minSpend = ""; usageLimit = ""; startDate = ""; endDate = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                ) {
                    Text("Create")
                }
            },
            dismissButton = { 
                TextButton(onClick = { 
                    showAddDialog = false
                    codeError = false
                    discountError = false
                }) { 
                    Text("Cancel") 
                } 
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(if (isAdmin) "Global Coupon Insights" else "My Promotion Insights", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Slate900)
            Spacer(Modifier.height(8.dp))
            val couponCodes = filteredCoupons.mapNotNull { it["code"]?.toString() }
            val filteredPerformance = uiState.couponPerformance.filter { it["code"]?.toString() in couponCodes }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MarketingStatCard(
                    title = "Total Uses",
                    value = filteredPerformance.sumOf { (it["use_count"] as? Number)?.toInt() ?: 0 }.toString(),
                    icon = Icons.Default.ConfirmationNumber,
                    color = Brand600,
                    modifier = Modifier.weight(1f)
                )
                MarketingStatCard(
                    title = "Revenue Saved",
                    value = "KES ${filteredPerformance.sumOf { (it["total_discount"] as? Number)?.toDouble() ?: 0.0 }.toInt()}",
                    icon = Icons.Default.Savings,
                    color = Color(0xFF00C853),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isAdmin) "All Active Coupons" else "My Active Coupons", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Slate900)
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("New", fontSize = 12.sp)
                }
            }
        }
        
        items(filteredCoupons) { coupon ->
            val couponId = coupon["id"]?.toString() ?: ""
            val codeStr = coupon["code"]?.toString() ?: "N/A"
            val performance = uiState.couponPerformance.find { it["code"] == codeStr }
            val useCount = (performance?.get("use_count") as? Number)?.toInt() ?: 0
            val usageLimit = (coupon["usage_limit"] as? Number)?.toInt() ?: 0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Surface(color = Brand50, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = codeStr,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Brand700,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Get ${coupon["discount_percent"]}% off • Max KES ${coupon["max_discount_kes"]}", 
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            if ((coupon["min_spend_kes"] as? Number)?.toInt() ?: 0 > 0) {
                                Text(
                                    "On orders above KES ${coupon["min_spend_kes"]}",
                                    fontSize = 11.sp,
                                    color = Brand600,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteCoupon(couponId) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red.copy(alpha = 0.5f))
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    if (usageLimit > 0) {
                        val stats = uiState.couponUsageStats.find { it["code"] == codeStr }
                        val usagePercent = (stats?.get("usage_percent") as? Number)?.toDouble() ?: 0.0
                        
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Usage Progress", fontSize = 11.sp, color = Slate500)
                                Text("${(usagePercent * 100).toInt()}% Used ($useCount / $usageLimit)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand600)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { usagePercent.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (usagePercent >= 0.9) Color.Red else Brand600,
                                trackColor = Slate100,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        coupon["description"]?.toString() ?: "No description provided",
                        color = Slate500,
                        fontSize = 13.sp
                    )
                    
                    if (coupon["start_date"] != null || coupon["end_date"] != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = Slate400)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${coupon["start_date"] ?: "Now"} - ${coupon["end_date"] ?: "Forever"}",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    
                    Divider(Modifier.padding(vertical = 12.dp), color = Slate100)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(14.dp), tint = Slate400)
                            Spacer(Modifier.width(4.dp))
                            Text("$useCount uses", fontSize = 12.sp, color = Slate600)
                        }
                        
                        Switch(
                            checked = coupon["active"] as? Boolean == true,
                            onCheckedChange = { /* Toggle status */ },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Success600)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BannerManagerContent(
    viewModel: HomeViewModel,
    isAdmin: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBannerId by remember { mutableStateOf<String?>(null) }
    var showRejectDialog by remember { mutableStateOf<String?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    
    // States for banner form
    var bannerTitle by remember { mutableStateOf("") }
    var bannerSubtitle by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var actionLink by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.uploadBannerImage(bytes) { url -> imageUrl = url }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAdminMarketingData()
    }

    if (showAddDialog || editingBannerId != null) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                editingBannerId = null
                bannerTitle = ""; bannerSubtitle = ""; imageUrl = ""; actionLink = ""; startDate = ""; endDate = ""
            },
            title = { Text(if (editingBannerId != null) "Edit Banner" else "Add Marketing Banner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (editingBannerId != null) {
                        val banner = uiState.banners.find { it["id"] == editingBannerId }
                        if (banner != null && bannerTitle.isEmpty() && imageUrl.isEmpty()) {
                            bannerTitle = banner["title"]?.toString() ?: ""
                            bannerSubtitle = banner["subtitle"]?.toString() ?: ""
                            imageUrl = banner["image_url"]?.toString() ?: ""
                            actionLink = banner["action_link"]?.toString() ?: ""
                            startDate = banner["start_date"]?.toString() ?: ""
                            endDate = banner["end_date"]?.toString() ?: ""
                        }
                    }
                    OutlinedTextField(
                        value = bannerTitle,
                        onValueChange = { bannerTitle = it },
                        label = { Text("Headline (e.g. New Arrivals)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = bannerSubtitle,
                        onValueChange = { bannerSubtitle = it },
                        label = { Text("Subtitle") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Slate50, RoundedCornerShape(12.dp))
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUrl.isEmpty()) {
                            Text("Click to select Image", color = Brand600)
                        } else {
                            AsyncImage(model = AppUtils.getOptimizedImageUrl(imageUrl, 600, 300), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                    OutlinedTextField(
                        value = actionLink,
                        onValueChange = { actionLink = it },
                        label = { Text("Action Link") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("Start Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("End Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            colors = AppUtils.standardOutlinedTextFieldColors()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bannerData = mapOf(
                            "title" to bannerTitle,
                            "subtitle" to bannerSubtitle,
                            "image_url" to imageUrl,
                            "action_link" to actionLink,
                            "start_date" to startDate,
                            "end_date" to endDate,
                            "active" to isAdmin,
                            "status" to (if (isAdmin) "approved" else "pending"),
                            "vendor_id" to (viewModel.getCurrentUserId() ?: "")
                        )
                        editingBannerId?.let { id ->
                            viewModel.updateBanner(id, bannerData)
                        } ?: viewModel.addBanner(bannerData)
                        
                        showAddDialog = false; editingBannerId = null
                        bannerTitle = ""; bannerSubtitle = ""; imageUrl = ""; actionLink = ""; startDate = ""; endDate = ""
                    },
                    enabled = imageUrl.isNotEmpty()
                ) { Text(if (editingBannerId != null) "Update" else "Publish") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingBannerId = null }) { Text("Cancel") } }
        )
    }

    showRejectDialog?.let { bannerId ->
        AlertDialog(
            onDismissRequest = { showRejectDialog = null },
            title = { Text("Reject Banner") },
            text = {
                OutlinedTextField(
                    value = rejectionReason,
                    onValueChange = { rejectionReason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppUtils.standardOutlinedTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectBanner(bannerId, rejectionReason)
                        showRejectDialog = null; rejectionReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = rejectionReason.isNotBlank()
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = null }) { Text("Cancel") } }
        )
    }

    val filteredBanners = uiState.banners.filter { 
        (selectedFilter == "All" || it["status"]?.toString().equals(selectedFilter, ignoreCase = true)) &&
        (isAdmin || it["vendor_id"] == viewModel.getCurrentUserId())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Pending", "Approved", "Rejected").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showAddDialog = true },
                colors = CardDefaults.cardColors(containerColor = Brand600)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text("Add Promotion Banner", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(filteredBanners) { banner ->
            val bannerId = banner["id"]?.toString() ?: ""
            val status = banner["status"]?.toString() ?: "approved"
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    AsyncImage(
                        model = AppUtils.getOptimizedImageUrl(banner["image_url"]?.toString(), 800, 400),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(140.dp).background(Slate200)
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(banner["title"]?.toString() ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(status.uppercase(), color = if(status == "approved") Success600 else Brand600, fontSize = 12.sp)
                            }
                            Row {
                                IconButton(onClick = { editingBannerId = bannerId }) { Icon(Icons.Default.Edit, contentDescription = null) }
                                IconButton(onClick = { viewModel.deleteBanner(bannerId) }) { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red) }
                            }
                        }
                        if (isAdmin && status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.approveBanner(bannerId) }, modifier = Modifier.weight(1f)) { Text("Approve") }
                                OutlinedButton(onClick = { showRejectDialog = bannerId }, modifier = Modifier.weight(1f)) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryManagerContent(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryDesc by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCategoryName, 
                        onValueChange = { newCategoryName = it }, 
                        label = { Text("Name") }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = newCategoryDesc, 
                        onValueChange = { newCategoryDesc = it }, 
                        label = { Text("Description") }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppUtils.standardOutlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        viewModel.addCategory(newCategoryName, newCategoryDesc)
                        showAddDialog = false; newCategoryName = ""; newCategoryDesc = ""
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Product Categories", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(onClick = { showAddDialog = true }) { Text("New") }
            }
        }

        items(uiState.categories) { categoryName ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoryName, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { viewModel.deleteCategory(categoryName) }) { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red) }
                }
            }
        }
    }
}

@Composable
fun MarketingStatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(title, fontSize = 12.sp, color = Slate500)
        }
    }
}
