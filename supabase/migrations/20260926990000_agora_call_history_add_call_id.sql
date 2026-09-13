-- -----------------------------------------------------------------------------
-- Add the string call_id column to call_history.
--
-- The AgoraCallManager + SocialViewModel use a UUID-ish STRING call id
-- ("call_<timestamp>") as the primary key in the call_signals table AND pass the
-- same value into startCallHistory()/finalizeCallHistory() and the
-- send-push-notification Edge Function. The original 20260916 migration only
-- defined an auto-generated UUID `id` column and forgot the string `call_id`,
-- so every RINGING insert failed with PGRST204 ("could not find call_id
-- column"), which in turn skipped notifyCall() (no ring push at all).
-- -----------------------------------------------------------------------------

ALTER TABLE public.call_history
    ADD COLUMN IF NOT EXISTS call_id TEXT;

-- Backfill: give existing rows a call_id derived from their UUID id, so the
-- Edge Function and the app can look them up by call_id either way.
UPDATE public.call_history
SET call_id = 'call_' || replace(id::text, '-', '')
WHERE call_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_call_history_call_id
    ON public.call_history (call_id);

-- This column backs the ring push authorization on the caller side.
CREATE INDEX IF NOT EXISTS idx_call_history_caller_id
    ON public.call_history (call_id, caller_handle);