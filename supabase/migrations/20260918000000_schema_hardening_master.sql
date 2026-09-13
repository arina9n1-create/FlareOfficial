-- =============================================================================
-- MASTER SCHEMA HARDENING: Fix for PGRST204 (Missing Columns)
-- -----------------------------------------------------------------------------
-- This migration ensures ALL tables have the necessary columns used by the
-- latest version of the FlareOfficial Android app.
-- It is idempotent (uses IF NOT EXISTS) and reloads the schema cache at the end.
-- =============================================================================

-- 1. App Users (Advanced Profile Metadata)
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS avatar_provider TEXT NOT NULL DEFAULT 'cloudflare_r2';
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS cover_provider  TEXT NOT NULL DEFAULT 'cloudflare_r2';
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS avatar_mime     TEXT;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS cover_mime      TEXT;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS avatar_size     BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS cover_size      BIGINT NOT NULL DEFAULT 0;

-- 2. Posts (Storage & Timestamp Alignment)
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS user_avatar_type TEXT NOT NULL DEFAULT 'default';
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS timestamp        BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS storage_provider TEXT NOT NULL DEFAULT 'cloudflare_r2';
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS mime_type        TEXT;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS file_size        BIGINT NOT NULL DEFAULT 0;

-- 3. Comments (Full Metadata Support)
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_path TEXT;
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS user_avatar_type TEXT NOT NULL DEFAULT 'default';
ALTER TABLE public.comments ADD COLUMN IF NOT EXISTS timestamp        BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;

-- 4. Stories (Storage Provider Alignment)
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS storage_provider TEXT NOT NULL DEFAULT 'cloudflare_r2';
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS mime_type        TEXT;
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS file_size        BIGINT NOT NULL DEFAULT 0;

-- 5. Chat Messages (Multimedia & Translation Support)
-- Note: 'sender_avatar' is often used for the type, 'sender_avatar_path' for the object key.
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS sender_avatar      TEXT NOT NULL DEFAULT 'default';
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS storage_provider   TEXT NOT NULL DEFAULT 'cloudflare_r2';
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS audio_duration_sec INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS original_text      TEXT;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS is_translated      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS translation_lang   TEXT;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS media_type         TEXT NOT NULL DEFAULT 'text';

-- Final Step: Refresh PostgREST schema cache so ALL columns are visible immediately.
NOTIFY pgrst, 'reload schema';
