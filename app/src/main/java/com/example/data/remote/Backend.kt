package com.example.data.remote

import com.example.BuildConfig

object Backend {
    // Supabase project URL and anon key now come from the `.env` file via the
    // Secrets Gradle Plugin (BuildConfig). Values are injected at build time,
    // so no credentials are hardcoded in source.
    val URL: String = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() && !it.contains("placeholder", ignoreCase = true) }
        ?: "https://crlrjkpoxlkbpjfqnyyr.supabase.co"
    val KEY: String = BuildConfig.SUPABASE_KEY.takeIf { it.isNotBlank() && !it.contains("placeholder", ignoreCase = true) }
        ?: ""

    // Centralized configuration for FlareOfficial public web base URL
    const val BASE_WEB_URL = "https://flareofficial.app"
}
