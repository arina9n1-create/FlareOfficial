-- Real-user chat and call routing. No demo rows or automatic replies are created.

ALTER TABLE public.chat_messages
    ADD COLUMN IF NOT EXISTS receiver_handle TEXT NOT NULL DEFAULT '';

ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_signals ENABLE ROW LEVEL SECURITY;

-- Preserve routing for legacy direct-message rows created as dm_<recipient_handle>.
UPDATE public.chat_messages
SET receiver_handle = btrim(substr(room_id, 4))
WHERE receiver_handle = ''
    AND room_id LIKE 'dm_%'
    AND position('__' IN room_id) = 0;

DROP POLICY IF EXISTS chat_messages_select_participants ON public.chat_messages;
CREATE POLICY chat_messages_select_participants ON public.chat_messages
    FOR SELECT TO authenticated
    USING (
        auth.uid()::text IN (
            SELECT u.uid FROM public.app_users u WHERE u.handle = chat_messages.sender_handle
        )
        OR auth.uid()::text IN (
            SELECT u.uid FROM public.app_users u WHERE u.handle = chat_messages.receiver_handle
        )
        OR room_id = 'global_live'
    );

DROP POLICY IF EXISTS chat_messages_insert_sender ON public.chat_messages;
CREATE POLICY chat_messages_insert_sender ON public.chat_messages
    FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text
          AND u.handle = chat_messages.sender_handle
    ));

DROP POLICY IF EXISTS call_signals_select_participants ON public.call_signals;
DROP POLICY IF EXISTS call_signals_insert_self ON public.call_signals;
DROP POLICY IF EXISTS call_signals_update_participants ON public.call_signals;
CREATE POLICY call_signals_select_participants ON public.call_signals
    FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text
          AND (u.handle = call_signals.caller_handle OR u.handle = call_signals.receiver_handle)
    ));

DROP POLICY IF EXISTS call_signals_insert_caller ON public.call_signals;
CREATE POLICY call_signals_insert_caller ON public.call_signals
    FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text AND u.handle = call_signals.caller_handle
    ));

CREATE POLICY call_signals_update_participants ON public.call_signals
    FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.app_users u
        WHERE u.uid = auth.uid()::text
          AND (u.handle = call_signals.caller_handle OR u.handle = call_signals.receiver_handle)
    ));

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