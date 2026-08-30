-- Hardening Supabase Schema for Stable Media Paths
-- This ensures all tables have the necessary storage_path and user_avatar_path columns.

-- 1. App Users (Avatar & Cover)
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS avatar_path TEXT;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS cover_path  TEXT;

-- 2. Posts (Storage Path & User Metadata)
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS storage_path     TEXT;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS thumbnail_path   TEXT;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;

-- 3. Reels (Storage Path & User Metadata)
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS storage_path     TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS thumbnail_path   TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;

-- 4. Stories (Storage Path & User Metadata)
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS storage_path     TEXT;
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;

-- 5. Chat Messages (Storage Path & Sender Metadata)
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS storage_path       TEXT;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS sender_avatar_path TEXT;

-- 6. Comments (User Metadata)
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_posts_storage_path ON public.posts(storage_path);
CREATE INDEX IF NOT EXISTS idx_reels_storage_path ON public.reels(storage_path);
CREATE INDEX IF NOT EXISTS idx_chat_messages_storage_path ON public.chat_messages(storage_path);
