package com.example.util

import com.example.data.remote.Backend

/**
 * Centralized resolver for all media content in Vyn9.
 * Handles stable storage paths and legacy full URLs.
 */
object MediaStorageResolver {

    /**
     * Resolves a storage reference into a fully qualified, downloadable URL.
     * 
     * @param storageReference The path from the DB (could be a stable path, a full URL, or a preset).
     * @return A valid URL for Coil/ExoPlayer, or the original string if it's a preset/special value.
     */
    fun resolve(storageReference: String?): String {
        if (storageReference.isNullOrBlank()) return ""
        
        val trimmed = storageReference.trim()
        
        // 1. Handle Presets & Special Values
        if (trimmed == "default" || trimmed == "none" || trimmed == "null") {
            return trimmed
        }

        // 2. Handle Legacy Full URLs (HTTP/HTTPS)
        if (trimmed.startsWith("http://", ignoreCase = true) || 
            trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        // 3. Handle Android Local URIs
        if (trimmed.startsWith("content://", ignoreCase = true) || 
            trimmed.startsWith("file://", ignoreCase = true)) {
            return trimmed
        }

        // 4. Handle Stable Storage Paths (The new architecture)
        // Paths like "users/<uid>/profile/<file>" are resolved through the
        // b2-download Edge Function gateway, which authenticates the viewer
        // and 302-redirects to a short-lived pre-signed B2 URL.
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        return "${Backend.URL}/functions/v1/b2-download?path=$encoded"
    }

    /**
     * Specifically for B2 direct download URLs if needed.
     */
    fun getDirectUrl(storagePath: String?): String {
        if (storagePath.isNullOrBlank()) return ""
        // Placeholder for direct S3-compatible or B2 public URLs if gateway isn't used
        return resolve(storagePath)
    }
}
