-- =============================================================================
-- MIGRATION-FRIENDLY MEDIA: BACKFILL legacy full URLs -> object keys
-- -----------------------------------------------------------------------------
-- GOAL: all media columns store storage-independent object keys only.
-- Delivery URLs are generated at read time (MediaStorageResolver in the app /
-- r2-download gateway). This backfill is NON-DESTRUCTIVE:
--   * only rewrites values that match OUR OWN gateway/bucket URL patterns
--   * presets ('default', 'img_*'), local URIs and foreign URLs are untouched
--   * rows that cannot be parsed keep their original value
-- Idempotent: after one pass, no row matches the URL patterns anymore.
-- =============================================================================

-- =============================================================================
-- MIGRATION-FRIENDLY MEDIA: BACKFILL legacy full URLs -> object keys
-- -----------------------------------------------------------------------------
-- Non-destructive: only rewrites values that match OUR OWN gateway/bucket URL
-- patterns. Presets ('default', 'img_*'), local URIs and foreign URLs are
-- untouched. Idempotent: after one pass, no row matches the URL patterns.
-- Pure SQL (no stored functions / dollar-quoting) so it is safe to paste into
-- the Supabase SQL Editor.
-- =============================================================================

-- 1. Reels ----------------------------------------------------------------------
UPDATE public.reels
SET video_url = CASE
    WHEN video_url LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(video_url, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN video_url LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(video_url, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN video_url LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(video_url, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE video_url
END
WHERE video_url LIKE 'http%';

UPDATE public.reels
SET image_res = CASE
    WHEN image_res LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(image_res, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN image_res LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(image_res, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN image_res LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(image_res, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE image_res
END
WHERE image_res LIKE 'http%';

-- 2. Posts ----------------------------------------------------------------------
UPDATE public.posts
SET post_image_res = CASE
    WHEN post_image_res LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(post_image_res, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN post_image_res LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(post_image_res, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN post_image_res LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(post_image_res, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE post_image_res
END
WHERE post_image_res LIKE 'http%';

-- 3. Stories --------------------------------------------------------------------
UPDATE public.stories
SET image_res = CASE
    WHEN image_res LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(image_res, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN image_res LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(image_res, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN image_res LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(image_res, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE image_res
END
WHERE image_res LIKE 'http%';

-- 4. Chat media ------------------------------------------------------------------
UPDATE public.chat_messages
SET media_url = CASE
    WHEN media_url LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(media_url, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN media_url LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(media_url, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN media_url LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(media_url, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE media_url
END
WHERE media_url LIKE 'http%';

-- 5. App users (avatar/cover type can hold a URL for legacy accounts) -----------
UPDATE public.app_users
SET avatar_type = CASE
    WHEN avatar_type LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(avatar_type, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN avatar_type LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(avatar_type, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN avatar_type LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(avatar_type, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE avatar_type
END
WHERE avatar_type LIKE 'http%';

UPDATE public.app_users
SET cover_type = CASE
    WHEN cover_type LIKE '%supabase.co/functions/v1/r2-download?path=%'
        THEN replace(replace(replace(
                regexp_replace(cover_type, '^.*r2-download\?path=', '', ''),
                '%2F', '/'), '%2f', '/'), '%20', ' ')
    WHEN cover_type LIKE '%.backblazeb2.com/file/%'
        THEN regexp_replace(cover_type, '^.*backblazeb2\.com/file/[^/]+/', '', '')
    WHEN cover_type LIKE '%.r2.cloudflarestorage.com/%'
        THEN regexp_replace(cover_type, '^.*r2\.cloudflarestorage\.com/[^/]+/', '', '')
    ELSE cover_type
END
WHERE cover_type LIKE 'http%';

-- Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';

