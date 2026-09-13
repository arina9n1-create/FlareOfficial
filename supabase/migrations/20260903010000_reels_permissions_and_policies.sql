-- =============================================================================
-- REELS PERMISSIONS & POLICY FIX (root cause: uploads invisible + white feed)
-- =============================================================================
-- Symptom: reel uploads to B2, but never appears in the Reels feed; the feed
-- shows white buffering. Live verification against the project returned:
--   {"code":"42501","message":"permission denied for table reels"}
--
-- Two problems:
--   1) 20260830100000_vyn_number.sql ran "REVOKE ALL ON ALL TABLES IN SCHEMA
--      public FROM anon", which silently revoked the 0829 SELECT grants on
--      app_users/posts/reels. Authenticated never had INSERT/UPDATE/DELETE
--      grants or an INSERT policy on reels either, so createReel fails AFTER
--      the B2 upload succeeds (orphaned B2 file, no DB row).
--   2) The app's fetchReels() swallows that error into an empty list and the
--      sync layer then deletes ALL cached reels -> empty feed -> white
--      buffering. (Also hardened app-side in this change set.)
-- =============================================================================

-- 1. Restore/ensure table grants ------------------------------------------------
GRANT SELECT ON public.reels, public.posts, public.app_users TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.reels, public.posts, public.stories TO authenticated;
GRANT SELECT, INSERT, DELETE ON public.reel_likes TO authenticated;
GRANT SELECT, INSERT, DELETE ON public.post_likes TO authenticated;

-- 2. RLS + policies for reels ---------------------------------------------------
ALTER TABLE public.reels ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public reels are viewable by everyone" ON public.reels;
CREATE POLICY "Public reels are viewable by everyone" ON public.reels
    FOR SELECT USING (is_public = TRUE);

DROP POLICY IF EXISTS "Authenticated users can view all reels" ON public.reels;
CREATE POLICY "Authenticated users can view all reels" ON public.reels
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS "Authenticated users can create reels" ON public.reels;
CREATE POLICY "Authenticated users can create reels" ON public.reels
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated users can update reels" ON public.reels;
CREATE POLICY "Authenticated users can update reels" ON public.reels
    FOR UPDATE TO authenticated USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated users can delete reels" ON public.reels;
CREATE POLICY "Authenticated users can delete reels" ON public.reels
    FOR DELETE TO authenticated USING (auth.uid() IS NOT NULL);

-- 3. Same hardening for posts & stories (INSERT policies were missing too) ------
ALTER TABLE public.posts  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Authenticated users can view all posts" ON public.posts;
CREATE POLICY "Authenticated users can view all posts" ON public.posts
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS "Authenticated users can create posts" ON public.posts;
CREATE POLICY "Authenticated users can create posts" ON public.posts
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated users can delete posts" ON public.posts;
CREATE POLICY "Authenticated users can delete posts" ON public.posts
    FOR DELETE TO authenticated USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated users can view all stories" ON public.stories;
CREATE POLICY "Authenticated users can view all stories" ON public.stories
    FOR SELECT TO authenticated USING (TRUE);

DROP POLICY IF EXISTS "Authenticated users can create stories" ON public.stories;
CREATE POLICY "Authenticated users can create stories" ON public.stories
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated users can delete stories" ON public.stories;
CREATE POLICY "Authenticated users can delete stories" ON public.stories
    FOR DELETE TO authenticated USING (auth.uid() IS NOT NULL);

-- 4. Refresh PostgREST schema cache ---------------------------------------------
NOTIFY pgrst, 'reload schema';