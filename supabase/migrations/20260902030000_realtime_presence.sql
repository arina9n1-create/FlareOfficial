-- ==============================================================================
-- REALTIME PRESENCE (social app_users + VYN NUMBER identities)
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
-- ==============================================================================
-- Clients touch their presence periodically; peers read last_seen_at and treat
-- anything within the last 70 seconds as "online".

-- 1) SOCIAL: presence columns on app_users -------------------------------------
ALTER TABLE public.app_users
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION public.social_touch_presence()
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    UPDATE public.app_users
    SET last_seen_at = now()
    WHERE uid = auth.uid()::text;
END;
$$;

-- Batch presence lookup by handle (SECURITY DEFINER so private profiles can
-- still share presence with their friends; only handle + last_seen are exposed).
CREATE OR REPLACE FUNCTION public.social_presence(p_handles TEXT[])
RETURNS TABLE (handle TEXT, last_seen TIMESTAMPTZ)
LANGUAGE sql SECURITY DEFINER SET search_path = public
AS $$
    SELECT LOWER(u.handle) AS handle, u.last_seen_at
    FROM public.app_users u
    WHERE LOWER(u.handle) = ANY (SELECT LOWER(h) FROM unnest(p_handles) AS h);
$$;

-- 2) VYN NUMBER: presence columns on identities --------------------------------
ALTER TABLE public.vyn_number_identities
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION public.vn_touch_presence()
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    me RECORD;
BEGIN
    SELECT * INTO me FROM public.vn_me();
    IF me.id IS NULL THEN RETURN; END IF;
    UPDATE public.vyn_number_identities
    SET last_seen_at = now()
    WHERE id = me.id;
END;
$$;

-- Peer presence for a conversation the caller is a member of.
CREATE OR REPLACE FUNCTION public.vn_conversation_presence(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    me RECORD;
    peer RECORD;
BEGIN
    SELECT * INTO me FROM public.vn_me();
    IF me.id IS NULL THEN RETURN jsonb_build_object('peer_last_seen', NULL); END IF;

    SELECT i.* INTO peer
    FROM public.vyn_number_conversation_members m
    JOIN public.vyn_number_identities i ON i.id = m.identity_id
    WHERE m.conversation_id = p_conversation_id
      AND m.identity_id <> me.id
    LIMIT 1;

    IF peer.id IS NULL THEN RETURN jsonb_build_object('peer_last_seen', NULL); END IF;
    RETURN jsonb_build_object('peer_last_seen', peer.last_seen_at);
END;
$$;

-- 3) GRANTS --------------------------------------------------------------------
GRANT EXECUTE ON FUNCTION public.social_touch_presence() TO authenticated;
GRANT EXECUTE ON FUNCTION public.social_presence(TEXT[]) TO authenticated;
GRANT EXECUTE ON FUNCTION public.vn_touch_presence() TO authenticated;
GRANT EXECUTE ON FUNCTION public.vn_conversation_presence(uuid) TO authenticated;
