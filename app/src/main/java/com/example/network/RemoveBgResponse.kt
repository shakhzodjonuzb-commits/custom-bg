package com.example.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoveBgResponse(
    val image: String?, // Compiled base64 transparent PNG
    val error: String?,
    val details: String? = null
)
