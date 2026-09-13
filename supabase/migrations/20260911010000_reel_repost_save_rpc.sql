-- Interaction RPCs required by the reels screen.
CREATE TABLE IF NOT EXISTS public.reel_reposts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id BIGINT REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reel_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.saved_reels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id BIGINT REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reel_id, user_id)
);

CREATE OR REPLACE FUNCTION public.toggle_reel_repost(p_reel_id BIGINT, p_user_id UUID, p_should_repost BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_repost THEN
        INSERT INTO public.reel_reposts (reel_id, user_id)
        VALUES (p_reel_id, p_user_id) ON CONFLICT (reel_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.reel_reposts WHERE reel_id = p_reel_id AND user_id = p_user_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_reel_save(p_reel_id BIGINT, p_user_id UUID, p_should_save BOOLEAN)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_should_save THEN
        INSERT INTO public.saved_reels (reel_id, user_id)
        VALUES (p_reel_id, p_user_id) ON CONFLICT (reel_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.saved_reels WHERE reel_id = p_reel_id AND user_id = p_user_id;
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.toggle_reel_repost(BIGINT, UUID, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.toggle_reel_save(BIGINT, UUID, BOOLEAN) TO authenticated;
NOTIFY pgrst, 'reload schema';