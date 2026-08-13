package com.example.nursewearconnect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*

@Composable
fun OrderTimelineView(currentStep: Int, stages: List<String> = listOf("Ordered", "Paid", "Processing", "Delivered")) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stages.forEachIndexed { index, stage ->
                val isCompleted = index <= currentStep
                val isActive = index == currentStep
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (isCompleted) Brand600 else Slate200,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted && !isActive) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        } else {
                            Text(
                                (index + 1).toString(),
                                color = if (isCompleted) Color.White else Slate500,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stage,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) Brand600 else if (isCompleted) Slate700 else Slate400
                    )
                }
            }
        }
        
        // Connection line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-38).dp) // Align with circles
                .height(2.dp)
                .background(Slate100)
        ) {
            val progress = if (stages.size > 1) currentStep.toFloat() / (stages.size - 1) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Brand600)
            )
        }
    }
}

@Composable
fun OrderTimelineView(status: String) {
    val stages = listOf("Pending", "Processing", "Shipped", "Delivered")
    val currentStageIndex = stages.indexOfFirst { it.equals(status, ignoreCase = true) }.let { if (it == -1) 0 else it }
    OrderTimelineView(currentStep = currentStageIndex, stages = stages)
}
