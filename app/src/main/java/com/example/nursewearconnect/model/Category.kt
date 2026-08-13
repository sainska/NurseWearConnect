package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName

data class Category(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerializedName("icon_name")
    val iconName: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("created_at")
    val createdAt: String? = null
)
