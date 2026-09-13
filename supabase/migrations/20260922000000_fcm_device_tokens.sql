-- FCM device token registration for push notifications (chat, incoming calls, alerts).
--
-- MATCHES THE LIVE LIVE SCHEMA: one row per FCM registration.
--   id         uuid PK (default gen_random_uuid())
--   user_id    uuid -> auth.users(id) on delete cascade
--   token      text (the FCM registration token)
--   created_at timestamptz default now()
--   updated_at timestamptz default now()
--
-- This migration is NON-DESTRUCTIVE: it never drops or removes rows. It creates
-- the table only if it does not already exist, adds missing columns/indexes
-- idempotently, and creates per-user RLS policies idempotently.
--
-- The Android app registers its FCM token here (INSERT after a DELETE of any
-- identical token), and the send-push-notification Edge Function reads tokens
-- back by user_id using the service role.

CREATE TABLE IF NOT EXISTS public.device_tokens (
    id         uuid        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    token      text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Idempotent column/index add-ons (ignore if already present).
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS updated_at timestamptz;
ALTER TABLE public.device_tokens ALTER COLUMN updated_at SET DEFAULT now();

CREATE INDEX IF NOT EXISTS device_tokens_user_id_idx ON public.device_tokens (user_id);
CREATE INDEX IF NOT EXISTS device_tokens_token_idx   ON public.device_tokens (token);

-- RLS: users manage only their own device tokens. Service role (Edge Function)
-- bypasses RLS to read tokens for routing.
ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "device_tokens_select_owner" ON public.device_tokens;
CREATE POLICY "device_tokens_select_owner"
    ON public.device_tokens
    FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "device_tokens_insert_owner" ON public.device_tokens;
CREATE POLICY "device_tokens_insert_owner"
    ON public.device_tokens
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "device_tokens_delete_owner" ON public.device_tokens;
CREATE POLICY "device_tokens_delete_owner"
    ON public.device_tokens
    FOR DELETE
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "device_tokens_update_owner" ON public.device_tokens;
CREATE POLICY "device_tokens_update_owner"
    ON public.device_tokens
    FOR UPDATE
    USING (auth.uid() = user_id);
