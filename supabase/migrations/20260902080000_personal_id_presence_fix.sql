-- =============================================================================
-- Personal ID presence fix: peer_online was computed from the is_online flag,
-- which clients only ever set to TRUE and never clear — so a peer could show
-- as "online" forever even after leaving the app.
-- Fix: derive online status from last_seen_at (heartbeat within 70 seconds),
-- matching the shared client-side Presence.ONLINE_WINDOW_MS = 70s logic.
-- =============================================================================

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
        'peer_online', peer.last_seen_at IS NOT NULL
            AND peer.last_seen_at > now() - interval '70 seconds',
        'peer_last_seen', peer.last_seen_at
    );
END;
$fn$;
