-- Remove empty media records from the app database.
-- This intentionally does NOT touch Backblaze B2 objects or auth accounts.
-- Real uploaded media rows are preserved.

DO $$
BEGIN
    IF to_regclass('public.posts') IS NOT NULL THEN
        DELETE FROM public.posts
        WHERE post_image_res IS NULL
              OR lower(btrim(post_image_res)) IN ('', 'default', 'null');
    END IF;

    IF to_regclass('public.reels') IS NOT NULL THEN
        DELETE FROM public.reels
                WHERE (video_url IS NULL OR lower(btrim(video_url)) IN ('', 'default', 'null'))
                    AND (image_res IS NULL OR lower(btrim(image_res)) IN ('', 'default', 'null'));
    END IF;

    IF to_regclass('public.stories') IS NOT NULL THEN
        DELETE FROM public.stories
        WHERE image_res IS NULL
              OR lower(btrim(image_res)) IN ('', 'default', 'null');
    END IF;

    IF to_regclass('public.app_users') IS NOT NULL THEN
        UPDATE public.app_users
        SET avatar_type = 'default'
        WHERE avatar_type IS NULL
              OR lower(btrim(avatar_type)) IN ('', 'default', 'null');

        UPDATE public.app_users
        SET cover_type = 'default'
        WHERE cover_type IS NULL
              OR lower(btrim(cover_type)) IN ('', 'default', 'null');
    END IF;
END $$;
