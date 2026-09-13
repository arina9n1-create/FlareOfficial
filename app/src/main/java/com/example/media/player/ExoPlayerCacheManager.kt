package com.example.media.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.remote.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * -------------------------------------------------------------
 * ExoPlayer Cloud Caching & Buffer Optimization Engine
 * -------------------------------------------------------------
 * - SimpleCache with LeastRecentlyUsedCacheEvictor (200MB max)
 * - CacheDataSource for offline playback & zero-latency instant start
 * - Background pre-caching of upcoming reels (preload first 2MB)
 * - Low-latency customized DefaultLoadControl
 * - Reusable player instances and bandwidth monitoring
 */
@OptIn(UnstableApi::class)
object ExoPlayerCacheManager {
    private const val TAG = "ExoPlayerCacheManager"
    private const val CACHE_DIR_NAME = "flareofficial_media3_video_cache"
    private const val MAX_CACHE_SIZE_BYTES = 200L * 1024L * 1024L // 200 MB LRU Cache
    private const val PRELOAD_BYTES = 4L * 1024L * 1024L // ~4 MB preload per reel (~1/3 of a typical reel)

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Volatile
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    /** Kept so auth headers can be refreshed without rebuilding the cache factory. */
    @Volatile
    private var httpDataSourceFactory: OkHttpDataSource.Factory? = null

    private val preloadJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initializes the Singleton Disk Cache with LRU Evictor.
     */
    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheFolder = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
            databaseProvider = dbProvider
            simpleCache = SimpleCache(cacheFolder, evictor, dbProvider)
            Log.d(TAG, "SimpleCache initialized with max size 200MB at ${cacheFolder.absolutePath}")
        }
        return simpleCache!!
    }

    /**
     * Builds and caches the CacheDataSource.Factory.
     */
    @Synchronized
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        if (cacheDataSourceFactory == null) {
            val cache = getCache(context)

            // Upstream HTTP Data Source using OkHttp. IMPORTANT: OkHttp (unlike
            // DefaultHttpDataSource) automatically strips Authorization/apikey
            // headers when following a cross-host redirect. Our r2-download
            // gateway responds 302 to a presigned R2 URL — if the Bearer header
            // were forwarded, R2 S3 rejects it with HTTP 400 "Only one auth
            // mechanism", which surfaced as infinite "Buffering Stream...".
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val httpFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("FlareOfficial-VideoPlayer/1.0 (Linux; Android; ExoPlayer/Media3)")
            httpDataSourceFactory = httpFactory

            // Default Data Source for fallback (assets / raw resources)
            val defaultDataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpFactory)

            cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        // IMPORTANT: auth headers must be re-applied on EVERY playback session —
        // the Supabase access token rotates (expires after ~1h), and a factory
        // created with a stale token would 401 forever (white reels).
        applyAuthHeaders(context)
        return cacheDataSourceFactory!!
    }

    /**
     * Pushes the CURRENT access token into the shared HTTP data source factory.
     * Safe to call on every player creation; keeps playback working across
     * token refreshes instead of baking in an expired one.
     */
    @Synchronized
    fun applyAuthHeaders(context: Context) {
        val httpFactory = httpDataSourceFactory ?: return
        val authPrefs = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        val accessToken = authPrefs.getString("access_token", null)
            ?.takeIf { authPrefs.getBoolean("session_valid", false) && it.isNotBlank() }
        val requestHeaders = mutableMapOf("apikey" to Backend.KEY)
        accessToken?.let { requestHeaders["Authorization"] = "Bearer $it" }
        httpFactory.setDefaultRequestProperties(requestHeaders)
    }



    /**
     * Ensures the cached Supabase access token is still valid BEFORE playback starts.
     * The r2-download media gateway rejects expired tokens with 401, which makes
     * reels sit in "Buffering Stream..." forever. Supabase access tokens expire
     * after ~1 hour, so proactively refresh a stale (or nearly stale) token and
     * re-apply the auth headers to the shared HTTP data source factory.
     */
    suspend fun ensureFreshToken(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("session_valid", false)) return
        val token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() } ?: return
        val expirySeconds = tokenJwtExpirySeconds(token) ?: return
        val nowSeconds = System.currentTimeMillis() / 1000
        if (expirySeconds - nowSeconds < 120) { // expiring in <2 min -> refresh first
            try {
                val result = com.example.data.remote.SupabaseService(context).refreshAuthSession()
                Log.d(TAG, "Stale access token refreshed before playback: $result")
            } catch (e: Exception) {
                Log.w(TAG, "Token refresh before playback failed", e)
            }
        }
        // Re-apply headers so the (possibly renewed) token reaches the data source.
        applyAuthHeaders(context)
    }

    private fun tokenJwtExpirySeconds(jwt: String): Long? = try {
        val payloadPart = jwt.split(".")[1]
        val payload = String(
            android.util.Base64.decode(
                payloadPart,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
        )
        val exp = org.json.JSONObject(payload).optLong("exp", -1L)
        exp.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }

    /**
     * Creates an optimized ExoPlayer instance tailored for vertical short-form Reels & Video Feeds.
     */
    fun createOptimizedExoPlayer(context: Context, isMuted: Boolean = false): ExoPlayer {
        val appContext = context.applicationContext

    // Fast-start: begin buffering in ~300ms of data instead of 1s,
    // and keep 8s min buffer so scrolling never stalls.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 8_000,
            /* maxBufferMs = */ 40_000,
            /* bufferForPlaybackMs = */ 300,
            /* bufferForPlaybackAfterRebufferMs = */ 500
        )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 2. Audio attributes for clean focus and ducking
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // 3. Track selector for adaptive bitrate
        val trackSelector = DefaultTrackSelector(appContext)

        val player = ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = if (isMuted) 0f else 1f
        return player
    }

    /**
     * Creates a MediaSource using our LRU Cloud Cache DataSource.
     */
    fun createCachedMediaSource(context: Context, uri: Uri): MediaSource {
        val factory = getCacheDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
    }

    /**
     * Warms up the shared media cache + data source factory as soon as the app
     * opens (not just when the Reels tab is entered), so the first reel starts
     * playing instantly no matter which tab the user is on.
     */
    fun warmUp(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                ensureFreshToken(appContext)
                getCacheDataSourceFactory(appContext)
                Log.d(TAG, "Media cache warm-up complete")
            } catch (e: Exception) {
                Log.w(TAG, "Media cache warm-up failed", e)
            }
        }
    }

    /**
     * Pre-caches upcoming video reels in the background (preload first ~4MB —
     * roughly a third of a typical reel).
     * When the user scrolls to this reel, playback starts instantly with 0ms buffering!
     */
    fun preloadVideo(context: Context, videoUrl: String) {
        if (videoUrl.isBlank() || !videoUrl.startsWith("http")) return
        if (preloadJobs.containsKey(videoUrl)) return

        val job = scope.launch {
            try {
                val uri = Uri.parse(videoUrl)
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setLength(PRELOAD_BYTES)
                    .build()

                val dataSource: DataSource = getCacheDataSourceFactory(context).createDataSource()
                val cacheWriter = CacheWriter(
                    /* dataSource = */ dataSource as CacheDataSource,
                    /* dataSpec = */ dataSpec,
                    /* temporaryBuffer = */ null,
                    /* listener = */ object : CacheWriter.ProgressListener {
                        override fun onProgress(requestLength: Long, bytesCached: Long, newBytesCached: Long) {
                            val percent = if (requestLength > 0) (bytesCached * 100 / requestLength) else 0
                            Log.v(TAG, "Preload progress for $videoUrl: $percent% ($bytesCached bytes)")
                        }
                    }
                )

                cacheWriter.cache()
                Log.d(TAG, "Preloaded first 2MB successfully for: $videoUrl")
            } catch (e: Exception) {
                Log.w(TAG, "Preload cancelled or failed for $videoUrl: ${e.message}")
            } finally {
                preloadJobs.remove(videoUrl)
            }
        }
        preloadJobs[videoUrl] = job
    }

    /**
     * Cancel preloading for a specific URL if user skipped past it.
     */
    fun cancelPreload(videoUrl: String) {
        preloadJobs[videoUrl]?.cancel()
        preloadJobs.remove(videoUrl)
    }

    /**
     * Checks if a given video URL is cached in disk.
     */
    fun isVideoCached(context: Context, videoUrl: String): Boolean {
        return try {
            val cache = getCache(context)
            val uri = Uri.parse(videoUrl)
            val dataSpec = DataSpec(uri)
            val key = dataSpec.key ?: uri.toString()
            val cachedBytes = cache.getCachedBytes(key, 0, C.LENGTH_UNSET.toLong())
            cachedBytes > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns total cache size formatted (e.g., "34.5 MB").
     */
    fun getFormattedCacheSize(context: Context): String {
        return try {
            val cache = getCache(context)
            val bytes = cache.cacheSpace
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } catch (_: Exception) {
            "0.0 MB"
        }
    }

    /**
     * Clears all cached video chunks from disk.
     */
    fun clearCache(context: Context) {
        scope.launch {
            try {
                val cache = getCache(context)
                val keys = cache.keys
                for (key in keys) {
                    cache.removeResource(key)
                }
                Log.d(TAG, "ExoPlayer video disk cache successfully cleared.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear video cache", e)
            }
        }
    }
}
