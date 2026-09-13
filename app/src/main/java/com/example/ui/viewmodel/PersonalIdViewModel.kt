package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.net.Uri
import java.io.File

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.personalId.PersonalIdService
import com.example.media.AgoraCallManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
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
    val unreadCount: Int,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false
)

data class PidReaction(
    val emoji: String,
    val count: Int,
    val reactedByMe: Boolean
)

data class PidMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val createdAtMs: Long?,
    val createdAt: String = "",
    val isRead: Boolean = false,
    val editedAtMs: Long? = null,
    val replyToId: String? = null,
    val replyText: String = "",
    val replyIsMine: Boolean = false,
    val mediaUrl: String = "",
    val mediaType: String = "",
    val mediaName: String = "",
    val isPinned: Boolean = false,
    val reactions: List<PidReaction> = emptyList()
)

data class PidConversationSettings(
    val muted: Boolean = false,
    val archived: Boolean = false,
    val readReceipts: Boolean = true,
    val peerOnline: Boolean = false,
    val peerLastSeenMs: Long? = null
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
    val agora = AgoraCallManager(app)

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


    private val _conversationSettings = MutableStateFlow(PidConversationSettings())
    val conversationSettings: StateFlow<PidConversationSettings> = _conversationSettings

    private val _replyingTo = MutableStateFlow<PidMessage?>(null)
    val replyingTo: StateFlow<PidMessage?> = _replyingTo

    private val _editingMessage = MutableStateFlow<PidMessage?>(null)
    val editingMessage: StateFlow<PidMessage?> = _editingMessage

    private val _mediaBusy = MutableStateFlow(false)
    val mediaBusy: StateFlow<Boolean> = _mediaBusy

    /** Overall attachment processing progress (0-100). >0 means an upload is running. */
    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress

    /** Personal IDs this user has blocked (usernames). */
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked

    // --- LOCAL (this device only) conversation tweaks --------------------------
    // Nicknames, pinned-to-top and hidden (delete for me) chats are stored per-device
    // and never leak to the network — perfect for a privacy-first Personal ID namespace.

    private val localPrefs =
        getApplication<Application>().getSharedPreferences("pid_local_prefs", Context.MODE_PRIVATE)

    private val _pinnedConvs = MutableStateFlow<Set<String>>(
        localPrefs.getStringSet("pinned_convs", emptySet())?.toSet() ?: emptySet()
    )
    val pinnedConvs: StateFlow<Set<String>> = _pinnedConvs

    private val _hiddenConvs = MutableStateFlow<Set<String>>(
        localPrefs.getStringSet("hidden_convs", emptySet())?.toSet() ?: emptySet()
    )
    val hiddenConvs: StateFlow<Set<String>> = _hiddenConvs

    // Peer display nickname map: conversationId -> friendly label.

    private val _nicknames = MutableStateFlow<Map<String, String>>(loadNicknames())
    val nicknames: StateFlow<Map<String, String>> = _nicknames

    // A pending forwarded message — consumed by the composer of the target chat.
    private val _forwardDraft = MutableStateFlow<String?>(null)
    val forwardDraft: StateFlow<String?> = _forwardDraft

    // Call state
    private val _activeCall = MutableStateFlow<CallState?>(null)
    val activeCall: StateFlow<CallState?> = _activeCall

    private val _incomingCall = MutableStateFlow<PidIncomingCall?>(null)
    val incomingCall: StateFlow<PidIncomingCall?> = _incomingCall

    private var pollJob: Job? = null
    private var callPollJob: Job? = null
    private var presenceJob: Job? = null
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
                        refreshBlockedList()
                        startPresenceHeartbeat()
                    } else {
                        _step.value = PidStep.Setup
                    }
                }
                .onFailure { e: Throwable ->
                    Log.e(TAG, "bootstrap failed", e)
                    _step.value = PidStep.Setup
                    _error.value = friendly(e, "Could not load Personal ID — please log in to FlareOfficial first")
                }
            _loading.value = false
        }
    }

    fun dismissError() { _error.value = null }

    /**
     * App-level presence heartbeat: touches last_seen_at every 25s (well inside
     * the 70s online window) as long as this ViewModel lives, so the user shows
     * as "Active now" anywhere in the app — not just inside a chat.
     */
    private fun startPresenceHeartbeat() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (coroutineContext.isActive) {
                service.touchPresence()
                kotlinx.coroutines.delay(25_000L)
            }
        }
    }

    /** Delegates username validation to the service (matches server normalization). */
    fun normalizeUsername(raw: String): String? = service.normalizeUsername(raw)

    fun isValidUsername(raw: String): Boolean = service.isValidUsername(raw)

    /** Logs in to one of the current account's Personal IDs (binding checked server-side). */
    fun login(raw: String) {
        val username = service.normalizeUsername(raw)
        if (username == null) {
            _error.value = "Use 3-20 characters, start with a letter, letters/numbers/underscore only"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.loginPersonalId(username)
                .onSuccess { obj ->
                    _myId.value = obj.optString("id", "")
                    _step.value = PidStep.Home(obj.optString("username", username))
                    refreshInbox()
                    refreshBlockedList()
                }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, "Could not log in to Personal ID")
                }
            _loading.value = false
        }
    }

    /**
     * Logs out of the ACTIVE Personal ID ONLY. The FlareOfficial account session
     * ("flareofficial_auth_prefs") is never touched — the account stays logged in.
     */
    fun signOutPersonalId() {
        viewModelScope.launch {
            service.logoutPersonalId()
            _myId.value = ""
            _chats.value = emptyList()
            _messages.value = emptyList()
            _search.value = emptyList()
            _replyingTo.value = null
            _editingMessage.value = null
            _step.value = PidStep.Setup
        }
    }

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

    /** Searches ONLY the Personal ID namespace. Never returns FlareOfficial accounts. */
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
                .onSuccess { arr ->
                    val sorted = parseChats(arr)
                        .filter { it.conversationId !in _hiddenConvs.value }
                        .sortedWith(
                            compareByDescending<PidChatItem> { it.conversationId in _pinnedConvs.value }
                                .thenByDescending { it.lastAt ?: 0L }
                        )
                    _chats.value = sorted
                }
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

    fun beginReply(message: PidMessage) {
        _editingMessage.value = null
        _replyingTo.value = message
    }

    fun beginEdit(message: PidMessage) {
        if (!message.isMine || message.mediaUrl.isNotBlank()) return
        _replyingTo.value = null
        _editingMessage.value = message
    }

    fun cancelComposerAction() {
        _replyingTo.value = null
        _editingMessage.value = null
    }

    fun sendMessage(conversationId: String, text: String) {
        val body = text.trim()
        val editing = _editingMessage.value
        if (body.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            val result = if (editing != null) {
                service.editMessage(conversationId, editing.id, body)
            } else {
                service.sendMessage(conversationId, body, _replyingTo.value?.id).map { Unit }
            }
            result
                .onSuccess {
                    cancelComposerAction()
                    refreshMessages(conversationId)
                    refreshInbox()
                }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, if (editing != null) "Message could not be edited" else "Message could not be sent")
                }
            _sending.value = false
        }
    }

    fun sendMedia(
        conversationId: String,
        uri: Uri,
        displayName: String,
        mimeType: String,
        caption: String = ""
    ) {
        if (_mediaBusy.value) return
        viewModelScope.launch {
            _mediaBusy.value = true
            service.uploadMedia(conversationId, uri, displayName, mimeType)
                .onSuccess { path ->
                    val type = when {
                        mimeType.startsWith("image/") -> "image"
                        mimeType.startsWith("audio/") -> "audio"
                        else -> "file"
                    }
                    service.sendMessage(
                        conversationId = conversationId,
                        text = caption.trim(),
                        replyToId = _replyingTo.value?.id,
                        mediaUrl = path,
                        mediaType = type,
                        mediaName = displayName.take(120)
                    ).onSuccess {
                        cancelComposerAction()
                        refreshMessages(conversationId)
                        refreshInbox()
                    }.onFailure { e -> _error.value = friendly(e, "Attachment could not be sent") }
                }
                .onFailure { e -> _error.value = friendly(e, "Attachment upload failed") }
            _mediaBusy.value = false
        }
    }

    suspend fun downloadMedia(message: PidMessage): Result<File> =
        service.downloadMedia(message.mediaUrl, message.mediaName)

    /** One pending attachment picked from the system picker. */
    data class MediaPick(val uri: Uri, val displayName: String, val mimeType: String)

    /**
     * Sends a batch of attachments. Reports overall processing percentage in
     * [uploadProgress] (0-100). At 100% the messages are refreshed so they show
     * up in the chat with the "sent" marker.
     */
    fun sendMediaBatch(
        conversationId: String,
        items: List<MediaPick>,
        caption: String = ""
    ) {
        if (items.isEmpty() || _mediaBusy.value) return
        viewModelScope.launch {
            _mediaBusy.value = true
            _uploadProgress.value = 1
            val total = items.size
            var done = 0
            for (item in items) {
                // Smooth incremental progress while the current file uploads.
                val ticker = launch {
                    while (isActive) {
                        kotlinx.coroutines.delay(200)
                        val perFile = 100f / total
                        val inFile = 0.85f // leave 15% of the file's share for the send step
                        _uploadProgress.value = ((done * perFile) + (perFile * inFile)).toInt().coerceAtMost(99)
                    }
                }
                service.uploadMedia(conversationId, item.uri, item.displayName, item.mimeType)
                    .onSuccess { path ->
                        val type = when {
                            item.mimeType.startsWith("image/") -> "image"
                            item.mimeType.startsWith("video/") -> "video"
                            item.mimeType.startsWith("audio/") -> "audio"
                            else -> "file"
                        }
                        service.sendMessage(
                            conversationId = conversationId,
                            text = caption.trim(),
                            replyToId = _replyingTo.value?.id,
                            mediaUrl = path,
                            mediaType = type,
                            mediaName = item.displayName.take(120)
                        ).onFailure { e -> _error.value = friendly(e, "Attachment could not be sent") }
                    }
                    .onFailure { e -> _error.value = friendly(e, "Attachment upload failed") }
                ticker.cancel()
                done++
                _uploadProgress.value = done * 100 / total
            }
            cancelComposerAction()
            refreshMessages(conversationId)
            refreshInbox()
            _uploadProgress.value = 100
            // Let the UI show 100% briefly before clearing the overlay.
            kotlinx.coroutines.delay(700)
            _mediaBusy.value = false
            _uploadProgress.value = 0
        }
    }

    fun toggleReaction(conversationId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            service.toggleReaction(conversationId, messageId, emoji)
                .onSuccess { refreshMessages(conversationId) }
                .onFailure { e -> _error.value = friendly(e, "Reaction could not be updated") }
        }
    }

    fun setPinned(conversationId: String, messageId: String, pinned: Boolean) {
        viewModelScope.launch {
            service.setPinned(conversationId, messageId, pinned)
                .onSuccess { refreshMessages(conversationId) }
                .onFailure { e -> _error.value = friendly(e, "Pin could not be updated") }
        }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            service.deleteMessage(conversationId, messageId)
                .onSuccess { refreshMessages(conversationId); refreshInbox() }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not delete message") }
        }
    }

    fun updateMuted(conversationId: String, muted: Boolean) = updateSettings(conversationId, muted, _conversationSettings.value.archived)

    fun updateArchived(conversationId: String, archived: Boolean) = updateSettings(conversationId, _conversationSettings.value.muted, archived)

    private fun updateSettings(conversationId: String, muted: Boolean, archived: Boolean) {
        viewModelScope.launch {
            service.updateConversationSettings(conversationId, muted, archived)
                .onSuccess {
                    _conversationSettings.value = _conversationSettings.value.copy(muted = muted, archived = archived)
                    refreshInbox()
                }
                .onFailure { e -> _error.value = friendly(e, "Chat settings could not be updated") }
        }
    }

    fun updateReadReceipts(enabled: Boolean) {
        viewModelScope.launch {
            service.updatePrivacy(enabled)
                .onSuccess { _conversationSettings.value = _conversationSettings.value.copy(readReceipts = enabled) }
                .onFailure { e -> _error.value = friendly(e, "Privacy setting could not be updated") }
        }
    }

    fun clearHistory(conversationId: String) {
        viewModelScope.launch {
            service.clearHistory(conversationId)
                .onSuccess { _messages.value = emptyList(); refreshInbox() }
                .onFailure { e -> _error.value = friendly(e, "Chat history could not be cleared") }
        }
    }

    /**
     * Toggles mute for a conversation straight from the HOME chat list. We take the
     * current flags from the list row (not the in-thread _conversationSettings) so
     * long-pressing from the inbox toggles the correct row.
     */
    fun toggleListMute(conversationId: String, currentlyMuted: Boolean, currentlyArchived: Boolean) {
        updateSettings(conversationId, muted = !currentlyMuted, archived = currentlyArchived)
    }

    /** Toggles archive for a conversation straight from the HOME chat list. */
    fun toggleListArchive(conversationId: String, currentlyMuted: Boolean, currentlyArchived: Boolean) {
        updateSettings(conversationId, muted = currentlyMuted, archived = !currentlyArchived)
    }

    /** Marks a conversation as read straight from the HOME chat list. */
    fun markListRead(conversationId: String) {
        viewModelScope.launch {
            service.markRead(conversationId)
                .onSuccess { refreshInbox() }
                .onFailure { e -> _error.value = friendly(e, "Could not mark as read") }
        }
    }

    /** Loads the list of blocked Personal ID usernames. */
    fun refreshBlockedList() {
        viewModelScope.launch {
            service.blockedList()
                .onSuccess { _blocked.value = it.toSet() }
                .onFailure { e -> Log.e(TAG, "blocked list refresh failed", e) }
        }
    }

    /**
     * Blocks / unblocks a Personal ID. After a successful toggle the inbox is
     * refreshed so blocked chats disappear from (or return to) the list.
     */
    fun toggleBlock(username: String, currentlyBlocked: Boolean) {
        viewModelScope.launch {
            service.toggleBlock(username, !currentlyBlocked)
                .onSuccess {
                    _blocked.value = if (currentlyBlocked) _blocked.value - username else _blocked.value + username
                    refreshInbox()
                }
                .onFailure { e -> _error.value = friendly(e, if (currentlyBlocked) "Could not unblock" else "Could not block") }
        }
    }

    // --------------------------------------------------------------------------
    // LOCAL conversation tweaks: pin to top, delete for me (hide), peer nickname,
    // forward draft. All stored per-device — no server round-trip needed.
    // --------------------------------------------------------------------------

    private fun loadNicknames(): Map<String, String> = try {
        val o = JSONObject(localPrefs.getString("nicknames", "{}") ?: "{}")
        val m = mutableMapOf<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k, "") }
        m
    } catch (e: Exception) { emptyMap() }

    private fun saveNicknames() {
        localPrefs.edit().putString("nicknames", JSONObject(_nicknames.value).toString()).apply()
    }

    /** Display name for a conversation: the per-device nickname, falling back to @username. */
    fun nickFor(conversationId: String, peerUsername: String): String =
        _nicknames.value[conversationId]?.takeIf { it.isNotBlank() } ?: "@$peerUsername"

    fun setPeerNickname(conversationId: String, peerUsername: String, label: String) {
        val clean = label.trim().replace(Regex("\\s+"), " ").take(24)
        val next = _nicknames.value.toMutableMap()
        if (clean.isBlank() || clean.equals("@$peerUsername", ignoreCase = true)) next.remove(conversationId) else next[conversationId] = clean
        _nicknames.value = next
        saveNicknames()
    }

    /** Pin/Unpin a conversation to the top of the Personal ID chat list. */
    fun togglePinConversation(conversationId: String) {
        val cur = _pinnedConvs.value
        val updated = if (conversationId in cur) cur - conversationId else cur + conversationId
        _pinnedConvs.value = updated
        localPrefs.edit().putStringSet("pinned_convs", updated.toSet()).apply()
        refreshInbox()
    }

    /** Delete chat (for me only: hides it from this device's inbox. */
    fun hideConversation(conversationId: String) {
        _hiddenConvs.value = _hiddenConvs.value + conversationId
        localPrefs.edit().putStringSet("hidden_convs", HashSet(_hiddenConvs.value)).apply()
        refreshInbox()
    }

    /** Restores every chat hidden with "Delete chat (for me)". */
    fun restoreAllHidden() {
        _hiddenConvs.value = emptySet()
        localPrefs.edit().putStringSet("hidden_convs", emptySet()).apply()
        refreshInbox()
    }

    /** Places a pending forwarded message that the composer of the target chat consumes. */
    fun setForwardDraft(text: String) { _forwardDraft.value = text }
    fun clearForwardDraft() { _forwardDraft.value = null }

    private fun refreshMessages(conversationId: String) {
        viewModelScope.launch {
            service.messages(conversationId)
                .onSuccess { arr -> _messages.value = parseMessages(arr) }
                .onFailure { e -> Log.e(TAG, "message refresh failed", e) }
        }
    }

    private suspend fun loadConversationSettings(conversationId: String) {
        service.conversationSettings(conversationId)
            .onSuccess { o ->
                _conversationSettings.value = PidConversationSettings(
                    muted = o.optBoolean("muted", false),
                    archived = o.optBoolean("archived", false),
                    readReceipts = o.optBoolean("read_receipts", true),
                    peerOnline = o.optBoolean("peer_online", false),
                    peerLastSeenMs = parseMillis(o.opt("peer_last_seen"))
                )
            }
            .onFailure { e -> Log.e(TAG, "conversation settings failed", e) }
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
        viewModelScope.launch {
            service.touchPresence()
            loadConversationSettings(conversationId)
            service.markRead(conversationId)
        }
        pollJob = viewModelScope.launch {
            while (coroutineContext.isActive) {
                delay(3000)
                if (!coroutineContext.isActive) break
                service.touchPresence()
                service.messages(conversationId)
                    .onSuccess { arr -> _messages.value = parseMessages(arr) }
                    .onFailure { e: Throwable -> Log.e(TAG, "message poll failed", e) }
                service.markRead(conversationId)
                loadConversationSettings(conversationId)
            }
        }
        callPollJob = viewModelScope.launch {
            while (coroutineContext.isActive) {
                delay(2500)
                if (!coroutineContext.isActive) break
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
        agora.endCall()
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
                    // Create the Agora offer (with gathered ICE encoded in the marker).
                    val offer = agora.createOutgoingOffer(isVideo, useFrontCamera = true)
                    if (offer == null) {
                        _error.value = "Could not start the call (media engine busy)"
                        _loading.value = false
                        return@onSuccess
                    }
                    service.callUpdate(currentCallId!!, sdp = offer.sdp, status = "OFFERING")
                    _activeCall.value = CallState(
                        partnerName = "@${s.peerUsername}",
                        partnerAvatar = "",
                        isVideo = isVideo,
                        isMuted = false,
                        isCameraOn = isVideo,
                        isSpeakerOn = true,
                        isFrontCamera = true,
                        status = "Connected",
                        callId = currentCallId ?: "",
                        remoteHandle = s.peerUsername,
                        isOutgoing = true
                    )
                    startCallDurationTicker()
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
                    val answer = runCatching { agora.receiveIncoming(isVideo, offer) }
                        .getOrElse {
                            _loading.value = false
                            _error.value = "Could not connect audio/video"
                            service.callReject(inc.callId)
                            return@onSuccess
                        }
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
        agora.endCall()
    }

    fun endCall() {
        stopCallDurationTicker()
        val callId = currentCallId
        clearCall()
        if (callId != null) {
            viewModelScope.launch { service.callEnd(callId) }
        }
        agora.endCall()
    }

    private fun clearCall() {
        _activeCall.value = null
        _incomingCall.value = null
    }

    fun toggleMute() {
        val c = _activeCall.value ?: return
        val muted = !c.isMuted
        agora.toggleMute(muted)
        _activeCall.value = c.copy(isMuted = muted)
    }

    fun toggleCamera() {
        val c = _activeCall.value ?: return
        if (!c.isVideo) return
        val enabled = !c.isCameraOn
        agora.toggleCamera(enabled)
        _activeCall.value = c.copy(isCameraOn = enabled)
    }

    fun toggleSpeaker() {
        val c = _activeCall.value ?: return
        val on = !c.isSpeakerOn
        agora.toggleSpeaker(on)
        _activeCall.value = c.copy(isSpeakerOn = on)
    }

    fun flipCamera() {
        agora.switchCamera()
        val c = _activeCall.value
        if (c != null) _activeCall.value = c.copy(isFrontCamera = !c.isFrontCamera)
    }

    private var callDurationJob: Job? = null

    private fun startCallDurationTicker() {
        if (callDurationJob?.isActive == true) return
        callDurationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val current = _activeCall.value ?: break
                _activeCall.value = current.copy(durationSec = current.durationSec + 1)
            }
        }
    }

    private fun stopCallDurationTicker() {
        callDurationJob?.cancel()
        callDurationJob = null
    }

    fun toggleScreenSharing() {
        val current = _activeCall.value ?: return
        // Screen capture requires MediaProjection permission (system scope);
        // this toggle is kept for UI flow consistency.
        _activeCall.value = current.copy(isScreenSharing = !current.isScreenSharing)
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
                    agora.applyRemoteAnswer(answer) { ok ->
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
                agora.endCall()
                currentCallId = null
                _error.value = if (status == "REJECTED") "Call was declined" else "Call ended"
            }

            // Callee: caller ended the call while it was connected.
            if (_activeCall.value != null && currentCallId == sigId && answerApplied &&
                (status == "ENDED" || status == "MISSED")
            ) {
                val wasActive = _activeCall.value != null
                clearCall()
                agora.endCall()
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
                    unreadCount = o.optInt("unread_count", 0),
                    isMuted = o.optBoolean("is_muted", false),
                    isArchived = o.optBoolean("is_archived", false)
                )
            )
        }
        return out
    }

    private fun parseMessages(arr: JSONArray): List<PidMessage> {
        val out = mutableListOf<PidMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val reactions = mutableListOf<PidReaction>()
            val arrR = o.optJSONArray("reactions")
            if (arrR != null) {
                for (j in 0 until arrR.length()) {
                    val r = arrR.optJSONObject(j) ?: continue
                    reactions.add(
                        PidReaction(
                            emoji = r.optString("emoji", ""),
                            count = r.optInt("count", 0),
                            reactedByMe = r.optBoolean("reacted_by_me", false)
                        )
                    )
                }
            }
            out.add(
                PidMessage(
                    id = jsonStr(o, "id"),
                    text = jsonStr(o, "message_text"),
                    isMine = o.optBoolean("is_mine", false),
                    createdAtMs = parseMillis(o.opt("created_at")),
                    createdAt = jsonStr(o, "created_at"),
                    isRead = o.optBoolean("is_read", false),
                    editedAtMs = parseMillis(o.opt("edited_at")),
                    replyToId = jsonStr(o, "reply_to_id").takeIf { it.isNotBlank() },
                    replyText = jsonStr(o, "reply_text"),
                    replyIsMine = o.optBoolean("reply_is_mine", false),
                    mediaUrl = jsonStr(o, "media_url"),
                    mediaType = jsonStr(o, "media_type"),
                    mediaName = jsonStr(o, "media_name"),
                    isPinned = o.optBoolean("is_pinned", false),
                    reactions = reactions
                )
            )
        }
        return out
    }

    /** Android's org.json optString() returns the literal string "null" for JSON null
     *  values — normalize those to blank so optional fields (reply/media) don't render
     *  wrongly (e.g. every message showing a "Replying to message" quote). */
    private fun jsonStr(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "").takeIf { it.isNotBlank() && it != "null" } ?: ""

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