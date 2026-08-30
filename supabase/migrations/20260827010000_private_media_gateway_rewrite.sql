-- Rewrites every stored Backblaze media URL to the secure gateway form.
--
-- Legacy rows hold direct bucket URLs like
--   https://<bucket>.s3.<region>.backblazeb2.com/users/<uid>/<type>/<file>
-- which return 401 while the bucket stays PRIVATE. New uploads already store the
-- gateway URL returned by the b2-upload Edge Function:
--   https://<project>.supabase.co/functions/v1/b2-download?path=users/<uid>/<type>/<file>
-- This one-time migration converts existing rows so previously uploaded avatars,
-- covers, posts, reels, stories and chat photos keep rendering.

DO $$
DECLARE
    r RECORD;
    gateway_base CONSTANT TEXT :=
        'https://crlrjkpoxlkbpjfqnyyr.supabase.co/functions/v1/b2-download?path=';
BEGIN
    FOR r IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN ('app_users', 'posts', 'stories', 'reels', 'chat_messages')
          AND column_name IN (
              'avatar_type', 'cover_type', 'user_avatar_type',
              'post_image_res', 'image_res', 'video_url', 'media_url'
          )
    LOOP
        EXECUTE format(
            'UPDATE public.%I SET %I = %L || substring(%I from ''https://[^/]+\.backblazeb2\.com/(.+)$'') '
                || 'WHERE %I LIKE ''https://%%.backblazeb2.com/%%''',
            r.table_name, r.column_name, gateway_base, r.column_name, r.column_name
        );
    END LOOP;
END $$;
