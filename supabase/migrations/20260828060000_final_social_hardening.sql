-- FLAREOFFICIAL Final Social Hardening Migration
-- 1. Notifications Table Alignment
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS recipient_handle TEXT;
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS actor_handle TEXT;
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS type TEXT; -- 'LIKE', 'COMMENT', 'REPOST', 'SAVE'
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS content_id BIGINT;

-- Index for fast retrieval of own notifications
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON public.notifications (recipient_handle, timestamp DESC);

-- 2. Comments Table Alignment
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
-- If there's no post_id column but some legacy name was used, ensure it's aligned.
-- Existing policies use post_id, so we assume it exists.

-- 3. Notification Logic (Consolidated & Fixed)
CREATE OR REPLACE FUNCTION public.create_interaction_notification()
RETURNS TRIGGER AS $$
DECLARE
    target_owner_handle TEXT;
    actor_name TEXT;
    actor_avatar TEXT;
    actor_handle_val TEXT;
    notif_action TEXT;
    notif_type TEXT;
    target_content_id BIGINT;
BEGIN
    -- Identify actor details
    SELECT name, avatar_type, handle INTO actor_name, actor_avatar, actor_handle_val
    FROM public.app_users
    WHERE uid = (CASE WHEN TG_OP = 'DELETE' THEN OLD.user_id ELSE NEW.user_id END)::text;

    -- Identify target content owner and content ID
    IF (TG_TABLE_NAME = 'post_likes') THEN
        SELECT user_handle, id INTO target_owner_handle, target_content_id FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'liked your post';
        notif_type := 'LIKE';
    ELSIF (TG_TABLE_NAME = 'reel_likes') THEN
        SELECT handle, id INTO target_owner_handle, target_content_id FROM public.reels WHERE id = NEW.reel_id;
        notif_action := 'liked your reel';
        notif_type := 'LIKE';
    ELSIF (TG_TABLE_NAME = 'comments') THEN
        -- Comments table uses post_id for both posts and reels currently
        SELECT user_handle, id INTO target_owner_handle, target_content_id FROM public.posts WHERE id = NEW.post_id;
        IF target_owner_handle IS NULL THEN
            SELECT handle, id INTO target_owner_handle, target_content_id FROM public.reels WHERE id = NEW.post_id;
        END IF;
        notif_action := 'commented: ' || LEFT(NEW.text, 30);
        notif_type := 'COMMENT';
    ELSIF (TG_TABLE_NAME = 'post_reposts') THEN
        SELECT user_handle, id INTO target_owner_handle, target_content_id FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'reposted your post';
        notif_type := 'REPOST';
    ELSIF (TG_TABLE_NAME = 'saved_posts') THEN
        SELECT user_handle, id INTO target_owner_handle, target_content_id FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'saved your post';
        notif_type := 'SAVE';
    END IF;

    -- Insert notification for the owner
    IF (target_owner_handle IS NOT NULL AND LOWER(target_owner_handle) <> LOWER(actor_handle_val)) THEN
        INSERT INTO public.notifications (
            recipient_handle,
            actor_handle,
            username, -- Actor name for display
            avatar_type,
            action_text,
            type,
            content_id,
            is_read,
            timestamp,
            time_ago
        )
        VALUES (
            target_owner_handle,
            actor_handle_val,
            actor_name,
            actor_avatar,
            notif_action,
            notif_type,
            target_content_id,
            FALSE,
            (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
            'Just now'
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Re-apply triggers (Safe cleanup first)
DROP TRIGGER IF EXISTS trg_notif_post_like ON public.post_likes;
CREATE TRIGGER trg_notif_post_like AFTER INSERT ON public.post_likes FOR EACH ROW EXECUTE FUNCTION public.create_interaction_notification();

DROP TRIGGER IF EXISTS trg_notif_reel_like ON public.reel_likes;
CREATE TRIGGER trg_notif_reel_like AFTER INSERT ON public.reel_likes FOR EACH ROW EXECUTE FUNCTION public.create_interaction_notification();

DROP TRIGGER IF EXISTS trg_notif_comment ON public.comments;
CREATE TRIGGER trg_notif_comment AFTER INSERT ON public.comments FOR EACH ROW EXECUTE FUNCTION public.create_interaction_notification();

DROP TRIGGER IF EXISTS trg_notif_repost ON public.post_reposts;
CREATE TRIGGER trg_notif_repost AFTER INSERT ON public.post_reposts FOR EACH ROW EXECUTE FUNCTION public.create_interaction_notification();

DROP TRIGGER IF EXISTS trg_notif_save ON public.saved_posts;
CREATE TRIGGER trg_notif_save AFTER INSERT ON public.saved_posts FOR EACH ROW EXECUTE FUNCTION public.create_interaction_notification();

-- 4. RLS Policy Fix for Notifications
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "View own notifications" ON public.notifications;
CREATE POLICY "View own notifications" ON public.notifications
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users me
            WHERE me.uid = auth.uid()::text AND LOWER(me.handle) = LOWER(notifications.recipient_handle)
        )
    );

-- 5. Comments RLS Fix
DROP POLICY IF EXISTS comments_insert_authenticated ON public.comments;
CREATE POLICY comments_insert_authenticated ON public.comments
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS comments_delete_own ON public.comments;
CREATE POLICY comments_delete_own ON public.comments
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);
