package com.example.data.model

data class MediaUploadResult(
    val url: String = "",
    val storagePath: String = "",
    val thumbnailPath: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0,
    val provider: String = "cloudflare_r2"
)
