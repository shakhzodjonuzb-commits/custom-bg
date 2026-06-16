package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreHelper {

    /**
     * Saves the final composited bitmap to Pictures/Custom BG/ as PNG.
     * Generates a filename with form: image_yyyyMMdd_HHmmss.png
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "image_$timeStamp.png"
        val relativePath = "Pictures/Custom BG"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                // Fallback for Android 9 / API 28 and below
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Custom BG")
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val file = File(directory, fileName)
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            try {
                val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
                outputStream?.let {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    it.flush()
                    it.close()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            } catch (e: Exception) {
                e.printStackTrace()
                // Rollback insertion on fail
                resolver.delete(imageUri, null, null)
            }
        }
        return null
    }
}
