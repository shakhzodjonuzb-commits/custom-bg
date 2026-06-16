package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppUiState
import com.example.network.NetworkClient
import com.example.network.RemoveBgRequest
import com.example.utils.BitmapUtils
import com.example.utils.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BgRemoverViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.SelectingOriginal)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val tag = "BgRemoverViewModel"

    /**
     * Resets state machine back to the first screen
     */
    fun resetToStart() {
        _uiState.value = AppUiState.SelectingOriginal
    }

    /**
     * Resets state back to foreground preview (removes currently chosen background image)
     */
    fun cancelBackgroundSelection(originalUri: Uri, transparentBitmap: android.graphics.Bitmap) {
        _uiState.value = AppUiState.PreviewForeground(originalUri, transparentBitmap)
    }

    /**
     * Sends original selected image to Cloud Function backend for background removal
     */
    fun processOriginalImage(context: Context, originalUri: Uri) {
        _uiState.value = AppUiState.Processing("Rasm yuklanmoqda va qayta ishlanmoqda...")

        viewModelScope.launch {
            try {
                // 1. Convert to base64
                val base64Input = withContext(Dispatchers.IO) {
                    BitmapUtils.uriToBase64(context, originalUri)
                }

                if (base64Input == null) {
                    _uiState.value = AppUiState.Error(
                        errorMessage = "Rasm ma'lumotlarini o'qishda xatolik. Iltimos qayta urinib ko'ring.",
                        onRetry = { processOriginalImage(context, originalUri) },
                        onCancel = { resetToStart() }
                    )
                    return@launch
                }

                // 2. Fetch endpoint URL from BuildConfig
                val endpointUrl = BuildConfig.FIREBASE_BACKEND_URL
                Log.d(tag, "Endpoint URL configured as: $endpointUrl")

                if (endpointUrl.isBlank() || endpointUrl.contains("your-project-id")) {
                    throw IllegalStateException(
                        "Firebase Cloud Function URL (.env faylidagi FIREBASE_BACKEND_URL) sozlanmagan!\n" +
                        "Iltimos, uni real HTTPS endpointingiz bilan almashtiring."
                    )
                }

                // 3. Make Retrofit call
                val request = RemoveBgRequest(image = base64Input)
                val response = withContext(Dispatchers.IO) {
                    NetworkClient.apiService.removeBackground(endpointUrl, request)
                }

                // 4. Handle errors from server response
                if (response.error != null) {
                    throw Exception(response.details ?: response.error)
                }

                val base64Output = response.image
                if (base64Output.isNullOrEmpty()) {
                    throw Exception("Orka fonni tozalovchi API rasm ma'lumotlarini qaytarmadi.")
                }

                // 5. Decode to Bitmap and present preview
                val transparentBitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.base64ToBitmap(base64Output)
                }

                if (transparentBitmap == null) {
                    throw Exception("Olingan PNG transparent rasmni ekranga chiqarib bo'lmadi.")
                }

                _uiState.value = AppUiState.PreviewForeground(originalUri, transparentBitmap)

            } catch (e: Exception) {
                Log.e(tag, "Failed to remove background", e)
                val cleanMessage = when {
                    e is IllegalStateException -> e.message ?: ""
                    e is java.net.UnknownHostException -> "Internet aloqasi mavjud emas. Tarmoq sozlamalarini tekshiring."
                    e is java.net.SocketTimeoutException -> "Server javob berish vaqti tugadi. Qayta urinib ko'ring."
                    else -> "Background tozalashda xatolik yuz berdi: ${e.localizedMessage ?: e.message}"
                }

                _uiState.value = AppUiState.Error(
                    errorMessage = cleanMessage,
                    onRetry = { processOriginalImage(context, originalUri) },
                    onCancel = { resetToStart() }
                )
            }
        }
    }

    /**
     * Custom Background selection helper. Draws transparent png centered onto selected scenery
     */
    fun selectBackground(context: Context, backgroundUri: Uri, originalUri: Uri, transparentBitmap: android.graphics.Bitmap) {
        _uiState.value = AppUiState.Processing("Yangi orqa fon tayyorlanmoqda...")

        viewModelScope.launch {
            try {
                val backgroundBitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.loadBitmapFromUri(context, backgroundUri)
                }

                if (backgroundBitmap == null) {
                    _uiState.value = AppUiState.Error(
                        errorMessage = "Tanlangan fon rasmini yuklashda xatolik. Boshqa rasm tanlang.",
                        onRetry = { selectBackground(context, backgroundUri, originalUri, transparentBitmap) },
                        onCancel = { cancelBackgroundSelection(originalUri, transparentBitmap) }
                    )
                    return@launch
                }

                // Create the composite picture in IO thread to avoid lagging UI
                val composited = withContext(Dispatchers.IO) {
                    BitmapUtils.compositeBitmaps(backgroundBitmap, transparentBitmap)
                }

                _uiState.value = AppUiState.Composited(
                    originalUri = originalUri,
                    transparentBitmap = transparentBitmap,
                    backgroundUri = backgroundUri,
                    backgroundBitmap = backgroundBitmap,
                    compositedBitmap = composited
                )

            } catch (e: Exception) {
                Log.e(tag, "Composite failed", e)
                _uiState.value = AppUiState.Error(
                    errorMessage = "Rasm kompozitsiyasida xatolikka yo'l qo'yildi: ${e.localizedMessage}",
                    onRetry = { selectBackground(context, backgroundUri, originalUri, transparentBitmap) },
                    onCancel = { cancelBackgroundSelection(originalUri, transparentBitmap) }
                )
            }
        }
    }

    /**
     * Saves composite artwork to Gallery using MediaStore
     */
    fun saveCompositedImage(context: Context, state: AppUiState.Composited) {
        _uiState.value = AppUiState.Saving(state.compositedBitmap)

        viewModelScope.launch {
            try {
                val savedUri = withContext(Dispatchers.IO) {
                    MediaStoreHelper.saveToGallery(context, state.compositedBitmap)
                }

                if (savedUri != null) {
                    _uiState.value = AppUiState.Success(savedUri)
                } else {
                    _uiState.value = AppUiState.Error(
                        errorMessage = "Tayyor rasm faylini galereyaga saqlash imkoni bo'lmadi.",
                        onRetry = { saveCompositedImage(context, state) },
                        onCancel = { _uiState.value = state }
                    )
                }

            } catch (e: Exception) {
                Log.e(tag, "Save failed", e)
                _uiState.value = AppUiState.Error(
                    errorMessage = "Sistemada saqlash xatoligi: ${e.localizedMessage}",
                    onRetry = { saveCompositedImage(context, state) },
                    onCancel = { _uiState.value = state }
                )
            }
        }
    }
}
