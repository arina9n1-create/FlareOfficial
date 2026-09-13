-- =============================================================================
-- MASTER INTERACTION SYSTEM FIX
-- Consolidates RPCs for Likes, Saves, and Reposts for both Posts and Reels.
-- Also ensures the comments table has all required columns.
-- =============================================================================

-- 1. Ensure columns exist on comments table
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS post_id BIGINT;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS username TEXT;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_type TEXT DEFAULT 'default';
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS text TEXT;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS timestamp BIGINT;

-- 2. Create RPCs for Post Interactions
CREATE OR REPLACE FUNCTION public.toggle_post_like(p_post_id BIGINT, p_user_id UUID, p_should_like BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_like THEN
        INSERT INTO public.post_likes (post_id, user_id) VALUES (p_post_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.post_likes WHERE post_id = p_post_id AND user_id = p_user_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_post_save(p_post_id BIGINT, p_user_id UUID, p_should_save BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_save THEN
        INSERT INTO public.saved_posts (post_id, user_id) VALUES (p_post_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.saved_posts WHERE post_id = p_post_id AND user_id = p_user_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_post_repost(p_post_id BIGINT, p_user_id UUID, p_should_repost BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_repost THEN
        INSERT INTO public.post_reposts (post_id, user_id) VALUES (p_post_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.post_reposts WHERE post_id = p_post_id AND user_id = p_user_id;
    END IF;
END;
$$;

-- 3. Create RPCs for Reel Interactions
CREATE OR REPLACE FUNCTION public.toggle_reel_like(p_reel_id BIGINT, p_user_id UUID, p_should_like BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_like THEN
        INSERT INTO public.reel_likes (reel_id, user_id) VALUES (p_reel_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.reel_likes WHERE reel_id = p_reel_id AND user_id = p_user_id;
    END IF;
END;
$$;

-- Note: If you have reel_reposts or saved_reels tables, add them here.
-- For now, let's assume they might be shared or need creation.
CREATE TABLE IF NOT EXISTS public.reel_reposts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id BIGINT REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(reel_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.saved_reels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id BIGINT REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(reel_id, user_id)
);

CREATE OR REPLACE FUNCTION public.toggle_reel_repost(p_reel_id BIGINT, p_user_id UUID, p_should_repost BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_repost THEN
        INSERT INTO public.reel_reposts (reel_id, user_id) VALUES (p_reel_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.reel_reposts WHERE reel_id = p_reel_id AND user_id = p_user_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_reel_save(p_reel_id BIGINT, p_user_id UUID, p_should_save BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_save THEN
        INSERT INTO public.saved_reels (reel_id, user_id) VALUES (p_reel_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.saved_reels WHERE reel_id = p_reel_id AND user_id = p_user_id;
    END IF;
END;
$$;

-- 4. Grants
GRANT EXECUTE ON FUNCTION public.toggle_post_like(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_post_save(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_post_repost(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_reel_like(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_reel_save(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_reel_repost(BIGINT, UUID, BOOLEAN) TO authenticated;

-- Refresh PostgREST cache
NOTIFY pgrst, 'reload schema';
