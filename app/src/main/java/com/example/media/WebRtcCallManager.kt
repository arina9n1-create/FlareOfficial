package com.example.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume

/**
 * Real WebRTC engine for Vyn9 voice/video calls.
 *
 * Signaling is handled by the existing `call_signals` Supabase table (see
 * [com.example.data.remote.SupabaseService]). This class only manages the
 * PeerConnection: local camera/mic capture, offer/answer SDP (with all ICE
 * candidates embedded once gathering completes — non-trickle), remote media
 * rendering and in-call toggles.
 *
 * STUN is configured so calls work on the same network and on public networks
 * behind common NATs. TURN is intentionally not hard-coded (no secrets in the
 * app); symmetric-NAT-only environments need a server-side TURN setup.
 */
class WebRtcCallManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val signalingThread = HandlerThread("Vyn9WebRtcSignaling").apply { start() }
    private val signalingHandler = Handler(signalingThread.looper)

    private lateinit var eglBase: EglBase

    /** Renders the local camera preview. Bound when a call is active. */
    lateinit var localVideoRenderer: SurfaceViewRenderer
        private set

    /** Renders the remote peer's video stream. */
    lateinit var remoteVideoRenderer: SurfaceViewRenderer
        private set

    private lateinit var factory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var capturerHelper: SurfaceTextureHelper? = null
    private var systemAudio: AudioManager? = null

    /** Receives textual state updates ("Connected" / "Failed") from ICE events. */
    private var onConnectionStateChange: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "WebRtcCallManager"
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FPS = 30

        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    init {
        // CRITICAL: WebRTC native libraries must be initialized BEFORE any WebRTC objects
        // (EglBase, PeerConnectionFactory, SurfaceViewRenderer) are created.
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        localVideoRenderer = SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(true)
        }

        remoteVideoRenderer = SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setMirror(false)
        }

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                org.webrtc.DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(org.webrtc.DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private class SimpleSdpObserver(
    private val onCreate: (SessionDescription?) -> Unit,
    private val onSet: () -> Unit,
    private val onErr: (String?) -> Unit
) : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) = onCreate(desc)
    override fun onSetSuccess() = onSet()
    override fun onCreateFailure(error: String?) = onErr(error)
    override fun onSetFailure(error: String?) = onErr(error)
}

    // =====================================================================
    // OUTGOING CALL — build offer (with gathered ICE candidates)
    // =====================================================================
    suspend fun createOutgoingOffer(isVideo: Boolean, useFrontCamera: Boolean): String? =
        suspendCancellableCoroutine { cont ->
            signalingHandler.post {
                try {
                    disposeConnectionQuiet()
                    val pc = createPeerConnection()
                    addLocalTracks(pc, isVideo, useFrontCamera)

                    val setObserver = SimpleSdpObserver(
                        onCreate = {},
                        onSet = {
                            waitForIceComplete(pc, onDone = { sdpWithCandidates ->
                                if (cont.isActive) mainHandler.post { cont.resume(sdpWithCandidates) }
                            })
                        },
                        onErr = { e ->
                            Log.e(TAG, "offer setLocal failed: $e")
                            if (cont.isActive) mainHandler.post { cont.resume(null) }
                        }
                    )
                    val createObserver = SimpleSdpObserver(
                        onCreate = { desc ->
                            if (desc != null) pc.setLocalDescription(setObserver, desc)
                        },
                        onSet = {},
                        onErr = { e ->
                            Log.e(TAG, "createOffer failed: $e")
                            if (cont.isActive) mainHandler.post { cont.resume(null) }
                        }
                    )
                    pc.createOffer(
                        createObserver,
                        mediaConstraints()
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "createOutgoingOffer crashed", t)
                    if (cont.isActive) mainHandler.post { cont.resume(null) }
                }
            }
        }
fun setConnectionStateListener(listener: (String) -> Unit) {
        onConnectionStateChange = listener
    }

    // =====================================================================
    // INCOMING CALL — apply remote offer and produce answer (gathered ICE)
    // =====================================================================
    suspend fun receiveIncoming(isVideo: Boolean, remoteOfferSdp: String): String? =
        suspendCancellableCoroutine { cont ->
            signalingHandler.post {
                try {
                    disposeConnectionQuiet()
                    val pc = createPeerConnection()
                    addLocalTracks(pc, isVideo, true)

                    val setRemoteObserver = SimpleSdpObserver(
                        onCreate = {},
                        onSet = {
                            val answerSetObserver = SimpleSdpObserver(
                                onCreate = {},
                                onSet = {
                                    waitForIceComplete(pc, onDone = { sdpWithCandidates ->
                                        if (cont.isActive) {
                                            mainHandler.post { cont.resume(sdpWithCandidates) }
                                        }
                                    })
                                },
                                onErr = { e ->
                                    Log.e(TAG, "answer setLocal failed: $e")
                                    if (cont.isActive) mainHandler.post { cont.resume(null) }
                                }
                            )
                            val answerCreateObserver = SimpleSdpObserver(
                                onCreate = { desc ->
                                    if (desc != null) pc.setLocalDescription(answerSetObserver, desc)
                                },
                                onSet = {},
                                onErr = { e ->
                                    Log.e(TAG, "createAnswer failed: $e")
                                    if (cont.isActive) mainHandler.post { cont.resume(null) }
                                }
                            )
                            pc.createAnswer(
                                answerCreateObserver,
                                mediaConstraints()
                            )
                        },
                        onErr = { e ->
                            Log.e(TAG, "setRemote(offer) failed: $e")
                            if (cont.isActive) mainHandler.post { cont.resume(null) }
                        }
                    )
                    pc.setRemoteDescription(
                        setRemoteObserver,
                        SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "receiveIncoming crashed", t)
                    if (cont.isActive) mainHandler.post { cont.resume(null) }
                }
            }
        }

    // =====================================================================
    // CALLER — apply the accepted answer from the callee
    // =====================================================================
    fun applyRemoteAnswer(answerSdp: String, onDone: (Boolean) -> Unit) {
        signalingHandler.post {
            val pc = peerConnection
            if (pc == null) {
                mainHandler.post { onDone(false) }
                return@post
            }
            val observer = SimpleSdpObserver(
                onCreate = {},
                onSet = { mainHandler.post { onDone(true) } },
                onErr = { e ->
                    Log.e(TAG, "setRemote(answer) failed: $e")
                    mainHandler.post { onDone(false) }
                }
            )
            pc.setRemoteDescription(
                observer,
                SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
            )
        }
    }

    // =====================================================================
    // IN-CALL CONTROLS
    // =====================================================================
    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleCamera(enabled: Boolean) {
        if (enabled) {
            videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        } else {
            videoCapturer?.stopCapture()
        }
    }

    fun toggleSpeaker(on: Boolean) {
        val audio = systemAudio ?: return
        setSpeakerphone(audio, on)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    fun endCall() {
        signalingHandler.post {
            try {
                disposeConnectionQuiet()
            } catch (t: Throwable) {
                Log.e(TAG, "endCall cleanup failed", t)
            }
        }
        mainHandler.post { restoreAudioMode() }
    }

    fun release() {
        mainHandler.post {
            localVideoRenderer.release()
            remoteVideoRenderer.release()
        }
        signalingHandler.post {
            try {
                disposeConnectionQuiet()
            } catch (_: Throwable) {
            }
            eglBase.release()
            signalingThread.quitSafely()
        }
    }

    // =====================================================================
    // INTERNALS
    // =====================================================================
    private fun createPeerConnection(): PeerConnection {
        val config = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED ->
                        mainHandler.post { onConnectionStateChange?.invoke("Connected") }

                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED ->
                        mainHandler.post { onConnectionStateChange?.invoke("Failed") }

                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream) {
                stream.videoTracks.firstOrNull()?.let { track ->
                    remoteVideoTrack = track
                    track.addSink(remoteVideoRenderer)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, tracks: Array<out MediaStream>?) {
                bindRemoteTrack(receiver)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                bindRemoteTrack(transceiver?.receiver)
            }
        }
        return factory.createPeerConnection(config, observer)
            ?: throw IllegalStateException("createPeerConnection returned null")
    }

    private fun bindRemoteTrack(receiver: RtpReceiver?) {
        val track = receiver?.track()
        if (track is VideoTrack) {
            remoteVideoTrack = track
            track.addSink(remoteVideoRenderer)
            mainHandler.post { onConnectionStateChange?.invoke("Stream") }
        }
    }

    private fun addLocalTracks(pc: PeerConnection, isVideo: Boolean, useFrontCamera: Boolean) {
        engageAudioMode()

        val audioSource: AudioSource = factory.createAudioSource(mediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        pc.addTrack(localAudioTrack, emptyList())

        if (isVideo) {
            val capturer = createCapturer(useFrontCamera)
            if (capturer != null) {
                val source: VideoSource = factory.createVideoSource(false)
                val helper = SurfaceTextureHelper.create("Vyn9VideoCapture", eglBase.eglBaseContext)
                capturer.initialize(helper, context, source.capturerObserver)
                capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                videoCapturer = capturer
                capturerHelper = helper
                val track = factory.createVideoTrack("video0", source)
                track.addSink(localVideoRenderer)
                localVideoTrack = track
                pc.addTrack(track, emptyList())
            } else {
                Log.w(TAG, "No camera available; continuing as audio-only call")
            }
        }
    }

    private fun createCapturer(useFrontCamera: Boolean): VideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(context)
            val names = enumerator.deviceNames
            if (names.isEmpty()) return null
            val target = if (useFrontCamera) {
                names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
            } else {
                names.firstOrNull { enumerator.isBackFacing(it) } ?: names.firstOrNull()
            } ?: return null
            enumerator.createCapturer(target, null)
        } catch (t: Throwable) {
            Log.e(TAG, "createCapturer failed", t)
            null
        }
    }

    private fun mediaConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    }

    /**
     * Non-trickle: wait until ICE gathering completes (all candidates are inside
     * the local description) then hand back the SDP string for signaling.
     */
    private fun waitForIceComplete(pc: PeerConnection, onDone: (String?) -> Unit, attempts: Int = 40) {
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            onDone(pc.localDescription?.description)
        } else if (attempts <= 0) {
            // Timeout — send what we have; the remote still connects over available candidates.
            onDone(pc.localDescription?.description)
        } else {
            signalingHandler.postDelayed({ waitForIceComplete(pc, onDone, attempts - 1) }, 150)
        }
    }

    private fun engageAudioMode() {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(audio, true)
            systemAudio = audio
        } catch (t: Throwable) {
            Log.w(TAG, "engageAudioMode failed", t)
        }
    }

    /** Routes audio to the loudspeaker (API 31+) with a pre-API-31 fallback. */
    private fun setSpeakerphone(audio: AudioManager, on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val speaker = audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) audio.setCommunicationDevice(speaker)
                else audio.clearCommunicationDevice()
            } else {
                audio.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }

    private fun restoreAudioMode() {
        try {
            systemAudio?.mode = AudioManager.MODE_NORMAL
            systemAudio = null
        } catch (t: Throwable) {
            Log.w(TAG, "restoreAudioMode failed", t)
        }
    }

    private fun disposeConnectionQuiet() {
        try {
            localVideoTrack?.removeSink(localVideoRenderer)
            remoteVideoTrack?.removeSink(remoteVideoRenderer)
        } catch (_: Throwable) {
        }
        localVideoTrack = null
        remoteVideoTrack = null
        localAudioTrack = null
        try {
            videoCapturer?.stopCapture()
        } catch (_: Throwable) {
        }
        videoCapturer = null
        try {
            capturerHelper?.dispose()
        } catch (_: Throwable) {
        }
        capturerHelper = null
        try {
            peerConnection?.close()
        } catch (_: Throwable) {
        }
        peerConnection = null
    }
}