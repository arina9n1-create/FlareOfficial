package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.remote.Backend

object ShareUtils {

    fun shareProfile(context: Context, handle: String) {
        val url = "${Backend.BASE_WEB_URL}/@$handle"
        val text = "Check out @$handle on Vyn9\n$url"
        launchShareIntent(context, text)
    }

    fun sharePost(context: Context, remoteId: String, caption: String?) {
        val url = "${Backend.BASE_WEB_URL}/post/$remoteId"
        val text = if (!caption.isNullOrBlank()) {
            "$caption\n\nView this on Vyn9:\n$url"
        } else {
            "View this post on Vyn9:\n$url"
        }
        launchShareIntent(context, text)
    }

    fun shareReel(context: Context, remoteId: String, caption: String?) {
        val url = "${Backend.BASE_WEB_URL}/reel/$remoteId"
        val text = if (!caption.isNullOrBlank()) {
            "Watch this reel by @${caption.substringAfter("@").substringBefore(" ")} on Vyn9:\n$url"
        } else {
            "Watch this reel on Vyn9:\n$url"
        }
        // Note: Reels in this project seem to have a different caption structure. 
        // I'll refine the reel share text to be more generic if handle isn't easily extractable.
        val finalReelText = "Watch this reel on Vyn9:\n$url"
        launchShareIntent(context, finalReelText)
    }

    fun shareVideo(context: Context, videoId: String, title: String?) {
        val url = "${Backend.BASE_WEB_URL}/video/$videoId"
        val text = if (!title.isNullOrBlank()) {
            "$title\n\nWatch this on Vyn9:\n$url"
        } else {
            "Watch this video on Vyn9:\n$url"
        }
        launchShareIntent(context, text)
    }

    private fun launchShareIntent(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }
}
