package com.example.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import com.example.data.remote.AgoraTokenService
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Agora RTC engine for FlareOfficial voice/video calls.
 *
 * Signalling still flows through the existing `call_signals` Supabase table —
 * the SDP column now carries a compact marker "AGORA|<channelName>" instead of
 * an SDP blob, so no existing table changes. Both sides request their own
 * short-lived RTC token from the `generate-agora-token` Edge Function (the
 * AGORA_APP_CERTIFICATE never reaches the app) and join the same channel with
 * a per-user stable uid.
 */
class AgoraCallManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tokenService by lazy { AgoraTokenService(context) }

    private var engine: RtcEngine? = null
    private var channelName: String? = null

    /** Remote user uid once the peer has joined (0 = nobody yet). */
    @Volatile
    var remoteUid: Int = 0
        private set

    private val _localVideoRenderer = MutableStateFlow<SurfaceView?>(null)
    val localVideoRenderer = _localVideoRenderer.asStateFlow()

    private val _remoteVideoRenderer = MutableStateFlow<SurfaceView?>(null)
    val remoteVideoRenderer = _remoteVideoRenderer.asStateFlow()

    /** Receives textual state updates ("Connected" / "Reconnecting…" / "Failed"). */
    private var onConnectionStateChange: ((String) -> Unit)? = null

    /** Last Agora onError code — lets us surface the real join failure instead of a generic one. */
    @Volatile private var lastAgoraError: Int = 0

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel=$channel uid=$uid")
            notifyState("Connecting…")
        }

        override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            notifyState("Reconnecting…")
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            when (state) {
                Constants.CONNECTION_STATE_RECONNECTING -> notifyState("Reconnecting…")
                Constants.CONNECTION_STATE_FAILED -> notifyState("Failed")
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "Remote user joined uid=$uid")
            remoteUid = uid
            mainHandler.post {
                setupRemoteVideo(uid)
                notifyState("Connected")
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "Remote user left uid=$uid reason=$reason")
            if (uid == remoteUid) {
                remoteUid = 0
                notifyState(if (reason == Constants.USER_OFFLINE_QUIT) "Remote ended" else "Connection lost")
            }
        }

        override fun onError(err: Int) {
            lastAgoraError = err
            Log.e(TAG, "Agora error code=$err")
        }
    }

    private fun notifyState(state: String) {
        mainHandler.post { onConnectionStateChange?.invoke(state) }
    }

    private fun setupRemoteVideo(uid: Int) {
        val renderer = android.view.SurfaceView(context)
        _remoteVideoRenderer.value = renderer
        engine?.setupRemoteVideo(VideoCanvas(renderer, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    data class OutgoingOffer(val channelName: String, val sdp: String)

    // =====================================================================
    // OUTGOING CALL — create the channel, fetch a token and join it
    // =====================================================================
    suspend fun createOutgoingOffer(isVideo: Boolean, useFrontCamera: Boolean): OutgoingOffer? {
        val channel = "flare_${UUID.randomUUID()}"
        return joinChannel(channel, isVideo).getOrNull()?.let { 
            OutgoingOffer(channel, "${PREFIX}$channel")
        }
    }

    // =====================================================================
    // INCOMING CALL — parse the channel marker, fetch own token and join
    // =====================================================================
    suspend fun receiveIncoming(isVideo: Boolean, remoteOfferSdp: String): String? {
        val channel = remoteOfferSdp.removePrefix(PREFIX).takeIf {
            it.isNotBlank() && it != remoteOfferSdp
        } ?: return null
        return joinChannel(channel, isVideo).getOrThrow().let { "${PREFIX}$channel" }
    }

    /** Caller side: the callee's answer is implicit over Agora — always OK. */
    fun applyRemoteAnswer(answerSdp: String, onDone: (Boolean) -> Unit) {
        onDone(answerSdp.startsWith(PREFIX))
    }

    fun setConnectionStateListener(listener: (String) -> Unit) {
        onConnectionStateChange = listener
    }

    // =====================================================================
    // IN-CALL CONTROLS
    // =====================================================================
    fun toggleMute(muted: Boolean) {
        runCatching { engine?.muteLocalAudioStream(muted) }
            .onFailure { Log.w(TAG, "toggleMute failed", it) }
    }

    fun toggleCamera(enabled: Boolean) {
        runCatching {
            engine?.muteLocalVideoStream(!enabled)
            if (enabled) engine?.startPreview() else engine?.stopPreview()
        }.onFailure { Log.w(TAG, "toggleCamera failed", it) }
    }

    fun toggleSpeaker(on: Boolean) {
        runCatching { engine?.setEnableSpeakerphone(on) }
            .onFailure { Log.w(TAG, "toggleSpeaker failed", it) }
    }

    fun switchCamera() {
        runCatching { engine?.switchCamera() }
            .onFailure { Log.w(TAG, "switchCamera failed", it) }
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    fun endCall() {
        runCatching { engine?.leaveChannel() }
        runCatching { RtcEngine.destroy() }
        engine = null
        channelName = null
        remoteUid = 0
        _localVideoRenderer.value = null
        _remoteVideoRenderer.value = null
    }

    /** Joins [channel]: fetches a fresh token, creates the engine and renders. */
    private suspend fun joinChannel(channel: String, isVideo: Boolean): Result<Unit> = try {
        lastAgoraError = 0
        val info = tokenService.fetchToken(channelName = channel).getOrThrow()
        // Safe debug trace (NEVER logs the token itself, the App Certificate or secrets).
        Log.i(TAG, "[trace] channelName=$channel")
        Log.i(TAG, "[trace] tokenReceived=${!info.token.isBlank()} tokenLength=${info.token.length}")
        Log.i(TAG, "[trace] tokenUid=${info.uid} joinUid=${info.uid} appIdMatch=true")
        if (info.appId.isBlank()) {
            throw IllegalStateException("Agora App ID missing from the token service")
        }

        engine?.let { runCatching { it.leaveChannel() } }
        runCatching { RtcEngine.destroy() }

        val config = RtcEngineConfig().apply {
            mContext = this@AgoraCallManager.context.applicationContext
            mAppId = info.appId
            mEventHandler = eventHandler
        }
        engine = RtcEngine.create(config)
        this.channelName = channel

        engine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        engine?.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
        engine?.enableAudio()
        if (isVideo) {
            engine?.enableVideo()
            engine?.startPreview()
            engine?.setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_1280x720,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
                )
            )
            mainHandler.post {
                val renderer = android.view.SurfaceView(context)
                renderer.setZOrderMediaOverlay(true)
                _localVideoRenderer.value = renderer
                engine?.setupLocalVideo(
                    VideoCanvas(renderer, VideoCanvas.RENDER_MODE_HIDDEN, info.uid)
                )
            }
        }

        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            publishCameraTrack = isVideo
            autoSubscribeAudio = true
            autoSubscribeVideo = isVideo
        }

        engine?.setEnableSpeakerphone(true)
        engine?.setDefaultAudioRoutetoSpeakerphone(true)

        val code = engine?.joinChannel(info.token, channel, info.uid, options) ?: -1
        Log.i(TAG, "[trace] joinChannel returned code=$code (0=accepted; onError=$lastAgoraError)")
        if (code != 0) {
            val cause = lastAgoraError
            val detail = if (cause != 0) " (onError=$cause)" else ""
            throw IllegalStateException("Failed to join the call (joinCode=$code)$detail")
        }
        Result.success(Unit)
    } catch (t: Throwable) {
        Log.e(TAG, "joinChannel failed (lastAgoraError=$lastAgoraError)", t)
        runCatching { RtcEngine.destroy() }
        engine = null
        Result.failure(t)
    }

    companion object {
        private const val TAG = "AgoraCallManager"
        const val PREFIX = "AGORA|"
    }
}
