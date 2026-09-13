-- FCM token persistence and duplicate prevention fix.
--
-- This migration ensures that each device has a unique entry in the device_tokens
-- table, preventing duplicate notifications and ensuring that tokens are
-- correctly associated with the current user.

-- 1. Add device_id column if it doesn't exist.
-- We use this to uniquely identify a physical device/installation.
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS device_id text;

-- 2. Add a unique constraint on device_id.
-- This ensures one row per device. If a new user logs in on the same device,
-- the row will be updated with the new user_id instead of creating a duplicate.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'device_tokens_device_id_key'
    ) THEN
        ALTER TABLE public.device_tokens ADD CONSTRAINT device_tokens_device_id_key UNIQUE (device_id);
    END IF;
END $$;

-- 3. Also add a unique constraint on token just in case.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'device_tokens_token_key'
    ) THEN
        ALTER TABLE public.device_tokens ADD CONSTRAINT device_tokens_token_key UNIQUE (token);
    END IF;
END $$;

-- 4. Create an index on device_id for faster lookups during upsert.
CREATE INDEX IF NOT EXISTS device_tokens_device_id_idx ON public.device_tokens (device_id);

-- 5. Ensure RLS allows the service role to perform upserts correctly.
-- The existing policies already allow insert/update for the owner.
-- The Edge Function uses the service role which bypasses RLS.
