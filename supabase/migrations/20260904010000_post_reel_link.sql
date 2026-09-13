-- Adds support for linking Reels to the Home Feed as Posts
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS is_reel_post BOOLEAN NOT NULL DEFAULT FALSE;
