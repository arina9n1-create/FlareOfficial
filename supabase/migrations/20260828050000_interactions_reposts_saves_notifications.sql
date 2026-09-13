-- FLAREOFFICIAL Master Social Features Expansion
-- 1. Support Tables for Reposts & Saved Posts
CREATE TABLE IF NOT EXISTS public.post_reposts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id BIGINT REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(post_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.saved_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id BIGINT REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(post_id, user_id)
);

-- 2. Repost Counter Trigger
CREATE OR REPLACE FUNCTION public.handle_repost_count_change()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.posts SET reposts_count = reposts_count + 1 WHERE id = NEW.post_id;
        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.posts SET reposts_count = GREATEST(0, reposts_count - 1) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_on_repost ON public.post_reposts;
CREATE TRIGGER trg_on_repost AFTER INSERT OR DELETE ON public.post_reposts FOR EACH ROW EXECUTE FUNCTION public.handle_repost_count_change();

-- 3. Automatic Notification Trigger (Server-Side Authority)
CREATE OR REPLACE FUNCTION public.create_interaction_notification()
RETURNS TRIGGER AS $$
DECLARE
    target_owner_handle TEXT;
    actor_handle TEXT;
    notif_action TEXT;
    notif_avatar TEXT;
BEGIN
    -- 1. Identify the actor (the person who liked/commented/etc)
    SELECT handle, avatar_type INTO actor_handle, notif_avatar FROM public.app_users WHERE uid = (CASE WHEN TG_OP = 'DELETE' THEN OLD.user_id ELSE NEW.user_id END)::text;

    -- 2. Identify the target content owner and action text
    IF (TG_TABLE_NAME = 'post_likes') THEN
        SELECT user_handle INTO target_owner_handle FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'liked your post';
    ELSIF (TG_TABLE_NAME = 'reel_likes') THEN
        SELECT handle INTO target_owner_handle FROM public.reels WHERE id = NEW.reel_id;
        notif_action := 'liked your reel';
    ELSIF (TG_TABLE_NAME = 'comments') THEN
        SELECT user_handle INTO target_owner_handle FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'commented on your post: ' || LEFT(NEW.text, 30);
    ELSIF (TG_TABLE_NAME = 'post_reposts') THEN
        SELECT user_handle INTO target_owner_handle FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'reposted your content';
    ELSIF (TG_TABLE_NAME = 'saved_posts') THEN
        SELECT user_handle INTO target_owner_handle FROM public.posts WHERE id = NEW.post_id;
        notif_action := 'saved your post';
    END IF;

    -- 3. Insert notification if the owner is not the actor
    IF (target_owner_handle IS NOT NULL AND target_owner_handle <> actor_handle) THEN
        INSERT INTO public.notifications (username, avatar_type, action_text, is_read, timestamp, time_ago)
        VALUES (actor_handle, notif_avatar, notif_action, FALSE, (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT, 'Just now');
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Apply Notification Triggers
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

-- 5. RLS Policies
ALTER TABLE public.post_reposts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_posts ENABLE ROW LEVEL SECURITY;

CREATE POLICY "View reposts" ON public.post_reposts FOR SELECT USING (true);
CREATE POLICY "Manage own reposts" ON public.post_reposts FOR ALL USING (auth.uid() = user_id);

CREATE POLICY "View own saved" ON public.saved_posts FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Manage own saved" ON public.saved_posts FOR ALL USING (auth.uid() = user_id);

-- Ensure users can only read their own notifications
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "View own notifications" ON public.notifications;
CREATE POLICY "View own notifications" ON public.notifications FOR SELECT USING (auth.uid()::text = username OR username IS NULL);

-- 6. Initial count sync
UPDATE public.posts p SET reposts_count = (SELECT count(*) FROM public.post_reposts r WHERE r.post_id = p.id);
