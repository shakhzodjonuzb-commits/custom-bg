package com.example.data

import android.net.Uri
import android.graphics.Bitmap

sealed class AppUiState {
    
    // 1. Initial State: Showing back.mp4 video and select.png button
    object SelectingOriginal : AppUiState()

    // 2. Loading State: processing background removal with Firebase Function
    data class Processing(val message: String = "Orqa fon tozalanmoqda...") : AppUiState()

    // 3. Foreground Preview State: Transparent foreground PNG successfully obtained
    data class PreviewForeground(
        val originalUri: Uri,
        val transparentBitmap: Bitmap
    ) : AppUiState()

    // 4. Composited State: Custom background selected and combined with transparent foreground
    data class Composited(
        val originalUri: Uri,
        val transparentBitmap: Bitmap,
        val backgroundUri: Uri,
        val backgroundBitmap: Bitmap,
        val compositedBitmap: Bitmap
    ) : AppUiState()

    // 5. Saving State: saving PNG file to MediaStore gallery
    data class Saving(val compositeBitmap: Bitmap) : AppUiState()

    // 6. Success State: saved to Pictures/Custom BG/
    data class Success(val savedUri: Uri, val message: String = "Rasm yutuqli saqlandi!") : AppUiState()

    // 7. Error State: general failures (network, file empty, api errors)
    data class Error(
        val errorMessage: String,
        val onRetry: () -> Unit,
        val onCancel: () -> Unit
    ) : AppUiState()
}
