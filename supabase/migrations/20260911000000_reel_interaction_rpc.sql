-- Ensure reel likes work on deployments where the earlier post-only RPC migration ran.
CREATE OR REPLACE FUNCTION public.toggle_reel_like(
    p_reel_id BIGINT,
    p_user_id UUID,
    p_should_like BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_should_like THEN
        INSERT INTO public.reel_likes (reel_id, user_id)
        VALUES (p_reel_id, p_user_id)
        ON CONFLICT (reel_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.reel_likes
        WHERE reel_id = p_reel_id AND user_id = p_user_id;
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.toggle_reel_like(BIGINT, UUID, BOOLEAN) TO authenticated;

NOTIFY pgrst, 'reload schema';