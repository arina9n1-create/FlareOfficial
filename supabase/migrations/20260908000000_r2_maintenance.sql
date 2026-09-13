-- =============================================================================
-- CLOUDFLARE R2 MAINTENANCE & HELPERS
-- =============================================================================

-- Helper function to identify if a media URL/path belongs to Cloudflare R2
CREATE OR REPLACE FUNCTION public.is_r2_media(media_ref TEXT)
RETURNS BOOLEAN LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN (
        media_ref LIKE '%/functions/v1/r2-download?path=%' OR
        media_ref ~ '^(profiles|covers|posts|reels|stories|chat)/'
    );
END;
$$;

-- Helper function to identify if a media URL/path belongs to legacy Backblaze B2
CREATE OR REPLACE FUNCTION public.is_b2_media(media_ref TEXT)
RETURNS BOOLEAN LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN (
        media_ref LIKE '%/functions/v1/b2-download?path=%' OR
        media_ref LIKE 'users/%'
    );
END;
$$;

-- Update the app_users table sync function if needed to handle R2 vs B2 logic
-- (Currently the app handles this via MediaStorageResolver, but these SQL helpers
-- will be vital for the b2-cleanup and r2-cleanup edge functions).

NOTIFY pgrst, 'reload schema';
