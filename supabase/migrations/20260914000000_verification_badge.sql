-- ==============================================================================
-- FLAREOFFICIAL — PAID VERIFICATION BADGE (Super-Admin-priced, paid from wallet)
-- Run from the Supabase SQL Editor. Safe to re-run (idempotent).
--
--  * admin_configs.verification_badge_fee_usd — fee set by the Super Admin.
--  * app_users.verification_badge(+_at)       — per-user badge activation flag.
--  * purchase_verification_badge()            — SECURITY DEFINER RPC. The ONLY
--    way a badge can be activated: atomically validates the fee against the
--    admin config, deducts the equivalent credits from the user's withdrawable
--    wallet balance, writes an immutable wallet transaction + audit log row and
--    flips the badge flag. Insufficient balance / double purchase rejected.
-- ==============================================================================

-- 1. Config column (fee in USD, Super Admin editable)
ALTER TABLE public.admin_configs
    ADD COLUMN IF NOT EXISTS verification_badge_fee_usd NUMERIC(10,2) NOT NULL DEFAULT 4.99;

-- 2. Badge flags on app_users (guarded: table may have a legacy layout)
DO $$ BEGIN
    IF to_regclass('public.app_users') IS NOT NULL THEN
        ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS verification_badge BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE public.app_users ADD COLUMN IF NOT EXISTS verification_badge_at TIMESTAMPTZ;
    END IF;
END $$;

-- 3. Purchase RPC
CREATE OR REPLACE FUNCTION public.purchase_verification_badge(p_user_handle TEXT)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user        TEXT := lower(COALESCE(p_user_handle, ''));
    v_uid         TEXT;
    v_fee_usd     NUMERIC(10,2);
    v_cpd         INTEGER;
    v_credits     INTEGER;
    v_available   INTEGER;
    v_pending     INTEGER;
    v_txn_id      TEXT;
    v_admin_h     TEXT;
    v_bdt_rate    NUMERIC;
BEGIN
    -- Resolve the caller from the authenticated session (never trust the handle alone).
    SELECT uid, lower(handle) INTO v_uid, v_admin_h
    FROM public.app_users WHERE uid = auth.uid()::text LIMIT 1;
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    IF v_admin_h IS DISTINCT FROM v_user THEN
        -- A user may only buy a badge for themselves; staff may buy for a target handle.
        IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text
                       AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER')) THEN
            RAISE EXCEPTION 'Access Denied: you can only activate your own badge' USING ERRCODE = '42501';
        END IF;
    END IF;

    SELECT lower(handle) INTO v_user FROM public.app_users WHERE uid = v_uid LIMIT 1;
    IF v_user IS NULL THEN RAISE EXCEPTION 'User not found'; END IF;
    IF EXISTS (SELECT 1 FROM public.app_users WHERE uid = v_uid AND verification_badge) THEN
        RAISE EXCEPTION 'Verification badge is already active for this account';
    END IF;

    -- Fee + conversion rate (Super Admin controlled)
    SELECT COALESCE(verification_badge_fee_usd, 4.99)::NUMERIC(10,2),
           COALESCE(credits_per_dollar, 2000)
    INTO v_fee_usd, v_cpd
    FROM public.admin_configs WHERE id = 'global_config' LIMIT 1;
    v_fee_usd := COALESCE(v_fee_usd, 4.99);
    v_cpd     := COALESCE(v_cpd, 2000);
    v_bdt_rate := public.wallet_rate_usd_to_bdt();
    IF v_fee_usd <= 0 THEN RAISE EXCEPTION 'Verification badge purchase is temporarily unavailable; contact support'; END IF;

    v_credits := CEIL(v_fee_usd * v_cpd)::INTEGER;
    IF v_credits < 1 THEN v_credits := 1; END IF;

    -- Server-authoritative balance check
    SELECT COALESCE(withdrawable_credits,0), COALESCE(pending_withdrawal_credits,0)
    INTO v_available, v_pending
    FROM public.user_rewards WHERE user_handle = v_user;
    v_available := COALESCE(v_available, 0);
    v_pending   := COALESCE(v_pending, 0);
    IF v_available - COALESCE(v_pending,0) < v_credits THEN
        RAISE EXCEPTION 'Insufficient wallet balance. Needed: % coins ($% / ৳%) — Available: % coins.',
            v_credits, ROUND(v_fee_usd, 2), ROUND(v_fee_usd * v_bdt_rate, 2), v_available - COALESCE(v_pending,0);
    END IF;

    v_txn_id := 'VB-' || upper(substr(md5(v_user || clock_timestamp()::text), 1, 16));

    -- Atomic debit (guarded against concurrent changes)
    UPDATE public.user_rewards
    SET withdrawable_credits = withdrawable_credits - v_credits,
        updated_at = (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT
    WHERE user_handle = v_user
      AND withdrawable_credits - COALESCE(pending_withdrawal_credits,0) >= v_credits;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Insufficient wallet balance (concurrent change detected)';
    END IF;
    -- Immutable transaction record
    INSERT INTO public.wallet_transactions
        (id, user_handle, tx_type, source, coin_amount, monetary_amount, currency,
         status, credit_or_debit, reference, metadata)
    VALUES
        (v_txn_id, v_user, 'VERIFICATION_BADGE', 'VERIFICATION_BADGE', -v_credits, v_fee_usd, 'USD',
         'COMPLETED', 'DEBIT', v_txn_id,
         jsonb_build_object('fee_usd', v_fee_usd, 'credits_per_dollar', v_cpd, 'badge', TRUE));

    -- Audit log
    INSERT INTO public.wallet_audit_log
        (id, admin_handle, target_handle, action, field_name, previous_value, new_value, reason, reference)
    VALUES
        ('AUD-' || md5(v_txn_id), v_admin_h, v_user, 'BADGE_PURCHASED', 'verification_badge',
         'false', 'true', format('Paid %s coins ($%s USD) for Verification Badge', v_credits, v_fee_usd), v_txn_id);

    -- Activate the badge
    UPDATE public.app_users
    SET verification_badge = TRUE, verification_badge_at = NOW()
    WHERE uid = v_uid;

    RETURN jsonb_build_object(
        'ok', TRUE,
        'user_handle', v_user,
        'fee_usd', v_fee_usd,
        'credits_spent', v_credits,
        'credits_per_dollar', v_cpd,
        'withdrawable_credits', v_available - v_credits,
        'verification_badge', TRUE
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.purchase_verification_badge(TEXT) TO authenticated;

-- 4. Status helper (cheap read for the settings / wallet screens)
CREATE OR REPLACE FUNCTION public.verification_badge_status()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid   TEXT; v_user TEXT; v_badge BOOLEAN; v_fee NUMERIC; v_rate NUMERIC;
BEGIN
    SELECT uid, lower(handle) INTO v_uid, v_user FROM public.app_users WHERE uid = auth.uid()::text LIMIT 1;
    IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501'; END IF;
    SELECT verification_badge INTO v_badge FROM public.app_users WHERE uid = v_uid LIMIT 1;
    SELECT verification_badge_fee_usd, credits_per_dollar * 0.0005
    INTO v_fee, v_rate FROM public.admin_configs WHERE id = 'global_config' LIMIT 1;
    RETURN jsonb_build_object(
        'user_handle', v_user,
        'verification_badge', COALESCE(v_badge, FALSE),
        'fee_usd', COALESCE(v_fee, 4.99),
        'usd_to_bdt', COALESCE(v_rate, 1.0)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.verification_badge_status() TO authenticated;

NOTIFY pgrst, 'reload schema';
