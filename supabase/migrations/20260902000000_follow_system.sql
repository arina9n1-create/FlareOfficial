-- FLAREOFFICIAL Follow System: notify the followed user + follow notifications
-- 1. SECURITY DEFINER RPC so the follower can insert a notification row
--    addressed to the followed user (RLS would otherwise block cross-user inserts).
CREATE OR REPLACE FUNCTION public.vn_follow_notify(p_target_handle TEXT, p_action TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_actor_uid     TEXT;
    v_actor_handle  TEXT;
    v_actor_avatar  TEXT;
    v_target_handle TEXT;
BEGIN
    -- Resolve the actor (must be the authenticated caller)
    SELECT uid, btrim(handle), COALESCE(NULLIF(btrim(avatar_type), ''), 'default')
      INTO v_actor_uid, v_actor_handle, v_actor_avatar
      FROM public.app_users
     WHERE uid = auth.uid()::text
     LIMIT 1;

    IF v_actor_uid IS NULL OR v_actor_handle IS NULL OR v_actor_handle = '' THEN
        RETURN;
    END IF;

    v_target_handle := COALESCE(btrim(p_target_handle), '');
    IF v_target_handle = '' THEN
        RETURN;
    END IF;

    -- Never notify yourself
    IF LOWER(v_target_handle) = LOWER(v_actor_handle) THEN
        RETURN;
    END IF;

    -- Only notify real users; store their canonical handle casing
    SELECT btrim(t.handle)
      INTO v_target_handle
      FROM public.app_users t
     WHERE LOWER(t.handle) = LOWER(v_target_handle)
     LIMIT 1;

    IF v_target_handle IS NULL OR v_target_handle = '' THEN
        RETURN;
    END IF;

    INSERT INTO public.notifications (
        recipient_handle, actor_handle, username, avatar_type,
        action_text, is_read, timestamp, time_ago, type
    )
    VALUES (
        v_target_handle,
        v_actor_handle,
        v_actor_handle,
        v_actor_avatar,
                COALESCE(NULLIF(btrim(p_action), ''), 'started following you 🤝'),
        FALSE,
        (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
        'Just now',
        'FOLLOW'
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.vn_follow_notify(TEXT, TEXT) TO authenticated;

-- 2. Convenience index for follow-state reads
CREATE INDEX IF NOT EXISTS idx_follows_state ON public.follows (follower_uid, following_uid, is_following);