-- =============================================================================
-- VYN NUMBER — email-based auth support (no SMS provider needed)
-- Run this once in: Supabase Dashboard -> SQL Editor -> Run
-- =============================================================================
-- The app authenticates with "vyn.<digits>@vyn-number.app" (deterministic per
-- phone number) via the FREE Supabase email+password provider — no SMS, no OTP,
-- no external API. The client must have "Confirm email" disabled in Auth so
-- signup returns the session directly.
--
-- Only vn_activate_identity() changes: it now derives the phone number from the
-- JWT "email" claim instead of the "phone" claim. All other RPCs key off
-- auth.uid() and stay untouched.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.vn_activate_identity()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
    v_phone TEXT;
    normalized TEXT;
    rec public.vyn_number_identities;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;
    v_email := current_setting('request.jwt.claims', true)::jsonb ->> 'email';
    IF v_email IS NULL OR v_email NOT LIKE 'vyn.%@vyn-number.app' THEN
        RAISE EXCEPTION 'VYN NUMBER requires a VYN NUMBER account session';
    END IF;
    v_phone := regexp_replace(v_email, '[^0-9]', '', 'g');
    normalized := public.vn_normalize_phone(v_phone);
    IF normalized IS NULL THEN
        RAISE EXCEPTION 'Invalid phone number';
    END IF;

    -- One auth user may not hold two different VYN NUMBER numbers.
    IF EXISTS (
        SELECT 1 FROM public.vyn_number_identities
        WHERE auth_user_id = auth.uid() AND phone <> normalized
    ) THEN
        RAISE EXCEPTION 'This VYN NUMBER session is already linked to a different number';
    END IF;

    INSERT INTO public.vyn_number_identities (phone, auth_user_id, verification_status, last_verified_at)
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
