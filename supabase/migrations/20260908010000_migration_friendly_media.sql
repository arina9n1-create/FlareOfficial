-- =============================================================================
-- MIGRATION-FRIENDLY MEDIA STORAGE ARCHITECTURE
-- -----------------------------------------------------------------------------
-- Stores only object keys and rich metadata instead of full URLs.
-- This allows switching providers (e.g. R2 to S3/B2) without DB rewrites.
-- =============================================================================

-- 1. Metadata columns for Reels
ALTER TABLE public.reels
    ADD COLUMN IF NOT EXISTS shares_count      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS storage_provider  TEXT    NOT NULL DEFAULT 'cloudflare_r2',
    ADD COLUMN IF NOT EXISTS mime_type         TEXT,
    ADD COLUMN IF NOT EXISTS file_size         BIGINT;

-- 2. Metadata columns for Posts
ALTER TABLE public.posts
    ADD COLUMN IF NOT EXISTS storage_provider  TEXT    NOT NULL DEFAULT 'cloudflare_r2',
    ADD COLUMN IF NOT EXISTS mime_type         TEXT,
    ADD COLUMN IF NOT EXISTS file_size         BIGINT;

-- 3. Metadata columns for Stories
ALTER TABLE public.stories
    ADD COLUMN IF NOT EXISTS storage_provider  TEXT    NOT NULL DEFAULT 'cloudflare_r2',
    ADD COLUMN IF NOT EXISTS mime_type         TEXT,
    ADD COLUMN IF NOT EXISTS file_size         BIGINT;

-- 4. Metadata columns for App Users (Avatar & Cover)
ALTER TABLE public.app_users
    ADD COLUMN IF NOT EXISTS avatar_provider   TEXT    NOT NULL DEFAULT 'cloudflare_r2',
    ADD COLUMN IF NOT EXISTS cover_provider    TEXT    NOT NULL DEFAULT 'cloudflare_r2',
    ADD COLUMN IF NOT EXISTS avatar_mime       TEXT,
    ADD COLUMN IF NOT EXISTS cover_mime        TEXT,
    ADD COLUMN IF NOT EXISTS avatar_size       BIGINT,
    ADD COLUMN IF NOT EXISTS cover_size        BIGINT;

-- 5. Metadata columns for Chat Messages
ALTER TABLE public.chat_messages
    ADD COLUMN IF NOT EXISTS storage_provider  TEXT    NOT NULL DEFAULT 'cloudflare_r2';

-- 5. Helper Function to build delivery URLs (Migration-Friendly)
-- If we ever move to a direct CDN or another gateway, we only change THIS function.
CREATE OR REPLACE FUNCTION public.resolve_media_url(object_key TEXT, provider TEXT)
RETURNS TEXT LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    base_url TEXT := 'https://crlrjkpoxlkbpjfqnyyr.supabase.co/functions/v1/r2-download?path=';
BEGIN
    IF object_key IS NULL OR object_key = '' OR object_key = 'default' THEN
        RETURN object_key;
    END IF;

    -- Legacy handling: if it's already an HTTP URL, return as-is
    IF object_key LIKE 'http%' THEN
        RETURN object_key;
    END IF;

    -- Standard resolution: prefix with current authoritative gateway
    RETURN base_url || encode(object_key::bytea, 'escape'); -- Simple concat, encode used loosely here for concept
END;
$$;

NOTIFY pgrst, 'reload schema';
