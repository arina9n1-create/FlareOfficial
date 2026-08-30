-- Server contract for authenticated feed writes and comment counters.
-- No media objects are touched by this migration.

ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS posts_select_authenticated ON public.posts;
CREATE POLICY posts_select_authenticated ON public.posts
    FOR SELECT TO authenticated
    USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS posts_insert_authenticated ON public.posts;
CREATE POLICY posts_insert_authenticated ON public.posts
    FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = posts.user_handle
    ));

DROP POLICY IF EXISTS posts_update_authenticated ON public.posts;
CREATE POLICY posts_update_authenticated ON public.posts
    FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = posts.user_handle
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = posts.user_handle
    ));

CREATE OR REPLACE FUNCTION public.update_post_engagement(
    target_post_id BIGINT,
    target_likes_count INTEGER DEFAULT NULL,
    target_is_liked BOOLEAN DEFAULT NULL,
    target_is_saved BOOLEAN DEFAULT NULL,
    target_is_reposted BOOLEAN DEFAULT NULL,
    target_reposts_count INTEGER DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    UPDATE public.posts
    SET likes_count = COALESCE(target_likes_count, likes_count),
        is_liked = COALESCE(target_is_liked, is_liked),
        is_saved = COALESCE(target_is_saved, is_saved),
        is_reposted = COALESCE(target_is_reposted, is_reposted),
        reposts_count = COALESCE(target_reposts_count, reposts_count)
    WHERE id = target_post_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Post % was not found', target_post_id;
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.update_post_engagement(BIGINT, INTEGER, BOOLEAN, BOOLEAN, BOOLEAN, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.update_post_engagement(BIGINT, INTEGER, BOOLEAN, BOOLEAN, BOOLEAN, INTEGER) TO authenticated;

DROP POLICY IF EXISTS posts_delete_own ON public.posts;
CREATE POLICY posts_delete_own ON public.posts
    FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text
          AND (u.handle = posts.user_handle
               OR u.role IN ('SUPER_ADMIN', 'ADMIN')
               OR u.can_delete_posts = true
               OR u.can_manage_users = true)
    ));

DROP POLICY IF EXISTS comments_select_authenticated ON public.comments;
CREATE POLICY comments_select_authenticated ON public.comments
    FOR SELECT TO authenticated
    USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS comments_insert_authenticated ON public.comments;
CREATE POLICY comments_insert_authenticated ON public.comments
    FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = comments.username
    ));

DROP POLICY IF EXISTS comments_delete_own ON public.comments;
CREATE POLICY comments_delete_own ON public.comments
    FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = comments.username
    ));

DROP POLICY IF EXISTS stories_delete_own ON public.stories;
CREATE POLICY stories_delete_own ON public.stories
    FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = stories.username
    ));

CREATE OR REPLACE FUNCTION public.increment_post_comments(target_post_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE public.posts
    SET comments_count = COALESCE(comments_count, 0) + 1
    WHERE id = target_post_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Post % was not found', target_post_id;
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.increment_post_comments(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.increment_post_comments(BIGINT) TO authenticated;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'comments'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.comments;
    END IF;
END $$;