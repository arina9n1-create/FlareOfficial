package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VynNumber.VynNumberService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** State machine of the VYN NUMBER tab. */
sealed class VynNumberStep {
    /** No active phone session — show the sign up / log in screen. */
    data object Setup : VynNumberStep()

    /** Verified + activated — show the VYN NUMBER home (number, search, chats). */
    data class Home(val phone: String, val displayName: String) : VynNumberStep()

    /** Inside a VYN NUMBER conversation. */
    data class Chat(
        val conversationId: String,
        val peerPhone: String,
        val peerName: String
    ) : VynNumberStep()
}

data class VynNumberChatItem(
    val conversationId: String,
    val peerPhone: String,
    val peerName: String,
    val lastPreview: String,
    val lastAt: Long?,       // epoch millis
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false
)

data class VynNumberMessage(
    val id: String,
    val senderIdentityId: String,
    val text: String,
    val mediaUrl: String,
    val mediaType: String,
    val isMine: Boolean,
    val createdAtMs: Long,
    val createdAt: String = ""
)

class VynNumberViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "VynNumberViewModel"
    }

    private val service = VynNumberService(app)

    /** Delegates phone validation to the service (matches server-side normalization). */
    fun isValidPhone(raw: String): Boolean = service.isValidPhone(raw)

    private val _step = MutableStateFlow<VynNumberStep>(VynNumberStep.Setup)
    val step: StateFlow<VynNumberStep> = _step

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _chats = MutableStateFlow<List<VynNumberChatItem>>(emptyList())
    val chats: StateFlow<List<VynNumberChatItem>> = _chats

    private val _chatsLoading = MutableStateFlow(false)
    val chatsLoading: StateFlow<Boolean> = _chatsLoading

    private val _messages = MutableStateFlow<List<VynNumberMessage>>(emptyList())
    val messages: StateFlow<List<VynNumberMessage>> = _messages

    private val _msgsLoading = MutableStateFlow(false)
    val msgsLoading: StateFlow<Boolean> = _msgsLoading

    private val _searchResult = MutableStateFlow<JSONObject?>(null)
    val searchResult: StateFlow<JSONObject?> = _searchResult

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    /** Aliases used by the VYN NUMBER composables. */
    val chatMessages: StateFlow<List<VynNumberMessage>> = _messages

    /** The caller's own VYN NUMBER identity id (empty until activated). */
    var myIdentityId: String = ""
        private set

    private var pollJob: Job? = null

    init {
        // Restore a still-valid session without re-verifying (same device only).
        if (service.hasActiveSession()) {
            activate()
        }
    }

    fun dismissError() { _error.value = null }

    // ---------------------------------------------------------------------------
    // Auth flow (phone number + password — no OTP/SMS)
    // ---------------------------------------------------------------------------

    /** Creates a new VYN NUMBER account with phone + password. */
    fun signUp(rawPhone: String, password: String) {
        authenticate(rawPhone, password) { p, pw -> service.signUpWithPhone(p, pw) }
    }

    /** Logs in to an existing VYN NUMBER with phone + password. */
    fun login(rawPhone: String, password: String) {
        authenticate(rawPhone, password) { p, pw -> service.signInWithPhone(p, pw) }
    }

    private fun authenticate(
        rawPhone: String,
        password: String,
        call: suspend (String, String) -> Result<String>
    ) {
        if (_loading.value) return
        if (!service.isValidPhone(rawPhone)) {
            _error.value = "Please enter a valid phone number"
            return
        }
        if (password.length < 6) {
            _error.value = "Password must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            call(rawPhone, password)
                .onSuccess { activate() }
                .onFailure { e: Throwable ->
                    _error.value = friendly(e, "Authentication failed — try again")
                }
            _loading.value = false
        }
    }

    /** Wipes only the VYN NUMBER session — the Vyn9 account stays logged in. */
    fun signOutVynNumber() {
        service.clearSession()
        _chats.value = emptyList()
        _messages.value = emptyList()
        _step.value = VynNumberStep.Setup
    }

    /** Loads (or lazily creates) the identity of the verified phone session. */
    private fun activate() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.activateIdentity()
                .onSuccess { identity ->
                    myIdentityId = identity.optString("identity_id", "")
                    _step.value = VynNumberStep.Home(
                        phone = identity.optString("phone"),
                        displayName = identity.optString("display_name", "")
                    )
                    refreshInbox()
                }
                .onFailure { e: Throwable ->
                    Log.e(TAG, "activate failed", e)
                    // Session unusable -> force re-verification.
                    service.clearSession()
                    _step.value = VynNumberStep.Setup
                    _error.value = friendly(e, "Could not activate VYN NUMBER — verify your number again")
                }
            _loading.value = false
        }
    }

    // ---------------------------------------------------------------------------
    // Inbox / search / conversations
    // ---------------------------------------------------------------------------

    fun refreshInbox() {
        viewModelScope.launch {
            _chatsLoading.value = true
            service.inbox()
                .onSuccess { arr ->
                    val items = mutableListOf<VynNumberChatItem>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        items.add(
                            VynNumberChatItem(
                                conversationId = o.optString("conversation_id"),
                                peerPhone = o.optString("peer_phone"),
                                peerName = o.optString("peer_name", ""),
                                lastPreview = o.optString("last_message_preview", ""),
                                lastAt = parseMillis(o.opt("last_message_at")),
                                unreadCount = o.optInt("unread_count", 0),
                                isPinned = o.optBoolean("is_pinned", false),
                                isMuted = o.optBoolean("is_muted", false),
                                isArchived = o.optBoolean("is_archived", false),
                                isFavorite = o.optBoolean("is_favorite", false)
                            )
                        )
                    }
                    _chats.value = items
                }
                .onFailure { e: Throwable -> Log.e(TAG, "inbox failed", e) }
            _chatsLoading.value = false
        }
    }

    fun searchNumber(rawPhone: String) {
        if (!service.isValidPhone(rawPhone)) {
            _error.value = "Please enter a valid phone number"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.searchNumber(rawPhone)
                .onSuccess { _searchResult.value = it }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Search failed — check your connection") }
            _loading.value = false
        }
    }

    fun clearSearch() { _searchResult.value = null }

    /** Exposes normalization for local device-contact handling. */
    fun normalizeNumber(raw: String): String? = service.normalizePhone(raw)


    /** Opens a chat with a number (must be active on VYN NUMBER). */
    fun openChatWith(rawPhone: String) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.openConversation(rawPhone)
                .onSuccess { o ->
                    _searchResult.value = null
                    enterChat(
                        conversationId = o.optString("conversation_id"),
                        peerPhone = o.optString("peer_phone"),
                        peerName = o.optString("peer_name", "")
                    )
                }
                .onFailure { e: Throwable -> _error.value = friendly(e, "This number is not active on VYN NUMBER") }
            _loading.value = false
        }
    }

    fun openExistingChat(item: VynNumberChatItem) {
        enterChat(item.conversationId, item.peerPhone, item.peerName)
    }

    /** (Re)loads message history for a conversation and marks it read. */
    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            service.messages(conversationId)
                .onSuccess { arr -> _messages.value = parseMessages(arr) }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not load messages") }
            service.markRead(conversationId)
        }
    }

    /** Screen-facing send: refreshes the thread on success. */
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

    /** Alias used by the conversation screen's back handler. */
    fun closeChat() = backFromChat()

    /** Marks a conversation as read on the server. */
    fun markRead(conversationId: String) {
        viewModelScope.launch {
            service.markRead(conversationId)
                .onFailure { e: Throwable -> Log.e(TAG, "markRead failed", e) }
        }
    }

    // ---------------------------------------------------------------------------
    // Chat settings (pin, mute, archive, favorite, block)
    // ---------------------------------------------------------------------------

    fun togglePin(conversationId: String) {
        viewModelScope.launch {
            service.togglePin(conversationId)
                .onSuccess { refreshInbox() }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not pin chat") }
        }
    }

    fun toggleMute(conversationId: String) {
        viewModelScope.launch {
            service.toggleMute(conversationId)
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not mute chat") }
        }
    }

    fun toggleArchive(conversationId: String) {
        viewModelScope.launch {
            service.toggleArchive(conversationId)
                .onSuccess { refreshInbox() }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not archive chat") }
        }
    }

    fun toggleFavorite(conversationId: String) {
        viewModelScope.launch {
            service.toggleFavorite(conversationId)
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not favorite chat") }
        }
    }

    fun markUnread(conversationId: String) {
        viewModelScope.launch {
            service.markUnread(conversationId)
                .onSuccess { refreshInbox() }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not mark unread") }
        }
    }

    fun clearHistory(conversationId: String) {
        viewModelScope.launch {
            service.clearHistory(conversationId)
                .onSuccess {
                    _messages.value = emptyList()
                    refreshInbox()
                }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not clear history") }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            service.deleteConversation(conversationId)
                .onSuccess { backFromChat() }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not delete conversation") }
        }
    }

    fun blockUser(peerPhone: String) {
        viewModelScope.launch {
            service.blockUser(peerPhone)
                .onSuccess { backFromChat() }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not block user") }
        }
    }

    fun reportUser(peerPhone: String, reason: String) {
        viewModelScope.launch {
            service.reportUser(peerPhone, reason)
                .onSuccess { _error.value = "Report submitted — thank you" }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not submit report") }
        }
    }

    /**
     * Reads device contact names + numbers LOCALLY for the manual picker.
     * PRIVACY: nothing is uploaded or stored server-side; no background sync.
     */
    fun loadDeviceContacts(): List<Pair<String, String>> =
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val out = mutableListOf<Pair<String, String>>()
                try {
                    val cr = getApplication<Application>().contentResolver
                    val cursor = cr.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null, null,
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                    )
                    cursor?.use {
                        val nameIdx = it.getColumnIndexOrThrow(
                            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        )
                        val numIdx = it.getColumnIndexOrThrow(
                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                        )
                        while (it.moveToNext() && out.size < 500) {
                            val name = it.getString(nameIdx) ?: continue
                            val number = it.getString(numIdx) ?: continue
                            if (number.isBlank()) continue
                            out.add(name to number)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "loadDeviceContacts failed", e)
                }
                out.distinctBy { it.second }
            }
        }

    private fun enterChat(conversationId: String, peerPhone: String, peerName: String) {
        _step.value = VynNumberStep.Chat(conversationId, peerPhone, peerName)
        viewModelScope.launch {
            _msgsLoading.value = true
            service.messages(conversationId)
                .onSuccess { arr -> _messages.value = parseMessages(arr) }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not load messages") }
            _msgsLoading.value = false
        }
        startPolling(conversationId)
    }

    fun backFromChat() {
        pollJob?.cancel()
        val s = _step.value
        _messages.value = emptyList()
        _msgsLoading.value = false
        viewModelScope.launch {
            if (s is VynNumberStep.Chat) service.markRead(s.conversationId)
            refreshInbox()
        }
        _step.value = if (s is VynNumberStep.Chat) {
            VynNumberStep.Home(phone = service.savedPhone(), displayName = "")
        } else {
            VynNumberStep.Setup
        }
    }

    // ---------------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------------

    fun sendText(text: String) {
        val s = _step.value as? VynNumberStep.Chat ?: return
        val body = text.trim()
        if (body.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            service.sendMessage(s.conversationId, body)
                .onSuccess {
                    service.messages(s.conversationId)
                        .onSuccess { arr -> _messages.value = parseMessages(arr) }
                }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Message could not be sent") }
            _sending.value = false
        }
    }

    private fun startPolling(conversationId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(4000)
                service.messages(conversationId)
                    .onSuccess { arr -> _messages.value = parseMessages(arr) }
                    .onFailure { e: Throwable -> Log.e(TAG, "poll failed", e) }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun parseMessages(arr: org.json.JSONArray): List<VynNumberMessage> {
        val out = mutableListOf<VynNumberMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                VynNumberMessage(
                    id = o.optString("id"),
                    senderIdentityId = o.optString("sender_identity_id"),
                    text = o.optString("message_text", ""),
                    mediaUrl = o.optString("media_url", ""),
                    mediaType = o.optString("media_type", ""),
                    isMine = o.optBoolean("is_mine", false),
                    createdAtMs = parseMillis(o.opt("created_at")) ?: 0L
                )
            )
        }
        return out
    }

    private fun friendly(e: Throwable, fallback: String): String {
        val raw = e.message ?: return fallback
        // PostgREST wraps RAISE EXCEPTION text as {"message":".."}; extract it.
        val msg = raw.substringAfter("message\":\"").substringBefore("\"")
        return msg.take(160).ifBlank { raw.take(160) }.ifBlank { fallback }
    }
}

/** Parses a PostgREST timestamp ("2026-08-30T12:34:56.789+00:00") to epoch millis. */
internal fun parseMillis(v: Any?): Long? {
    val s = (v as? String) ?: return null
    return try {
        java.time.Instant.parse(s).toEpochMilli()
    } catch (e: Throwable) {
        try {
            java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (e2: Exception) { null }
    }
}





