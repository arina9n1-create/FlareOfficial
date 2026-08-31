-- =============================================================================
-- VYN NUMBER — Chat settings (pin, mute, archive, favorites, block)
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================

-- 1) Add settings columns to conversation_members
ALTER TABLE public.vyn_number_conversation_members
    ADD COLUMN IF NOT EXISTS is_pinned    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_muted     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_archived  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_favorite  BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Pin / Unpin
CREATE OR REPLACE FUNCTION public.vn_toggle_pin(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    UPDATE public.vyn_number_conversation_members
    SET is_pinned = NOT is_pinned
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
END;
$$;

-- 3) Mute / Unmute
CREATE OR REPLACE FUNCTION public.vn_toggle_mute(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    UPDATE public.vyn_number_conversation_members
    SET is_muted = NOT is_muted
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
END;
$$;

-- 4) Archive / Unarchive
CREATE OR REPLACE FUNCTION public.vn_toggle_archive(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    UPDATE public.vyn_number_conversation_members
    SET is_archived = NOT is_archived
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
END;
$$;

-- 5) Favorite / Unfavorite
CREATE OR REPLACE FUNCTION public.vn_toggle_favorite(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    UPDATE public.vyn_number_conversation_members
    SET is_favorite = NOT is_favorite
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
END;
$$;

-- 6) Mark as unread
CREATE OR REPLACE FUNCTION public.vn_mark_unread(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    UPDATE public.vyn_number_conversation_members
    SET unread_count = GREATEST(unread_count, 1)
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
END;
$$;

-- 7) Clear chat history
CREATE OR REPLACE FUNCTION public.vn_clear_history(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.vyn_number_conversation_members
        WHERE conversation_id = p_conversation_id AND identity_id = me.id
    ) THEN RAISE EXCEPTION 'Not a member'; END IF;
    DELETE FROM public.vyn_number_messages WHERE conversation_id = p_conversation_id;
    UPDATE public.vyn_number_conversations
    SET last_message_preview = '', last_message_at = NULL
    WHERE id = p_conversation_id;
END;
$$;

-- 8) Delete conversation
CREATE OR REPLACE FUNCTION public.vn_delete_conversation(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    DELETE FROM public.vyn_number_conversation_members
    WHERE conversation_id = p_conversation_id AND identity_id = me.id;
END;
$$;

-- 9) Block user
CREATE OR REPLACE FUNCTION public.vn_block_user(p_peer_phone TEXT)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
    peer public.vyn_number_identities;
    v_norm TEXT;
    conv RECORD;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    v_norm := public.vn_normalize_phone(p_peer_phone);
    IF v_norm IS NULL THEN RAISE EXCEPTION 'Invalid phone number'; END IF;
    SELECT * INTO peer FROM public.vyn_number_identities
    WHERE phone = v_norm AND verification_status = 'VERIFIED' LIMIT 1;
    IF peer.id IS NULL THEN RAISE EXCEPTION 'User not found'; END IF;
    FOR conv IN
        SELECT c.id FROM public.vyn_number_conversations c
        JOIN public.vyn_number_conversation_members a ON a.conversation_id = c.id AND a.identity_id = me.id
        JOIN public.vyn_number_conversation_members b ON b.conversation_id = c.id AND b.identity_id = peer.id
        WHERE NOT c.is_group
    LOOP
        DELETE FROM public.vyn_number_messages WHERE conversation_id = conv.id;
        DELETE FROM public.vyn_number_conversation_members WHERE conversation_id = conv.id;
        DELETE FROM public.vyn_number_conversations WHERE id = conv.id;
    END LOOP;
END;
$$;

-- 10) Updated inbox to include settings (pinned first, then by last message)
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
    SELECT COALESCE(jsonb_agg(row ORDER BY
        (row->>'is_pinned') DESC,
        (row->>'last_message_at') DESC NULLS LAST
    ), '[]'::jsonb)
    INTO result
    FROM (
        SELECT jsonb_build_object(
            'conversation_id', c.id,
            'peer_phone', peer.phone,
            'peer_name', peer.display_name,
            'last_message_preview', c.last_message_preview,
            'last_message_at', c.last_message_at,
            'unread_count', mm.unread_count,
            'is_pinned', mm.is_pinned,
            'is_muted', mm.is_muted,
            'is_archived', mm.is_archived,
            'is_favorite', mm.is_favorite
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

-- 11) Report user (logs report for moderation)
CREATE TABLE IF NOT EXISTS public.vyn_number_reports (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     uuid NOT NULL REFERENCES public.vyn_number_identities(id) ON DELETE CASCADE,
    reported_phone  TEXT NOT NULL,
    reason          TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION public.vn_report_user(p_peer_phone TEXT, p_reason TEXT DEFAULT '')
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    me public.vyn_number_identities;
BEGIN
    me := public.vn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'VYN NUMBER is not activated'; END IF;
    INSERT INTO public.vyn_number_reports (reporter_id, reported_phone, reason)
    VALUES (me.id, public.vn_normalize_phone(p_peer_phone), COALESCE(p_reason, ''));
END;
$$;