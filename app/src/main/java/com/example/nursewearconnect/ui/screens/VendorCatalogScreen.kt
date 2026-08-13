package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorCatalogScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Shop Preview", fontWeight = FontWeight.Bold)
                        Text("Viewing as a customer", fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Normal)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Slate900
                ),
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 16.dp),
                        color = Brand50,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "PREVIEW MODE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand700
                        )
                    }
                }
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        // We reuse the CatalogScreen but with logic to disable consumer actions
        // In a real app, we might pass a 'isPreview' flag to CatalogScreen
        // For now, let's wrap the CatalogScreen and we'll need to modify CatalogScreen 
        // to handle the 'isPreview' flag to hide "Add to Cart" etc.
        
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            CatalogScreen(
                innerPadding = PaddingValues(0.dp),
                viewModel = viewModel,
                onBack = onBack,
                isPreviewMode = true
            )
            
            // Helpful overlay indicating this is a preview
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                color = Slate900.copy(alpha = 0.9f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Interactive elements like 'Add to Cart' are disabled in preview.",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
