-- =============================================================================
-- VYN NUMBER — vn_messages ordering fix
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================
-- The deployed vn_messages fails at runtime with:
--   column "created_at" does not exist  (code 42703)
-- because the jsonb_agg(...) ORDER BY referenced a bare column instead of the
-- jsonb field. This replaces it with the jsonb-field ordering (ISO timestamps
-- sort correctly) — same proven pattern as vn_inbox.

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
        ORDER BY msg.created_at
    ) sub;
    RETURN result;
END;
$$;