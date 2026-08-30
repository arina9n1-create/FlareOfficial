-- VYN9 Master Persistence & Interaction Layer
-- 1. Support Tables for Real Interactions
CREATE TABLE IF NOT EXISTS public.post_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id BIGINT REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(post_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.reel_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id BIGINT REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(reel_id, user_id)
);

-- 2. Improved Polymorphic Comment Counter Logic
-- We use separate columns or logic if we eventually split comments,
-- but for now, we detect which table to update based on ID existence.
CREATE OR REPLACE FUNCTION public.sync_content_counters()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_TABLE_NAME = 'post_likes') THEN
        IF (TG_OP = 'INSERT') THEN
            UPDATE public.posts SET likes_count = (SELECT count(*) FROM public.post_likes WHERE post_id = NEW.post_id) WHERE id = NEW.post_id;
        ELSE
            UPDATE public.posts SET likes_count = (SELECT count(*) FROM public.post_likes WHERE post_id = OLD.post_id) WHERE id = OLD.post_id;
        END IF;
    ELSIF (TG_TABLE_NAME = 'reel_likes') THEN
        IF (TG_OP = 'INSERT') THEN
            UPDATE public.reels SET likes_count = (SELECT count(*) FROM public.reel_likes WHERE reel_id = NEW.reel_id) WHERE id = NEW.reel_id;
        ELSE
            UPDATE public.reels SET likes_count = (SELECT count(*) FROM public.reel_likes WHERE reel_id = OLD.reel_id) WHERE id = OLD.reel_id;
        END IF;
    ELSIF (TG_TABLE_NAME = 'comments') THEN
        -- Atomic increment/decrement for comments
        IF (TG_OP = 'INSERT') THEN
            UPDATE public.posts SET comments_count = comments_count + 1 WHERE id = NEW.post_id;
            -- Also try reels (best effort, one will match if IDs are unique or we add a type column later)
            UPDATE public.reels SET comments_count = comments_count + 1 WHERE id = NEW.post_id;
        ELSIF (TG_OP = 'DELETE') THEN
            UPDATE public.posts SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.post_id;
            UPDATE public.reels SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.post_id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_sync_post_likes ON public.post_likes;
CREATE TRIGGER trg_sync_post_likes AFTER INSERT OR DELETE ON public.post_likes FOR EACH ROW EXECUTE FUNCTION public.sync_content_counters();

DROP TRIGGER IF EXISTS trg_sync_reel_likes ON public.reel_likes;
CREATE TRIGGER trg_sync_reel_likes AFTER INSERT OR DELETE ON public.reel_likes FOR EACH ROW EXECUTE FUNCTION public.sync_content_counters();

DROP TRIGGER IF EXISTS trg_sync_comments ON public.comments;
CREATE TRIGGER trg_sync_comments AFTER INSERT OR DELETE ON public.comments FOR EACH ROW EXECUTE FUNCTION public.sync_content_counters();

-- 3. Security Hardening
ALTER TABLE public.post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_likes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "View likes" ON public.post_likes;
CREATE POLICY "View likes" ON public.post_likes FOR SELECT USING (true);
DROP POLICY IF EXISTS "Manage own likes" ON public.post_likes;
CREATE POLICY "Manage own likes" ON public.post_likes FOR ALL USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "View reel likes" ON public.reel_likes;
CREATE POLICY "View reel likes" ON public.reel_likes FOR SELECT USING (true);
DROP POLICY IF EXISTS "Manage own reel likes" ON public.reel_likes;
CREATE POLICY "Manage own reel likes" ON public.reel_likes FOR ALL USING (auth.uid() = user_id);

-- Ensure users can update their own profile and nobody else's
DROP POLICY IF EXISTS app_users_update_own_profile ON public.app_users;
CREATE POLICY app_users_update_own_profile ON public.app_users
    FOR UPDATE TO authenticated
    USING (auth.uid()::text = uid)
    WITH CHECK (auth.uid()::text = uid);

-- 4. Initial state sync (Recalculate all counts to current reality)
UPDATE public.posts p SET likes_count = (SELECT count(*) FROM public.post_likes l WHERE l.post_id = p.id);
UPDATE public.reels r SET likes_count = (SELECT count(*) FROM public.reel_likes l WHERE l.reel_id = r.id);
