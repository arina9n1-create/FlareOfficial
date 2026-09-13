package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.flarenumber.FlareNumberService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** State machine of the FLARE NUMBER tab. */
sealed class FlareNumberStep {
    /** No active phone session — show the sign up / log in screen. */
    data object Setup : FlareNumberStep()

    /** Verified + activated — show the FLARE NUMBER home (number, search, chats). */
    data class Home(val phone: String, val displayName: String) : FlareNumberStep()

    /** Inside a FLARE NUMBER conversation. */
    data class Chat(
        val conversationId: String,
        val peerPhone: String,
        val peerName: String
    ) : FlareNumberStep()
}

data class FlareNumberChatItem(
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

data class FlareNumberMessage(
    val id: String,
    val senderIdentityId: String,
    val text: String,
    val mediaUrl: String,
    val mediaType: String,
    val isMine: Boolean,
    val createdAtMs: Long,
    val createdAt: String = ""
)

class FlareNumberViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "FlareNumberViewModel"
    }

    private val service = FlareNumberService(app)

    /** Delegates phone validation to the service (matches server-side normalization). */
    fun isValidPhone(raw: String): Boolean = service.isValidPhone(raw)

    private val _step = MutableStateFlow<FlareNumberStep>(FlareNumberStep.Setup)
    val step: StateFlow<FlareNumberStep> = _step

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _chats = MutableStateFlow<List<FlareNumberChatItem>>(emptyList())
    val chats: StateFlow<List<FlareNumberChatItem>> = _chats

    private val _chatsLoading = MutableStateFlow(false)
    val chatsLoading: StateFlow<Boolean> = _chatsLoading

    private val _messages = MutableStateFlow<List<FlareNumberMessage>>(emptyList())
    val messages: StateFlow<List<FlareNumberMessage>> = _messages

    private val _msgsLoading = MutableStateFlow(false)
    val msgsLoading: StateFlow<Boolean> = _msgsLoading

    private val _searchResult = MutableStateFlow<JSONObject?>(null)
    val searchResult: StateFlow<JSONObject?> = _searchResult

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    /** Nickname for the user's own FLARE number (empty if none set). */
    private val _selfNickname = MutableStateFlow("")
    val selfNickname: StateFlow<String> = _selfNickname

    /** Aliases used by the FLARE NUMBER composables. */
    val chatMessages: StateFlow<List<FlareNumberMessage>> = _messages

    /** The caller's own FLARE NUMBER identity id (empty until activated). */
    var myIdentityId: String = ""
        private set

        private var pollJob: Job? = null

    /** Peer last_seen (epoch millis) for the open conversation — realtime via polling. */
    private val _peerLastSeenMs = MutableStateFlow<Long?>(null)
    val peerLastSeenMs: StateFlow<Long?> = _peerLastSeenMs

    // ---------------------------------------------------------------------------
    // Auto-reply configuration & state
    // ---------------------------------------------------------------------------

    /** Configurable auto-reply settings (persisted locally via the service). */
    private val _autoReplyConfig = MutableStateFlow(service.loadAutoReplyConfig())
    val autoReplyConfig: StateFlow<FlareNumberService.AutoReplyConfig> = _autoReplyConfig

    /** Timestamp of the last incoming message that already triggered an auto-reply. */
    private var lastAutoRepliedMsgTimestamp: Long = 0L

    /** Whether the incoming-message timestamp baseline has been established. Prevents
     *  auto-replying to pre-existing messages when the conversation is first opened. */
    private var autoReplyTimestampInitialized: Boolean = false

    /** Ongoing auto-reply coroutine (prevents overlapping reply bursts). */
    private var autoReplyJob: Job? = null

    init {
        // Restore a still-valid session without re-verifying (same device only).
        if (service.hasActiveSession()) {
            activate()
        }
        // Restore any locally-saved nickname for the user's own number.
        _selfNickname.value = service.loadSelfNickname()
    }

    fun dismissError() { _error.value = null }

    // ---------------------------------------------------------------------------
    // Auth flow (phone number + password — no OTP/SMS)
    // ---------------------------------------------------------------------------

    /** Creates a new FLARE NUMBER account with phone + password. */
    fun signUp(rawPhone: String, password: String) {
        authenticate(rawPhone, password) { p, pw -> service.signUpWithPhone(p, pw) }
    }

    /** Logs in to an existing FLARE NUMBER with phone + password. */
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

    /** Wipes only the FLARE NUMBER session — the FlareOfficial account stays logged in. */
    fun signOutFlareNumber() {
        service.clearSession()
        _chats.value = emptyList()
        _messages.value = emptyList()
        _selfNickname.value = ""
        _step.value = FlareNumberStep.Setup
    }

    /** Saves a nickname for the user's own FLARE number (persists locally). */
    fun setSelfNickname(nickname: String) {
        val trimmed = nickname.trim()
        service.saveSelfNickname(trimmed)
        _selfNickname.value = trimmed
    }

    /** Clears the nickname for the user's own FLARE number. */
        fun clearSelfNickname() {
        service.saveSelfNickname("")
        _selfNickname.value = ""
    }

    // ---------------------------------------------------------------------------
    // Auto-reply configuration
    // ---------------------------------------------------------------------------

    /** Persists the full auto-reply configuration and updates observers. */
    fun updateAutoReplyConfig(config: FlareNumberService.AutoReplyConfig) {
        _autoReplyConfig.value = config
        service.saveAutoReplyConfig(config)
    }

    /** Quick toggle for enabling/disabling auto-reply without touching other settings. */
    fun toggleAutoReplyEnabled(enabled: Boolean) {
        val config = _autoReplyConfig.value.copy(isEnabled = enabled)
        _autoReplyConfig.value = config
        service.saveAutoReplyConfig(config)
    }

    /** Loads (or lazily creates) the identity of the verified phone session. */
    private fun activate() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.activateIdentity()
                .onSuccess { identity ->
                    myIdentityId = identity.optString("identity_id", "")
                    _step.value = FlareNumberStep.Home(
                        phone = identity.optString("phone"),
                        displayName = identity.optString("display_name", "")
                    )
                    refreshInbox()
                }
                .onFailure { e: Throwable ->
                    Log.e(TAG, "activate failed", e)
                    // Session unusable -> force re-verification.
                    service.clearSession()
                    _step.value = FlareNumberStep.Setup
                    _error.value = friendly(e, "Could not activate FLARE NUMBER — verify your number again")
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
                    val items = mutableListOf<FlareNumberChatItem>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        items.add(
                            FlareNumberChatItem(
                                conversationId = jsonStr(o, "conversation_id"),
                                peerPhone = jsonStr(o, "peer_phone"),
                                peerName = jsonStr(o, "peer_name"),
                                lastPreview = jsonStr(o, "last_message_preview"),
                                lastAt = parseMillis(o.opt("last_message_at")),
                                unreadCount = o.optInt("unread_count", 0),
                                isPinned = o.optBoolean("is_pinned", false),
                                isMuted = o.optBoolean("is_muted", false),
                                isArchived = o.optBoolean("is_archived", false),
                                isFavorite = o.optBoolean("is_favorite", false)
                            )
                        )
                    }
                    // WhatsApp-style contact-name sync: the name saved for the number
                    // in the DEVICE contacts always wins, re-resolved on every refresh
                    // so renaming a contact updates the chat list too.
                    val synced = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        items.map { item ->
                            item.copy(peerName = resolveContactName(item.peerPhone) ?: item.peerName)
                        }
                    }
                    _chats.value = synced
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

    // --- Device contact-name resolution (WhatsApp-style contact sync) ---

    private var contactNamesCache: List<Pair<String, String>>? = null

    /** Resolves the name saved for this number in the device contacts.
     *  Matching uses the LAST 8 DIGITS so country codes and formatting
     *  ("+8801…", "01…", spaces, dashes) don't break the match. */
    fun resolveContactName(rawPhone: String): String? {
        val digits = rawPhone.filter { it.isDigit() }
        val suffix = if (digits.length > 8) digits.takeLast(8) else digits
        if (suffix.isBlank()) return null
        val contacts = contactNamesCache
            ?: runCatching { loadDeviceContacts() }.getOrNull()?.also { contactNamesCache = it }
            ?: return null
        return contacts.firstOrNull { (name, number) ->
            val n = number.filter { ch -> ch.isDigit() }
            ((if (n.length > 8) n.takeLast(8) else n) == suffix) && name.isNotBlank()
        }?.first
    }

    /** Call after the user edits their device contacts so names re-resolve. */
    fun clearContactCache() { contactNamesCache = null }


    /** Opens a chat with a number (must be active on FLARE NUMBER).
     *  [contactName] is the name picked from the device contacts (if any) — it
     *  overrides the server nickname, WhatsApp-style. */
    fun openChatWith(rawPhone: String, contactName: String? = null) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            service.openConversation(rawPhone)
                .onSuccess { o ->
                    _searchResult.value = null
                    val phone = jsonStr(o, "peer_phone")
                    val resolved = contactName?.takeIf { it.isNotBlank() }
                        ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { resolveContactName(phone) }
                    enterChat(
                        conversationId = jsonStr(o, "conversation_id"),
                        peerPhone = phone,
                        peerName = resolved ?: jsonStr(o, "peer_name")
                    )
                }
                .onFailure { e: Throwable -> _error.value = friendly(e, "This number is not active on FLARE NUMBER") }
            _loading.value = false
        }
    }

    fun openExistingChat(item: FlareNumberChatItem) {
        viewModelScope.launch {
            // Always re-resolve the contact name so the chat header reflects the
            // name currently saved in the device contacts (WhatsApp behaviour).
            val contact = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                resolveContactName(item.peerPhone)
            }
            enterChat(item.conversationId, item.peerPhone, contact ?: item.peerName)
        }
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
        _step.value = FlareNumberStep.Chat(conversationId, peerPhone, peerName)
        // Reset auto-reply tracking so only messages received AFTER entering
        // this conversation can trigger an auto-reply.
        autoReplyTimestampInitialized = false
        lastAutoRepliedMsgTimestamp = 0L
        autoReplyJob?.cancel()
        _peerLastSeenMs.value = null
        viewModelScope.launch {
            service.touchPresence()
            service.conversationPresence(conversationId).onSuccess { _peerLastSeenMs.value = it }
        }
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
        autoReplyJob?.cancel()
        val s = _step.value
        _messages.value = emptyList()
        _msgsLoading.value = false
        viewModelScope.launch {
            if (s is FlareNumberStep.Chat) service.markRead(s.conversationId)
            refreshInbox()
        }
        // If we have a valid active FLARE NUMBER session, always land back on the
        // Home/inbox screen — never the Setup (login) screen. This keeps delete /
        // block actions invoked from the chat LIST from accidentally logging out.
        _step.value = if (s is FlareNumberStep.Chat || service.savedPhone().isNotBlank()) {
            FlareNumberStep.Home(phone = service.savedPhone(), displayName = "")
        } else {
            FlareNumberStep.Setup
        }
    }

    // ---------------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------------

    fun sendText(text: String) {
        val s = _step.value as? FlareNumberStep.Chat ?: return
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

    /** Deletes (unsends) one of the caller's own messages, then reloads the thread. */
    fun deleteMessage(messageId: String) {
        val s = _step.value as? FlareNumberStep.Chat ?: return
        viewModelScope.launch {
            service.deleteMessage(s.conversationId, messageId)
                .onSuccess {
                    _messages.value = _messages.value.filterNot { it.id == messageId }
                    service.messages(s.conversationId)
                        .onSuccess { arr -> _messages.value = parseMessages(arr) }
                    refreshInbox()
                }
                .onFailure { e: Throwable -> _error.value = friendly(e, "Could not delete message") }
        }
    }

        private fun startPolling(conversationId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (coroutineContext.isActive) {
                delay(4000)
                if (!coroutineContext.isActive) break
                service.messages(conversationId)
                    .onSuccess { arr ->
                        val newMessages = parseMessages(arr)
                        _messages.value = newMessages
                        checkIncomingAndAutoReply(conversationId, newMessages)
                    }
                    .onFailure { e: Throwable -> Log.e(TAG, "poll failed", e) }
                // Realtime presence: heartbeat for me + last_seen for the peer.
                service.touchPresence()
                service.conversationPresence(conversationId)
                    .onSuccess { _peerLastSeenMs.value = it }
                    .onFailure { e: Throwable -> Log.e(TAG, "presence poll failed", e) }
            }
        }
    }

        /**
     * Inspects freshly-pollled messages for NEW incoming messages (from the other
     * party) and, when auto-reply is enabled, sends a burst of replies.
     *
     *  - Only messages newer than [lastAutoRepliedMsgTimestamp] and NOT from me
     *    are considered.
     *  - On the very first poll for a conversation the timestamp baseline is
     *    established so pre-existing messages do not trigger a reply.
     *  - If [AutoReplyConfig.repeatOnReply] is false, no further auto-replies
     *    fire after the first burst (the threshold is locked to MAX_VALUE).
     */
    private fun checkIncomingAndAutoReply(conversationId: String, messages: List<FlareNumberMessage>) {
        val config = _autoReplyConfig.value
        if (!config.isEnabled) return

        // Don't start a new burst while one is already in flight.
        autoReplyJob?.let { if (it.isActive) return }

        // First call for this conversation: just record the latest timestamp
        // as the baseline so existing messages don't trigger a reply.
        if (!autoReplyTimestampInitialized) {
            lastAutoRepliedMsgTimestamp = messages.maxOfOrNull { it.createdAtMs } ?: 0L
            autoReplyTimestampInitialized = true
            return
        }

        // Find incoming messages that arrived since our last auto-reply.
        val newIncoming = messages.filter {
            !it.isMine && it.createdAtMs > lastAutoRepliedMsgTimestamp
        }
        if (newIncoming.isEmpty()) return

        // Use the newest unseen message as the trigger.
        val triggerMsg = newIncoming.maxByOrNull { it.createdAtMs }!!
        lastAutoRepliedMsgTimestamp = triggerMsg.createdAtMs

        // Send the configured number of replies, spaced by the delay so they
        // arrive as a natural-looking burst.
        autoReplyJob = viewModelScope.launch {
            repeat(config.replyCount) { index ->
                if (index > 0) delay(config.replyDelayMs)
                val replyText = config.primaryMessage
                if (replyText.isNotBlank()) {
                    service.sendMessage(conversationId, replyText)
                        .onFailure { e -> Log.e(TAG, "auto-reply send failed", e) }
                }
            }
            // Reload the thread so the auto-replies are visible immediately.
            service.messages(conversationId)
                .onSuccess { arr -> _messages.value = parseMessages(arr) }
            refreshInbox()

            // If repeat is disabled, lock the threshold so no future incoming
            // message ever triggers another auto-reply.
            if (!config.repeatOnReply) {
                lastAutoRepliedMsgTimestamp = Long.MAX_VALUE
            }
            autoReplyJob = null
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun parseMessages(arr: org.json.JSONArray): List<FlareNumberMessage> {
        val out = mutableListOf<FlareNumberMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                FlareNumberMessage(
                    id = jsonStr(o, "id"),
                    senderIdentityId = jsonStr(o, "sender_identity_id"),
                    text = jsonStr(o, "message_text"),
                    mediaUrl = jsonStr(o, "media_url"),
                    mediaType = jsonStr(o, "media_type"),
                    isMine = o.optBoolean("is_mine", false),
                    createdAtMs = parseMillis(o.opt("created_at")) ?: 0L
                )
            )
        }
        return out
    }

    /** Android's org.json optString() returns the literal string "null" for JSON null
     *  values — normalize those to blank so optional fields (media etc.) don't render
     *  as phantom attachments. */
    private fun jsonStr(o: org.json.JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "").takeIf { it.isNotBlank() && it != "null" } ?: ""

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
