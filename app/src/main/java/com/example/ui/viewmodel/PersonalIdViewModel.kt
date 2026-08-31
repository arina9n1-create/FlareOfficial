package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.personalId.PersonalIdService
import com.example.media.WebRtcCallManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** State machine of the Personal ID tab. */
sealed class PidStep {
    /** Resolving whether this account already has a Personal ID. */
    data object Loading : PidStep()

    /** No Personal ID created yet — show the creation screen. */
    data object Setup : PidStep()

    /** Personal ID active — show home (username header, search, chats). */
    data class Home(val username: String, val usernameInitial: String = "") : PidStep()

    /** Inside a Personal ID conversation. */
    data class Chat(
        val conversationId: String,
        val peerUsername: String,
        val peerAvatarUrl: String = ""
    ) : PidStep()
}

data class PidSearchResult(
    val username: String,
    val avatarUrl: String = ""
)

data class PidChatItem(
    val conversationId: String,
    val peerUsername: String,
    val peerAvatarUrl: String = "",
    val lastPreview: String,
    val lastAt: Long?,
    val unreadCount: Int
)

data class PidMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val createdAtMs: Long?,
    val createdAt: String = "",
    val isRead: Boolean = false
)

/** Incoming-call banner data (privacy-safe: only the peer Personal ID + type). */
data class PidIncomingCall(
    val callId: String,
    val peerUsername: String,
    val callType: String
)

class PersonalIdViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "PersonalIdViewModel"
    }

    private val service = PersonalIdService(app)
    val webRtc = WebRtcCallManager(app)

    private val _step = MutableStateFlow<PidStep>(PidStep.Loading)
    val step: StateFlow<PidStep> = _step

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _myId = MutableStateFlow("")
    val myId: StateFlow<String> = _myId

    private val _search = MutableStateFlow<List<PidSearchResult>>(emptyList())
    val search: StateFlow<List<PidSearchResult>> = _search

    private val _chats = MutableStateFlow<List<PidChatItem>>(emptyList())
    val chats: StateFlow<List<PidChatItem>> = _chats

    private val _chatsLoading = MutableStateFlow(false)
    val chatsLoading: StateFlow<Boolean> = _chatsLoading

    private val _messages = MutableStateFlow<List<PidMessage>>(emptyList())
    val messages: StateFlow<List<PidMessage>> = _messages

    private val _msgsLoading = MutableStateFlow(false)
    val msgsLoading: StateFlow<Boolean> = _msgsLoading

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    // Call state
    private val _activeCall = MutableStateFlow<CallState?>(null)
    val activeCall: StateFlow<CallState?> = _activeCall

    private val _incomingCall = MutableStateFlow<PidIncomingCall?>(null)
    val incomingCall: StateFlow<PidIncomingCall?> = _incomingCall

    private var pollJob: Job? = null
    private var callPollJob: Job? = null
    private var currentCallId: String? = null
    private var currentConversationId: String? = null
    private var answerApplied = false

    init { bootstrap() }
/** Resolves the current account state in the background. */
    fun bootstrap() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.me()
                .onSuccess { obj ->
                    val username = obj.optString("username", "")
                    if (obj.has("id") && !obj.isNull("id")) {
                        _myId.value = obj.optString("id", "")
                        _step.value = PidStep.Home(username)
                        refreshInbox()
                    } else {
                        _step.value = PidStep.Setup
                    }
                }
                .onFailure { e: Throwable ->
                    Log.e(TAG, "bootstrap failed", e)
                    _step.value = PidStep.Setup
                    _error.value = friendly(e, "Could not load Personal ID — please log in to Vyn9 first")
                }
            _loading.value = false
        }
    }

    fun dismissError() { _error.value = null }

    /** Delegates username validation to the service (matches server normalization). */
    fun normalizeUsername(raw: String): String? = service.normalizeUsername(raw)

    fun isValidUsername(raw: String): Boolean = service.isValidUsername(raw)

    /** Creates the account's single Personal ID. */
    fun create(raw: String) {
        val username = service.normalizeUsername(raw)
        if (username == null) {
            _error.value = "Use 3-20 characters, start with a letter, letters/numbers/underscore only"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.create(username)
                .onSuccess { obj ->
                    _myId.value = obj.optString("id", "")
                    _step.value = PidStep.Home(obj.optString("username", username))
                    refreshInbox()
                }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, "Could not create Personal ID")
                }
            _loading.value = false
        }
    }

    fun clearSearch() { _search.value = emptyList() }

    /** Searches ONLY the Personal ID namespace. Never returns Vyn9 accounts. */
    fun searchUsernames(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) { _search.value = emptyList(); return@launch }
            service.search(query.trim())
                .onSuccess { arr -> _search.value = parseSearch(arr) }
                .onFailure { e: Throwable -> Log.e(TAG, "search failed", e) }
        }
    }

    fun openChatWith(username: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.openConversation(username)
                .onSuccess { obj ->
                    _search.value = emptyList()
                    enterChat(
                        conversationId = obj.optString("conversation_id"),
                        peerUsername = obj.optString("peer_username", username),
                        peerAvatarUrl = obj.optString("peer_avatar_url", "")
                    )
                }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, "This Personal ID is not active")
                }
            _loading.value = false
        }
    }

    fun openExistingChat(item: PidChatItem) {
        enterChat(item.conversationId, item.peerUsername, item.peerAvatarUrl)
    }

    fun refreshInbox() {
        viewModelScope.launch {
            _chatsLoading.value = true
            service.inbox()
                .onSuccess { arr -> _chats.value = parseChats(arr) }
                .onFailure { e: Throwable -> Log.e(TAG, "inbox failed", e) }
            _chatsLoading.value = false
        }
    }

    fun loadMessages(conversationId: String, markRead: Boolean = true) {
        viewModelScope.launch {
            service.messages(conversationId)
                .onSuccess { arr -> _messages.value = parseMessages(arr) }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not load messages") }
            if (markRead) service.markRead(conversationId)
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            service.sendMessage(conversationId, body)
                .onSuccess {
                    service.messages(conversationId)
                        .onSuccess { arr -> _messages.value = parseMessages(arr) }
                    refreshInbox()
                }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Message could not be sent") }
            _sending.value = false
        }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            service.deleteMessage(conversationId, messageId)
                .onSuccess { loadMessages(conversationId) }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not delete message") }
        }
    }
// ---------------------------------------------------------------------------
    // Chat navigation
    // ---------------------------------------------------------------------------

    private fun enterChat(conversationId: String, peerUsername: String, peerAvatarUrl: String) {
        currentConversationId = conversationId
        pollJob?.cancel()
        callPollJob?.cancel()
        _step.value = PidStep.Chat(conversationId, peerUsername, peerAvatarUrl)
        loadMessages(conversationId)
        viewModelScope.launch { service.markRead(conversationId) }
        pollJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                service.messages(conversationId)
                    .onSuccess { arr -> _messages.value = parseMessages(arr) }
                    .onFailure { e: Throwable -> Log.e(TAG, "message poll failed", e) }
            }
        }
        callPollJob = viewModelScope.launch {
            while (true) {
                delay(2500)
                pollCallSignals(conversationId)
            }
        }
    }

    fun backFromChat() {
        pollJob?.cancel()
        callPollJob?.cancel()
        pollJob = null
        callPollJob = null
        currentConversationId = null
        currentCallId = null
        answerApplied = false
        _messages.value = emptyList()
        clearCall()
        restoreHome()
    }

    /** Re-resolves the Home step (my username) after leaving a chat. */
    fun restoreHome() {
        viewModelScope.launch {
            service.me().onSuccess { obj ->
                val u = obj.optString("username", "")
                if (u.isNotBlank()) {
                    _step.value = PidStep.Home(u)
                    refreshInbox()
                } else {
                    _step.value = PidStep.Setup
                }
            }.onFailure { e: Throwable ->
                Log.e(TAG, "restoreHome failed", e)
                _step.value = PidStep.Setup
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        callPollJob?.cancel()
        webRtc.endCall()
        super.onCleared()
    }
// ---------------------------------------------------------------------------
    // Voice / Video calls (real WebRTC — signaling via personal_id_call_signals)
    // ---------------------------------------------------------------------------

    /** Caller: initiate an outgoing call. */
    fun startCall(conversationId: String, isVideo: Boolean) {
        if (_activeCall.value != null || _incomingCall.value != null) return
        val s = _step.value as? PidStep.Chat ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.callInitiate(conversationId, if (isVideo) "VIDEO" else "AUDIO")
                .onSuccess { sig ->
                    currentCallId = sig.optString("id")
                    answerApplied = false
                    // Create the WebRTC offer (with gathered ICE encoded in the SDP).
                    val offer = webRtc.createOutgoingOffer(isVideo, useFrontCamera = true)
                    if (offer.isNullOrBlank()) {
                        _error.value = "Could not start the call (media engine busy)"
                        _loading.value = false
                        return@onSuccess
                    }
                    service.callUpdate(currentCallId!!, sdp = offer, status = "OFFERING")
                    _activeCall.value = CallState(
                        partnerName = "@${s.peerUsername}",
                        partnerAvatar = "",
                        isVideo = isVideo,
                        isMuted = false,
                        isCameraOn = isVideo,
                        isSpeakerOn = true,
                        isFrontCamera = true,
                        status = "Ringing…",
                        callId = currentCallId ?: "",
                        remoteHandle = s.peerUsername,
                        isOutgoing = true
                    )
                }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, "Could not start the call")
                }
            _loading.value = false
        }
    }

    /** Callee: accept an incoming call and connect the media. */
    fun acceptIncomingCall() {
        val inc = _incomingCall.value ?: return
        val s = _step.value as? PidStep.Chat ?: return
        val isVideo = inc.callType.equals("VIDEO", ignoreCase = true)
        viewModelScope.launch {
            _incomingCall.value = null
            _loading.value = true
            // Fetch the caller's offer so we can build an answer.
            service.callPoll(s.conversationId)
                .onSuccess { arr ->
                    val call = findSignal(arr, inc.callId) ?: run {
                        _loading.value = false
                        _error.value = "Call is no longer active"; return@onSuccess
                    }
                    val offer = call.optString("sdp", "")
                    if (offer.isBlank()) {
                        _loading.value = false
                        _error.value = "Call offer not ready yet"; return@onSuccess
                    }
                    val answer = webRtc.receiveIncoming(isVideo, offer)
                    if (answer.isNullOrBlank()) {
                        _loading.value = false
                        _error.value = "Could not connect audio/video"
                        service.callReject(inc.callId)
                        return@onSuccess
                    }
                    service.callAnswer(inc.callId, answer)
                    currentCallId = inc.callId
                    answerApplied = true
                    _activeCall.value = CallState(
                        partnerName = "@${inc.peerUsername}",
                        partnerAvatar = "",
                        isVideo = isVideo,
                        isMuted = false,
                        isCameraOn = isVideo,
                        isSpeakerOn = true,
                        isFrontCamera = true,
                        status = "Connected",
                        callId = inc.callId,
                        remoteHandle = inc.peerUsername,
                        isOutgoing = false
                    )
                }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, "Could not accept the call")
                }
            _loading.value = false
        }
    }

    fun rejectIncomingCall() {
        val inc = _incomingCall.value ?: return
        _incomingCall.value = null
        viewModelScope.launch { service.callReject(inc.callId) }
        webRtc.endCall()
    }

    fun endCall() {
        val callId = currentCallId
        clearCall()
        if (callId != null) {
            viewModelScope.launch { service.callEnd(callId) }
        }
        webRtc.endCall()
    }

    private fun clearCall() {
        _activeCall.value = null
        _incomingCall.value = null
    }

    fun toggleMute() {
        val c = _activeCall.value ?: return
        val muted = !c.isMuted
        webRtc.toggleMute(muted)
        _activeCall.value = c.copy(isMuted = muted)
    }

    fun toggleCamera() {
        val c = _activeCall.value ?: return
        if (!c.isVideo) return
        val enabled = !c.isCameraOn
        webRtc.toggleCamera(enabled)
        _activeCall.value = c.copy(isCameraOn = enabled)
    }

    fun toggleSpeaker() {
        val c = _activeCall.value ?: return
        val on = !c.isSpeakerOn
        webRtc.toggleSpeaker(on)
        _activeCall.value = c.copy(isSpeakerOn = on)
    }

    fun flipCamera() {
        webRtc.switchCamera()
        val c = _activeCall.value
        if (c != null) _activeCall.value = c.copy(isFrontCamera = !c.isFrontCamera)
    }
/** Polls call signals: detects incoming calls and connects the caller once answered. */
    private fun pollCallSignals(conversationId: String) {
        viewModelScope.launch {
            service.callPoll(conversationId)
                .onSuccess { arr -> handleCallSignals(arr) }
                .onFailure { e: Throwable -> Log.e(TAG, "call poll failed", e) }
        }
    }

    private fun handleCallSignals(arr: JSONArray) {
        val s = _step.value as? PidStep.Chat ?: return
        val myPid = _myId.value
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val sigId = o.optString("id")
            val status = o.optString("status")
            val callerPid = o.optString("caller_personal_id")
            val type = o.optString("call_type", "VIDEO")

            // Incoming call (I am the receiver, still offering, no modal yet).
            if (status == "OFFERING" && callerPid != myPid && _activeCall.value == null
                && currentCallId == null
            ) {
                if (_incomingCall.value?.callId != sigId) {
                    _incomingCall.value = PidIncomingCall(sigId, s.peerUsername, type)
                }
                continue
            }

            // Caller: the callee answered — apply the answer SDP to complete media.
            if (status == "ACCEPTED" && currentCallId == sigId && !answerApplied) {
                val answer = o.optString("sdp", "")
                if (answer.isNotBlank()) {
                    answerApplied = true
                    webRtc.applyRemoteAnswer(answer) { ok ->
                        val c = _activeCall.value
                        if (c != null) {
                            _activeCall.value = c.copy(status = if (ok) "Connected" else "Connection failed")
                        }
                    }
                }
                continue
            }

            // Any active call by me was rejected / ended / missed.
            if (currentCallId == sigId && !answerApplied &&
                (status == "REJECTED" || status == "ENDED" || status == "MISSED")
            ) {
                clearCall()
                webRtc.endCall()
                currentCallId = null
                _error.value = if (status == "REJECTED") "Call was declined" else "Call ended"
            }

            // Callee: caller ended the call while it was connected.
            if (_activeCall.value != null && currentCallId == sigId && answerApplied &&
                (status == "ENDED" || status == "MISSED")
            ) {
                val wasActive = _activeCall.value != null
                clearCall()
                webRtc.endCall()
                currentCallId = null
                if (wasActive) _error.value = "Call ended"
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Parsing helpers
    // ---------------------------------------------------------------------------

    private fun parseSearch(arr: JSONArray): List<PidSearchResult> {
        val out = mutableListOf<PidSearchResult>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(PidSearchResult(o.optString("username"), o.optString("avatar_url", "")))
        }
        return out
    }

    private fun parseChats(arr: JSONArray): List<PidChatItem> {
        val out = mutableListOf<PidChatItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                PidChatItem(
                    conversationId = o.optString("conversation_id"),
                    peerUsername = o.optString("peer_username", ""),
                    peerAvatarUrl = o.optString("peer_avatar_url", ""),
                    lastPreview = o.optString("last_message_preview", ""),
                    lastAt = parseMillis(o.opt("last_message_at")),
                    unreadCount = o.optInt("unread_count", 0)
                )
            )
        }
        return out
    }

    private fun parseMessages(arr: JSONArray): List<PidMessage> {
        val out = mutableListOf<PidMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                PidMessage(
                    id = o.optString("id"),
                    text = o.optString("message_text", ""),
                    isMine = o.optBoolean("is_mine", false),
                    createdAtMs = parseMillis(o.opt("created_at")),
                    createdAt = o.optString("created_at", ""),
                    isRead = o.optBoolean("is_read", false)
                )
            )
        }
        return out
    }

    private fun findSignal(arr: JSONArray, sigId: String): JSONObject? {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == sigId) return o
        }
        return null
    }

    private fun friendly(e: Throwable, fallback: String): String {
        val raw = e.message ?: return fallback
        val msg = raw.substringAfter("message\":\"").substringBefore("\"")
        return msg.take(160).ifBlank { raw.take(160) }.ifBlank { fallback }
    }
}