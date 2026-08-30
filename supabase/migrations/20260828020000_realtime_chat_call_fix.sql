-- =============================================================================
-- REALTIME CHAT + CALL DELIVERY FIX
-- Run this whole file once in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================
-- Fixes:
--   1. Chat messages blocked by case-sensitive handle matching in RLS.
--   2. Stale call_signals rows stuck on OFFERING forever (ghost / "auto accepted" calls).
--   3. Realtime publication guarantees for chat_messages and call_signals.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) Stale call cleanup: every OFFERING row older than 45s becomes MISSED.
--    A trigger runs this on every new call so old ghosts can never re-ring.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.expire_stale_call_signals()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE public.call_signals
    SET status = 'MISSED'
    WHERE status = 'OFFERING'
      AND timestamp < (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT - 45000;
END;
$$;

-- Trigger wrapper: PostgreSQL requires trigger functions to RETURN trigger.
CREATE OR REPLACE FUNCTION public.expire_stale_call_signals_trigger()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    PERFORM public.expire_stale_call_signals();
    RETURN NULL;
END;
$$;

-- Immediate one-time cleanup of rows that are already stuck.
SELECT public.expire_stale_call_signals();

DROP TRIGGER IF EXISTS trg_expire_stale_call_signals ON public.call_signals;
CREATE TRIGGER trg_expire_stale_call_signals
    BEFORE INSERT ON public.call_signals
    FOR EACH STATEMENT
    EXECUTE FUNCTION public.expire_stale_call_signals_trigger();

GRANT EXECUTE ON FUNCTION public.expire_stale_call_signals() TO authenticated;

-- -----------------------------------------------------------------------------
-- 2) chat_messages RLS: case-INSENSITIVE handle matching. The previous policies
--    compared handles with "=", so "Rafi" vs "rafi" silently blocked delivery.
-- -----------------------------------------------------------------------------
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS chat_messages_select_participants ON public.chat_messages;
CREATE POLICY chat_messages_select_participants ON public.chat_messages
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users me
            WHERE me.uid = auth.uid()::text
              AND (
                    lower(me.handle) = lower(chat_messages.sender_handle)
                 OR lower(me.handle) = lower(chat_messages.receiver_handle)
              )
        )
        OR chat_messages.room_id = 'global_live'
    );

DROP POLICY IF EXISTS chat_messages_insert_sender ON public.chat_messages;
CREATE POLICY chat_messages_insert_sender ON public.chat_messages
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.app_users me
            WHERE me.uid = auth.uid()::text
              AND lower(me.handle) = lower(chat_messages.sender_handle)
        )
    );

-- -----------------------------------------------------------------------------
-- 3) call_signals RLS: same case-insensitive participant rules.
-- -----------------------------------------------------------------------------
ALTER TABLE public.call_signals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS call_signals_select_participants ON public.call_signals;
CREATE POLICY call_signals_select_participants ON public.call_signals
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users me
            WHERE me.uid = auth.uid()::text
              AND (
                    lower(me.handle) = lower(call_signals.caller_handle)
                 OR lower(me.handle) = lower(call_signals.receiver_handle)
              )
        )
    );

DROP POLICY IF EXISTS call_signals_insert_caller ON public.call_signals;
CREATE POLICY call_signals_insert_caller ON public.call_signals
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.app_users me
            WHERE me.uid = auth.uid()::text
              AND lower(me.handle) = lower(call_signals.caller_handle)
        )
    );

DROP POLICY IF EXISTS call_signals_update_participants ON public.call_signals;
CREATE POLICY call_signals_update_participants ON public.call_signals
    FOR UPDATE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users me
            WHERE me.uid = auth.uid()::text
              AND (
                    lower(me.handle) = lower(call_signals.caller_handle)
                 OR lower(me.handle) = lower(call_signals.receiver_handle)
              )
        )
    );

-- -----------------------------------------------------------------------------
-- 4) Realtime: both tables MUST be in the supabase_realtime publication or the
--    app's WebSocket postgres_changes subscription receives nothing.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'chat_messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_messages;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'call_signals'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.call_signals;
    END IF;
END $$;

-- chat_messages is queried by receiver_handle / sender_handle / room_id and
-- call_signals by receiver_handle / status — indexes keep the polling fast.
CREATE INDEX IF NOT EXISTS idx_chat_messages_receiver_ts ON public.chat_messages (receiver_handle, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_sender_ts   ON public.chat_messages (sender_handle, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_room        ON public.chat_messages (room_id, timestamp ASC);
CREATE INDEX IF NOT EXISTS idx_call_signals_receiver     ON public.call_signals (receiver_handle, status, timestamp DESC);
