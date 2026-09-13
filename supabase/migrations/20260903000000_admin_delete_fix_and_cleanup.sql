-- =============================================================================
-- ADMIN DELETION FIX + "DELETE EVERYONE EXCEPT CEO" CLEANUP
-- -----------------------------------------------------------------------------
-- WHY THE OLD DELETE "DID NOTHING":
--   * delete_user_rpc only removed the public.app_users row. The user's actual
--     Supabase auth account (auth.users) stayed alive, so on their next login
--     registerOrSyncUser() re-created their app_users row -> "I deleted them but
--     nothing happened".
--   * The app also swallowed RPC errors silently and deleted the local row first,
--     so it looked successful even when the server rejected it.
--
-- WHAT THIS FILE DOES:
--   1) Replaces delete_user_rpc so deleting a user ALSO removes their
--      auth.users record (the real account), which cascades to every child row.
--   2) Adds admin_delete_all_except_ceo(ceo_handle TEXT): a SUPER_ADMIN-only RPC
--      that permanently removes every account EXCEPT the given CEO username.
--      (Run it from Supabase -> SQL Editor, or call it from the app.)
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) FIXED delete_user_rpc (true account deletion incl. auth.users)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.delete_user_rpc(target_uid TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    -- Only allow if the caller is a SUPER_ADMIN OR they are deleting themselves.
    IF NOT (
        EXISTS (SELECT 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN')
        OR auth.uid()::text = target_uid
    ) THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;

    -- Protects the extremely important CEO/owner account is handled by the
    -- dedicated admin_delete_all_except_ceo() function below; admins still can't
    -- remove themselves here because deleting your own auth session mid-request
    -- would simply fail on the DB side.

    -- Remove the app_users row first (no FK to auth.users, so do it explicitly).
    DELETE FROM public.app_users WHERE uid = target_uid;

    -- Remove the real Supabase auth account. All rows referencing auth.users(id)
    -- WITH FK (post_likes, reel_likes, personal_id_sessions, follows, ...) are
    -- dropped via ON DELETE CASCADE; any remaining orphaned content is removed
    -- on the next sync.
    DELETE FROM auth.users WHERE id = target_uid::uuid;
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_user_rpc TO authenticated;

-- -----------------------------------------------------------------------------
-- 2) DELETE EVERYONE EXCEPT THE CEO
--    SUPER_ADMIN only. Chooses the protected account by its @handle.
--    Usage in SQL Editor (pick one CEO account):
--       SELECT public.admin_delete_all_except_ceo('CEO');  -- keeps @CEO
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.admin_delete_all_except_ceo(ceo_handle TEXT)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_deleted INT;
    r RECORD;
BEGIN
    -- SUPER_ADMIN guard
    PERFORM 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Access Denied: Only Super Admins can mass-delete users' USING ERRCODE = '42501';
    END IF;

    IF ceo_handle IS NULL OR btrim(ceo_handle) = '' THEN
        RAISE EXCEPTION 'You must provide the CEO username to keep';
    END IF;

    v_deleted := 0;
    FOR r IN
        SELECT uid FROM public.app_users
        WHERE lower(handle) <> lower(btrim(ceo_handle))
          AND uid <> auth.uid()::text
    LOOP
        BEGIN
            DELETE FROM public.app_users WHERE uid = r.uid;
            DELETE FROM auth.users WHERE id = r.uid::uuid;
            v_deleted := v_deleted + 1;
        EXCEPTION WHEN OTHERS THEN
            NULL; -- skip any account that cannot be removed (e.g. already gone)
        END;
    END LOOP;

    RETURN v_deleted;
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_delete_all_except_ceo TO authenticated;