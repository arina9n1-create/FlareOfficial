-- -----------------------------------------------------------------------------
-- Agora RTC call history for FlareOfficial.
-- Tracks every Agora voice/video call: participants, type, channel, status,
-- start/end timestamps and duration. Existing call_signals / personal_id_call_signals
-- tables are intentionally left untouched.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.call_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caller_handle   TEXT NOT NULL,
    receiver_handle TEXT NOT NULL,
    call_type       TEXT NOT NULL DEFAULT 'AUDIO',      -- 'AUDIO' | 'VIDEO'
    channel_name    TEXT NOT NULL,                      -- Agora channel name
    status          TEXT NOT NULL DEFAULT 'RINGING',    -- RINGING | IN_PROGRESS | ENDED | MISSED | REJECTED | FAILED
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    duration_sec    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Query by participants.
CREATE INDEX IF NOT EXISTS idx_call_history_caller   ON public.call_history (caller_handle);
CREATE INDEX IF NOT EXISTS idx_call_history_receiver ON public.call_history (receiver_handle);
CREATE INDEX IF NOT EXISTS idx_call_history_created  ON public.call_history (created_at DESC);
-- Agora channels are per-call unique and used to finalize a session.
CREATE INDEX IF NOT EXISTS idx_call_history_channel  ON public.call_history (channel_name);

ALTER TABLE public.call_history ENABLE ROW LEVEL SECURITY;

-- Same convention as the existing call_signals table: any authenticated user may
-- read/write history rows (privacy is handled at the app layer / conversation view).
DROP POLICY IF EXISTS call_history_select_authenticated ON public.call_history;
CREATE POLICY call_history_select_authenticated ON public.call_history
    FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS call_history_insert_authenticated ON public.call_history;
CREATE POLICY call_history_insert_authenticated ON public.call_history
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS call_history_update_participants ON public.call_history;
CREATE POLICY call_history_update_participants ON public.call_history
    FOR UPDATE USING (auth.uid() IS NOT NULL);

GRANT SELECT, INSERT, UPDATE ON TABLE public.call_history TO authenticated;

-- Realtime: let in-app surfaces observe new call_history rows if needed.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'call_history'
    ) THEN
        EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', 'call_history');
    END IF;
END $$;