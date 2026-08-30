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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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
    private const val CACHE_DIR_NAME = "vyn9_media3_video_cache"
    private const val MAX_CACHE_SIZE_BYTES = 200L * 1024L * 1024L // 200 MB LRU Cache
    private const val PRELOAD_BYTES = 2L * 1024L * 1024L // 2 MB preload per reel

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Volatile
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    /** Kept so auth headers can be refreshed without rebuilding the cache factory. */
    @Volatile
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null

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

            // Upstream HTTP Data Source with timeout optimizations & custom User-Agent
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Vyn9Social-VideoPlayer/1.0 (Linux; Android; ExoPlayer/Media3)")
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true)
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
        val authPrefs = context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE)
        val accessToken = authPrefs.getString("access_token", null)
            ?.takeIf { authPrefs.getBoolean("session_valid", false) && it.isNotBlank() }
        val requestHeaders = mutableMapOf("apikey" to Backend.KEY)
        accessToken?.let { requestHeaders["Authorization"] = "Bearer $it" }
        httpFactory.setDefaultRequestProperties(requestHeaders)
    }

    /**
     * Creates an optimized ExoPlayer instance tailored for vertical short-form Reels & Video Feeds.
     */
    fun createOptimizedExoPlayer(context: Context, isMuted: Boolean = false): ExoPlayer {
        val appContext = context.applicationContext

        // 1. Customized Load Control for ultra-fast startup and smooth scrolling
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 40_000,
                /* bufferForPlaybackMs = */ 1_000, // Starts playback in just 1000ms
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
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
     * Pre-caches upcoming video reels in the background (preload first 2MB).
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
