package com.example.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoveBgRequest(
    val image: String // Base64 encoded JPEG/PNG input
)
