-- =============================================================================
-- FIX: activation failed with "requires a phone-verified Supabase session"
-- because FLARE NUMBER sessions are created via hidden email+password auth
-- (flare.<digits>@flare-number.app), so the JWT has an "email" claim but never
-- a "phone" claim. flare_session_phone() now derives the number from the
-- pseudo-email as a fallback.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.flare_session_phone()
RETURNS TEXT LANGUAGE plpgsql STABLE SET search_path = public AS $$
DECLARE
    v_phone TEXT;
    v_email TEXT;
    v_digits TEXT;
BEGIN
    -- Preferred: a real phone claim (Supabase phone auth), if ever used.
    v_phone := current_setting('request.jwt.claims', true)::jsonb ->> 'phone';
    IF COALESCE(v_phone, '') <> '' THEN
        RETURN v_phone;
    END IF;

    -- Fallback: parse the deterministic hidden email flare.<digits>@flare-number.app
    v_email := LOWER(COALESCE(auth.email(), ''));
    IF v_email LIKE 'flare.%@flare-number.app' THEN
        v_digits := split_part(split_part(v_email, '@', 1), '.', 2);
        -- Only bare digits are accepted (no '.', '+', or other characters).
        IF v_digits ~ '^[0-9]{7,17}$' THEN
            RETURN '+' || v_digits;
        END IF;
    END IF;

    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.flare_activate_identity()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_phone TEXT;
    normalized TEXT;
    rec public.flare_number_identities;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;
    v_phone := public.flare_session_phone();
    IF v_phone IS NULL OR v_phone = '' THEN
        RAISE EXCEPTION 'FLARE NUMBER requires a phone-verified Supabase session';
    END IF;
    normalized := public.flare_normalize_phone(v_phone);
    IF normalized IS NULL THEN RAISE EXCEPTION 'Invalid phone number'; END IF;

    IF EXISTS (
        SELECT 1 FROM public.flare_number_identities
        WHERE auth_user_id = auth.uid() AND phone <> normalized
    ) THEN
        RAISE EXCEPTION 'This FLARE NUMBER session is already linked to a different number';
    END IF;

    INSERT INTO public.flare_number_identities (phone, auth_user_id, verification_status, last_verified_at)
    VALUES (normalized, auth.uid(), 'VERIFIED', now())
    ON CONFLICT (phone) DO UPDATE
        SET auth_user_id = EXCLUDED.auth_user_id,
            verification_status = 'VERIFIED',
            last_verified_at = now()
    RETURNING * INTO rec;

    RETURN jsonb_build_object(
        'identity_id', rec.id,
        'phone', rec.phone,
        'display_name', rec.display_name,
        'avatar_type', rec.avatar_type
    );
END;
$$;
