package com.example.util

import com.example.data.remote.Backend

/**
 * Centralized resolver for all media content in FlareOfficial.
 * Handles stable storage paths and legacy full URLs.
 * 
 * DESIGN PRINCIPLE:
 * The database stores only the 'object_key' (path). This resolver converts
 * that key into a delivery URL based on the current storage configuration.
 * This makes the app migration-friendly: changing storage providers only
 * requires updating this resolver.
 */
object MediaStorageResolver {

    // Current authoritative storage gateway base
    private val GATEWAY_BASE = "${Backend.URL}/functions/v1/r2-download"

    /**
     * Resolves a storage reference into a fully qualified, downloadable URL.
     * 
     * @param storageReference The legacy path/URL from the DB.
     * @param storagePath The authoritative stable object key (prioritized).
     * @param provider The storage provider (e.g., "cloudflare_r2").
     * @return A valid URL for Coil/ExoPlayer, or the original string if it's a preset.
     */
    fun resolve(
        storageReference: String?, 
        storagePath: String? = null,
        provider: String = "cloudflare_r2"
    ): String {
        val key = (storagePath ?: storageReference)?.trim()
        if (key.isNullOrBlank()) return ""
        
        // 1. Handle Presets & Special Values
        if (key == "default" || key == "none" || key == "null") {
            return key
        }

        // 2. Handle Android Local URIs
        if (key.startsWith("content://", ignoreCase = true) || 
            key.startsWith("file://", ignoreCase = true)) {
            return key
        }

        // 3. Handle Full URLs (HTTP/HTTPS)
        if (key.startsWith("http://", ignoreCase = true) || 
            key.startsWith("https://", ignoreCase = true)) {
            
            // SECURITY/ROBUSTNESS: If this is an OLD gateway URL, a raw B2 URL, or a Cloudflare R2 URL,
            // we rewrite it to use the current authoritative gateway.
            if (key.contains("supabase.co/functions/v1/") || 
                key.contains(".backblazeb2.com/") ||
                key.contains(".r2.cloudflarestorage.com/")) {
                val extractedPath = extractPath(key)
                if (extractedPath.isNotBlank()) {
                    return buildGatewayUrl(extractedPath)
                }
            }
            return key
        }

        // 4. Handle Stable Object Keys (The current architecture)
        // keys like "profiles/<uid>/<file>" are resolved through the gateway.
        return buildGatewayUrl(key)
    }

    /**
     * Normalizes any media value into a storage-independent OBJECT KEY suitable
     * for persisting in the database.
     *
     * - Gateway URLs (.../r2-download?path=KEY)  -> KEY
     * - Raw R2/B2 bucket URLs .../bucket/KEY     -> KEY
     * - Object keys / presets / local URIs       -> unchanged
     * - Foreign http(s) URLs (not ours)          -> unchanged (best effort)
     *
     * Call this at every DB WRITE boundary (SupabaseService payload builders)
     * so the database never stores generated delivery URLs. Delivery URLs are
     * generated ONLY at read time via [resolve].
     */
    fun toStorableKey(value: String?): String {
        val v = value?.trim() ?: return ""
        if (v.isBlank() || v == "default" || v == "none" || v == "null") return v

        // Local Android URIs are transient session-scoped references; pass through
        // (callers must upload them and persist the returned key instead).
        if (v.startsWith("content://", ignoreCase = true) ||
            v.startsWith("file://", ignoreCase = true)) {
            return v
        }

        if (v.startsWith("http://", ignoreCase = true) ||
            v.startsWith("https://", ignoreCase = true)) {
            // Only rewrite URLs that belong to our own gateways/buckets.
            if (v.contains("supabase.co/functions/v1/") ||
                v.contains(".backblazeb2.com/") ||
                v.contains(".r2.cloudflarestorage.com/") ||
                v.contains(".r2.dev/")) {
                val extracted = extractPath(v)
                if (extracted.isNotBlank()) return extracted
            }
            // Unknown host: keep as-is (legacy data must keep working).
            return v
        }

        // Already an object key (e.g. "reels/<uid>/reel_123.mp4") or a preset.
        return v
    }

    private fun buildGatewayUrl(path: String): String {
        val cleanPath = path.trim().removePrefix("/")
        
        // URL Parameter encoding for safety
        val encoded = try {
            java.net.URLEncoder.encode(cleanPath, "UTF-8")
        } catch (_: Exception) { cleanPath }
        
        return "$GATEWAY_BASE?path=$encoded"
    }

    private fun extractPath(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            // 1. Gateway URL check
            val queryPath = uri.getQueryParameter("path")
            if (!queryPath.isNullOrBlank()) return queryPath

            // 2. Raw S3-style URL check (R2/B2)
            if (url.contains(".r2.cloudflarestorage.com/")) {
                return url.substringAfter(".r2.cloudflarestorage.com/", "")
            }
            if (url.contains(".r2.dev/")) {
                return url.substringAfter(".r2.dev/", "")
            }
            if (url.contains(".backblazeb2.com/")) {
                val afterBucket = url.substringAfter(".backblazeb2.com/", "")
                return if (afterBucket.startsWith("file/")) {
                    val parts = afterBucket.split("/")
                    if (parts.size > 2) parts.subList(2, parts.size).joinToString("/") else afterBucket
                } else afterBucket
            }
            ""
        } catch (_: Exception) { "" }
    }

    /**
     * Legacy B2 check for cleanup.
     */
    fun isBrokenLegacyB2(storageReference: String?, storagePath: String? = null): Boolean {
        val reference = (storagePath ?: storageReference)?.trim() ?: return false
        if (reference.isBlank() || reference == "default" || reference == "none") return false
        
        return reference.contains(".backblazeb2.com/") || 
               reference.contains("functions/v1/b2-download") ||
               reference.startsWith("users/")
    }
}
