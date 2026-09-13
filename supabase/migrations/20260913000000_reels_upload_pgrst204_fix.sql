-- =============================================================================
-- FIX: Reel upload failed with PGRST204 (column not found in schema cache)
-- -----------------------------------------------------------------------------
-- createReel() in SupabaseService.kt inserts: author, handle, avatar_type,
-- user_avatar_path, caption, video_url, image_res, storage_path,
-- thumbnail_path, duration_secs, is_public, timestamp, storage_provider,
-- mime_type, file_size, shares_count.
-- No prior migration ever added `timestamp` (and some others) to public.reels,
-- so PostgREST rejected the insert with PGRST204. This migration is idempotent
-- and guarantees every column the app writes exists, then reloads the schema.
-- =============================================================================

ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS timestamp        BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS avatar_type      TEXT NOT NULL DEFAULT 'default';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS music            TEXT NOT NULL DEFAULT 'Original Audio';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS caption          TEXT NOT NULL DEFAULT '';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS video_url        TEXT NOT NULL DEFAULT '';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS image_res        TEXT NOT NULL DEFAULT '';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS author           TEXT NOT NULL DEFAULT '';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS handle           TEXT NOT NULL DEFAULT '';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS storage_path     TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS thumbnail_path   TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS duration_secs    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS is_public        BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS shares_count     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS storage_provider TEXT NOT NULL DEFAULT 'cloudflare_r2';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS mime_type        TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS file_size        BIGINT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS likes_count      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS comments_count   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS location         TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS effect_name      TEXT;

-- Refresh PostgREST schema cache so the new columns are visible immediately.
NOTIFY pgrst, 'reload schema';
