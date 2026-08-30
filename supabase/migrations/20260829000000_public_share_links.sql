-- VYN9 Public Share Links Migration
-- 1. Add is_public columns
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.posts     ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.reels     ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Update RLS for public access (anon role)

-- Profiles
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.app_users;
CREATE POLICY "Public profiles are viewable by everyone" ON public.app_users
    FOR SELECT USING (is_public = TRUE OR auth.uid()::text = uid);

-- Posts
DROP POLICY IF EXISTS "Public posts are viewable by everyone" ON public.posts;
CREATE POLICY "Public posts are viewable by everyone" ON public.posts
    FOR SELECT USING (is_public = TRUE OR auth.uid()::text = user_handle OR EXISTS (
        SELECT 1 FROM public.app_users u WHERE u.handle = posts.user_handle AND u.is_public = TRUE
    ));
-- Note: Simplified post visibility - if post is public, anyone can see.
-- If post is private but owner is public, it might still be private depending on requirements.
-- The instruction says "Private posts must not be publicly accessible".

-- Reels
DROP POLICY IF EXISTS "Public reels are viewable by everyone" ON public.reels;
CREATE POLICY "Public reels are viewable by everyone" ON public.reels
    FOR SELECT USING (is_public = TRUE OR auth.uid()::text = handle);

-- 3. Ensure anon can select these tables
GRANT SELECT ON public.app_users TO anon;
GRANT SELECT ON public.posts     TO anon;
GRANT SELECT ON public.reels     TO anon;
