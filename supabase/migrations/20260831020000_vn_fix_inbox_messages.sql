-- Fixes: vn_inbox / vn_messages failed at runtime with
--   'column "last_message_at" does not exist' / 'column "created_at" does not exist'
-- because the aggregates ordered by a column not exposed by the subquery.
-- They now order by the jsonb field instead (ISO timestamps sort correctly).
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
        FROM public.vyn_number_messages msg
        WHERE msg.conversation_id = p_conversation_id
        ORDER BY msg.created_at DESC
        LIMIT 300
    ) sub;
    RETURN result;
END;
$$;
