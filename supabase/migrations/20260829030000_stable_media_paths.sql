-- VYN9 Stable Media Storage Architecture
-- 1. Add storage_path columns to all media tables

ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS avatar_path TEXT;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS cover_path  TEXT;

ALTER TABLE public.posts     ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.posts     ADD COLUMN IF NOT EXISTS storage_path   TEXT;
ALTER TABLE public.posts     ADD COLUMN IF NOT EXISTS thumbnail_path TEXT;

ALTER TABLE public.reels     ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.reels     ADD COLUMN IF NOT EXISTS storage_path   TEXT;
ALTER TABLE public.reels     ADD COLUMN IF NOT EXISTS thumbnail_path TEXT;

ALTER TABLE public.stories   ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.stories   ADD COLUMN IF NOT EXISTS storage_path   TEXT;

ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS sender_avatar_path TEXT;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS storage_path TEXT;

-- 2. Indexes for faster lookup by path
CREATE INDEX IF NOT EXISTS idx_posts_storage_path ON public.posts(storage_path);
CREATE INDEX IF NOT EXISTS idx_reels_storage_path ON public.reels(storage_path);

-- 3. Update RLS for anonymous read (if needed, but usually storage paths are public metadata)
-- Existing policies should work if they allow SELECT on the whole row.
