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
 * apikey plus the logged-in user's access token to every media request aimed at
 * the secure `r2-download` Edge Function. The Cloudflare R2 bucket stays fully PRIVATE,
 * so images only load for authenticated app sessions; everyone else (and every raw
 * bucket URL) gets a 401, which the avatar/post placeholders render gracefully.
 */
class FlareOfficialApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Create notification channels as early as possible (not only in
        // MainActivity) so system-displayed FCM notifications — which can
        // arrive before any Activity is ever launched — always have their
        // high-importance channels available. Idempotent by design.
        try {
            com.example.data.notification.NotificationHelper.createNotificationChannels(this)
        } catch (t: Throwable) {
            android.util.Log.e("FlareOfficialApp", "Failed to create notification channels", t)
        }
    }

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
                (url.host.endsWith("supabase.co", ignoreCase = true) || 
                 url.host.contains(Backend.URL.substringAfter("://").substringBefore("/"))) && (
                    url.encodedPath.contains("/functions/v1/r2-download") ||
                    url.encodedPath.contains("/functions/v1/b2-download") ||
                    url.encodedPath.contains("/storage/v1/object/authenticated/")
                )

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
        val prefs = getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("session_valid", false)) return null
        return prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
    }
}