-- =============================================================================
-- FLAREOFFICIAL FULL MODERATION SYSTEM
-- -----------------------------------------------------------------------------
-- Reports, Warnings, Activity Log, granular moderation permissions, content
-- duration, and server-side (RLS + SECURITY DEFINER RPC) enforcement.
-- Safe/idempotent: does NOT drop or reset existing user/content data.
-- =============================================================================

-- 1. Granular moderation permission columns on app_users ------------------------
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_view_reports         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_review_reports       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_give_warning         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_delete_reel          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_delete_video         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_suspend_user         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_ban_user             BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_remove_warning       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_view_warning_history BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS can_view_activity_log    BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Content duration (seconds) for reel/video classification ------------------
-- Rules: <= 60 seconds => Reel; > 60 seconds => Video.
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS duration_secs INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS duration_secs INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_reels_duration ON public.reels(duration_secs);
CREATE INDEX IF NOT EXISTS idx_posts_created ON public.posts(timestamp);
CREATE INDEX IF NOT EXISTS idx_reels_created ON public.reels(timestamp);

-- 3. Reports table --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.moderation_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type    TEXT NOT NULL,               -- 'POST','REEL','VIDEO','USER'
    content_id      TEXT NOT NULL DEFAULT '',
    content_preview TEXT NOT NULL DEFAULT '',
    target_handle   TEXT NOT NULL DEFAULT '',    -- owner of reported content
    target_uid      TEXT NOT NULL DEFAULT '',
    reporter_handle TEXT NOT NULL DEFAULT '',
    reason          TEXT NOT NULL DEFAULT '',
    details         TEXT NOT NULL DEFAULT '',
    status          TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, REVIEWED, DISMISSED, ACTION_TAKEN
    report_count    INT  NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by     TEXT NOT NULL DEFAULT '',
    resolved_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mod_reports_status  ON public.moderation_reports(status);
CREATE INDEX IF NOT EXISTS idx_mod_reports_created ON public.moderation_reports(created_at);
CREATE INDEX IF NOT EXISTS idx_mod_reports_target  ON public.moderation_reports(target_handle);

-- 4. Warnings table -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.moderation_warnings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      TEXT NOT NULL DEFAULT '',
    user_handle  TEXT NOT NULL,
    reason       TEXT NOT NULL,
    warned_by    TEXT NOT NULL,
    content_type TEXT NOT NULL DEFAULT '',
    content_id   TEXT NOT NULL DEFAULT '',
    report_id    TEXT NOT NULL DEFAULT '',
    status       TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REMOVED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_by   TEXT NOT NULL DEFAULT '',
    removed_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mod_warnings_user    ON public.moderation_warnings(user_id);
CREATE INDEX IF NOT EXISTS idx_mod_warnings_created ON public.moderation_warnings(created_at);

-- 5. Moderation activity log ----------------------------------------------------
CREATE TABLE IF NOT EXISTS public.moderation_activity (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_handle TEXT NOT NULL,
    action       TEXT NOT NULL,
    target_handle TEXT NOT NULL DEFAULT '',
    target_type  TEXT NOT NULL DEFAULT '',
    content_id   TEXT NOT NULL DEFAULT '',
    report_id    TEXT NOT NULL DEFAULT '',
    reason       TEXT NOT NULL DEFAULT '',
-- 6. Row Level Security + grants ------------------------------------------------
ALTER TABLE public.moderation_reports  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_warnings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_activity ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT ON public.moderation_reports TO authenticated;
GRANT SELECT               ON public.moderation_warnings TO authenticated;
GRANT SELECT               ON public.moderation_activity TO authenticated;

-- Any authenticated user may submit a report (safe, non-private) ...
DROP POLICY IF EXISTS "Authenticated can report" ON public.moderation_reports;
CREATE POLICY "Authenticated can report" ON public.moderation_reports
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

-- ... but only authorized staff may VIEW private reports.
DROP POLICY IF EXISTS "Staff can view reports" ON public.moderation_reports;
CREATE POLICY "Staff can view reports" ON public.moderation_reports
    FOR SELECT TO authenticated USING (
        EXISTS (SELECT 1 FROM public.app_users u
                WHERE u.uid = auth.uid()::text
                  AND (u.role = 'SUPER_ADMIN' OR u.can_view_reports OR u.can_review_reports))
    );

DROP POLICY IF EXISTS "Staff can view warnings" ON public.moderation_warnings;
CREATE POLICY "Staff can view warnings" ON public.moderation_warnings
    FOR SELECT TO authenticated USING (
        EXISTS (SELECT 1 FROM public.app_users u
                WHERE u.uid = auth.uid()::text
                  AND (u.role = 'SUPER_ADMIN' OR u.can_view_warning_history OR u.can_give_warning))
    );

DROP POLICY IF EXISTS "Staff can view activity" ON public.moderation_activity;
CREATE POLICY "Staff can view activity" ON public.moderation_activity
    FOR SELECT TO authenticated USING (
        EXISTS (SELECT 1 FROM public.app_users u
                WHERE u.uid = auth.uid()::text
                  AND (u.role = 'SUPER_ADMIN' OR u.can_view_activity_log))
    );

-- 7. HARD DEFENSE: clients must NEVER self-grant moderation permissions --------
-- The app_users UPDATE policy allows a user to edit their own row; revoke UPDATE
-- on every moderation column so a direct REST PATCH can't escalate privileges.
REVOKE UPDATE (can_view_reports, can_review_reports, can_give_warning,
               can_delete_reel, can_delete_video, can_suspend_user, can_ban_user,
               can_remove_warning, can_view_warning_history, can_view_activity_log)
    ON public.app_users FROM anon, authenticated;

-- 8. Server-side permission helpers (SECURITY DEFINER) --------------------------
CREATE OR REPLACE FUNCTION public.mod_has_perm(p_perm TEXT)
RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE u public.app_users%ROWTYPE;
BEGIN
    SELECT * INTO u FROM public.app_users WHERE uid = auth.uid()::text;
    IF u.uid IS NULL THEN RETURN FALSE; END IF;
    IF u.role = 'SUPER_ADMIN' THEN RETURN TRUE; END IF;
    RETURN (
        (p_perm = 'can_view_reports'         AND u.can_view_reports)         OR
        (p_perm = 'can_review_reports'       AND u.can_review_reports)       OR
        (p_perm = 'can_give_warning'         AND u.can_give_warning)         OR
        (p_perm = 'can_delete_posts'         AND u.can_delete_posts)         OR
        (p_perm = 'can_delete_reel'          AND u.can_delete_reel)          OR
        (p_perm = 'can_delete_video'         AND u.can_delete_video)         OR
        (p_perm = 'can_suspend_user'         AND u.can_suspend_user)         OR
        (p_perm = 'can_ban_user'             AND u.can_ban_user)             OR
        (p_perm = 'can_remove_warning'       AND u.can_remove_warning)       OR
        (p_perm = 'can_view_warning_history' AND u.can_view_warning_history) OR
        (p_perm = 'can_view_activity_log'    AND u.can_view_activity_log)
    );
END;
$$;
-- 9. Moderation action RPCs (each checks permissions server-side) ---------------

-- Issue a warning (can_give_warning) -------------------------------------------
CREATE OR REPLACE FUNCTION public.mod_issue_warning(
    p_user_id TEXT, p_user_handle TEXT, p_reason TEXT,
    p_content_type TEXT DEFAULT '', p_content_id TEXT DEFAULT '', p_report_id TEXT DEFAULT ''
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT;
BEGIN
    IF NOT mod_has_perm('can_give_warning') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    INSERT INTO public.moderation_warnings(user_id, user_handle, reason, warned_by,
                                           content_type, content_id, report_id)
    VALUES (p_user_id, p_user_handle, p_reason, actor_h,
            p_content_type, p_content_id, p_report_id);
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, content_id, report_id, reason)
    VALUES (actor_h, 'WARNING_ISSUED', p_user_handle, 'USER', p_content_id, p_report_id, p_reason);
    IF p_report_id <> '' THEN
        UPDATE public.moderation_reports SET status = 'ACTION_TAKEN', reviewed_by = actor_h, resolved_at = now()
        WHERE id::text = p_report_id;
    END IF;
END;
$$;

-- Remove a warning (can_remove_warning) ----------------------------------------
CREATE OR REPLACE FUNCTION public.mod_remove_warning(
    p_warning_id TEXT, p_reason TEXT DEFAULT ''
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; w public.moderation_warnings%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_remove_warning') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    SELECT * INTO w FROM public.moderation_warnings WHERE id::text = p_warning_id;
    IF w.id IS NULL THEN RETURN; END IF;
    UPDATE public.moderation_warnings SET status = 'REMOVED', removed_by = actor_h, removed_at = now()
    WHERE id::text = p_warning_id;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, content_id, reason)
    VALUES (actor_h, 'WARNING_REMOVED', w.user_handle, 'USER', p_warning_id, p_reason);
END;
$$;

-- Delete a post (can_delete_posts) ---------------------------------------------
CREATE OR REPLACE FUNCTION public.mod_delete_post(
    p_post_id BIGINT, p_reason TEXT DEFAULT '', p_report_id TEXT DEFAULT ''
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; post public.posts%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_delete_posts') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    SELECT * INTO post FROM public.posts WHERE id = p_post_id;
    IF post.id IS NULL THEN RETURN; END IF;
    IF post.is_reel_post THEN
        DELETE FROM public.reels WHERE id::text = post.remote_id::text;
    END IF;
    DELETE FROM public.posts WHERE id = p_post_id;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, content_id, report_id, reason)
    VALUES (actor_h, 'POST_DELETED', COALESCE(post.user_handle, ''), 'POST', p_post_id::text, p_report_id, p_reason);
    IF p_report_id <> '' THEN
        UPDATE public.moderation_reports SET status = 'ACTION_TAKEN', reviewed_by = actor_h, resolved_at = now()
-- Delete a reel (can_delete_reel) ----------------------------------------------
CREATE OR REPLACE FUNCTION public.mod_delete_reel(
    p_reel_id BIGINT, p_reason TEXT DEFAULT '', p_report_id TEXT DEFAULT ''
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; r public.reels%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_delete_reel') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    SELECT * INTO r FROM public.reels WHERE id = p_reel_id;
    IF r.id IS NULL THEN RETURN; END IF;
    DELETE FROM public.posts WHERE remote_id::text = r.id::text;
    DELETE FROM public.reels WHERE id = p_reel_id;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, content_id, report_id, reason)
    VALUES (actor_h, 'REEL_DELETED', COALESCE(r.handle, ''), 'REEL', p_reel_id::text, p_report_id, p_reason);
    IF p_report_id <> '' THEN
        UPDATE public.moderation_reports SET status = 'ACTION_TAKEN', reviewed_by = actor_h, resolved_at = now()
        WHERE id::text = p_report_id;
    END IF;
END;
$$;

-- Delete a long video >60s (can_delete_video) ----------------------------------
CREATE OR REPLACE FUNCTION public.mod_delete_video(
    p_reel_id BIGINT, p_reason TEXT DEFAULT '', p_report_id TEXT DEFAULT ''
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; r public.reels%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_delete_video') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    SELECT * INTO r FROM public.reels WHERE id = p_reel_id;
    IF r.id IS NULL THEN RETURN; END IF;
    DELETE FROM public.posts WHERE remote_id::text = r.id::text;
    DELETE FROM public.reels WHERE id = p_reel_id;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, content_id, report_id, reason)
    VALUES (actor_h, 'VIDEO_DELETED', COALESCE(r.handle, ''), 'VIDEO', p_reel_id::text, p_report_id, p_reason);
    IF p_report_id <> '' THEN
        UPDATE public.moderation_reports SET status = 'ACTION_TAKEN', reviewed_by = actor_h, resolved_at = now()
        WHERE id::text = p_report_id;
    END IF;
END;
$$;

-- Review / dismiss a report (can_review_reports) --------------------------------
CREATE OR REPLACE FUNCTION public.mod_review_report(
    p_report_id TEXT, p_status TEXT, p_reason TEXT DEFAULT ''
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; rp public.moderation_reports%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_review_reports') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    SELECT * INTO rp FROM public.moderation_reports WHERE id::text = p_report_id;
    IF rp.id IS NULL THEN RETURN; END IF;
    UPDATE public.moderation_reports SET status = p_status, reviewed_by = actor_h, resolved_at = now()
    WHERE id::text = p_report_id;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, content_id, report_id, reason)
    VALUES (actor_h, 'REPORT_' || upper(p_status), rp.target_handle, rp.content_type, rp.content_id, p_report_id, p_reason);
END;
-- Suspend user (can_suspend_user) ----------------------------------------------
CREATE OR REPLACE FUNCTION public.mod_suspend_user(
    p_target_uid TEXT, p_reason TEXT
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; u public.app_users%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_suspend_user') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    UPDATE public.app_users SET is_banned = TRUE, ban_reason = p_reason
    WHERE uid = p_target_uid RETURNING * INTO u;
    IF u.uid IS NULL THEN RETURN; END IF;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, reason)
    VALUES (actor_h, 'USER_SUSPENDED', u.handle, 'USER', p_reason);
END;
$$;

-- Ban user (can_ban_user) -------------------------------------------------------
CREATE OR REPLACE FUNCTION public.mod_ban_user(
    p_target_uid TEXT, p_reason TEXT
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; u public.app_users%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_ban_user') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    UPDATE public.app_users SET is_banned = TRUE, ban_reason = 'BAN: ' || p_reason
    WHERE uid = p_target_uid RETURNING * INTO u;
    IF u.uid IS NULL THEN RETURN; END IF;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, reason)
    VALUES (actor_h, 'USER_BANNED', u.handle, 'USER', p_reason);
END;
$$;

-- Unban / unsuspend user (can_ban_user) ----------------------------------------
CREATE OR REPLACE FUNCTION public.mod_unban_user(
    p_target_uid TEXT
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE actor_h TEXT; u public.app_users%ROWTYPE;
BEGIN
    IF NOT mod_has_perm('can_ban_user') AND NOT mod_has_perm('can_suspend_user') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    actor_h := mod_actor_handle();
    UPDATE public.app_users SET is_banned = FALSE, ban_reason = ''
    WHERE uid = p_target_uid RETURNING * INTO u;
    IF u.uid IS NULL THEN RETURN; END IF;
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type)
    VALUES (actor_h, 'USER_UNBANNED', u.handle, 'USER');
END;
$$;

-- Super Admin: grant/revoke an individual moderation permission -----------------
CREATE OR REPLACE FUNCTION public.set_mod_permission(
    p_target_uid TEXT, p_perm TEXT, p_value BOOLEAN
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE col TEXT; target public.app_users%ROWTYPE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    IF auth.uid()::text = p_target_uid THEN
        RAISE EXCEPTION 'Cannot change your own permissions' USING ERRCODE = '42501';
    END IF;
    SELECT * INTO target FROM public.app_users WHERE uid = p_target_uid;
    IF target.uid IS NULL THEN RETURN; END IF;
    IF lower(target.handle) = 'ceo' THEN
        RAISE EXCEPTION 'CEO account cannot be modified' USING ERRCODE = '42501';
    END IF;
    col := CASE p_perm
        WHEN 'can_view_reports'         THEN 'can_view_reports'
        WHEN 'can_review_reports'       THEN 'can_review_reports'
        WHEN 'can_give_warning'         THEN 'can_give_warning'
        WHEN 'can_delete_posts'         THEN 'can_delete_posts'
        WHEN 'can_delete_reel'          THEN 'can_delete_reel'
        WHEN 'can_delete_video'         THEN 'can_delete_video'
        WHEN 'can_suspend_user'         THEN 'can_suspend_user'
        WHEN 'can_ban_user'             THEN 'can_ban_user'
        WHEN 'can_remove_warning'       THEN 'can_remove_warning'
        WHEN 'can_view_warning_history' THEN 'can_view_warning_history'
        WHEN 'can_view_activity_log'    THEN 'can_view_activity_log'
        ELSE ''
    END;
    IF col = '' THEN RAISE EXCEPTION 'Unknown permission' USING ERRCODE = '42883'; END IF;
    EXECUTE format('UPDATE public.app_users SET %I = %L WHERE uid = %L', col, p_value, p_target_uid);
    INSERT INTO public.moderation_activity(actor_handle, action, target_handle, target_type, reason)
    VALUES ((SELECT handle FROM public.app_users WHERE uid = auth.uid()::text),
            CASE WHEN p_value THEN 'PERMISSION_GRANTED' ELSE 'PERMISSION_REVOKED' END,
            target.handle, 'PERMISSION', col);
GRANT EXECUTE ON FUNCTION public.mod_issue_warning   TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_remove_warning  TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_delete_post     TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_delete_reel     TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_delete_video    TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_review_report   TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_suspend_user    TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_ban_user        TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_unban_user      TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_mod_permission  TO authenticated;

-- 10. Carry the new moderation flags through promote_user ----------------------
CREATE OR REPLACE FUNCTION public.promote_user(
    target_uid TEXT,
    new_role TEXT,
    new_can_manage_users BOOLEAN,
    new_can_delete_posts BOOLEAN,
    new_can_edit_posts BOOLEAN,
    new_can_moderate_comments BOOLEAN,
    new_can_manage_chats BOOLEAN,
    new_can_manage_monetization BOOLEAN,
    new_can_manage_rewards BOOLEAN,
    new_can_clean_storage BOOLEAN,
    new_can_view_reports BOOLEAN DEFAULT FALSE,
    new_can_review_reports BOOLEAN DEFAULT FALSE,
    new_can_give_warning BOOLEAN DEFAULT FALSE,
    new_can_delete_reel BOOLEAN DEFAULT FALSE,
    new_can_delete_video BOOLEAN DEFAULT FALSE,
    new_can_suspend_user BOOLEAN DEFAULT FALSE,
    new_can_ban_user BOOLEAN DEFAULT FALSE,
    new_can_remove_warning BOOLEAN DEFAULT FALSE,
    new_can_view_warning_history BOOLEAN DEFAULT FALSE,
    new_can_view_activity_log BOOLEAN DEFAULT FALSE
)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied: Only Super Admins can change roles' USING ERRCODE = '42501';
    END IF;
    IF EXISTS (SELECT 1 FROM public.app_users WHERE uid = target_uid AND lower(handle) = 'ceo') THEN
        RAISE EXCEPTION 'Access Denied: The CEO account cannot be modified' USING ERRCODE = '42501';
    END IF;
    IF auth.uid()::text = target_uid AND new_role <> 'SUPER_ADMIN' THEN
        RAISE EXCEPTION 'Access Denied: You cannot demote yourself' USING ERRCODE = '42501';
    END IF;
    EXECUTE format(
        'UPDATE public.app_users SET role = %L,
            can_manage_users = %L, can_delete_posts = %L, can_edit_posts = %L,
            can_moderate_comments = %L, can_manage_chats = %L, can_manage_monetization = %L,
            can_manage_rewards = %L, can_clean_storage = %L,
            can_view_reports = %L, can_review_reports = %L, can_give_warning = %L,
            can_delete_reel = %L, can_delete_video = %L, can_suspend_user = %L,
            can_ban_user = %L, can_remove_warning = %L, can_view_warning_history = %L,
            can_view_activity_log = %L
        WHERE uid = %L',
        new_role, new_can_manage_users, new_can_delete_posts, new_can_edit_posts,
        new_can_moderate_comments, new_can_manage_chats, new_can_manage_monetization,
        new_can_manage_rewards, new_can_clean_storage,
        new_can_view_reports, new_can_review_reports, new_can_give_warning,
        new_can_delete_reel, new_can_delete_video, new_can_suspend_user,
        new_can_ban_user, new_can_remove_warning, new_can_view_warning_history,
        new_can_view_activity_log, target_uid
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.promote_user TO authenticated;

-- 11. Ensure every SUPER_ADMIN (and @ceo) has all moderation powers -------------
UPDATE public.app_users
SET can_view_reports = TRUE, can_review_reports = TRUE, can_give_warning = TRUE,
    can_delete_reel = TRUE, can_delete_video = TRUE, can_suspend_user = TRUE,
    can_ban_user = TRUE, can_remove_warning = TRUE, can_view_warning_history = TRUE,
    can_view_activity_log = TRUE
WHERE role = 'SUPER_ADMIN';
UPDATE public.app_users
SET can_view_reports = TRUE, can_review_reports = TRUE, can_give_warning = TRUE,
    can_delete_reel = TRUE, can_delete_video = TRUE, can_suspend_user = TRUE,
    can_ban_user = TRUE, can_remove_warning = TRUE, can_view_warning_history = TRUE,
    can_view_activity_log = TRUE
WHERE lower(handle) = 'ceo';

-- 12. Hardening: block clients from changing the new moderation columns too -----
DROP TRIGGER IF EXISTS trg_app_users_block_mod_perm ON public.app_users;
CREATE TRIGGER trg_app_users_block_mod_perm
    BEFORE UPDATE OF can_view_reports, can_review_reports, can_give_warning,
                     can_delete_reel, can_delete_video, can_suspend_user, can_ban_user,
                     can_remove_warning, can_view_warning_history, can_view_activity_log
    ON public.app_users FOR EACH ROW EXECUTE FUNCTION public.block_client_role_change();

NOTIFY pgrst, 'reload schema';
END;
$$;

$$;

        WHERE id::text = p_report_id;
    END IF;
END;
$$;


CREATE OR REPLACE FUNCTION public.mod_actor_handle()
RETURNS TEXT LANGUAGE sql SECURITY DEFINER AS $$
    SELECT handle FROM public.app_users WHERE uid = auth.uid()::text;
$$;

GRANT EXECUTE ON FUNCTION public.mod_has_perm TO authenticated;
GRANT EXECUTE ON FUNCTION public.mod_actor_handle TO authenticated;

    meta         TEXT NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mod_activity_created ON public.moderation_activity(created_at);
CREATE INDEX IF NOT EXISTS idx_mod_activity_actor   ON public.moderation_activity(actor_handle);
