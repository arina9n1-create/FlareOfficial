-- =============================================================================
-- PERSONAL ID — Block / Unblock support
-- Run this whole file in: Supabase Dashboard -> SQL Editor -> New query -> Run
--   * personal_id_blocks: who blocked whom (per Personal ID, NOT FlareOfficial account)
--   * pn_toggle_block / pn_blocked_list: SECURITY DEFINER RPCs used by the app
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.personal_id_blocks (
    blocker_personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    blocked_personal_id uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_personal_id, blocked_personal_id)
);

ALTER TABLE public.personal_id_blocks ENABLE ROW LEVEL SECURITY;
-- No direct policies: every access goes through the SECURITY DEFINER RPCs below.

-- ---------------------------------------------------------------------------
-- pn_toggle_block(p_username, p_blocked): block or unblock a Personal ID
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_toggle_block(
    p_username TEXT,
    p_blocked BOOLEAN
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $fn$
DECLARE
    me public.personal_ids;
    target public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RAISE EXCEPTION 'No Personal ID'; END IF;

    SELECT * INTO target FROM public.personal_ids WHERE username = lower(p_username);
    IF target.id IS NULL THEN RAISE EXCEPTION 'Personal ID not found'; END IF;
    IF target.id = me.id THEN RAISE EXCEPTION 'You cannot block yourself'; END IF;

    IF p_blocked THEN
        INSERT INTO public.personal_id_blocks (blocker_personal_id, blocked_personal_id)
        VALUES (me.id, target.id)
        ON CONFLICT (blocker_personal_id, blocked_personal_id) DO NOTHING;
    ELSE
        DELETE FROM public.personal_id_blocks
        WHERE blocker_personal_id = me.id AND blocked_personal_id = target.id;
    END IF;
END;
$fn$;

-- ---------------------------------------------------------------------------
-- pn_blocked_list(): usernames this Personal ID has blocked
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pn_blocked_list()
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

    SELECT COALESCE(jsonb_agg(b.username ORDER BY b.username), '[]'::jsonb)
    INTO result
    FROM (
        SELECT p.username
        FROM public.personal_id_blocks bl
        JOIN public.personal_ids p ON p.id = bl.blocked_personal_id
        WHERE bl.blocker_personal_id = me.id
    ) b;
    RETURN result;
END;
$fn$;

GRANT EXECUTE ON FUNCTION public.pn_toggle_block(TEXT, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.pn_blocked_list() TO authenticated;