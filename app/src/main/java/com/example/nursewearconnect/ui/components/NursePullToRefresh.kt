package com.example.nursewearconnect.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.nursewearconnect.ui.theme.Brand600
import com.example.nursewearconnect.ui.theme.Slate100
import com.example.nursewearconnect.ui.theme.Slate50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    screenIcon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            NurseRefreshIndicator(state, isRefreshing, screenIcon)
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NurseRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    screenIcon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRefreshing && state.distanceFraction > 0f) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Brand600,
                strokeWidth = 3.dp
            )
        } else if (!isRefreshing) {
            // Rotating and scaling icon as you pull
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        // Rotation driven by pull distance
                        rotationZ = state.distanceFraction * 360f
                        // Scaling up from 0 to 1
                        val scale = state.distanceFraction.coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                        alpha = scale
                    },
                shape = CircleShape,
                color = Slate50,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = screenIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Brand600
                    )
                }
            }
        }
    }
}
