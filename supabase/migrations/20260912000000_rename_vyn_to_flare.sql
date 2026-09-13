-- =============================================================================
-- FLARE OFFICIAL - Rename migration from Vyn9 to FlareOfficial
-- =============================================================================

-- 1) RENAME TABLES -----------------------------------------------------------
ALTER TABLE IF EXISTS public.vyn_number_identities           RENAME TO flare_number_identities;
ALTER TABLE IF EXISTS public.vyn_number_conversations        RENAME TO flare_number_conversations;
ALTER TABLE IF EXISTS public.vyn_number_conversation_members RENAME TO flare_number_conversation_members;
ALTER TABLE IF EXISTS public.vyn_number_messages             RENAME TO flare_number_messages;

-- 2) RENAME INDEXES ----------------------------------------------------------
ALTER INDEX IF EXISTS idx_vw_identities_phone       RENAME TO idx_flare_identities_phone;
ALTER INDEX IF EXISTS idx_vw_conversations_last_msg RENAME TO idx_flare_conversations_last_msg;
ALTER INDEX IF EXISTS idx_vw_members_identity       RENAME TO idx_flare_members_identity;
ALTER INDEX IF EXISTS idx_vw_messages_conv_time     RENAME TO idx_flare_messages_conv_time;

-- 3) DROP OLD RPC FUNCTIONS --------------------------------------------------
DROP FUNCTION IF EXISTS public.vn_activate_identity();
DROP FUNCTION IF EXISTS public.vn_search_number(TEXT);
DROP FUNCTION IF EXISTS public.vn_open_conversation(TEXT);
DROP FUNCTION IF EXISTS public.vn_inbox();
DROP FUNCTION IF EXISTS public.vn_messages(uuid);
DROP FUNCTION IF EXISTS public.vn_send_message(uuid, TEXT, TEXT, TEXT, INTEGER);
DROP FUNCTION IF EXISTS public.vn_mark_read(uuid);
DROP FUNCTION IF EXISTS public.vn_update_profile(TEXT);
DROP FUNCTION IF EXISTS public.vn_me();
DROP FUNCTION IF EXISTS public.vn_normalize_phone(TEXT);

-- 4) CREATE NEW RPC FUNCTIONS (REPLACING vn_ WITH flare_) --------------------

CREATE OR REPLACE FUNCTION public.flare_normalize_phone(p_phone TEXT)
RETURNS TEXT LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    d TEXT := regexp_replace(COALESCE(p_phone, ''), '[^0-9]', '', 'g');
BEGIN
    IF d IS NULL OR length(d) < 7 THEN RETURN NULL; END IF;
    IF left(d, 2) = '00' THEN d := substr(d, 3); END IF;
    IF left(d, 1) = '0' THEN d := '880' || substr(d, 2); END IF;
    RETURN '+' || d;
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_activate_identity()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_phone TEXT;
    normalized TEXT;
    rec public.flare_number_identities;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    v_phone := current_setting('request.jwt.claims', true)::jsonb ->> 'phone';
    IF v_phone IS NULL OR v_phone = '' THEN
        RAISE EXCEPTION 'FLARE NUMBER requires a phone-verified Supabase session';
    END IF;
    normalized := public.flare_normalize_phone(v_phone);
    IF normalized IS NULL THEN RAISE EXCEPTION 'Invalid phone number'; END IF;

    IF EXISTS (
        SELECT 1 FROM public.flare_number_identities
        WHERE auth_user_id = auth.uid() AND phone <> normalized
    ) THEN
        RAISE EXCEPTION 'This FLARE NUMBER session is already linked to a different number';
    END IF;

    INSERT INTO public.flare_number_identities (phone, auth_user_id, verification_status, last_verified_at)
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

CREATE OR REPLACE FUNCTION public.flare_search_number(p_phone TEXT)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    normalized TEXT;
    active BOOLEAN;
    v_name TEXT;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    normalized := public.flare_normalize_phone(p_phone);
    IF normalized IS NULL THEN
        RETURN jsonb_build_object('active', FALSE, 'phone', NULL);
    END IF;
    SELECT TRUE, display_name INTO active, v_name
    FROM public.flare_number_identities
    WHERE phone = normalized AND verification_status = 'VERIFIED'
    LIMIT 1;
    IF active IS NULL THEN
        RETURN jsonb_build_object('active', FALSE, 'phone', normalized);
    END IF;
    RETURN jsonb_build_object('active', TRUE, 'phone', normalized, 'display_name', COALESCE(v_name, ''));
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_me()
RETURNS public.flare_number_identities LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE
    rec public.flare_number_identities;
BEGIN
    SELECT * INTO rec FROM public.flare_number_identities
    WHERE auth_user_id = auth.uid() AND verification_status = 'VERIFIED'
    LIMIT 1;
    RETURN rec;
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_open_conversation(p_peer_phone TEXT)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    me public.flare_number_identities;
    peer public.flare_number_identities;
    v_norm TEXT;
    conv public.flare_number_conversations;
BEGIN
    me := public.flare_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'FLARE NUMBER is not activated for this account'; END IF;
    v_norm := public.flare_normalize_phone(p_peer_phone);
    IF v_norm IS NULL THEN RAISE EXCEPTION 'Invalid phone number'; END IF;
    IF v_norm = me.phone THEN RAISE EXCEPTION 'You cannot start a conversation with your own number'; END IF;
    SELECT * INTO peer FROM public.flare_number_identities
    WHERE phone = v_norm AND verification_status = 'VERIFIED' LIMIT 1;
    IF peer.id IS NULL THEN RAISE EXCEPTION 'This number is not active on FLARE NUMBER'; END IF;

    SELECT c.* INTO conv
    FROM public.flare_number_conversations c
    JOIN public.flare_number_conversation_members a ON a.conversation_id = c.id AND a.identity_id = me.id
    JOIN public.flare_number_conversation_members b ON b.conversation_id = c.id AND b.identity_id = peer.id
    WHERE NOT c.is_group
    LIMIT 1;

    IF conv.id IS NULL THEN
        INSERT INTO public.flare_number_conversations DEFAULT VALUES RETURNING * INTO conv;
        INSERT INTO public.flare_number_conversation_members (conversation_id, identity_id)
        VALUES (conv.id, me.id), (conv.id, peer.id);
    END IF;

    RETURN jsonb_build_object(
        'conversation_id', conv.id,
        'peer_phone', peer.phone,
        'peer_name', peer.display_name
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_inbox()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    me public.flare_number_identities;
    result jsonb;
BEGIN
    me := public.flare_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'FLARE NUMBER is not activated for this account'; END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'last_message_at') DESC NULLS LAST), '[]'::jsonb)
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
        FROM public.flare_number_conversations c
        JOIN public.flare_number_conversation_members mm
            ON mm.conversation_id = c.id AND mm.identity_id = me.id
        JOIN public.flare_number_conversation_members om
            ON om.conversation_id = c.id AND om.identity_id <> me.id
        JOIN public.flare_number_identities peer ON peer.id = om.identity_id
        WHERE NOT c.is_group
    ) sub;
    RETURN result;
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_messages(p_conversation_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    me public.flare_number_identities;
    result jsonb;
BEGIN
    me := public.flare_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'FLARE NUMBER is not activated for this account'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.flare_number_conversation_members
        WHERE conversation_id = p_conversation_id AND identity_id = me.id
    ) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'created_at')), '[]'::jsonb)
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
        FROM public.flare_number_messages msg
        WHERE msg.conversation_id = p_conversation_id
        ORDER BY msg.created_at DESC
        LIMIT 300
    ) sub;
    RETURN result;
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_send_message(
    p_conversation_id    uuid,
    p_message_text       TEXT DEFAULT '',
    p_media_url          TEXT DEFAULT '',
    p_media_type         TEXT DEFAULT '',
    p_audio_duration_sec INTEGER DEFAULT 0
)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    me public.flare_number_identities;
    msg public.flare_number_messages;
    preview TEXT;
BEGIN
    me := public.flare_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'FLARE NUMBER is not activated for this account'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.flare_number_conversation_members
        WHERE conversation_id = p_conversation_id AND identity_id = me.id
    ) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;
    IF COALESCE(p_message_text, '') = '' AND COALESCE(p_media_url, '') = '' THEN
        RAISE EXCEPTION 'Empty message';
    END IF;

    INSERT INTO public.flare_number_messages (
        conversation_id, sender_identity_id, message_text,
        media_url, media_type, audio_duration_sec
    ) VALUES (
        p_conversation_id, me.id,
        COALESCE(p_message_text, ''), COALESCE(p_media_url, ''),
        COALESCE(p_media_type, ''), COALESCE(p_audio_duration_sec, 0)
    ) RETURNING * INTO msg;

    preview := COALESCE(NULLIF(p_message_text, ''), '[Photo]');
    UPDATE public.flare_number_conversations
    SET last_message_at = msg.created_at, last_message_preview = preview
    WHERE id = p_conversation_id;

    UPDATE public.flare_number_conversation_members
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

CREATE OR REPLACE FUNCTION public.flare_mark_read(p_conversation_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    me public.flare_number_identities;
BEGIN
    me := public.flare_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'FLARE NUMBER is not activated for this account'; END IF;
    UPDATE public.flare_number_conversation_members
    SET unread_count = 0, last_read_at = now()
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
    UPDATE public.flare_number_messages
    SET is_read = TRUE
    WHERE conversation_id = p_conversation_id
      AND sender_identity_id <> me.id
      AND NOT is_read;
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_update_profile(p_display_name TEXT)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    rec public.flare_number_identities;
BEGIN
    UPDATE public.flare_number_identities
    SET display_name = COALESCE(NULLIF(TRIM(p_display_name), ''), display_name)
    WHERE auth_user_id = auth.uid() AND verification_status = 'VERIFIED'
    RETURNING * INTO rec;
    IF rec.id IS NULL THEN RAISE EXCEPTION 'FLARE NUMBER is not activated for this account'; END IF;
    RETURN jsonb_build_object(
        'identity_id', rec.id, 'phone', rec.phone,
        'display_name', rec.display_name, 'avatar_type', rec.avatar_type
    );
END;
$$;

-- 5) UPDATE RLS POLICIES -----------------------------------------------------

DROP POLICY IF EXISTS vn_identities_self_read ON public.flare_number_identities;
CREATE POLICY flare_identities_self_read ON public.flare_number_identities
    FOR SELECT TO authenticated USING (auth_user_id = auth.uid());

DROP POLICY IF EXISTS vn_conversations_member_read ON public.flare_number_conversations;
CREATE POLICY flare_conversations_member_read ON public.flare_number_conversations
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.flare_number_conversation_members m
            JOIN public.flare_number_identities i ON i.id = m.identity_id
            WHERE m.conversation_id = id AND i.auth_user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS vn_members_self_read ON public.flare_number_conversation_members;
CREATE POLICY flare_members_self_read ON public.flare_number_conversation_members
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.flare_number_identities i
            WHERE i.id = identity_id AND i.auth_user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS vn_messages_member_read ON public.flare_number_messages;
CREATE POLICY flare_messages_member_read ON public.flare_number_messages
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.flare_number_conversation_members m
            JOIN public.flare_number_identities i ON i.id = m.identity_id
            WHERE m.conversation_id = conversation_id AND i.auth_user_id = auth.uid()
        )
    );

-- 6) GRANT PERMISSIONS -------------------------------------------------------

GRANT SELECT ON public.flare_number_identities,
    public.flare_number_conversations,
    public.flare_number_conversation_members,
    public.flare_number_messages TO authenticated;

GRANT EXECUTE ON FUNCTION
    public.flare_activate_identity(),
    public.flare_search_number(TEXT),
    public.flare_open_conversation(TEXT),
    public.flare_inbox(),
    public.flare_messages(uuid),
    public.flare_send_message(uuid, TEXT, TEXT, TEXT, INTEGER),
    public.flare_mark_read(uuid),
    public.flare_update_profile(TEXT)
TO authenticated;

NOTIFY pgrst, 'reload schema';
