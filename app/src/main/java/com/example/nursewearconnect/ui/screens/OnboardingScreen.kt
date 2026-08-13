package com.example.nursewearconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val tag: String,
    val mainIcon: ImageVector,
    val accentIcon1: ImageVector,
    val accentIcon2: ImageVector,
    val accentColor: Color
)

val onboardingPages = listOf(
    OnboardingPage(
        title = "Find Your Perfect\nScrubs",
        description = "Discover premium healthcare wear designed for comfort. Customize your fit and track orders in real time.",
        tag = "DISCOVERY",
        mainIcon = Icons.Default.Person,
        accentIcon1 = Icons.Default.Checkroom,
        accentIcon2 = Icons.Default.Straighten,
        accentColor = Brand500
    ),
    OnboardingPage(
        title = "Browse Trusted\nVendors",
        description = "Explore a wide range of verified uniform vendors offering quality scrubs and medical apparel.",
        tag = "MARKETPLACE",
        mainIcon = Icons.Default.ShoppingCart,
        accentIcon1 = Icons.Default.Checkroom,
        accentIcon2 = Icons.Default.Person,
        accentColor = Color(0xFF8B5CF6) // Purple
    ),
    OnboardingPage(
        title = "Customize Your\nFit",
        description = "Choose sizes, colors, and styles that match your needs and personal comfort.",
        tag = "TAILORED",
        mainIcon = Icons.Default.Straighten,
        accentIcon1 = Icons.Default.Checkroom,
        accentIcon2 = Icons.Default.ShoppingCart,
        accentColor = Color(0xFFF59E0B) // Amber
    ),
    OnboardingPage(
        title = "Fast & Reliable\nDelivery",
        description = "Get your uniforms delivered quickly and track your order every step of the way.",
        tag = "LOGISTICS",
        mainIcon = Icons.Default.LocalShipping,
        accentIcon1 = Icons.Default.ShoppingCart,
        accentIcon2 = Icons.Default.Checkroom,
        accentColor = Color(0xFF10B981) // Emerald
    )
)

@Composable
fun OnboardingScreen(onSkip: () -> Unit, onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Decorative Gradients
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.4f)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Brand100.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFDBEAFE).copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < onboardingPages.size - 1) {
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 600.dp)
            ) { pageIndex ->
                val page = onboardingPages[pageIndex]
                OnboardingPageContent(page)
            }

            // Footer
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Pagination dots
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(onboardingPages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 6.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                        if (index < onboardingPages.size - 1) Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < onboardingPages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (pagerState.currentPage == onboardingPages.size - 1) "Get Started" else "Next Step",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image/Icon Container
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(48.dp))
                .background(Brand50.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            // Floating Secondary Icon 1 (Top End - Clothes Hanger)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .rotate(12f)
                    .size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        page.accentIcon1,
                        contentDescription = null,
                        tint = page.accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Floating Secondary Icon 2 (Bottom Start - Ruler)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-8).dp, y = 8.dp)
                    .rotate(-8f)
                    .size(60.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        page.accentIcon2,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Main Core Icon
            Surface(
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        page.mainIcon,
                        contentDescription = null,
                        tint = page.accentColor,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Feature Tag
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = Brand50,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(page.accentColor, CircleShape))
                Text(
                    page.tag,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = page.accentColor,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Text(
            text = page.title,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = page.description,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingPreview() {
    NurseWearConnectTheme {
        OnboardingScreen({}, {})
    }
}
