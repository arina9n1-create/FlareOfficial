-- =============================================================================
-- MASTER SCHEMA: Personal ID + Social Core Fixes
-- Includes: Personal ID (v2), Social Presence, and System Cleanup.
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- =============================================================================

-- 1) CORE TABLES (Personal ID) ------------------------------------------------
CREATE TABLE IF NOT EXISTS public.personal_ids (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    username        TEXT NOT NULL,
    avatar_url      TEXT,
    read_receipts   BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at    TIMESTAMPTZ,
    is_online       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_ids_user ON public.personal_ids (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_ids_username_lower ON public.personal_ids (lower(username));
CREATE INDEX IF NOT EXISTS idx_personal_ids_username_prefix ON public.personal_ids (lower(username) text_pattern_ops);

-- 2) CONVERSATIONS & MEMBERS (Personal ID) -------------------------------------
CREATE TABLE IF NOT EXISTS public.personal_id_conversations (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at      TIMESTAMPTZ,
    last_message_preview TEXT NOT NULL DEFAULT '',
    is_muted             BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS public.personal_id_conversation_members (
    conversation_id uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    personal_id     uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    unread_count     INTEGER NOT NULL DEFAULT 0,
    last_read_at     TIMESTAMPTZ,
    is_muted         BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived      BOOLEAN NOT NULL DEFAULT FALSE,
    read_receipts    BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (conversation_id, personal_id)
);

-- 3) MESSAGES & REACTIONS (Personal ID) ---------------------------------------
CREATE TABLE IF NOT EXISTS public.personal_id_messages (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    sender_personal_id  uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    message_text        TEXT NOT NULL DEFAULT '',
    media_url           TEXT NOT NULL DEFAULT '',
    media_type          TEXT NOT NULL DEFAULT '',
    media_name          TEXT NOT NULL DEFAULT '',
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned           BOOLEAN NOT NULL DEFAULT FALSE,
    reply_to_id         uuid REFERENCES public.personal_id_messages(id) ON DELETE SET NULL,
    edited_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.personal_id_reactions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    message_id      uuid NOT NULL REFERENCES public.personal_id_messages(id) ON DELETE CASCADE,
    personal_id     uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    emoji           TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (message_id, personal_id, emoji)
);

-- 4) BLOCKS & SIGNALING (Personal ID) -----------------------------------------
CREATE TABLE IF NOT EXISTS public.personal_id_blocks (
    blocker_personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    blocked_personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_personal_id, blocked_personal_id)
);

CREATE TABLE IF NOT EXISTS public.personal_id_call_signals (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      uuid NOT NULL REFERENCES public.personal_id_conversations(id) ON DELETE CASCADE,
    caller_personal_id   uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    receiver_personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    call_type            TEXT NOT NULL DEFAULT 'VIDEO',
    status               TEXT NOT NULL DEFAULT 'OFFERING',
    sdp                  TEXT,
    timestamp            BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- SOCIAL CORE FUNCTIONS (Presence & System)
-- =============================================================================

-- Social Presence Heartbeat.
CREATE OR REPLACE FUNCTION public.social_touch_presence()
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    UPDATE public.app_users SET last_seen_at = now() WHERE uid = auth.uid()::text;
END;
$$;

-- Batch presence lookup.
CREATE OR REPLACE FUNCTION public.social_presence(p_handles TEXT[])
RETURNS TABLE (handle TEXT, last_seen TIMESTAMPTZ) LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT LOWER(u.handle) AS handle, u.last_seen_at FROM public.app_users u
    WHERE LOWER(u.handle) = ANY (SELECT LOWER(h) FROM unnest(p_handles) AS h);
$$;

-- Cleanup Corrupted Posts (System Maintenance).
CREATE OR REPLACE FUNCTION public.cleanup_corrupted_posts()
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    DELETE FROM public.posts WHERE video_url IS NULL OR video_url = '' OR video_url = 'undefined';
END;
$$;

-- =============================================================================
-- PERSONAL ID RPCs
-- =============================================================================

-- Helpers.
CREATE OR REPLACE FUNCTION public.pn_me() RETURNS public.personal_ids LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE rec public.personal_ids; BEGIN SELECT * INTO rec FROM public.personal_ids WHERE user_id = auth.uid() LIMIT 1; RETURN rec; END; $$;

CREATE OR REPLACE FUNCTION public.pn_is_member(p_conversation_id uuid) RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN IF auth.uid() IS NULL THEN RETURN FALSE; END IF; RETURN EXISTS (SELECT 1 FROM public.personal_id_conversation_members WHERE conversation_id = p_conversation_id AND personal_id = (public.pn_me()).id); END; $$;

CREATE OR REPLACE FUNCTION public.pn_normalize_username(p_username TEXT) RETURNS TEXT LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE u TEXT := lower(btrim(COALESCE(p_username, ''))); BEGIN IF u ~ '^[a-z][a-z0-9_]{2,19}$' THEN RETURN u; END IF; RETURN NULL; END; $$;

-- RPCs.
CREATE OR REPLACE FUNCTION public.pn_search(p_query TEXT) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE result jsonb; BEGIN IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF; IF COALESCE(btrim(p_query), '') = '' THEN RETURN '[]'::jsonb; END IF; SELECT COALESCE(jsonb_agg(row), '[]'::jsonb) INTO result FROM (SELECT jsonb_build_object('username', pid.username, 'avatar_url', pid.avatar_url, 'has_avatar', (pid.avatar_url IS NOT NULL AND pid.avatar_url <> '')) AS row FROM public.personal_ids pid WHERE lower(pid.username) LIKE lower('%' || btrim(p_query) || '%') AND pid.user_id <> auth.uid() ORDER BY lower(pid.username) LIMIT 20) sub; RETURN result; END; $$;

CREATE OR REPLACE FUNCTION public.pn_create(p_username TEXT) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_norm TEXT; rec public.personal_ids; BEGIN IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF; v_norm := public.pn_normalize_username(p_username); IF v_norm IS NULL THEN RAISE EXCEPTION 'Invalid username format'; END IF; IF EXISTS (SELECT 1 FROM public.personal_ids WHERE lower(username) = v_norm) THEN RAISE EXCEPTION 'Taken'; END IF; IF EXISTS (SELECT 1 FROM public.personal_ids WHERE user_id = auth.uid()) THEN RAISE EXCEPTION 'Limit reached'; END IF; INSERT INTO public.personal_ids (user_id, username) VALUES (auth.uid(), v_norm) RETURNING * INTO rec; RETURN jsonb_build_object('id', rec.id, 'username', rec.username, 'avatar_url', rec.avatar_url); END; $$;

CREATE OR REPLACE FUNCTION public.pn_message_json(p_message_id uuid, p_viewer_id uuid) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE m public.personal_id_messages; replied public.personal_id_messages; reacts jsonb; BEGIN SELECT * INTO m FROM public.personal_id_messages WHERE id = p_message_id; IF NOT FOUND THEN RETURN NULL::jsonb; END IF; SELECT * INTO replied FROM public.personal_id_messages WHERE id = m.reply_to_id; SELECT COALESCE(jsonb_agg(jsonb_build_object('emoji', r.emoji, 'count', r.cnt, 'reacted_by_me', r.me) ORDER BY r.emoji), '[]'::jsonb) INTO reacts FROM (SELECT emoji, count(*) AS cnt, bool_or(personal_id = p_viewer_id) AS me FROM public.personal_id_reactions WHERE message_id = p_message_id GROUP BY emoji) r; RETURN jsonb_build_object('id', m.id, 'conversation_id', m.conversation_id, 'is_mine', (m.sender_personal_id = p_viewer_id), 'message_text', m.message_text, 'media_url', m.media_url, 'media_type', m.media_type, 'media_name', m.media_name, 'is_read', m.is_read, 'is_pinned', m.is_pinned, 'created_at', m.created_at, 'edited_at', m.edited_at, 'reply_to_id', m.reply_to_id, 'reply_text', COALESCE(replied.message_text, ''), 'reply_is_mine', (replied.sender_personal_id = p_viewer_id), 'reactions', reacts); END; $$;

CREATE OR REPLACE FUNCTION public.pn_send_message_v2(p_conversation_id uuid, p_message_text TEXT DEFAULT '', p_reply_to_id uuid DEFAULT NULL, p_media_url TEXT DEFAULT '', p_media_type TEXT DEFAULT '', p_media_name TEXT DEFAULT '') RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; msg public.personal_id_messages; BEGIN IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF; me := public.pn_me(); IF me.id IS NULL THEN RAISE EXCEPTION 'Create a Personal ID first'; END IF; IF NOT public.pn_is_member(p_conversation_id) THEN RAISE EXCEPTION 'Forbidden'; END IF; INSERT INTO public.personal_id_messages (conversation_id, sender_personal_id, message_text, reply_to_id, media_url, media_type, media_name) VALUES (p_conversation_id, me.id, btrim(p_message_text), p_reply_to_id, COALESCE(p_media_url, ''), COALESCE(p_media_type, ''), COALESCE(p_media_name, '')) RETURNING * INTO msg; UPDATE public.personal_id_conversations SET last_message_at = msg.created_at, last_message_preview = COALESCE(NULLIF(msg.message_text, ''), '[' || msg.media_type || ']') WHERE id = p_conversation_id; UPDATE public.personal_id_conversation_members SET unread_count = unread_count + 1 WHERE conversation_id = p_conversation_id AND personal_id <> me.id; RETURN public.pn_message_json(msg.id, me.id); END; $$;

CREATE OR REPLACE FUNCTION public.pn_messages(p_conversation_id uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; my_settings public.personal_id_conversation_members; result jsonb; BEGIN me := public.pn_me(); IF NOT public.pn_is_member(p_conversation_id) THEN RAISE EXCEPTION 'Forbidden'; END IF; SELECT * INTO my_settings FROM public.personal_id_conversation_members WHERE conversation_id = p_conversation_id AND personal_id = me.id; SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'created_at')), '[]'::jsonb) INTO result FROM (SELECT public.pn_message_json(m.id, me.id) AS row FROM public.personal_id_messages m WHERE m.conversation_id = p_conversation_id ORDER BY m.created_at) sub; IF NOT COALESCE(my_settings.read_receipts, TRUE) THEN result := (SELECT COALESCE(jsonb_agg(CASE WHEN (el->>'is_mine')::boolean THEN el ELSE el - 'is_read' END ORDER BY ord), '[]'::jsonb) FROM (SELECT el, row_number() OVER () AS ord FROM jsonb_array_elements(result) el) t); END IF; RETURN result; END; $$;

CREATE OR REPLACE FUNCTION public.pn_touch_presence() RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN IF auth.uid() IS NULL THEN RETURN; END IF; UPDATE public.personal_ids SET is_online = TRUE, last_seen_at = now() WHERE user_id = auth.uid(); END; $$;

CREATE OR REPLACE FUNCTION public.pn_conversation_settings(p_conversation_id uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; my_row public.personal_id_conversation_members; peer public.personal_ids; BEGIN me := public.pn_me(); SELECT * INTO my_row FROM public.personal_id_conversation_members WHERE conversation_id = p_conversation_id AND personal_id = me.id; IF my_row.conversation_id IS NULL THEN RAISE EXCEPTION 'Forbidden'; END IF; SELECT p.* INTO peer FROM public.personal_id_conversation_members om JOIN public.personal_ids p ON p.id = om.personal_id WHERE om.conversation_id = p_conversation_id AND om.personal_id <> me.id LIMIT 1; RETURN jsonb_build_object('muted', COALESCE(my_row.is_muted, FALSE), 'archived', COALESCE(my_row.is_archived, FALSE), 'read_receipts', COALESCE(my_row.read_receipts, TRUE), 'peer_online', peer.last_seen_at IS NOT NULL AND peer.last_seen_at > now() - interval '70 seconds', 'peer_last_seen', peer.last_seen_at); END; $$;

CREATE OR REPLACE FUNCTION public.pn_inbox() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; result jsonb; BEGIN me := public.pn_me(); IF me.id IS NULL THEN RETURN '[]'::jsonb; END IF; SELECT COALESCE(jsonb_agg(row ORDER BY (row->>'is_archived')::boolean ASC, (row->>'last_message_at') DESC NULLS LAST), '[]'::jsonb) INTO result FROM (SELECT jsonb_build_object('conversation_id', c.id, 'peer_username', peer.username, 'peer_avatar_url', peer.avatar_url, 'last_message_preview', c.last_message_preview, 'last_message_at', c.last_message_at, 'unread_count', mm.unread_count, 'is_muted', COALESCE(mm.is_muted, FALSE), 'is_archived', COALESCE(mm.is_archived, FALSE)) AS row FROM public.personal_id_conversations c JOIN public.personal_id_conversation_members mm ON mm.conversation_id = c.id AND mm.personal_id = me.id JOIN public.personal_id_conversation_members om ON om.conversation_id = c.id AND om.personal_id <> me.id JOIN public.personal_ids peer ON peer.id = om.personal_id) sub; RETURN result; END; $$;

-- Signaling RPCs.
CREATE OR REPLACE FUNCTION public.pn_call_initiate(p_conversation_id uuid, p_call_type TEXT DEFAULT 'VIDEO') RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; peer_id uuid; sig public.personal_id_call_signals; BEGIN me := public.pn_me(); SELECT personal_id INTO peer_id FROM public.personal_id_conversation_members WHERE conversation_id = p_conversation_id AND personal_id <> me.id LIMIT 1; INSERT INTO public.personal_id_call_signals (conversation_id, caller_personal_id, receiver_personal_id, call_type, status) VALUES (p_conversation_id, me.id, peer_id, COALESCE(p_call_type, 'VIDEO'), 'OFFERING') RETURNING * INTO sig; RETURN jsonb_build_object('id', sig.id, 'conversation_id', sig.conversation_id, 'call_type', sig.call_type, 'status', sig.status, 'timestamp', sig.timestamp); END; $$;

CREATE OR REPLACE FUNCTION public.pn_call_poll(p_conversation_id uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN RETURN (SELECT COALESCE(jsonb_agg(row ORDER BY timestamp DESC), '[]'::jsonb) FROM (SELECT jsonb_build_object('id', id, 'caller_personal_id', caller_personal_id, 'receiver_personal_id', receiver_personal_id, 'call_type', call_type, 'status', status, 'sdp', sdp, 'timestamp', timestamp) AS row FROM public.personal_id_call_signals WHERE conversation_id = p_conversation_id AND timestamp > (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT - 3600000 LIMIT 5) s); END; $$;

CREATE OR REPLACE FUNCTION public.pn_call_update(p_call_id uuid, p_sdp TEXT DEFAULT NULL, p_status TEXT DEFAULT NULL) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN UPDATE public.personal_id_call_signals SET sdp = COALESCE(p_sdp, sdp), status = COALESCE(p_status, status) WHERE id = p_call_id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_call_answer(p_call_id uuid, p_sdp TEXT) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN UPDATE public.personal_id_call_signals SET status = 'ACCEPTED', sdp = p_sdp WHERE id = p_call_id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_call_reject(p_call_id uuid) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN UPDATE public.personal_id_call_signals SET status = 'REJECTED' WHERE id = p_call_id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_call_end(p_call_id uuid) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN UPDATE public.personal_id_call_signals SET status = 'ENDED' WHERE id = p_call_id; END; $$;

-- Messaging Utilities (Mark Read, Edit, Reactions, Pin, Clear, Open).
CREATE OR REPLACE FUNCTION public.pn_mark_read(p_conversation_id uuid) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); UPDATE public.personal_id_conversation_members SET unread_count = 0, last_read_at = now() WHERE conversation_id = p_conversation_id AND personal_id = me.id; UPDATE public.personal_id_messages SET is_read = TRUE WHERE conversation_id = p_conversation_id AND sender_personal_id <> me.id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_edit_message(p_conversation_id uuid, p_message_id uuid, p_message_text TEXT) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); UPDATE public.personal_id_messages SET message_text = btrim(p_message_text), edited_at = now() WHERE id = p_message_id AND conversation_id = p_conversation_id AND sender_personal_id = me.id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_toggle_reaction(p_conversation_id uuid, p_message_id uuid, p_emoji TEXT) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); IF EXISTS (SELECT 1 FROM public.personal_id_reactions WHERE message_id = p_message_id AND personal_id = me.id AND emoji = p_emoji) THEN DELETE FROM public.personal_id_reactions WHERE message_id = p_message_id AND personal_id = me.id AND emoji = p_emoji; ELSE INSERT INTO public.personal_id_reactions (conversation_id, message_id, personal_id, emoji) VALUES (p_conversation_id, p_message_id, me.id, p_emoji); END IF; END; $$;

CREATE OR REPLACE FUNCTION public.pn_set_message_pinned(p_conversation_id uuid, p_message_id uuid, p_pinned BOOLEAN) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); UPDATE public.personal_id_messages SET is_pinned = p_pinned WHERE id = p_message_id AND conversation_id = p_conversation_id AND sender_personal_id = me.id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_clear_history(p_conversation_id uuid) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN IF NOT public.pn_is_member(p_conversation_id) THEN RETURN; END IF; DELETE FROM public.personal_id_messages WHERE conversation_id = p_conversation_id; DELETE FROM public.personal_id_reactions WHERE conversation_id = p_conversation_id; UPDATE public.personal_id_conversation_members SET unread_count = 0 WHERE conversation_id = p_conversation_id; UPDATE public.personal_id_conversations SET last_message_preview = '', last_message_at = NULL WHERE id = p_conversation_id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_open_conversation(p_username TEXT) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; peer public.personal_ids; conv_id uuid; BEGIN me := public.pn_me(); SELECT * INTO peer FROM public.personal_ids WHERE lower(username) = lower(p_username) LIMIT 1; IF peer.id IS NULL THEN RAISE EXCEPTION 'Not found'; END IF; SELECT conversation_id INTO conv_id FROM public.personal_id_conversation_members WHERE personal_id = me.id AND conversation_id IN (SELECT conversation_id FROM public.personal_id_conversation_members WHERE personal_id = peer.id) LIMIT 1; IF conv_id IS NULL THEN INSERT INTO public.personal_id_conversations DEFAULT VALUES RETURNING id INTO conv_id; INSERT INTO public.personal_id_conversation_members (conversation_id, personal_id) VALUES (conv_id, me.id), (conv_id, peer.id); END IF; RETURN jsonb_build_object('conversation_id', conv_id, 'peer_username', peer.username, 'peer_avatar_url', peer.avatar_url); END; $$;

CREATE OR REPLACE FUNCTION public.pn_update_conversation_settings(p_conversation_id uuid, p_muted BOOLEAN, p_archived BOOLEAN) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); UPDATE public.personal_id_conversation_members SET is_muted = COALESCE(p_muted, is_muted), is_archived = COALESCE(p_archived, is_archived) WHERE conversation_id = p_conversation_id AND personal_id = me.id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_update_privacy(p_read_receipts BOOLEAN) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); UPDATE public.personal_ids SET read_receipts = p_read_receipts WHERE id = me.id; UPDATE public.personal_id_conversation_members SET read_receipts = p_read_receipts WHERE personal_id = me.id; END; $$;

CREATE OR REPLACE FUNCTION public.pn_toggle_block(p_username TEXT, p_blocked BOOLEAN) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; target public.personal_ids; BEGIN me := public.pn_me(); SELECT * INTO target FROM public.personal_ids WHERE lower(username) = lower(p_username); IF target.id IS NULL OR target.id = me.id THEN RETURN; END IF; IF p_blocked THEN INSERT INTO public.personal_id_blocks VALUES (me.id, target.id) ON CONFLICT DO NOTHING; ELSE DELETE FROM public.personal_id_blocks WHERE blocker_personal_id = me.id AND blocked_personal_id = target.id; END IF; END; $$;

CREATE OR REPLACE FUNCTION public.pn_blocked_list() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me public.personal_ids; BEGIN me := public.pn_me(); RETURN (SELECT COALESCE(jsonb_agg(p.username ORDER BY p.username), '[]'::jsonb) FROM public.personal_id_blocks bl JOIN public.personal_ids p ON p.id = bl.blocked_personal_id WHERE bl.blocker_personal_id = me.id); END; $$;

-- Realtime Publication Fix.
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN CREATE PUBLICATION supabase_realtime; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'personal_id_messages') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.personal_id_messages; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'personal_id_call_signals') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.personal_id_call_signals; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'personal_id_reactions') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.personal_id_reactions; END IF;
END $$;

-- Storage Policies.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types) SELECT 'personal-id-media', 'Personal ID media', FALSE, 52428800, ARRAY['image/*','audio/*','video/*','application/pdf','application/octet-stream'] WHERE NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'personal-id-media');
DROP POLICY IF EXISTS "personal_id_media_select_member" ON storage.objects; CREATE POLICY "personal_id_media_select_member" ON storage.objects FOR SELECT USING (bucket_id = 'personal-id-media' AND auth.role() = 'authenticated' AND public.pn_is_member(((storage.foldername(name))[1])::uuid));
DROP POLICY IF EXISTS "personal_id_media_insert_member" ON storage.objects; CREATE POLICY "personal_id_media_insert_member" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'personal-id-media' AND auth.role() = 'authenticated' AND public.pn_is_member(((storage.foldername(name))[1])::uuid));

-- Final Permissions.
REVOKE ALL ON public.personal_ids, public.personal_id_conversations, public.personal_id_conversation_members, public.personal_id_messages, public.personal_id_reactions, public.personal_id_blocks, public.personal_id_call_signals FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.pn_me(), public.pn_search(text), public.pn_create(text), public.pn_inbox(), public.pn_send_message_v2(uuid, text, uuid, text, text, text), public.pn_messages(uuid), public.pn_touch_presence(), public.pn_conversation_settings(uuid), public.pn_toggle_block(text, boolean), public.pn_blocked_list(), public.pn_mark_read(uuid), public.pn_edit_message(uuid, uuid, text), public.pn_toggle_reaction(uuid, uuid, text), public.pn_open_conversation(text), public.pn_clear_history(uuid), public.pn_call_initiate(uuid, text), public.pn_call_poll(uuid), public.pn_call_update(uuid, text, text), public.pn_call_answer(uuid, text), public.pn_call_reject(uuid), public.pn_call_end(uuid), public.pn_set_message_pinned(uuid, uuid, boolean), public.pn_update_conversation_settings(uuid, boolean, boolean), public.pn_update_privacy(boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.social_touch_presence(), public.social_presence(text[]), public.cleanup_corrupted_posts() TO authenticated;
