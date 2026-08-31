-- =============================================================================
-- VYN NUMBER — Phone-number communication system
-- Run this whole file once in: Supabase Dashboard -> SQL Editor -> Run
-- =============================================================================
-- Concept:
--   * VYN NUMBER identity = exactly ONE per verified, normalized (E.164) phone.
--   * Identity is tied to the Supabase phone-auth user created by the OTP flow
--     (same phone always returns the same auth uid -> same identity), so
--     re-verifying the same number from a different Vyn9 account loads the
--     SAME persistent identity and its existing conversations.
--   * All writes go through SECURITY DEFINER RPCs that check auth.uid();
--     direct INSERT/UPDATE/DELETE is blocked by RLS (no write policies).
-- =============================================================================

-- 1) IDENTITIES ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vyn_number_identities (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phone               TEXT NOT NULL,
    auth_user_id        uuid NOT NULL,
    display_name        TEXT NOT NULL DEFAULT '',
    avatar_type         TEXT NOT NULL DEFAULT 'default',
    verification_status TEXT NOT NULL DEFAULT 'VERIFIED',
    last_verified_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT vyn_number_identities_phone_unique UNIQUE (phone),
    CONSTRAINT vyn_number_identities_auth_user_unique UNIQUE (auth_user_id)
);
CREATE INDEX IF NOT EXISTS idx_vw_identities_phone ON public.vyn_number_identities (phone);

-- 2) CONVERSATIONS ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vyn_number_conversations (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at      TIMESTAMPTZ,
    last_message_preview TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_vw_conversations_last_msg
    ON public.vyn_number_conversations (last_message_at DESC NULLS LAST);

-- 3) MEMBERS ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vyn_number_conversation_members (
    conversation_id uuid NOT NULL REFERENCES public.vyn_number_conversations(id) ON DELETE CASCADE,
    identity_id     uuid NOT NULL REFERENCES public.vyn_number_identities(id) ON DELETE CASCADE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    unread_count    INTEGER NOT NULL DEFAULT 0,
    last_read_at    TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, identity_id)
);
CREATE INDEX IF NOT EXISTS idx_vw_members_identity
    ON public.vyn_number_conversation_members (identity_id);

-- 4) MESSAGES (fully separate from Primary chat_messages) ----------------------
CREATE TABLE IF NOT EXISTS public.vyn_number_messages (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     uuid NOT NULL REFERENCES public.vyn_number_conversations(id) ON DELETE CASCADE,
    sender_identity_id  uuid NOT NULL REFERENCES public.vyn_number_identities(id) ON DELETE CASCADE,
    message_text        TEXT NOT NULL DEFAULT '',
    media_url           TEXT NOT NULL DEFAULT '',
    media_type          TEXT NOT NULL DEFAULT '',
    audio_duration_sec  INTEGER NOT NULL DEFAULT 0,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vw_messages_conv_time
    ON public.vyn_number_messages (conversation_id, created_at);

-- 5) NORMALIZATION (E.164, BD default like the client) --------------------------
CREATE OR REPLACE FUNCTION public.vn_normalize_phone(p_phone TEXT)
RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE
AS $$
DECLARE
    d TEXT := regexp_replace(COALESCE(p_phone, ''), '[^0-9]', '', 'g');
BEGIN
    IF d IS NULL OR length(d) < 7 THEN RETURN NULL; END IF;
    IF left(d, 2) = '00' THEN d := substr(d, 3); END IF;
    IF left(d, 1) = '0' THEN d := '880' || substr(d, 2); END IF;
    RETURN '+' || d;
END;
$$;

-- 6) ACTIVATE / RESOLVE IDENTITY ------------------------------------------------
-- The phone comes from the verified Supabase phone-auth JWT ("phone" claim),
-- NEVER from a client-supplied string. Idempotent: first call creates the
-- identity, every later call returns the SAME identity.
CREATE OR REPLACE FUNCTION public.vn_activate_identity()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_phone TEXT;
    normalized TEXT;
    rec public.vyn_number_identities;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;
    v_phone := current_setting('request.jwt.claims', true)::jsonb ->> 'phone';
    IF v_phone IS NULL OR v_phone = '' THEN
        RAISE EXCEPTION 'VYN NUMBER requires a phone-verified Supabase session';
    END IF;
    normalized := public.vn_normalize_phone(v_phone);
    IF normalized IS NULL THEN
        RAISE EXCEPTION 'Invalid phone number';
    END IF;

    -- One auth user may not hold two different VYN NUMBER numbers.
    IF EXISTS (
        SELECT 1 FROM public.vyn_number_identities
        WHERE auth_user_id = auth.uid() AND phone <> normalized
    ) THEN
        RAISE EXCEPTION 'This VYN NUMBER session is already linked to a different number';
    END IF;

    INSERT INTO public.vyn_number_identities (phone, auth_user_id, verification_status, last_verified_at)
    VALUES (normalized, auth.uid(), 'VERIFIED', now())
    ON CONFLICT (phone) DO UPDATE
        SET auth_user_id = EXCLUDED.auth_user_id,
            verification_status = 'VERIFIED',
            last_verified_at = now()
    RETURNING * INTO rec;

    RETURN jsonb_build_object(
        'identity_id', rec.id,
        'phone', rec.phone,
        'display_name', rec.display_name,
        'avatar_type', rec.avatar_type
    );
END;
$$;

-- Public search: ONLY reveals whether a number is active on VYN NUMBER.
CREATE OR REPLACE FUNCTION public.vn_search_number(p_phone TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    normalized TEXT;
    active BOOLEAN;
    v_name TEXT;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;
    normalized := public.vn_normalize_phone(p_phone);
    IF normalized IS NULL THEN
        RETURN jsonb_build_object('active', FALSE, 'phone', NULL);
    END IF;
    SELECT TRUE, display_name INTO active, v_name
    FROM public.vyn_number_identities
    WHERE phone = normalized AND verification_status = 'VERIFIED'
    LIMIT 1;
    IF active IS NULL THEN
        RETURN jsonb_build_object('active', FALSE, 'phone', normalized);
    END IF;
    RETURN jsonb_build_object('active', TRUE, 'phone', normalized, 'display_name', COALESCE(v_name, ''));
END;
$$;

-- 7) CONVERSATIONS --------------------------------------------------------------
-- Caller's identity (must be VERIFIED); shared by all RPCs below.
CREATE OR REPLACE FUNCTION public.vn_me()
RETURNS public.vyn_number_identities
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    rec public.vyn_number_identities;
BEGIN
    SELECT * INTO rec FROM public.vyn_number_identities
    WHERE auth_user_id = auth.uid() AND verification_status = 'VERIFIED'
    LIMIT 1;
    RETURN rec;
END;
$$;

-- Opens (or returns the existing) 1:1 conversation with a peer phone.
CREATE OR REPLACE FUNCTION public.vn_open_conversation(p_peer_phone TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
    peer public.vyn_number_identities;
    v_norm TEXT;
    conv public.vyn_number_conversations;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated for this account';
    END IF;
    v_norm := public.vn_normalize_phone(p_peer_phone);
    IF v_norm IS NULL THEN
        RAISE EXCEPTION 'Invalid phone number';
    END IF;
    IF v_norm = me.phone THEN
        RAISE EXCEPTION 'You cannot start a conversation with your own number';
    END IF;
    SELECT * INTO peer FROM public.vyn_number_identities
    WHERE phone = v_norm AND verification_status = 'VERIFIED' LIMIT 1;
    IF peer.id IS NULL THEN
        RAISE EXCEPTION 'This number is not active on VYN NUMBER';
    END IF;

    SELECT c.* INTO conv
    FROM public.vyn_number_conversations c
    JOIN public.vyn_number_conversation_members a ON a.conversation_id = c.id AND a.identity_id = me.id
    JOIN public.vyn_number_conversation_members b ON b.conversation_id = c.id AND b.identity_id = peer.id
    WHERE NOT c.is_group
    LIMIT 1;

    IF conv.id IS NULL THEN
        INSERT INTO public.vyn_number_conversations DEFAULT VALUES RETURNING * INTO conv;
        INSERT INTO public.vyn_number_conversation_members (conversation_id, identity_id)
        VALUES (conv.id, me.id), (conv.id, peer.id);
    END IF;

    RETURN jsonb_build_object(
        'conversation_id', conv.id,
        'peer_phone', peer.phone,
        'peer_name', peer.display_name
    );
END;
$$;

-- Chat list: every 1:1 conversation of the caller with partner info.
CREATE OR REPLACE FUNCTION public.vn_inbox()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
    result jsonb;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated for this account';
    END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY last_message_at DESC NULLS LAST), '[]'::jsonb)
    INTO result
    FROM (
        SELECT jsonb_build_object(
            'conversation_id', c.id,
            'peer_phone', peer.phone,
            'peer_name', peer.display_name,
            'last_message_preview', c.last_message_preview,
            'last_message_at', c.last_message_at,
            'unread_count', mm.unread_count
        ) AS row
        FROM public.vyn_number_conversations c
        JOIN public.vyn_number_conversation_members mm
            ON mm.conversation_id = c.id AND mm.identity_id = me.id
        JOIN public.vyn_number_conversation_members om
            ON om.conversation_id = c.id AND om.identity_id <> me.id
        JOIN public.vyn_number_identities peer ON peer.id = om.identity_id
        WHERE NOT c.is_group
    ) sub;
    RETURN result;
END;
$$;

-- Message history of a conversation the caller is a member of.
CREATE OR REPLACE FUNCTION public.vn_messages(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
    result jsonb;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated for this account';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.vyn_number_conversation_members
        WHERE conversation_id = p_conversation_id AND identity_id = me.id
    ) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY created_at), '[]'::jsonb)
    INTO result
    FROM (
        SELECT jsonb_build_object(
            'id', msg.id,
            'conversation_id', msg.conversation_id,
            'sender_identity_id', msg.sender_identity_id,
            'is_mine', (msg.sender_identity_id = me.id),
            'message_text', msg.message_text,
            'media_url', msg.media_url,
            'media_type', msg.media_type,
            'audio_duration_sec', msg.audio_duration_sec,
            'is_read', msg.is_read,
            'created_at', msg.created_at
        ) AS row
        FROM public.vyn_number_messages msg
        WHERE msg.conversation_id = p_conversation_id
        ORDER BY msg.created_at DESC
        LIMIT 300
    ) sub;
    RETURN result;
END;
$$;

-- Sends a message. Only members may send; recipient unread counters are bumped.
CREATE OR REPLACE FUNCTION public.vn_send_message(
    p_conversation_id    uuid,
    p_message_text       TEXT DEFAULT '',
    p_media_url          TEXT DEFAULT '',
    p_media_type         TEXT DEFAULT '',
    p_audio_duration_sec INTEGER DEFAULT 0
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
    msg public.vyn_number_messages;
    preview TEXT;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated for this account';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.vyn_number_conversation_members
        WHERE conversation_id = p_conversation_id AND identity_id = me.id
    ) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;
    IF COALESCE(p_message_text, '') = '' AND COALESCE(p_media_url, '') = '' THEN
        RAISE EXCEPTION 'Empty message';
    END IF;

    INSERT INTO public.vyn_number_messages (
        conversation_id, sender_identity_id, message_text,
        media_url, media_type, audio_duration_sec
    ) VALUES (
        p_conversation_id, me.id,
        COALESCE(p_message_text, ''), COALESCE(p_media_url, ''),
        COALESCE(p_media_type, ''), COALESCE(p_audio_duration_sec, 0)
    ) RETURNING * INTO msg;

    preview := COALESCE(NULLIF(p_message_text, ''), '[Photo]');
    UPDATE public.vyn_number_conversations
    SET last_message_at = msg.created_at, last_message_preview = preview
    WHERE id = p_conversation_id;

    UPDATE public.vyn_number_conversation_members
    SET unread_count = unread_count + 1
    WHERE conversation_id = p_conversation_id AND identity_id <> me.id;

    RETURN jsonb_build_object(
        'id', msg.id,
        'conversation_id', msg.conversation_id,
        'sender_identity_id', msg.sender_identity_id,
        'message_text', msg.message_text,
        'media_url', msg.media_url,
        'media_type', msg.media_type,
        'audio_duration_sec', msg.audio_duration_sec,
        'is_read', msg.is_read,
        'created_at', msg.created_at
    );
END;
$$;

-- Marks a conversation read for the caller.
CREATE OR REPLACE FUNCTION public.vn_mark_read(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated for this account';
    END IF;
    UPDATE public.vyn_number_conversation_members
    SET unread_count = 0, last_read_at = now()
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
    UPDATE public.vyn_number_messages
    SET is_read = TRUE
    WHERE conversation_id = p_conversation_id
      AND sender_identity_id <> me.id
      AND NOT is_read;
END;
$$;

-- Updates the caller's VYN NUMBER display name.
CREATE OR REPLACE FUNCTION public.vn_update_profile(p_display_name TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    rec public.vyn_number_identities;
BEGIN
    UPDATE public.vyn_number_identities
    SET display_name = COALESCE(NULLIF(TRIM(p_display_name), ''), display_name)
    WHERE auth_user_id = auth.uid() AND verification_status = 'VERIFIED'
    RETURNING * INTO rec;
    IF rec.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated for this account';
    END IF;
    RETURN jsonb_build_object(
        'identity_id', rec.id, 'phone', rec.phone,
        'display_name', rec.display_name, 'avatar_type', rec.avatar_type
    );
END;
$$;

-- 8) LOCKDOWN + RLS --------------------------------------------------------------
-- Direct writes are blocked everywhere: RLS enabled with NO write policies.
-- All inserts/updates/deletes happen inside SECURITY DEFINER RPCs above.
ALTER TABLE public.vyn_number_identities           ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyn_number_conversations        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyn_number_conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyn_number_messages             ENABLE ROW LEVEL SECURITY;

-- Identities: a caller can only see their own row (search goes via RPC).
CREATE POLICY vn_identities_self_read ON public.vyn_number_identities
    FOR SELECT TO authenticated USING (auth_user_id = auth.uid());

-- Conversations: only visible to members.
CREATE POLICY vn_conversations_member_read ON public.vyn_number_conversations
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.vyn_number_conversation_members m
            JOIN public.vyn_number_identities i ON i.id = m.identity_id
            WHERE m.conversation_id = id AND i.auth_user_id = auth.uid()
        )
    );

-- Members rows: only your own membership rows.
CREATE POLICY vn_members_self_read ON public.vyn_number_conversation_members
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.vyn_number_identities i
            WHERE i.id = identity_id AND i.auth_user_id = auth.uid()
        )
    );

-- Messages: only members of the conversation.
CREATE POLICY vn_messages_member_read ON public.vyn_number_messages
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.vyn_number_conversation_members m
            JOIN public.vyn_number_identities i ON i.id = m.identity_id
            WHERE m.conversation_id = conversation_id AND i.auth_user_id = auth.uid()
        )
    );

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;

GRANT SELECT ON public.vyn_number_identities,
    public.vyn_number_conversations,
    public.vyn_number_conversation_members,
    public.vyn_number_messages TO authenticated;

GRANT EXECUTE ON FUNCTION
    public.vn_activate_identity(),
    public.vn_search_number(TEXT),
    public.vn_open_conversation(TEXT),
    public.vn_inbox(),
    public.vn_messages(uuid),
    public.vn_send_message(uuid, TEXT, TEXT, TEXT, INTEGER),
    public.vn_mark_read(uuid),
    public.vn_update_profile(TEXT)
TO authenticated;

-- Refresh PostgREST schema cache so the app sees the new RPCs immediately.
NOTIFY pgrst, 'reload schema';




