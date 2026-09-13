-- =============================================================================
-- FIX: Comment creation failed with PGRST204 (column not found in schema cache)
-- -----------------------------------------------------------------------------
-- addComment() in SupabaseService.kt inserts: post_id, user_id, username,
-- user_avatar_type, user_avatar_path, text, and timestamp.
-- Some of these columns are missing in the public.comments table.
-- This migration ensures they exist and reloads the PostgREST schema cache.
-- =============================================================================

ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_type TEXT NOT NULL DEFAULT 'default';
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS timestamp        BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;

-- Refresh PostgREST schema cache so the new columns are visible immediately.
NOTIFY pgrst, 'reload schema';
