package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.FlareAvatar
import com.example.ui.theme.FlareTextSecondary
import com.example.ui.viewmodel.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class ModerationTab(val label: String) {
    POSTS("📝 Posts"),
    REELS("🎬 Reels"),
    VIDEOS("🎞️ Videos"),
    REPORTS("🚩 Reports"),
    USERS("👥 Users"),
    WARNINGS("⚠️ Warnings"),
    ACTIVITY("📜 Activity Log")
}

enum class DateFilterOption(val label: String) {
    ALL("All"), TODAY("Today"), YESTERDAY("Yesterday"),
    LAST_7("Last 7 Days"), LAST_30("Last 30 Days"), CUSTOM("Custom")
}

/** Lightweight warning target — content cards only know the author handle. */
data class ModWarnTarget(val uid: String, val handle: String)

/** Resolves a date filter option + custom range into [fromMs, toMs] epoch bounds (0 = unbounded). */
fun resolveDateBounds(option: DateFilterOption, fromText: String, toText: String): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    fun startOfDay(): Long {
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    fun parseDate(text: String, endOfDay: Boolean): Long {
        if (text.isBlank()) return 0L
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = p.parse(text.trim()) ?: return 0L
            if (endOfDay) d.time + 24L * 60 * 60 * 1000 - 1 else d.time
        } catch (e: Exception) { 0L }
    }
    return when (option) {
        DateFilterOption.ALL -> 0L to 0L
        DateFilterOption.TODAY -> startOfDay() to System.currentTimeMillis()
        DateFilterOption.YESTERDAY -> {
            val end = startOfDay() - 1
            (end - 24L * 60 * 60 * 1000 + 1) to end
        }
        DateFilterOption.LAST_7 -> (startOfDay() - 6L * 24 * 60 * 60 * 1000) to System.currentTimeMillis()
        DateFilterOption.LAST_30 -> (startOfDay() - 29L * 24 * 60 * 60 * 1000) to System.currentTimeMillis()
        DateFilterOption.CUSTOM -> parseDate(fromText, false) to parseDate(toText, true)
    }
}

private val modDateFmt = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US)
private fun fmtModDate(ms: Long): String = if (ms <= 0) "—" else modDateFmt.format(Date(ms))
private fun fmtDuration(secs: Int): String = if (secs <= 0) "—"
else if (secs < 60) "${secs}s"
else String.format(Locale.US, "%d:%02d", secs / 60, secs % 60)

/** True when a creation timestamp falls inside the selected date bounds. */
private fun inDateRange(ts: Long, bounds: Pair<Long, Long>): Boolean {
    val (from, to) = bounds
    if (from <= 0 && to <= 0) return true
    return ts in from..(if (to > 0) to else Long.MAX_VALUE)
}

private const val MOD_PAGE_SIZE = 20
private fun <T> paginate(list: List<T>, page: Int): List<T> =
    list.drop(page * MOD_PAGE_SIZE).take(MOD_PAGE_SIZE)
private fun pageCount(size: Int): Int = if (size == 0) 1 else (size + MOD_PAGE_SIZE - 1) / MOD_PAGE_SIZE
@Composable
fun ModerationCenter(viewModel: SocialViewModel) {
    var selectedTab by remember { mutableStateOf(ModerationTab.POSTS) }
    var searchQuery by remember { mutableStateOf("") }
    var dateOption by remember { mutableStateOf(DateFilterOption.ALL) }
    var customFrom by remember { mutableStateOf("") }
    var customTo by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(0) }

    val isSuperAdmin = viewModel.isSuperAdmin()
    val posts by viewModel.posts.collectAsState()
    val reels by viewModel.reels.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val reports by viewModel.moderationReports.collectAsState()
    val warnings by viewModel.moderationWarnings.collectAsState()
    val activity by viewModel.moderationActivity.collectAsState()
    val loading by viewModel.moderationLoading.collectAsState()

    val bounds = resolveDateBounds(dateOption, customFrom, customTo)

    // Server-side refresh for the private moderation datasets whenever filters change
    LaunchedEffect(selectedTab, dateOption, customFrom, customTo, searchQuery) {
        page = 0
        when (selectedTab) {
            ModerationTab.REPORTS -> viewModel.refreshModerationReports(
                status = "", fromMs = bounds.first, toMs = bounds.second,
                search = searchQuery.trim(), limit = 200
            )
            ModerationTab.WARNINGS -> viewModel.refreshModerationWarnings()
            ModerationTab.ACTIVITY -> viewModel.refreshModerationActivity(bounds.first, bounds.second)
            else -> Unit
        }
    }

    // Content classification by ACTUAL duration: <=60s => Reel, >60s => Video
    val videos = remember(reels) { reels.filter { it.durationSecs > 60 } }
    val shortReels = remember(reels) { reels.filter { it.durationSecs <= 60 } }

    // Permission gating — the Moderate center only lists sections staff may use
    val visibleTabs = remember(isSuperAdmin) {
        buildList {
            if (isSuperAdmin || viewModel.canDeletePosts()) add(ModerationTab.POSTS)
            if (isSuperAdmin || viewModel.canDeleteReel()) add(ModerationTab.REELS)
            if (isSuperAdmin || viewModel.canDeleteVideo()) add(ModerationTab.VIDEOS)
            if (isSuperAdmin || viewModel.canViewReports() || viewModel.canReviewReports()) add(ModerationTab.REPORTS)
            if (isSuperAdmin || viewModel.canSuspendUser() || viewModel.canBanUser() || viewModel.canGiveWarning()) add(ModerationTab.USERS)
            if (isSuperAdmin || viewModel.canViewWarningHistory() || viewModel.canGiveWarning()) add(ModerationTab.WARNINGS)
            if (isSuperAdmin || viewModel.canViewActivityLog()) add(ModerationTab.ACTIVITY)
        }
    }
    val effectiveTab = if (selectedTab in visibleTabs) selectedTab else visibleTabs.firstOrNull() ?: ModerationTab.POSTS

    var pendingDeletePost by remember { mutableStateOf<PostEntity?>(null) }
    var pendingDeleteReel by remember { mutableStateOf<ReelEntity?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<ReelEntity?>(null) }
    var warnTarget by remember { mutableStateOf<ModWarnTarget?>(null) }
    var removeWarningTarget by remember { mutableStateOf<ModerationWarning?>(null) }
    var suspendTarget by remember { mutableStateOf<AppUserEntity?>(null) }
    var banTarget by remember { mutableStateOf<AppUserEntity?>(null) }
    var unbanTarget by remember { mutableStateOf<AppUserEntity?>(null) }
    var reviewReport by remember { mutableStateOf<ModerationReport?>(null) }
    var permsTarget by remember { mutableStateOf<AppUserEntity?>(null) }
    var showCustomRange by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleTabs.forEach { tab ->
                FilterChip(
                    selected = effectiveTab == tab,
                    onClick = { selectedTab = tab; page = 0 },
                    label = { Text(tab.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        ModerationFilterBar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it; page = 0 },
            dateOption = dateOption,
            onDateOptionChange = { dateOption = it; page = 0 },
            newestFirst = newestFirst,
            onSortChange = { newestFirst = it; page = 0 },
            onOpenCustomRange = { showCustomRange = true },
            rangeLabel = if (dateOption == DateFilterOption.CUSTOM && (customFrom.isNotBlank() || customTo.isNotBlank()))
                "$customFrom → ${customTo.ifBlank { "now" }}" else null
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (loading && (effectiveTab == ModerationTab.REPORTS || effectiveTab == ModerationTab.ACTIVITY)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                when (effectiveTab) {
                    ModerationTab.POSTS -> {
                        val list = posts
                            .filter { inDateRange(it.timestamp, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.username.contains(searchQuery, true) || q.userHandle.contains(searchQuery, true) || q.caption.contains(searchQuery, true) }
                        ModerationPostsTab(
                            posts = if (newestFirst) list.sortedByDescending { it.timestamp } else list.sortedBy { it.timestamp },
                            page = page, onPage = { page = it },
                            reports = reports, warnings = warnings,
                            canDelete = isSuperAdmin || viewModel.canDeletePosts(),
                            canWarn = viewModel.canGiveWarning(),
                            onDelete = { pendingDeletePost = it },
                            onWarn = { handle -> if (handle.isNotBlank()) warnTarget = ModWarnTarget(allUsers.firstOrNull { it.handle.equals(handle, true) }?.uid ?: "", handle) },
                            onView = { viewModel.openPostInFeed(it.id) }
                        )
                    }
                    ModerationTab.REELS -> {
                        val list = shortReels
                            .filter { inDateRange(it.timestamp, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.author.contains(searchQuery, true) || q.handle.contains(searchQuery, true) || q.caption.contains(searchQuery, true) }
                        ModerationReelsTab(
                            reels = if (newestFirst) list.sortedByDescending { it.timestamp } else list.sortedBy { it.timestamp },
                            page = page, onPage = { page = it },
                            reports = reports, warnings = warnings,
                            canDelete = isSuperAdmin || viewModel.canDeleteReel(),
                            canWarn = viewModel.canGiveWarning(),
                            onDelete = { pendingDeleteReel = it },
                            onWarn = { handle -> if (handle.isNotBlank()) warnTarget = ModWarnTarget(allUsers.firstOrNull { it.handle.equals(handle, true) }?.uid ?: "", handle) },
                            onViewReel = { viewModel.openReelInFeed(it.remoteId) }
                        )
                    }
                    ModerationTab.VIDEOS -> {
                        val list = videos
                            .filter { inDateRange(it.timestamp, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.author.contains(searchQuery, true) || q.handle.contains(searchQuery, true) || q.caption.contains(searchQuery, true) }
                        ModerationVideosTab(
                            videos = if (newestFirst) list.sortedByDescending { it.timestamp } else list.sortedBy { it.timestamp },
                            page = page, onPage = { page = it },
                            reports = reports, warnings = warnings,
                            canDelete = isSuperAdmin || viewModel.canDeleteVideo(),
                            canWarn = viewModel.canGiveWarning(),
                            onDelete = { pendingDeleteVideo = it },
                            onWarn = { handle -> if (handle.isNotBlank()) warnTarget = ModWarnTarget(allUsers.firstOrNull { it.handle.equals(handle, true) }?.uid ?: "", handle) },
                            onViewVideo = { viewModel.openReelInFeed(it.remoteId) }
                        )
                    }
                    ModerationTab.REPORTS -> {
                        val list = reports
                            .filter { inDateRange(it.createdAt, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.targetHandle.contains(searchQuery, true) || q.reporterHandle.contains(searchQuery, true) || q.reason.contains(searchQuery, true) }
                        ModerationReportsTab(
                            reports = if (newestFirst) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt },
                            page = page, onPage = { page = it },
                            canReview = isSuperAdmin || viewModel.canReviewReports(),
                            canGiveWarning = viewModel.canGiveWarning(),
                            canDeletePost = isSuperAdmin || viewModel.canDeletePosts(),
                            canDeleteReel = isSuperAdmin || viewModel.canDeleteReel(),
                            canDeleteVideo = isSuperAdmin || viewModel.canDeleteVideo(),
                            canSuspend = isSuperAdmin || viewModel.canSuspendUser(),
                            canBan = isSuperAdmin || viewModel.canBanUser(),
                            onReview = { reviewReport = it }
                        )
                    }
                    ModerationTab.USERS -> {
                        val list = allUsers
                            .filter { inDateRange(it.registeredAt, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.name.contains(searchQuery, true) || q.handle.contains(searchQuery, true) || q.email.contains(searchQuery, true) }
                        ModerationUsersTab(
                            users = if (newestFirst) list.sortedByDescending { it.registeredAt } else list.sortedBy { it.registeredAt },
                            warnings = warnings, page = page, onPage = { page = it },
                            isSuperAdmin = isSuperAdmin,
                            canWarn = viewModel.canGiveWarning(),
                            canSuspend = isSuperAdmin || viewModel.canSuspendUser(),
                            canBan = isSuperAdmin || viewModel.canBanUser(),
                            onWarn = { warnTarget = ModWarnTarget(it.uid, it.handle) },
                            onSuspendToggle = { if (it.isBanned) unbanTarget = it else suspendTarget = it },
                            onBan = { banTarget = it },
                            onPerms = { permsTarget = it }
                        )
                    }
                    ModerationTab.WARNINGS -> {
                        val list = warnings
                            .filter { inDateRange(it.createdAt, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.userHandle.contains(searchQuery, true) || q.reason.contains(searchQuery, true) || q.warnedBy.contains(searchQuery, true) }
                        ModerationWarningsTab(
                            warnings = if (newestFirst) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt },
                            page = page, onPage = { page = it },
                            canRemove = isSuperAdmin || viewModel.canRemoveWarning(),
                            onRemove = { removeWarningTarget = it }
                        )
                    }
                    ModerationTab.ACTIVITY -> {
                        val list = activity
                            .filter { inDateRange(it.createdAt, bounds) }
                            .filter { q -> searchQuery.isBlank() || q.actorHandle.contains(searchQuery, true) || q.targetHandle.contains(searchQuery, true) || q.action.contains(searchQuery, true) || q.reason.contains(searchQuery, true) }
                        ModerationActivityTab(
                            items = if (newestFirst) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt },
                            page = page, onPage = { page = it }
                        )
                    }
                }
            }
        }
    }
    // --- Custom date range dialog ---
    if (showCustomRange) {
        var fromDraft by remember(showCustomRange) { mutableStateOf(customFrom) }
        var toDraft by remember(showCustomRange) { mutableStateOf(customTo) }
        val invalidRange = remember(fromDraft, toDraft) {
            val p = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val f = runCatching { p.parse(fromDraft.trim()) }.getOrNull()
            val t = runCatching { p.parse(toDraft.trim()) }.getOrNull()
            f != null && t != null && t.before(f)
        }
        AlertDialog(
            onDismissRequest = { showCustomRange = false },
            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            title = { Text("Custom Date Range", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Format: YYYY-MM-DD. Leave a field blank to unbind that side.",
                        fontSize = 12.sp, color = FlareTextSecondary)
                    OutlinedTextField(
                        value = fromDraft, onValueChange = { fromDraft = it },
                        label = { Text("From Date") }, placeholder = { Text("2026-01-01") },
                        singleLine = true, isError = invalidRange, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = toDraft, onValueChange = { toDraft = it },
                        label = { Text("To Date") }, placeholder = { Text("2026-12-31") },
                        singleLine = true, isError = invalidRange, modifier = Modifier.fillMaxWidth()
                    )
                    if (invalidRange) {
                        Text("'To Date' must be on or after 'From Date'.", color = Color(0xFFFF4757), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !invalidRange,
                    onClick = { customFrom = fromDraft; customTo = toDraft; dateOption = DateFilterOption.CUSTOM; page = 0; showCustomRange = false }
                ) { Text("Apply", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCustomRange = false }) { Text("Cancel") } }
        )
    }

    ConfirmModerationDialog(
        visible = pendingDeletePost != null,
        title = "Delete this post?",
        body = pendingDeletePost?.let { "The post by @${it.userHandle.ifBlank { it.username }} will be permanently removed from the server and every device." } ?: "",
        confirmLabel = "Delete Post",
        onDismiss = { pendingDeletePost = null },
        onConfirm = { reason -> pendingDeletePost?.let { viewModel.moderationDeletePost(it, reason) }; pendingDeletePost = null }
    )
    ConfirmModerationDialog(
        visible = pendingDeleteReel != null,
        title = "Delete this reel?",
        body = pendingDeleteReel?.let { "The reel by @${it.handle.ifBlank { it.author }} will be permanently removed from the server and every device." } ?: "",
        confirmLabel = "Delete Reel",
        onDismiss = { pendingDeleteReel = null },
        onConfirm = { reason -> pendingDeleteReel?.let { viewModel.moderationDeleteReel(it, reason) }; pendingDeleteReel = null }
    )
    ConfirmModerationDialog(
        visible = pendingDeleteVideo != null,
        title = "Delete this video?",
        body = pendingDeleteVideo?.let { "The video by @${it.handle.ifBlank { it.author }} will be permanently removed from the server and every device." } ?: "",
        confirmLabel = "Delete Video",
        onDismiss = { pendingDeleteVideo = null },
        onConfirm = { reason -> pendingDeleteVideo?.let { viewModel.moderationDeleteVideo(it, reason) }; pendingDeleteVideo = null }
    )
    ReasonActionDialog(
        visible = warnTarget != null,
        icon = "⚠️",
        title = warnTarget?.let { "Warn @${it.handle}" } ?: "",
        body = "A warning is recorded privately. The user only sees a ⚠️ indicator on their profile — never the reason.",
        label = "Warning reason",
        confirmLabel = "Give Warning",
        onDismiss = { warnTarget = null },
        onConfirm = { reason ->
            warnTarget?.let { viewModel.moderationIssueWarning(it.uid, it.handle, reason.ifBlank { "Community guidelines violation" }) }
            warnTarget = null
        }
    )

    ConfirmModerationDialog(
        visible = removeWarningTarget != null,
        title = "Remove this warning?",
        body = removeWarningTarget?.let { "Warning for @${it.userHandle}: \"${it.reason}\" will be marked as removed." } ?: "",
        confirmLabel = "Remove Warning",
        optionalReason = true,
        onDismiss = { removeWarningTarget = null },
        onConfirm = { reason ->
            removeWarningTarget?.let { viewModel.moderationRemoveWarning(it.id, reason.ifBlank { "Revoked by staff" }) }
            removeWarningTarget = null
        }
    )

    ReasonActionDialog(
        visible = suspendTarget != null,
        icon = "⏸️",
        title = suspendTarget?.let { "Suspend @${it.handle}" } ?: "",
        body = "The user will be temporarily suspended from the platform.",
        label = "Suspension reason",
        confirmLabel = "Suspend User",
        onDismiss = { suspendTarget = null },
        onConfirm = { reason ->
            suspendTarget?.let { viewModel.moderationSuspendUser(it.uid, reason.ifBlank { "Policy violation" }) }
            suspendTarget = null
        }
    )

    ReasonActionDialog(
        visible = banTarget != null,
        icon = "🚫",
        title = banTarget?.let { "Ban @${it.handle}" } ?: "",
        body = "The user will be permanently banned from the platform. This is a serious action.",
        label = "Ban reason",
        confirmLabel = "Ban User",
        onDismiss = { banTarget = null },
        onConfirm = { reason ->
            banTarget?.let { viewModel.moderationBanUser(it.uid, reason.ifBlank { "Severe policy violation" }) }
            banTarget = null
        }
    )

    ConfirmModerationDialog(
        visible = unbanTarget != null,
        title = "Unsuspend @${unbanTarget?.handle ?: ""}?",
        body = "The user will regain full platform access.",
        confirmLabel = "Unsuspend",
        onDismiss = { unbanTarget = null },
        onConfirm = { _ -> unbanTarget?.let { viewModel.moderationUnbanUser(it.uid) }; unbanTarget = null }
    )
    reviewReport?.let { rpt ->
        ReportReviewDialog(
            report = rpt,
            canReview = isSuperAdmin || viewModel.canReviewReports(),
            canGiveWarning = viewModel.canGiveWarning(),
            canDeletePost = isSuperAdmin || viewModel.canDeletePosts(),
            canDeleteReel = isSuperAdmin || viewModel.canDeleteReel(),
            canDeleteVideo = isSuperAdmin || viewModel.canDeleteVideo(),
            canSuspend = isSuperAdmin || viewModel.canSuspendUser(),
            canBan = isSuperAdmin || viewModel.canBanUser(),
            onDismiss = { reviewReport = null },
            onSetStatus = { status, reason ->
                viewModel.moderationReviewReport(rpt.id, status, reason); reviewReport = null
            },
            onWarn = { reason ->
                viewModel.moderationIssueWarning(rpt.targetUid, rpt.targetHandle, reason, rpt.contentType, rpt.contentId, rpt.id)
                reviewReport = null
            },
            onDeleteContent = { reason ->
                when (rpt.contentType.uppercase()) {
                    "POST" -> posts.firstOrNull { it.remoteId == rpt.contentId }?.let { viewModel.moderationDeletePost(it, reason, rpt.id) }
                    "REEL" -> reels.firstOrNull { it.remoteId == rpt.contentId }?.let { viewModel.moderationDeleteReel(it, reason, rpt.id) }
                    "VIDEO" -> reels.firstOrNull { it.remoteId == rpt.contentId }?.let { viewModel.moderationDeleteVideo(it, reason, rpt.id) }
                    else -> viewModel.moderationReviewReport(rpt.id, "ACTION_TAKEN", reason)
                }
                reviewReport = null
            },
            onSuspend = { reason ->
                viewModel.moderationSuspendUser(rpt.targetUid, reason)
                viewModel.moderationReviewReport(rpt.id, "ACTION_TAKEN", "User suspended: $reason")
                reviewReport = null
            },
            onBan = { reason ->
                viewModel.moderationBanUser(rpt.targetUid, reason)
                viewModel.moderationReviewReport(rpt.id, "ACTION_TAKEN", "User banned: $reason")
                reviewReport = null
            },
            onViewContent = {
                when (rpt.contentType.uppercase()) {
                    "POST" -> rpt.contentId.toLongOrNull()?.let { viewModel.openPostInFeed(it) }
                    "REEL", "VIDEO" -> viewModel.openReelInFeed(rpt.contentId)
                    "USER" -> viewModel.viewUserProfile(rpt.targetHandle)
                }
                reviewReport = null
            }
        )
    }

    permsTarget?.let { target ->
        ModerationPermissionDialog(
            user = target,
            isSuperAdmin = isSuperAdmin,
            onDismiss = { permsTarget = null },
            onToggle = { perm, value -> viewModel.setModerationPermission(target.uid, perm, value) }
        )
    }
}
// =============================================================================
// SHARED FILTER BAR / PAGINATION / STATES
// =============================================================================

@Composable
fun ModerationFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    dateOption: DateFilterOption,
    onDateOptionChange: (DateFilterOption) -> Unit,
    newestFirst: Boolean,
    onSortChange: (Boolean) -> Unit,
    onOpenCustomRange: () -> Unit,
    rangeLabel: String?
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search by username, handle or content...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DateFilterOption.entries.forEach { opt ->
                    FilterChip(
                        selected = dateOption == opt,
                        onClick = { if (opt == DateFilterOption.CUSTOM) onOpenCustomRange() else onDateOptionChange(opt) },
                        label = { Text(opt.label, fontSize = 11.sp) }
                    )
                }
            }
            if (rangeLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Range: $rangeLabel", fontSize = 11.sp, color = FlareTextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sort", fontSize = 11.sp, color = FlareTextSecondary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = newestFirst, onClick = { onSortChange(true) }, label = { Text("Newest ↓", fontSize = 11.sp) })
                    FilterChip(selected = !newestFirst, onClick = { onSortChange(false) }, label = { Text("Oldest ↑", fontSize = 11.sp) })
                }
            }
        }
    }
}
@Composable
fun ModerationPaginationControls(totalItems: Int, page: Int, onPage: (Int) -> Unit) {
    val pages = pageCount(totalItems)
    if (pages <= 1) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { if (page > 0) onPage(page - 1) }, enabled = page > 0) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Prev", fontSize = 12.sp)
        }
        Text("Page ${page + 1} / $pages", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
        TextButton(onClick = { if (page < pages - 1) onPage(page + 1) }, enabled = page < pages - 1) {
            Text("Next", fontSize = 12.sp)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ModerationEmptyState(message: String, hint: String = "") {
    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Inbox, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(40.dp))
            Text(message, color = FlareTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (hint.isNotBlank()) Text(hint, color = FlareTextSecondary, fontSize = 12.sp)
        }
    }
}

/** Small ⚠️ badge showing a user's ACTIVE warning count (never exposes reasons). */
@Composable
fun WarningCountBadge(warningCount: Int) {
    if (warningCount <= 0) return
    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFA502).copy(alpha = 0.15f)) {
        Text(
            if (warningCount == 1) "⚠️¹" else "⚠️$warningCount",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFA502),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun ReportCountBadge(reportCount: Int) {
    if (reportCount <= 0) return
    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFF4757).copy(alpha = 0.15f)) {
        Text(
            "🚩 $reportCount",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF4757),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
// =============================================================================
// POSTS / REELS / VIDEOS TABS
// =============================================================================

@Composable
fun ModerationPostsTab(
    posts: List<PostEntity>,
    page: Int,
    onPage: (Int) -> Unit,
    reports: List<ModerationReport>,
    warnings: List<ModerationWarning>,
    canDelete: Boolean,
    canWarn: Boolean,
    onDelete: (PostEntity) -> Unit,
    onWarn: (String) -> Unit,
    onView: (PostEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val paged = paginate(posts, page)
        if (posts.isEmpty()) {
            item { ModerationEmptyState("No posts match.", "Try clearing search or date filters.") }
        }
        items(paged, key = { it.id }) { post ->
            val reportCount = reports.count { it.contentId == post.id.toString() }
            val warningCount = warnings.count { it.userHandle.equals(post.userHandle, true) && it.status == "ACTIVE" }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlareAvatar(avatarType = post.userAvatarType, storagePath = post.userAvatarPath, size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("@${post.userHandle.ifBlank { post.username }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            WarningCountBadge(warningCount)
                            ReportCountBadge(reportCount)
                        }
                        Text(
                            post.caption.ifBlank { post.actionText.ifBlank { "(No content)" } },
                            fontSize = 12.sp, color = FlareTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "❤️ ${post.likesCount} · 💬 ${post.commentsCount} · 🔁 ${post.repostsCount} · ${fmtModDate(post.timestamp)}",
                            fontSize = 11.sp, color = FlareTextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onView(post) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("View in Feed", fontSize = 11.sp)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (canWarn) {
                            TextButton(
                                onClick = { onWarn(post.userHandle.ifBlank { post.username }) },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("⚠️", fontSize = 14.sp) }
                        }
                        if (canDelete) {
                            IconButton(onClick = { onDelete(post) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete post", tint = Color(0xFFFF4757))
                            }
                        }
                    }
                }
            }
        }
        item { ModerationPaginationControls(posts.size, page, onPage) }
    }
}
@Composable
private fun ModerationVideoContentCard(
    title: String,
    handle: String,
    author: String,
    avatarType: String,
    avatarPath: String?,
    previewRes: String,
    durationSecs: Int,
    createdMs: Long,
    likes: Int,
    comments: Int,
    reportCount: Int,
    warningCount: Int,
    isVideo: Boolean,
    canDelete: Boolean,
    canWarn: Boolean,
    onDelete: () -> Unit,
    onWarn: () -> Unit,
    onView: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .clickable { onView() },
                contentAlignment = Alignment.Center
            ) {
                FlareAvatar(avatarType = previewRes.ifBlank { "default" }, storagePath = null, size = 72.dp)
                Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomEnd)) {
                    Text(fmtDuration(durationSecs), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("@${handle.ifBlank { author }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    WarningCountBadge(warningCount)
                    ReportCountBadge(reportCount)
                }
                Text(
                    if (isVideo) "Video · ${fmtDuration(durationSecs)} (>60s)" else "Reel · ${fmtDuration(durationSecs)}",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6C5CE7)
                )
                Text(
                    "❤️ $likes · 💬 $comments · ${fmtModDate(createdMs)}",
                    fontSize = 11.sp, color = FlareTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onView, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.Visibility, contentDescription = "View", modifier = Modifier.size(14.dp))
                        Text("View", fontSize = 11.sp)
                    }
                    if (canWarn) {
                        TextButton(onClick = onWarn, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("⚠️ Warn", fontSize = 11.sp, color = Color(0xFFFFA502))
                        }
                    }
                    if (canDelete) {
                        TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF4757), modifier = Modifier.size(14.dp))
                            Text("Delete", fontSize = 11.sp, color = Color(0xFFFF4757))
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ModerationReelsTab(
    reels: List<ReelEntity>,
    page: Int,
    onPage: (Int) -> Unit,
    reports: List<ModerationReport>,
    warnings: List<ModerationWarning>,
    canDelete: Boolean,
    canWarn: Boolean,
    onDelete: (ReelEntity) -> Unit,
    onWarn: (String) -> Unit,
    onViewReel: (ReelEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (reels.isEmpty()) {
            item { ModerationEmptyState("No reels match.", "Reels are videos of 60 seconds or less.") }
        }
        items(paginate(reels, page), key = { it.id }) { reel ->
            val reportCount = reports.count { it.contentId == reel.id.toString() }
            val warningCount = warnings.count { it.userHandle.equals(reel.handle, true) && it.status == "ACTIVE" }
            ModerationVideoContentCard(
                title = reel.caption, handle = reel.handle, author = reel.author,
                avatarType = reel.avatarType, avatarPath = reel.userAvatarPath,
                previewRes = reel.imageRes.ifBlank { reel.thumbnailPath ?: "" },
                durationSecs = reel.durationSecs, createdMs = reel.timestamp,
                likes = reel.likesCount, comments = reel.commentsCount,
                reportCount = reportCount, warningCount = warningCount,
                isVideo = false,
                canDelete = canDelete, canWarn = canWarn,
                onDelete = { onDelete(reel) },
                onWarn = { onWarn(reel.handle.ifBlank { reel.author }) },
                onView = { onViewReel(reel) }
            )
        }
        item { ModerationPaginationControls(reels.size, page, onPage) }
    }
}

@Composable
fun ModerationVideosTab(
    videos: List<ReelEntity>,
    page: Int,
    onPage: (Int) -> Unit,
    reports: List<ModerationReport>,
    warnings: List<ModerationWarning>,
    canDelete: Boolean,
    canWarn: Boolean,
    onDelete: (ReelEntity) -> Unit,
    onWarn: (String) -> Unit,
    onViewVideo: (ReelEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (videos.isEmpty()) {
            item { ModerationEmptyState("No videos match.", "Videos are videos longer than 60 seconds.") }
        }
        items(paginate(videos, page), key = { it.id }) { video ->
            val reportCount = reports.count { it.contentId == video.id.toString() }
            val warningCount = warnings.count { it.userHandle.equals(video.handle, true) && it.status == "ACTIVE" }
            ModerationVideoContentCard(
                title = video.caption, handle = video.handle, author = video.author,
                avatarType = video.avatarType, avatarPath = video.userAvatarPath,
                previewRes = video.imageRes.ifBlank { video.thumbnailPath ?: "" },
                durationSecs = video.durationSecs, createdMs = video.timestamp,
                likes = video.likesCount, comments = video.commentsCount,
                reportCount = reportCount, warningCount = warningCount,
                isVideo = true,
                canDelete = canDelete, canWarn = canWarn,
                onDelete = { onDelete(video) },
                onWarn = { onWarn(video.handle.ifBlank { video.author }) },
                onView = { onViewVideo(video) }
            )
        }
        item { ModerationPaginationControls(videos.size, page, onPage) }
    }
}

@Composable
fun ModerationPreviewDialog(
    visible: Boolean,
    title: String,
    previewRes: String,
    durationSecs: Int,
    caption: String,
    author: String,
    createdMs: Long,
    extra: String,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Content Preview", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlareAvatar(avatarType = previewRes.ifBlank { "default" }, storagePath = null, size = 180.dp)
                Text(caption.ifBlank { "(No caption)" }, fontSize = 13.sp)
                Text("$author · ${fmtDuration(durationSecs)} · ${fmtModDate(createdMs)}", fontSize = 12.sp, color = FlareTextSecondary)
                if (extra.isNotBlank()) Text(extra, fontSize = 12.sp, color = FlareTextSecondary)
            }
        }
    )
}
// =============================================================================
// REPORTS / USERS / WARNINGS / ACTIVITY TABS
// =============================================================================

@Composable
fun ModerationReportsTab(
    reports: List<ModerationReport>,
    page: Int,
    onPage: (Int) -> Unit,
    canReview: Boolean,
    canGiveWarning: Boolean,
    canDeletePost: Boolean,
    canDeleteReel: Boolean,
    canDeleteVideo: Boolean,
    canSuspend: Boolean,
    canBan: Boolean,
    onReview: (ModerationReport) -> Unit
) {
    var statusFilter by remember { mutableStateOf("ALL") }
    val filtered = remember(reports, statusFilter) {
        if (statusFilter == "ALL") reports else reports.filter { it.status.equals(statusFilter, true) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "PENDING", "REVIEWED", "DISMISSED", "ACTION_TAKEN").forEach { s ->
                FilterChip(
                    selected = statusFilter == s,
                    onClick = { statusFilter = s; onPage(0) },
                    label = { Text(if (s == "ALL") "All" else s.replace('_', ' '), fontSize = 11.sp) }
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item { ModerationEmptyState("No reports match.", "Reports are private — only authorized staff can see them.") }
            }
            items(paginate(filtered, page), key = { it.id }) { rpt ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (rpt.status.uppercase()) {
                            "PENDING" -> Color(0xFFFFA502).copy(alpha = 0.10f)
                            "ACTION_TAKEN" -> Color(0xFF00B894).copy(alpha = 0.10f)
                            "DISMISSED" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(rpt.contentType, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF6C5CE7).copy(alpha = 0.15f)) {
                                Text(rpt.status.replace('_', ' '), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C5CE7), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                            ReportCountBadge(rpt.reportCount)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(fmtModDate(rpt.createdAt), fontSize = 10.sp, color = FlareTextSecondary)
                        }
                        Text("Reported: @${rpt.targetHandle.ifBlank { "unknown" }} · by @${rpt.reporterHandle.ifBlank { "unknown" }}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Reason: ${rpt.reason.ifBlank { "(none provided)" }}", fontSize = 12.sp, color = FlareTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (rpt.contentPreview.isNotBlank()) {
                            Text("Content: ${rpt.contentPreview}", fontSize = 11.sp, color = FlareTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (canReview) {
                            TextButton(onClick = { onReview(rpt) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Icon(Icons.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Review & Act", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { ModerationPaginationControls(filtered.size, page, onPage) }
        }
    }
}
@Composable
fun ModerationUsersTab(
    users: List<AppUserEntity>,
    warnings: List<ModerationWarning>,
    page: Int,
    onPage: (Int) -> Unit,
    isSuperAdmin: Boolean,
    canWarn: Boolean,
    canSuspend: Boolean,
    canBan: Boolean,
    onWarn: (AppUserEntity) -> Unit,
    onSuspendToggle: (AppUserEntity) -> Unit,
    onBan: (AppUserEntity) -> Unit,
    onPerms: (AppUserEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (users.isEmpty()) {
            item { ModerationEmptyState("No users match.", "Try clearing search or date filters.") }
        }
        items(paginate(users, page), key = { it.uid }) { user ->
            val warningCount = warnings.count { it.userHandle.equals(user.handle, true) && it.status == "ACTIVE" }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (user.isBanned) Color(0xFFFF4757).copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlareAvatar(avatarType = user.avatarType, storagePath = user.avatarPath, size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("@${user.handle}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            WarningCountBadge(warningCount)
                            if (user.isBanned) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFF4757).copy(alpha = 0.2f)) {
                                    Text("BANNED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4757), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                        }
                        Text("${user.name} · ${user.role}", fontSize = 11.sp, color = FlareTextSecondary)
                        Text("Joined ${fmtModDate(user.registeredAt)}", fontSize = 10.sp, color = FlareTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (canWarn) {
                                TextButton(onClick = { onWarn(user) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("⚠️ Warn", fontSize = 11.sp, color = Color(0xFFFFA502))
                                }
                            }
                            if (canSuspend || canBan) {
                                TextButton(
                                    onClick = { onSuspendToggle(user) },
                                    enabled = canSuspend || (canBan && user.isBanned),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) { Text(if (user.isBanned) "Unsuspend" else "⏸️ Suspend", fontSize = 11.sp) }
                            }
                            if (canBan && !user.isBanned) {
                                TextButton(onClick = { onBan(user) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("🚫 Ban", fontSize = 11.sp, color = Color(0xFFFF4757))
                                }
                            }
                        }
                    }
                    if (isSuperAdmin) {
                        IconButton(onClick = { onPerms(user) }) {
                            Icon(Icons.Outlined.AdminPanelSettings, contentDescription = "Moderation permissions", tint = Color(0xFF6C5CE7))
                        }
                    }
                }
            }
        }
        item { ModerationPaginationControls(users.size, page, onPage) }
    }
}
@Composable
fun ModerationWarningsTab(
    warnings: List<ModerationWarning>,
    page: Int,
    onPage: (Int) -> Unit,
    canRemove: Boolean,
    onRemove: (ModerationWarning) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (warnings.isEmpty()) {
            item { ModerationEmptyState("No warnings recorded.", "Warning history is private to authorized staff.") }
        }
        items(paginate(warnings, page), key = { it.id }) { w ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (w.status == "ACTIVE") Color(0xFFFFA502).copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("@${w.userHandle}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF6C5CE7).copy(alpha = 0.15f)) {
                            Text(w.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C5CE7), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(fmtModDate(w.createdAt), fontSize = 10.sp, color = FlareTextSecondary)
                    }
                    Text("Reason: ${w.reason}", fontSize = 12.sp, color = FlareTextSecondary)
                    Text("Warned by @${w.warnedBy.ifBlank { "system" }}", fontSize = 11.sp, color = FlareTextSecondary)
                    if (w.contentType.isNotBlank()) {
                        Text("Related: ${w.contentType} ${w.contentId}", fontSize = 11.sp, color = FlareTextSecondary)
                    }
                    if (w.status == "REMOVED" && w.removedBy.isNotBlank()) {
                        Text("Removed by @${w.removedBy} · ${fmtModDate(w.removedAt)}", fontSize = 10.sp, color = FlareTextSecondary)
                    }
                    if (canRemove && w.status == "ACTIVE") {
                        TextButton(onClick = { onRemove(w) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Icon(Icons.Outlined.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Remove Warning", fontSize = 11.sp, color = Color(0xFFFF4757))
                        }
                    }
                }
            }
        }
        item { ModerationPaginationControls(warnings.size, page, onPage) }
    }
}

@Composable
fun ModerationActivityTab(
    items: List<ModerationActivityItem>,
    page: Int,
    onPage: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (items.isEmpty()) {
            item { ModerationEmptyState("No moderation activity yet.", "Deletions, warnings, bans and permission changes appear here.") }
        }
        items(paginate(items, page), key = { it.id }) { a ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            a.action.contains("DELETE") -> "🗑️"
                            a.action.contains("WARNING_ISSUED") -> "⚠️"
                            a.action.contains("WARNING_REMOVED") -> "↩️"
                            a.action.contains("BAN") -> "🚫"
                            a.action.contains("SUSPEND") -> "⏸️"
                            a.action.contains("PERMISSION_GRANTED") -> "✅"
                            a.action.contains("PERMISSION_REVOKED") -> "⛔"
                            a.action.contains("REPORT") -> "🚩"
                            else -> "•"
                        },
                        fontSize = 16.sp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(a.action.replace('_', ' '), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            buildString {
                                append("by @${a.actorHandle.ifBlank { "system" }}")
                                if (a.targetHandle.isNotBlank()) append(" → @${a.targetHandle}")
                                if (a.reason.isNotBlank()) append(" · ${a.reason}")
                            },
                            fontSize = 11.sp, color = FlareTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(fmtModDate(a.createdAt), fontSize = 10.sp, color = FlareTextSecondary)
                    }
                }
            }
        }
        item { ModerationPaginationControls(items.size, page, onPage) }
    }
}
// =============================================================================
// MODERATION DIALOGS
// =============================================================================

@Composable
fun ConfirmModerationDialog(
    visible: Boolean,
    title: String,
    body: String,
    confirmLabel: String,
    optionalReason: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var reason by remember(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4757)) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, fontSize = 13.sp)
                if (optionalReason) {
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it },
                        label = { Text("Reason (optional)") }, minLines = 1, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
            ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ReasonActionDialog(
    visible: Boolean,
    icon: String,
    title: String,
    body: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var reason by remember(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(icon, fontSize = 26.sp) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, fontSize = 13.sp, color = FlareTextSecondary)
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text(label) },
                    placeholder = { Text("e.g. Community guidelines violation") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
            ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
@Composable
fun ReportReviewDialog(
    report: ModerationReport,
    canReview: Boolean,
    canGiveWarning: Boolean,
    canDeletePost: Boolean,
    canDeleteReel: Boolean,
    canDeleteVideo: Boolean,
    canSuspend: Boolean,
    canBan: Boolean,
    onDismiss: () -> Unit,
    onSetStatus: (String, String) -> Unit,
    onWarn: (String) -> Unit,
    onDeleteContent: (String) -> Unit,
    onSuspend: (String) -> Unit,
    onBan: (String) -> Unit,
    onViewContent: () -> Unit = {}
) {
    var note by remember(report.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🚩", fontSize = 24.sp) },
        title = { Text("Review Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Type: ${report.contentType} · Status: ${report.status.replace('_', ' ')}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Reported: @${report.targetHandle.ifBlank { "unknown" }}", fontSize = 12.sp)
                        Text("Reporter: @${report.reporterHandle.ifBlank { "unknown" }}", fontSize = 12.sp)
                        Text("Reason: ${report.reason.ifBlank { "(none)" }}", fontSize = 12.sp, color = FlareTextSecondary)
                        if (report.details.isNotBlank()) Text("Details: ${report.details}", fontSize = 11.sp, color = FlareTextSecondary)
                        Text("Content ID: ${report.contentId.ifBlank { "—" }}", fontSize = 10.sp, color = FlareTextSecondary)
                        Text(fmtModDate(report.createdAt), fontSize = 10.sp, color = FlareTextSecondary)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onViewContent,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View Reported ${report.contentType}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Moderation note / reason") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                if (canReview) {
                    Text("Review status", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onSetStatus("REVIEWED", note.ifBlank { "Reviewed" }) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("✅ Reviewed", fontSize = 11.sp)
                        }
                        TextButton(onClick = { onSetStatus("DISMISSED", note.ifBlank { "Dismissed" }) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("🚫 Dismiss", fontSize = 11.sp, color = FlareTextSecondary)
                        }
                    }
                }
                Text("Take action", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (canGiveWarning) {
                        TextButton(onClick = { onWarn(note.ifBlank { "Reported content violation" }) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("⚠️ Warn @${report.targetHandle.ifBlank { "user" }}", fontSize = 11.sp, color = Color(0xFFFFA502))
                        }
                    }
                    val canDeleteThis = when (report.contentType.uppercase()) {
                        "POST" -> canDeletePost
                        "REEL" -> canDeleteReel
                        "VIDEO" -> canDeleteVideo
                        else -> false
                    }
                    if (canDeleteThis) {
                        TextButton(onClick = { onDeleteContent(note.ifBlank { "Violates community guidelines" }) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("🗑️ Delete reported ${report.contentType.lowercase()}", fontSize = 11.sp, color = Color(0xFFFF4757))
                        }
                    }
                    if (canSuspend) {
                        TextButton(onClick = { onSuspend(note.ifBlank { "Repeated violations" }) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("⏸️ Suspend user", fontSize = 11.sp)
                        }
                    }
                    if (canBan) {
                        TextButton(onClick = { onBan(note.ifBlank { "Severe violations" }) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("🚫 Ban user", fontSize = 11.sp, color = Color(0xFFFF4757))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
/** Super Admin only: grant/revoke individual moderation permissions for a staff member. */
@Composable
fun ModerationPermissionDialog(
    user: AppUserEntity,
    isSuperAdmin: Boolean,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    if (!isSuperAdmin) return
    val perms = listOf(
        "can_view_reports" to "📋 View Reports",
        "can_review_reports" to "✅ Review Reports",
        "can_give_warning" to "⚠️ Give Warnings",
        "can_delete_posts" to "🗑️ Delete Posts",
        "can_delete_reel" to "🎬 Delete Reels",
        "can_delete_video" to "🎞️ Delete Videos",
        "can_suspend_user" to "⏸️ Suspend Users",
        "can_ban_user" to "🚫 Ban Users",
        "can_remove_warning" to "↩️ Remove Warnings",
        "can_view_warning_history" to "🕘 View Warning History",
        "can_view_activity_log" to "📜 View Activity Log"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = Color(0xFF6C5CE7)) },
        title = {
            Column {
                Text("Moderation Permissions", fontWeight = FontWeight.Bold)
                Text("@${user.handle} · ${user.role}", fontSize = 12.sp, color = FlareTextSecondary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Toggle individual moderation powers. The server enforces every change — these are not just UI switches.",
                    fontSize = 11.sp, color = FlareTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                perms.forEach { (key, label) ->
                    var checked by remember(user.uid, key) {
                        mutableStateOf(
                            when (key) {
                                "can_view_reports" -> user.canViewReports
                                "can_review_reports" -> user.canReviewReports
                                "can_give_warning" -> user.canGiveWarning
                                "can_delete_posts" -> user.canDeletePosts
                                "can_delete_reel" -> user.canDeleteReel
                                "can_delete_video" -> user.canDeleteVideo
                                "can_suspend_user" -> user.canSuspendUser
                                "can_ban_user" -> user.canBanUser
                                "can_remove_warning" -> user.canRemoveWarning
                                "can_view_warning_history" -> user.canViewWarningHistory
                                else -> user.canViewActivityLog
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked = !checked; onToggle(key, checked) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = checked, onCheckedChange = { v -> checked = v; onToggle(key, v) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
