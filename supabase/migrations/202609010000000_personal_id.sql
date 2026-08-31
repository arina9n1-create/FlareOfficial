-- =============================================================================
-- Personal ID — private, separate identity namespace on top of the Vyn9 account
-- Run this whole file once in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================
-- Privacy model:
--   * personal_ids.username is a globally unique, searchable handle.
--   * The internal account mapping (user_id -> auth.uid()) is NEVER exposed
--     through any RPC or policy. Search returns ONLY username + avatar_url.
--   * All tables are RLS ON with NO direct access policies; every operation
--     goes through SECURITY DEFINER RPCs that authorize by auth.uid().
--   * One account can hold at MOST ONE Personal ID.
-- =============================================================================

-- 1) PERSONAL IDS --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.personal_ids (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    username    TEXT NOT NULL,
    avatar_url  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One Personal ID per account.
CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_ids_user
    ON public.personal_ids (user_id);

-- Globally unique username, case-insensitively.
CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_ids_username_lower
    ON public.personal_ids (lower(username));
CREATE INDEX IF NOT EXISTS idx_personal_ids_username_prefix
    ON public.personal_ids (lower(username) text_pattern_ops);

ALTER TABLE public.personal_ids ENABLE ROW LEVEL SECURITY; -- no direct policies = deny all

-- 2) CONVERSATIONS -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.personal_id_conversations (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at      TIMESTAMPTZ,
    last_message_preview TEXT NOT NULL DEFAULT ''
);

-- 3) MEMBERS
CREATE TABLE IF NOT EXISTS public.personal_id_conversation_members (
    conversation_id uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    personal_id     uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    unread_count     INTEGER NOT NULL DEFAULT 0,
    last_read_at     TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, personal_id));
CREATE INDEX IF NOT EXISTS idx_personal_id_members_pid
    ON public.personal_id_conversation_members (personal_id);

-- 4) MESSAGES
CREATE TABLE IF NOT EXISTS public.personal_id_messages (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    sender_personal_id  uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    message_text        TEXT NOT NULL DEFAULT '',
    media_url           TEXT NOT NULL DEFAULT '',
    media_type          TEXT NOT NULL DEFAULT '',
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS idx_personal_id_messages_conv
    ON public.personal_id_messages (conversation_id, created_at);
-- 5) CALL SIGNALING (privacy-scoped to Personal ID)
CREATE TABLE IF NOT EXISTS public.personal_id_call_signals (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    caller_personal_id   uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    receiver_personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    call_type            TEXT NOT NULL DEFAULT 'VIDEO',
    status               TEXT NOT NULL DEFAULT 'OFFERING',
    sdp                  TEXT,
    timestamp            BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS idx_personal_id_call_signals_conv
    ON public.personal_id_call_signals (conversation_id, timestamp DESC);

ALTER TABLE public.personal_id_conversations        ENABLE ROW LEVEL SECURITY; -- deny direct
ALTER TABLE public.personal_id_conversation_members ENABLE ROW LEVEL SECURITY; -- deny direct
ALTER TABLE public.personal_id_messages             ENABLE ROW LEVEL SECURITY; -- deny direct
ALTER TABLE public.personal_id_call_signals         ENABLE ROW LEVEL SECURITY; -- deny direct

-- =============================================================================
-- HELPERS
-- =============================================================================

-- Username normalization + validation. Allowed: 3-20 chars, starts with a letter,
-- then letters/digits/underscore. Returns lowercase or NULL.
CREATE OR REPLACE FUNCTION public.pn_normalize_username(p_username TEXT)
RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE
AS $$
DECLARE u TEXT := lower(btrim(COALESCE(p_username, '')));
BEGIN
    IF u ~ '^[a-z][a-z0-9_]{2,19}$' THEN RETURN u; END IF;
    RETURN NULL;
END;
$$;

-- Current caller's Personal ID row (null when not created yet).
CREATE OR REPLACE FUNCTION public.pn_me()
RETURNS public.personal_ids
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE rec public.personal_ids;
BEGIN
    SELECT * INTO rec FROM public.personal_ids
    WHERE user_id = auth.uid() LIMIT 1;
    RETURN rec;
END;
$$;
-- 6) CREATE (one per account, globally unique username) ------------------------
CREATE OR REPLACE FUNCTION public.pn_create(p_username TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_norm TEXT;
    rec public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    v_norm := public.pn_normalize_username(p_username);
    IF v_norm IS NULL THEN
        RAISE EXCEPTION 'Username must be 3-20 characters, start with a letter, and use only letters, numbers, or underscores';
    END IF;
    IF EXISTS (SELECT 1 FROM public.personal_ids WHERE lower(username) = v_norm) THEN
        RAISE EXCEPTION 'This Personal ID is already taken';
    END IF;
    IF EXISTS (SELECT 1 FROM public.personal_ids WHERE user_id = auth.uid()) THEN
        RAISE EXCEPTION 'You can only create one Personal ID';
    END IF;
    INSERT INTO public.personal_ids (user_id, username)
    VALUES (auth.uid(), v_norm)
    RETURNING * INTO rec;
    RETURN jsonb_build_object('id', rec.id, 'username', rec.username, 'avatar_url', rec.avatar_url);
END;
$$;

-- 7) SEARCH (personal ID namespace ONLY — never Vyn9 usernames or accounts) ----
CREATE OR REPLACE FUNCTION public.pn_search(p_query TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE result jsonb;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    IF COALESCE(btrim(p_query), '') = '' THEN RETURN '[]'::jsonb; END IF;
    SELECT COALESCE(jsonb_agg(row), '[]'::jsonb) INTO result
    FROM (
        SELECT jsonb_build_object(
            'username', pid.username,
            'avatar_url', pid.avatar_url,
            'has_avatar', (pid.avatar_url IS NOT NULL AND pid.avatar_url <> '')
        ) AS row
        FROM public.personal_ids pid
        WHERE lower(pid.username) LIKE lower('%' || btrim(p_query) || '%')
          AND pid.user_id <> auth.uid()
        ORDER BY lower(pid.username)
        LIMIT 20
    ) sub;
    RETURN result;
END;
$$;

-- 8) CONVERSATIONS ---------------------------------------------------------------
-- Opens (or finds) a 1:1 conversation with a peer Personal ID username.
CREATE OR REPLACE FUNCTION public.pn_open_conversation(p_username TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.personal_ids;
    peer public.personal_ids;
    v_norm TEXT;
    conv public.personal_id_conversations;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    v_norm := public.pn_normalize_username(p_username);
    IF v_norm IS NULL THEN RAISE EXCEPTION 'Invalid Personal ID'; END IF;
    IF v_norm = me.username THEN RAISE EXCEPTION 'You cannot chat with yourself'; END IF;
    SELECT * INTO peer FROM public.personal_ids WHERE lower(username) = v_norm LIMIT 1;
    IF peer.id IS NULL THEN RAISE EXCEPTION 'Personal ID not found'; END IF;

    SELECT c.* INTO conv
    FROM public.personal_id_conversations c
    JOIN public.personal_id_conversation_members a ON a.conversation_id = c.id AND a.personal_id = me.id
    JOIN public.personal_id_conversation_members b ON b.conversation_id = c.id AND b.personal_id = peer.id
    LIMIT 1;
    IF conv.id IS NULL THEN
        INSERT INTO public.personal_id_conversations DEFAULT VALUES RETURNING * INTO conv;
        INSERT INTO public.personal_id_conversation_members (conversation_id, personal_id)
        VALUES (conv.id, me.id), (conv.id, peer.id);
    END IF;
    RETURN jsonb_build_object(
        'conversation_id', conv.id,
        'peer_username', peer.username,
        'peer_avatar_url', peer.avatar_url
    );
END;
$$;

-- 9) INBOX (chat list with peer personal-id preview)
CREATE OR REPLACE FUNCTION public.pn_inbox()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.personal_ids;
    result jsonb;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN '[]'::jsonb; END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'last_message_at') DESC NULLS LAST), '[]'::jsonb)
    INTO result
    FROM (
        SELECT jsonb_build_object(
            'conversation_id', c.id,
            'peer_username', peer.username,
            'peer_avatar_url', peer.avatar_url,
            'last_message_preview', c.last_message_preview,
            'last_message_at', c.last_message_at,
            'unread_count', mm.unread_count
        ) AS row
        FROM public.personal_id_conversations c
        JOIN public.personal_id_conversation_members mm
            ON mm.conversation_id = c.id AND mm.personal_id = me.id
        JOIN public.personal_id_conversation_members om
            ON om.conversation_id = c.id AND om.personal_id <> me.id
        JOIN public.personal_ids peer ON peer.id = om.personal_id
    ) sub;
    RETURN result;
END;
$$;
-- 10) MESSAGES -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_messages(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.personal_ids;
    result jsonb;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id
    ) THEN RAISE EXCEPTION 'Not a member of this conversation'; END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'created_at')), '[]'::jsonb)
    INTO result
    FROM (
        SELECT jsonb_build_object(
            'id', msg.id,
            'conversation_id', msg.conversation_id,
            'is_mine', (msg.sender_personal_id = me.id),
            'message_text', msg.message_text,
            'media_url', msg.media_url,
            'media_type', msg.media_type,
            'is_read', msg.is_read,
            'created_at', msg.created_at
        ) AS row
        FROM public.personal_id_messages msg
        WHERE msg.conversation_id = p_conversation_id
        ORDER BY msg.created_at
    ) sub;
    RETURN result;
END;
$$;

-- 11) SEND MESSAGE
CREATE OR REPLACE FUNCTION public.pn_send_message(
    p_conversation_id uuid,
    p_message_text TEXT DEFAULT ''
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.personal_ids;
    msg public.personal_id_messages;
    preview TEXT;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id
    ) THEN RAISE EXCEPTION 'Not a member of this conversation'; END IF;
    IF COALESCE(btrim(p_message_text), '') = '' THEN RAISE EXCEPTION 'Empty message'; END IF;

    INSERT INTO public.personal_id_messages (conversation_id, sender_personal_id, message_text)
    VALUES (p_conversation_id, me.id, btrim(p_message_text))
    RETURNING * INTO msg;

    preview := msg.message_text;
    UPDATE public.personal_id_conversations
    SET last_message_at = msg.created_at, last_message_preview = preview
    WHERE id = p_conversation_id;

    UPDATE public.personal_id_conversation_members
    SET unread_count = unread_count + 1
    WHERE conversation_id = p_conversation_id AND personal_id <> me.id;

    RETURN jsonb_build_object(
        'id', msg.id, 'conversation_id', msg.conversation_id,
        'is_mine', TRUE, 'message_text', msg.message_text,
        'media_url', '', 'media_type', '',
        'is_read', msg.is_read, 'created_at', msg.created_at
    );
END;
$$;

-- 12) MARK READ
CREATE OR REPLACE FUNCTION public.pn_mark_read(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN; END IF;
    UPDATE public.personal_id_conversation_members
    SET unread_count = 0, last_read_at = now()
    WHERE conversation_id = p_conversation_id AND personal_id = me.id;
    UPDATE public.personal_id_messages
    SET is_read = TRUE
    WHERE conversation_id = p_conversation_id
      AND sender_personal_id <> me.id;
END;
$$;

-- 13) DELETE MESSAGE (own messages only)
CREATE OR REPLACE FUNCTION public.pn_delete_message(p_conversation_id uuid, p_message_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    DELETE FROM public.personal_id_messages
    WHERE id = p_message_id AND conversation_id = p_conversation_id
      AND sender_personal_id = me.id;
    IF NOT FOUND THEN RAISE EXCEPTION 'You can only delete your own messages'; END IF;
    UPDATE public.personal_id_conversations c SET
        last_message_preview = COALESCE((SELECT m.message_text FROM public.personal_id_messages m
            WHERE m.conversation_id = c.id ORDER BY m.created_at DESC LIMIT 1), ''),
        last_message_at = (SELECT MAX(m.created_at) FROM public.personal_id_messages m
            WHERE m.conversation_id = c.id)
    WHERE c.id = p_conversation_id;
END;
$$;
-- =============================================================================
-- CALL SIGNALING (Personal ID privacy-scoped)
-- =============================================================================

-- Stale OFFERING cleanup (matches the existing call_signals pattern).
CREATE OR REPLACE FUNCTION public.expire_stale_pid_call_signals()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE public.personal_id_call_signals
    SET status = 'MISSED'
    WHERE status = 'OFFERING'
      AND timestamp < (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT - 45000;
END;
$$;
CREATE OR REPLACE FUNCTION public.expire_stale_pid_call_signals_trigger()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    PERFORM public.expire_stale_pid_call_signals();
    RETURN NULL;
END;
$$;
SELECT public.expire_stale_pid_call_signals();
DROP TRIGGER IF EXISTS trg_expire_stale_pid_call_signals ON public.personal_id_call_signals;
CREATE TRIGGER trg_expire_stale_pid_call_signals
    BEFORE INSERT ON public.personal_id_call_signals
    FOR EACH STATEMENT EXECUTE FUNCTION public.expire_stale_pid_call_signals_trigger();

-- Initiate: caller creates OFFERING signal for a personal-ID conversation.
CREATE OR REPLACE FUNCTION public.pn_call_initiate(p_conversation_id uuid, p_call_type TEXT DEFAULT 'VIDEO')
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    me public.personal_ids;
    peer public.personal_id_conversation_members%ROWTYPE;
    sig public.personal_id_call_signals;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    SELECT * INTO peer FROM public.personal_id_conversation_members
    WHERE conversation_id = p_conversation_id AND personal_id <> me.id LIMIT 1;
    IF peer IS NULL THEN RAISE EXCEPTION 'Conversation not found'; END IF;
    INSERT INTO public.personal_id_call_signals
        (conversation_id, caller_personal_id, receiver_personal_id, call_type, status)
    VALUES (p_conversation_id, me.id, peer.personal_id, COALESCE(p_call_type, 'VIDEO'), 'OFFERING')
    RETURNING * INTO sig;
    RETURN jsonb_build_object(
        'id', sig.id, 'conversation_id', sig.conversation_id,
        'call_type', sig.call_type, 'status', sig.status,
        'timestamp', sig.timestamp
    );
END;
$$;

-- Poll active signals for a conversation (both sides; includes sdp).
CREATE OR REPLACE FUNCTION public.pn_call_poll(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE result jsonb;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    IF (public.pn_me()).id IS NULL THEN RETURN '[]'::jsonb; END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'timestamp') DESC), '[]'::jsonb) INTO result
    FROM (
        SELECT jsonb_build_object(
            'id', sig.id,
            'caller_personal_id', sig.caller_personal_id,
            'receiver_personal_id', sig.receiver_personal_id,
            'call_type', sig.call_type,
            'status', sig.status,
            'sdp', sig.sdp,
            'timestamp', sig.timestamp
        ) AS row
        FROM public.personal_id_call_signals sig
        JOIN public.personal_id_conversation_members mm
            ON mm.conversation_id = sig.conversation_id AND mm.personal_id = (public.pn_me()).id
        WHERE sig.conversation_id = p_conversation_id
          AND sig.status IN ('OFFERING', 'ACCEPTED', 'REJECTED', 'ENDED')
          AND sig.timestamp > (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT - 3600000
        LIMIT 5
    ) sub;
    RETURN result;
END;
$$;
-- Caller updates its own sdp / state on an OFFERING or ACCEPTED call.
CREATE OR REPLACE FUNCTION public.pn_call_update(p_call_id uuid, p_sdp TEXT DEFAULT NULL, p_status TEXT DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    UPDATE public.personal_id_call_signals
    SET sdp = COALESCE(p_sdp, sdp),
        status = COALESCE(p_status, status)
    WHERE id = p_call_id
      AND (caller_personal_id = me.id OR receiver_personal_id = me.id);
END;
$$;

-- Callee accepts: sets status ACCEPTED and stores the answer sdp.
CREATE OR REPLACE FUNCTION public.pn_call_answer(p_call_id uuid, p_sdp TEXT)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    UPDATE public.personal_id_call_signals
    SET status = 'ACCEPTED', sdp = p_sdp
    WHERE id = p_call_id AND receiver_personal_id = me.id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Not your call to answer'; END IF;
END;
$$;

-- Reject / end.
CREATE OR REPLACE FUNCTION public.pn_call_reject(p_call_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN; END IF;
    UPDATE public.personal_id_call_signals
    SET status = 'REJECTED'
    WHERE id = p_call_id AND receiver_personal_id = me.id;
END;
$$;

CREATE OR REPLACE FUNCTION public.pn_call_end(p_call_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN; END IF;
    UPDATE public.personal_id_call_signals
    SET status = 'ENDED'
    WHERE id = p_call_id AND (caller_personal_id = me.id OR receiver_personal_id = me.id);
END;
$$;

-- Realtime publication for instant message/call delivery (client also polls).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public' AND tablename = 'personal_id_messages') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.personal_id_messages;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public' AND tablename = 'personal_id_call_signals') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.personal_id_call_signals;
    END IF;
END $$;

-- Explicitly revoke direct table permissions from anon/authenticated (RPC-only access).
REVOKE ALL ON public.personal_ids, public.personal_id_conversations,
    public.personal_id_conversation_members, public.personal_id_messages,
    public.personal_id_call_signals FROM anon, authenticated;

GRANT EXECUTE ON FUNCTION public.pn_create(text), public.pn_me(), public.pn_search(text),
    public.pn_open_conversation(text), public.pn_inbox(), public.pn_messages(uuid),
    public.pn_send_message(uuid, text), public.pn_mark_read(uuid),
    public.pn_delete_message(uuid, uuid), public.pn_call_initiate(uuid, text),
    public.pn_call_poll(uuid), public.pn_call_update(uuid, text, text),
    public.pn_call_answer(uuid, text), public.pn_call_reject(uuid),
    public.pn_call_end(uuid) TO authenticated;