package com.example.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * OkHttp request body that reports upload progress as a percentage (0-100)
 * while the bytes are being streamed to the server.
 */
class ProgressRequestBody(
    private val mediaType: MediaType?,
    private val bytes: ByteArray,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size.toLong()
        if (total == 0L) { onProgress(100); return }
        val chunkSize = 64 * 1024
        var offset = 0
        var lastPct = -1
        while (offset < bytes.size) {
            val toWrite = minOf(chunkSize, bytes.size - offset)
            sink.write(bytes, offset, toWrite)
            offset += toWrite
            val pct = ((offset.toLong() * 100) / total).toInt().coerceIn(0, 100)
            if (pct != lastPct) {
                lastPct = pct
                onProgress(pct)
            }
        }
    }
}
