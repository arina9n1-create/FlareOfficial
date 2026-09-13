-- Interaction RPCs to handle Like, Save, and Repost operations
-- These RPCs ensure atomicity and can bypass RLS constraints if necessary (SECURITY DEFINER).

CREATE OR REPLACE FUNCTION public.toggle_post_like(p_post_id BIGINT, p_user_id UUID, p_should_like BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_should_like THEN
        INSERT INTO public.post_likes (post_id, user_id)
        VALUES (p_post_id, p_user_id)
        ON CONFLICT (post_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.post_likes
        WHERE post_id = p_post_id AND user_id = p_user_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_post_save(p_post_id BIGINT, p_user_id UUID, p_should_save BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_should_save THEN
        INSERT INTO public.saved_posts (post_id, user_id)
        VALUES (p_post_id, p_user_id)
        ON CONFLICT (post_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.saved_posts
        WHERE post_id = p_post_id AND user_id = p_user_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_post_repost(p_post_id BIGINT, p_user_id UUID, p_should_repost BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_should_repost THEN
        INSERT INTO public.post_reposts (post_id, user_id)
        VALUES (p_post_id, p_user_id)
        ON CONFLICT (post_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.post_reposts
        WHERE post_id = p_post_id AND user_id = p_user_id;
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.toggle_post_like(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_post_save(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_post_repost(BIGINT, UUID, BOOLEAN) TO authenticated;
