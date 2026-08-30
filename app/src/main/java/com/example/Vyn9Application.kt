package com.example

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.remote.Backend
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Application-level setup.
 *
 * Registers a global Coil [ImageLoader] whose HTTP client attaches the Supabase
 * `apikey` plus the logged-in user's access token to every media request aimed at
 * the secure `b2-download` Edge Function. The Backblaze bucket stays fully PRIVATE,
 * so images only load for authenticated app sessions; everyone else (and every raw
 * bucket URL) gets a 401, which the avatar/post placeholders render gracefully.
 */
class Vyn9Application : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { buildMediaHttpClient() }
            .crossfade(true)
            .build()
    }

    private fun buildMediaHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain: Interceptor.Chain ->
            val request = chain.request()
            val url = request.url
            val isSecureMediaRequest =
                url.host.endsWith("supabase.co", ignoreCase = true) &&
                        url.encodedPath.startsWith("/functions/v1/b2-download")

            val outgoing = if (isSecureMediaRequest) {
                request.newBuilder()
                    .header("apikey", Backend.KEY)
                    .apply {
                        persistedAccessToken()?.let { header("Authorization", "Bearer $it") }
                    }
                    .build()
            } else {
                request
            }
            chain.proceed(outgoing)
        }

        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .build()
    }

    // Same persisted session store that SupabaseService uses for PostgREST auth.
    private fun persistedAccessToken(): String? {
        val prefs = getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("session_valid", false)) return null
        return prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
    }
}
