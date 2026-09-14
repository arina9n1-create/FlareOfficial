-- ============================================================================
-- FINAL SINGLE-OVERLOAD FIX — sorts AFTER every other migration, wins.
-- WHY your 03:20 log shows PGRST203: 20261001000000 re-created the
-- (BIGINT, UUID, BOOLEAN) overloads AFTER 20260922 dropped them, so the
-- LIVE db has BOTH (bigint,text,bool) AND (bigint,uuid,bool).
-- PostgREST cannot choose -> HTTP 300, like never writes.
-- This file drops EVERY overload of all 6 RPCs, then re-creates exactly
-- ONE TEXT overload each (what Android sends). Idempotent.
-- APPLY: Supabase Dashboard -> SQL Editor -> paste whole file -> Run.
-- ============================================================================

DO $mig$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT p.oid::regprocedure::text AS sig
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.proname IN ('toggle_post_like', 'toggle_reel_like',
                            'toggle_post_save', 'toggle_reel_save',
                            'toggle_post_repost', 'toggle_reel_repost')
    LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS ' || r.sig;
    END LOOP;
END
$mig$;

-- ONE canonical TEXT overload per RPC (like + save). SECURITY DEFINER
-- so RLS can never block the write. v_uid falls back to auth.uid().
CREATE OR REPLACE FUNCTION public.toggle_post_like(p_post_id BIGINT, p_user_id TEXT, p_should_like BOOLEAN)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $fn$
DECLARE v_uid TEXT := COALESCE(NULLIF(trim(p_user_id), ''), auth.uid()::text);
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'toggle_post_like: missing user id'; END IF;
    IF p_should_like THEN
        INSERT INTO public.post_likes (post_id, user_id) VALUES (p_post_id, v_uid)
        ON CONFLICT (post_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.post_likes WHERE post_id = p_post_id AND user_id = v_uid;
    END IF;
    UPDATE public.posts p SET likes_count = (SELECT count(*) FROM public.post_likes l WHERE l.post_id = p.id)
    WHERE p.id = p_post_id;
END $fn$;

CREATE OR REPLACE FUNCTION public.toggle_reel_like(p_reel_id BIGINT, p_user_id TEXT, p_should_like BOOLEAN)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $fn$
DECLARE v_uid TEXT := COALESCE(NULLIF(trim(p_user_id), ''), auth.uid()::text);
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'toggle_reel_like: missing user id'; END IF;
    IF p_should_like THEN
        INSERT INTO public.reel_likes (reel_id, user_id) VALUES (p_reel_id, v_uid)
        ON CONFLICT (reel_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.reel_likes WHERE reel_id = p_reel_id AND user_id = v_uid;
    END IF;
    UPDATE public.reels r SET likes_count = (SELECT count(*) FROM public.reel_likes l WHERE l.reel_id = r.id)
    WHERE r.id = p_reel_id;
END $fn$;

CREATE OR REPLACE FUNCTION public.toggle_post_save(p_post_id BIGINT, p_user_id TEXT, p_should_save BOOLEAN)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $fn$
DECLARE v_uid TEXT := COALESCE(NULLIF(trim(p_user_id), ''), auth.uid()::text);
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'toggle_post_save: missing user id'; END IF;
    IF p_should_save THEN
        INSERT INTO public.saved_posts (post_id, user_id) VALUES (p_post_id, v_uid)
        ON CONFLICT (post_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.saved_posts WHERE post_id = p_post_id AND user_id = v_uid;
    END IF;
END $fn$;

CREATE OR REPLACE FUNCTION public.toggle_reel_save(p_reel_id BIGINT, p_user_id TEXT, p_should_save BOOLEAN)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $fn$
DECLARE v_uid TEXT := COALESCE(NULLIF(trim(p_user_id), ''), auth.uid()::text);
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'toggle_reel_save: missing user id'; END IF;
    IF p_should_save THEN
        INSERT INTO public.saved_reels (reel_id, user_id) VALUES (p_reel_id, v_uid)
        ON CONFLICT (reel_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.saved_reels WHERE reel_id = p_reel_id AND user_id = v_uid;
    END IF;
END $fn$;

-- REPOST RPCs.
CREATE OR REPLACE FUNCTION public.toggle_post_repost(p_post_id BIGINT, p_user_id TEXT, p_should_repost BOOLEAN)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $fn$
DECLARE v_uid TEXT := COALESCE(NULLIF(trim(p_user_id), ''), auth.uid()::text);
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'toggle_post_repost: missing user id'; END IF;
    IF p_should_repost THEN
        INSERT INTO public.post_reposts (post_id, user_id) VALUES (p_post_id, v_uid)
        ON CONFLICT (post_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.post_reposts WHERE post_id = p_post_id AND user_id = v_uid;
    END IF;
    UPDATE public.posts p SET reposts_count = (SELECT count(*) FROM public.post_reposts r WHERE r.post_id = p.id)
    WHERE p.id = p_post_id;
END $fn$;

CREATE OR REPLACE FUNCTION public.toggle_reel_repost(p_reel_id BIGINT, p_user_id TEXT, p_should_repost BOOLEAN)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $fn$
DECLARE v_uid TEXT := COALESCE(NULLIF(trim(p_user_id), ''), auth.uid()::text);
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'toggle_reel_repost: missing user id'; END IF;
    IF p_should_repost THEN
        INSERT INTO public.reel_reposts (reel_id, user_id) VALUES (p_reel_id, v_uid)
        ON CONFLICT (reel_id, user_id) DO NOTHING;
    ELSE
        DELETE FROM public.reel_reposts WHERE reel_id = p_reel_id AND user_id = v_uid;
    END IF;
    UPDATE public.reels r SET reposts_count = (SELECT count(*) FROM public.reel_reposts q WHERE q.reel_id = r.id)
    WHERE r.id = p_reel_id;
END $fn$;

GRANT EXECUTE ON FUNCTION
      public.toggle_post_like(BIGINT, TEXT, BOOLEAN),
      public.toggle_reel_like(BIGINT, TEXT, BOOLEAN),
      public.toggle_post_save(BIGINT, TEXT, BOOLEAN),
      public.toggle_reel_save(BIGINT, TEXT, BOOLEAN),
      public.toggle_post_repost(BIGINT, TEXT, BOOLEAN),
      public.toggle_reel_repost(BIGINT, TEXT, BOOLEAN)
    TO authenticated;

-- Force PostgREST to reload so PGRST203 disappears immediately.
NOTIFY pgrst, 'reload schema';
