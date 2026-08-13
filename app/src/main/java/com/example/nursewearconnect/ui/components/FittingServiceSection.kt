package com.example.nursewearconnect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FittingServiceSection(
    isRequested: Boolean,
    onRequestedChange: (Boolean) -> Unit,
    fittingDate: String?,
    fittingSlot: String?,
    onAppointmentSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showSlotPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isRequested) Brand50.copy(alpha = 0.5f) else Color.White,
            border = BorderStroke(1.dp, if (isRequested) Brand200 else Slate200),
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (isRequested) Brand100 else Slate100
                    ) {
                        Icon(
                            Icons.Default.Checkroom,
                            contentDescription = null,
                            tint = if (isRequested) Brand600 else Slate500,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Home Fitting Service",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            "Try before you buy at your doorstep",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                    Switch(
                        checked = isRequested,
                        onCheckedChange = onRequestedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Brand600,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate200
                        )
                    )
                }

                if (isRequested) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Brand200.copy(alpha = 0.5f))
                    
                    Text(
                        "Schedule Appointment",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Date Picker Trigger
                        Surface(
                            modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, if (fittingDate != null) Brand300 else Slate300)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = if (fittingDate != null) Brand600 else Slate400)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    fittingDate ?: "Select Date",
                                    fontSize = 12.sp,
                                    color = if (fittingDate != null) Slate900 else Slate400
                                )
                            }
                        }
                        
                        // Slot Picker Trigger
                        Surface(
                            modifier = Modifier.weight(1f).clickable { showSlotPicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, if (fittingSlot != null) Brand300 else Slate300)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp), tint = if (fittingSlot != null) Brand600 else Slate400)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    fittingSlot ?: "Select Slot",
                                    fontSize = 12.sp,
                                    color = if (fittingSlot != null) Slate900 else Slate400
                                )
                            }
                        }
                    }
                    
                    Text(
                        "Service Fee: KSh 150 (Non-refundable)",
                        fontSize = 10.sp,
                        color = Brand700,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        val dateString = sdf.format(java.util.Date(it))
                        onAppointmentSelected(dateString, fittingSlot ?: "")
                    }
                    showDatePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Slot Picker Bottom Sheet / Dialog
    if (showSlotPicker) {
        val slots = listOf("09:00 AM - 11:00 AM", "11:00 AM - 01:00 PM", "02:00 PM - 04:00 PM", "04:00 PM - 06:00 PM")
        AlertDialog(
            onDismissRequest = { showSlotPicker = false },
            title = { Text("Available Slots", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    slots.forEach { slot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAppointmentSelected(fittingDate ?: "", slot)
                                    showSlotPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = fittingSlot == slot, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(slot, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
