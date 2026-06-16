package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

object BitmapUtils {

    /**
     * Converts an image from a Content URI to a clean Base64 string for API transmission.
     */
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a base64 encoded PNG string back into a system Bitmap.
     */
    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads a Bitmap from a Content URI.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compositions/Flattens the transparent foreground PNG onto the chosen background.
     * Scale/Crop background to fill completely (center crop style) and place the
     * foreground cleanly scaled in the center.
     */
    fun compositeBitmaps(background: Bitmap, foreground: Bitmap): Bitmap {
        val bgWidth = background.width
        val bgHeight = background.height

        // 1. Create a matching mutable bitmap
        val result = Bitmap.createBitmap(bgWidth, bgHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 2. Draw background first
        canvas.drawBitmap(background, 0f, 0f, null)

        // 3. Center and beautifully scale the foreground PNG
        val fgWidth = foreground.width
        val fgHeight = foreground.height

        // Let the foreground cover up to 85% of the background height or width to look natural
        val scale = Math.min(
            (bgWidth * 0.85f) / fgWidth,
            (bgHeight * 0.85f) / fgHeight
        )

        val targetWidth = (fgWidth * scale).toInt().coerceAtLeast(10)
        val targetHeight = (fgHeight * scale).toInt().coerceAtLeast(10)

        val scaledForeground = Bitmap.createScaledBitmap(foreground, targetWidth, targetHeight, true)

        val left = (bgWidth - targetWidth) / 2f
        val top = (bgHeight - targetHeight) / 2f

        canvas.drawBitmap(scaledForeground, left, top, null)

        if (scaledForeground != foreground) {
            scaledForeground.recycle()
        }

        return result
    }
}
