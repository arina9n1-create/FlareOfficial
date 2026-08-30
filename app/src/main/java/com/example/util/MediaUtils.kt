package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

object MediaUtils {
    /**
     * Compresses an image from a Uri into a ByteArray.
     * Reduces dimensions if they exceed maxWidth/maxHeight and applies JPEG compression.
     */
    fun compressImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1080,
        maxHeight: Int = 1920,
        quality: Int = 75
    ): ByteArray? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Calculate sample size to reduce memory usage during decode
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            val bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            bitmap?.let {
                // Further scale if still too large
                val scaledBitmap = if (it.width > maxWidth || it.height > maxHeight) {
                    val ratio = Math.min(maxWidth.toFloat() / it.width, maxHeight.toFloat() / it.height)
                    Bitmap.createScaledBitmap(it, (it.width * ratio).toInt(), (it.height * ratio).toInt(), true)
                } else {
                    it
                }

                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val result = outputStream.toByteArray()
                
                if (scaledBitmap != it) scaledBitmap.recycle()
                it.recycle()
                
                android.util.Log.d("MediaUtils", "Compressed image from ${options.outWidth}x${options.outHeight} to byte size: ${result.size}")
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaUtils", "Compression error", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
