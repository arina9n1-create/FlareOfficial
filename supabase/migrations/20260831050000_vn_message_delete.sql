-- =============================================================================
-- VYN NUMBER — per-message delete ("unsend")
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================

-- Deletes a single message if the caller is the sender (unsend).
-- Refreshes the conversation's last-message preview afterwards.
CREATE OR REPLACE FUNCTION public.vn_delete_message(
    p_conversation_id uuid,
    p_message_id     uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN
        RAISE EXCEPTION 'VYN NUMBER is not activated';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.vyn_number_conversation_members
        WHERE conversation_id = p_conversation_id AND identity_id = me.id
    ) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;

    DELETE FROM public.vyn_number_messages
    WHERE id = p_message_id
      AND conversation_id = p_conversation_id
      AND sender_identity_id = me.id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'You can only delete your own messages';
    END IF;

    -- Refresh the last-message preview for the conversation.
    UPDATE public.vyn_number_conversations c SET
        last_message_preview = COALESCE((
            SELECT m.message_text FROM public.vyn_number_messages m
            WHERE m.conversation_id = c.id
            ORDER BY m.created_at DESC LIMIT 1
        ), ''),
        last_message_at = (
            SELECT MAX(m.created_at) FROM public.vyn_number_messages m
            WHERE m.conversation_id = c.id
        )
    WHERE c.id = p_conversation_id;
END;
$$;