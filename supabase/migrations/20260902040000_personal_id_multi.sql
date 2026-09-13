-- =============================================================================
-- Personal ID v3: multi Personal ID (max 3 per FlareOfficial account) + account binding
--   * A FlareOfficial account can own UP TO 3 Personal IDs (was 1).
--   * Every Personal ID is permanently bound to the FlareOfficial account (user_id) that
--     created it. It can only be used while THAT account is logged in.
--   * "Login" selects the ACTIVE Personal ID for the account (personal_id_sessions).
--     All existing RPCs keep resolving identity via pn_me() -> active row.
--   * "Logout" (from the settings button) clears ONLY the active Personal ID
--     selection — the FlareOfficial account session is never touched.
-- =============================================================================

-- 1) Active Personal ID selection per FlareOfficial account ----------------------------
CREATE TABLE IF NOT EXISTS public.personal_id_sessions (
    user_id             uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    active_personal_id  uuid NOT NULL REFERENCES public.personal_ids(id) ON DELETE CASCADE,
    updated_at          timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.personal_id_sessions ENABLE ROW LEVEL SECURITY; -- deny all direct access

-- Allow up to 3 Personal IDs per account (replaces one-per-account unique index).
DROP INDEX IF EXISTS public.uq_personal_ids_user;

-- 2) pn_me() now returns the ACTIVE Personal ID row (null when none selected) --
CREATE OR REPLACE FUNCTION public.pn_me()
RETURNS public.personal_ids
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE rec public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RETURN rec; END IF;
    SELECT p.* INTO rec
    FROM public.personal_ids p
    JOIN public.personal_id_sessions s ON s.active_personal_id = p.id
    WHERE s.user_id = auth.uid()
    LIMIT 1;
    RETURN rec;
END;
$$;

-- 3) LOGIN: select one of THIS account's Personal IDs as active ----------------
CREATE OR REPLACE FUNCTION public.pn_login(p_username TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE rec public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    SELECT * INTO rec FROM public.personal_ids
    WHERE lower(username) = lower(btrim(p_username))
      AND user_id = auth.uid()
    LIMIT 1;
    IF rec.id IS NULL THEN
        RAISE EXCEPTION 'This Personal ID is not connected to your current FlareOfficial account';
    END IF;
    INSERT INTO public.personal_id_sessions (user_id, active_personal_id, updated_at)
    VALUES (auth.uid(), rec.id, now())
    ON CONFLICT (user_id) DO UPDATE
        SET active_personal_id = EXCLUDED.active_personal_id, updated_at = now();
    RETURN jsonb_build_object('id', rec.id, 'username', rec.username, 'avatar_url', rec.avatar_url);
END;
$$;

-- 4) LOGOUT: clears ONLY the active Personal ID selection (account stays in) ---
CREATE OR REPLACE FUNCTION public.pn_logout()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    DELETE FROM public.personal_id_sessions WHERE user_id = auth.uid();
END;
$$;

-- 5) CREATE: up to 3 per account; the new ID becomes the active one ------------
CREATE OR REPLACE FUNCTION public.pn_create(p_username TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_norm TEXT;
    rec public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    v_norm := public.pn_normalize_username(p_username);
    IF v_norm IS NULL THEN
        RAISE EXCEPTION 'Username must be 3-20 characters, start with a letter, and use only letters, numbers, or underscores';
    END IF;
    IF EXISTS (SELECT 1 FROM public.personal_ids WHERE lower(username) = v_norm) THEN
        RAISE EXCEPTION 'This Personal ID is already taken';
    END IF;
    IF (SELECT count(*) FROM public.personal_ids WHERE user_id = auth.uid()) >= 3 THEN
        RAISE EXCEPTION 'You can create a maximum of 3 Personal IDs per FlareOfficial account';
    END IF;
    INSERT INTO public.personal_ids (user_id, username)
    VALUES (auth.uid(), v_norm)
    RETURNING * INTO rec;
    INSERT INTO public.personal_id_sessions (user_id, active_personal_id, updated_at)
    VALUES (auth.uid(), rec.id, now())
    ON CONFLICT (user_id) DO UPDATE
        SET active_personal_id = EXCLUDED.active_personal_id, updated_at = now();
    RETURN jsonb_build_object('id', rec.id, 'username', rec.username, 'avatar_url', rec.avatar_url);
END;
$$;

-- 6) Grants ---------------------------------------------------------------------
GRANT EXECUTE ON FUNCTION public.pn_login(text), public.pn_logout()
    TO authenticated;
