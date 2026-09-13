-- ======================================================================
-- Personal ID v2: rich messaging (edit, reply, reactions, pin, media),
-- mute/archive, read-receipts, presence, clear history.
-- Additive on top of 202609010000000_personal_id.sql. Run once via SQL Editor.
-- ALL operations stay behind SECURITY DEFINER RPCs; direct table access revoked.
-- ======================================================================

ALTER TABLE public.personal_id_conversations
    ADD COLUMN IF NOT EXISTS is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.personal_id_conversation_members
    ADD COLUMN IF NOT EXISTS is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS read_receipts BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE public.personal_ids
    ADD COLUMN IF NOT EXISTS read_receipts BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS is_online BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.personal_id_messages
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reply_to_id uuid REFERENCES public.personal_id_messages(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS media_name TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS public.personal_id_reactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    message_id uuid NOT NULL REFERENCES public.personal_id_messages(id) ON DELETE CASCADE,
    personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    emoji TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (message_id, personal_id, emoji)
);

ALTER TABLE public.personal_id_reactions ENABLE ROW LEVEL SECURITY;
CREATE INDEX IF NOT EXISTS idx_pid_reactions_msg ON public.personal_id_reactions (message_id);

-- ----------------------------------------------------------------------
-- SEND MESSAGE v2 (text / reply / media)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_send_message_v2(
    p_conversation_id uuid,
    p_message_text TEXT DEFAULT '',
    p_reply_to_id uuid DEFAULT NULL,
    p_media_url TEXT DEFAULT '',
    p_media_type TEXT DEFAULT '',
    p_media_name TEXT DEFAULT ''
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
    me public.personal_ids;
    msg public.personal_id_messages;
    msg_text TEXT;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id
    ) THEN RAISE EXCEPTION 'Not a member of this conversation'; END IF;
    IF COALESCE(btrim(p_message_text), '') = '' AND COALESCE(p_media_url, '') = '' THEN
        RAISE EXCEPTION 'Empty message';
    END IF;
    IF p_reply_to_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.personal_id_messages m
        WHERE m.id = p_reply_to_id AND m.conversation_id = p_conversation_id
    ) THEN RAISE EXCEPTION 'Reply target not found'; END IF;

    msg_text := btrim(p_message_text);
    INSERT INTO public.personal_id_messages (
        conversation_id, sender_personal_id, message_text,
        reply_to_id, media_url, media_type, media_name
    ) VALUES (
        p_conversation_id, me.id, msg_text,
        p_reply_to_id, COALESCE(p_media_url, ''), COALESCE(p_media_type, ''), COALESCE(p_media_name, '')
    )
    RETURNING * INTO msg;

    UPDATE public.personal_id_conversations
    SET last_message_at = msg.created_at,
        last_message_preview = COALESCE(NULLIF(msg.message_text, ''), '[' || msg.media_type || ']')
    WHERE id = p_conversation_id;

    UPDATE public.personal_id_conversation_members
    SET unread_count = unread_count + 1
    WHERE conversation_id = p_conversation_id AND personal_id <> me.id;

    RETURN public.pn_message_json(msg.id, me.id);
END;
$fn$;

-- ----------------------------------------------------------------------
-- Message JSON helper (reply + reactions + edit/read flags)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_message_json(p_message_id uuid, p_viewer_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
    m public.personal_id_messages;
    replied public.personal_id_messages;
    reacts jsonb;
BEGIN
    SELECT * INTO m FROM public.personal_id_messages WHERE id = p_message_id;
    IF NOT FOUND THEN RETURN NULL::jsonb; END IF;

    SELECT * INTO replied FROM public.personal_id_messages WHERE id = m.reply_to_id;

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'emoji', r.emoji,
            'count', r.cnt,
            'reacted_by_me', r.me
        ) ORDER BY r.emoji), '[]'::jsonb)
    INTO reacts
    FROM (
        SELECT emoji, count(*) AS cnt, bool_or(personal_id = p_viewer_id) AS me
        FROM public.personal_id_reactions
        WHERE message_id = p_message_id
        GROUP BY emoji
    ) r;

    RETURN jsonb_build_object(
        'id', m.id,
        'conversation_id', m.conversation_id,
        'is_mine', (m.sender_personal_id = p_viewer_id),
        'message_text', m.message_text,
        'media_url', m.media_url,
        'media_type', m.media_type,
        'media_name', m.media_name,
        'is_read', m.is_read,
        'is_pinned', m.is_pinned,
        'created_at', m.created_at,
        'edited_at', m.edited_at,
        'reply_to_id', m.reply_to_id,
        'reply_text', COALESCE(replied.message_text, ''),
        'reply_is_mine', (replied.sender_personal_id = p_viewer_id),
        'reactions', reacts
    );
END;
$fn$;

-- ----------------------------------------------------------------------
-- MESSAGES (v2: privacy-aware, includes reply + reactions fields)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_messages(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
    me public.personal_ids;
    my_settings public.personal_id_conversation_members;
    result jsonb;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id
    ) THEN RAISE EXCEPTION 'Not a member of this conversation'; END IF;

    SELECT * INTO my_settings FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id;

    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'created_at')), '[]'::jsonb)
    INTO result
    FROM (
        SELECT public.pn_message_json(m.id, me.id) AS row
        FROM public.personal_id_messages m
        WHERE m.conversation_id = p_conversation_id
        ORDER BY m.created_at
    ) sub;

    IF NOT COALESCE(my_settings.read_receipts, TRUE) THEN
        result := (
            SELECT COALESCE(jsonb_agg(
                CASE WHEN (el->>'is_mine')::boolean THEN el ELSE el - 'is_read' END
                ORDER BY ord), '[]'::jsonb)
            FROM (
                SELECT el, row_number() OVER () AS ord
                FROM jsonb_array_elements(result) el
            ) t
        );
    END IF;
    RETURN result;
END;
$fn$;


-- ----------------------------------------------------------------------
-- EDIT MESSAGE (own text only)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_edit_message(p_conversation_id uuid, p_message_id uuid, p_message_text TEXT)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF COALESCE(btrim(p_message_text), '') = '' THEN RAISE EXCEPTION 'Empty message'; END IF;
    UPDATE public.personal_id_messages
    SET message_text = btrim(p_message_text), edited_at = now()
    WHERE id = p_message_id AND conversation_id = p_conversation_id AND sender_personal_id = me.id
      AND media_type = '';
    IF NOT FOUND THEN RAISE EXCEPTION 'You can only edit your own text messages'; END IF;
END;
$fn$;

-- ----------------------------------------------------------------------
-- REACTIONS (toggle)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_toggle_reaction(p_conversation_id uuid, p_message_id uuid, p_emoji TEXT)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.personal_id_messages
        WHERE id = p_message_id AND conversation_id = p_conversation_id) THEN
        RAISE EXCEPTION 'Message not found';
    END IF;
    IF EXISTS (SELECT 1 FROM public.personal_id_reactions
        WHERE message_id = p_message_id AND personal_id = me.id AND emoji = p_emoji) THEN
        DELETE FROM public.personal_id_reactions
        WHERE message_id = p_message_id AND personal_id = me.id AND emoji = p_emoji;
    ELSE
        INSERT INTO public.personal_id_reactions (conversation_id, message_id, personal_id, emoji)
        VALUES (p_conversation_id, p_message_id, me.id, p_emoji);
    END IF;
END;
$fn$;

-- ----------------------------------------------------------------------
-- PIN / UNPIN
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_set_message_pinned(p_conversation_id uuid, p_message_id uuid, p_pinned BOOLEAN)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    UPDATE public.personal_id_messages
    SET is_pinned = p_pinned
    WHERE id = p_message_id AND conversation_id = p_conversation_id
      AND sender_personal_id = me.id;
    IF NOT FOUND THEN RAISE EXCEPTION 'You can only pin your own messages'; END IF;
END;
$fn$;

-- ----------------------------------------------------------------------
-- CHAT SETTINGS (mute / archive / read receipts + peer presence)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_conversation_settings(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE
    me public.personal_ids;
    my_row public.personal_id_conversation_members;
    peer public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    SELECT * INTO my_row FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id;
    IF my_row.conversation_id IS NULL THEN RAISE EXCEPTION 'Not a member of this conversation'; END IF;
    SELECT p.* INTO peer FROM public.personal_id_conversation_members om
        JOIN public.personal_ids p ON p.id = om.personal_id
        WHERE om.conversation_id = p_conversation_id AND om.personal_id <> me.id
        LIMIT 1;
    RETURN jsonb_build_object(
        'muted', COALESCE(my_row.is_muted, FALSE),
        'archived', COALESCE(my_row.is_archived, FALSE),
        'read_receipts', COALESCE(my_row.read_receipts, TRUE),
        'peer_online', COALESCE(peer.is_online, FALSE),
        'peer_last_seen', peer.last_seen_at
    );
END;
$fn$;

CREATE OR REPLACE FUNCTION public.pn_update_conversation_settings(
    p_conversation_id uuid, p_muted BOOLEAN, p_archived BOOLEAN)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    UPDATE public.personal_id_conversation_members
    SET is_muted = COALESCE(p_muted, is_muted),
        is_archived = COALESCE(p_archived, is_archived)
    WHERE conversation_id = p_conversation_id AND personal_id = me.id;
    UPDATE public.personal_id_conversations c SET
        is_muted = COALESCE((SELECT bool_or(is_muted) FROM public.personal_id_conversation_members
            WHERE conversation_id = c.id), FALSE),
        is_archived = COALESCE((SELECT bool_or(is_archived) FROM public.personal_id_conversation_members
            WHERE conversation_id = c.id), FALSE)
    WHERE c.id = p_conversation_id;
END;
$fn$;

-- ----------------------------------------------------------------------
-- PRIVACY (global read-receipts toggle)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_update_privacy(p_read_receipts BOOLEAN)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    UPDATE public.personal_ids SET read_receipts = p_read_receipts WHERE id = me.id;
    UPDATE public.personal_id_conversation_members
    SET read_receipts = p_read_receipts WHERE personal_id = me.id;
END;
$fn$;

-- ----------------------------------------------------------------------
-- PRESENCE
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_touch_presence()
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RETURN; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN; END IF;
    UPDATE public.personal_ids SET is_online = TRUE, last_seen_at = now() WHERE id = me.id;
END;
$fn$;

-- ----------------------------------------------------------------------
-- INBOX (honors archived + mute flags)
-- ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_clear_history(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id) THEN
        RAISE EXCEPTION 'Not a member of this conversation';
    END IF;
    DELETE FROM public.personal_id_messages WHERE conversation_id = p_conversation_id;
    DELETE FROM public.personal_id_reactions WHERE conversation_id = p_conversation_id;
    UPDATE public.personal_id_conversation_members
    SET unread_count = 0 WHERE conversation_id = p_conversation_id;
    UPDATE public.personal_id_conversations c SET
        last_message_preview = COALESCE((SELECT m.message_text FROM public.personal_id_messages m
            WHERE m.conversation_id = c.id ORDER BY m.created_at DESC LIMIT 1), ''),
        last_message_at = (SELECT MAX(m.created_at) FROM public.personal_id_messages m
            WHERE m.conversation_id = c.id)
    WHERE c.id = p_conversation_id;
END;
$fn$;

CREATE OR REPLACE FUNCTION public.pn_inbox()
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE
    me public.personal_ids;
    result jsonb;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN '[]'::jsonb; END IF;
    SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'is_archived')::boolean ASC, (row->>'last_message_at') DESC NULLS LAST), '[]'::jsonb)
    INTO result
    FROM (
        SELECT jsonb_build_object(
            'conversation_id', c.id,
            'peer_username', peer.username,
            'peer_avatar_url', peer.avatar_url,
            'last_message_preview', c.last_message_preview,
            'last_message_at', c.last_message_at,
            'unread_count', mm.unread_count,
            'is_muted', COALESCE(mm.is_muted, FALSE),
            'is_archived', COALESCE(mm.is_archived, FALSE)
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
$fn$;

-- ----------------------------------------------------------------------
-- MEDIA: private bucket + member-scoped RLS
-- ----------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
SELECT 'personal-id-media', 'Personal ID media', FALSE, 52428800, ARRAY['image/*','audio/*','video/*','application/pdf','application/octet-stream']
WHERE NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'personal-id-media');

DROP POLICY IF EXISTS "personal_id_media_select_member" ON storage.objects;
CREATE POLICY "personal_id_media_select_member"
    ON storage.objects FOR SELECT USING (
        bucket_id = 'personal-id-media'
        AND auth.role() = 'authenticated'
        AND EXISTS (
            SELECT 1 FROM public.personal_id_conversation_members mm
            WHERE mm.conversation_id = ((storage.foldername(name))[1])::uuid
              AND mm.personal_id = (public.pn_me()).id
        )
    );

DROP POLICY IF EXISTS "personal_id_media_insert_member" ON storage.objects;
CREATE POLICY "personal_id_media_insert_member"
    ON storage.objects FOR INSERT WITH CHECK (
        bucket_id = 'personal-id-media'
        AND auth.role() = 'authenticated'
        AND EXISTS (
            SELECT 1 FROM public.personal_id_conversation_members mm
            WHERE mm.conversation_id = ((storage.foldername(name))[1])::uuid
              AND mm.personal_id = (public.pn_me()).id
        )
    );

-- ----------------------------------------------------------------------
-- Realtime + grants + isolation
-- ----------------------------------------------------------------------
DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public' AND tablename = 'personal_id_reactions') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.personal_id_reactions;
    END IF;
END $do$;

REVOKE ALL ON public.personal_id_reactions FROM anon, authenticated;

GRANT EXECUTE ON FUNCTION
    public.pn_send_message_v2(uuid, text, uuid, text, text, text),
    public.pn_edit_message(uuid, uuid, text),
    public.pn_toggle_reaction(uuid, uuid, text),
    public.pn_set_message_pinned(uuid, uuid, boolean),
    public.pn_conversation_settings(uuid),
    public.pn_update_conversation_settings(uuid, boolean, boolean),
    public.pn_update_privacy(boolean),
    public.pn_touch_presence(),
    public.pn_clear_history(uuid),
    public.pn_messages(uuid),
    public.pn_inbox()
TO authenticated;

