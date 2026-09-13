-- ==============================================================================
-- FLAREOFFICIAL WALLET SYSTEM (Professional Wallet / Rewards upgrade)
-- Builds on the existing rewards backend (user_rewards, withdrawals, admin_configs).
--
--  * Keeps all existing tables and columns intact.
--  * Adds SEPARATE, independently-tracked balances to user_rewards (referral /
--    watch / challenge / withdrawable / pending / lifetime-earned).
--  * Adds normalized, immutable transaction / audit / fraud tables with indexes.
--  * Adds SECURITY DEFINER RPCs that are the ONLY way balances can change at
--    runtime, so the client app can never directly mutate a final wallet balance.
--  * Enforces atomicity, prevents negative balances, prevents double redemption
--    and double withdrawal submission.
--
-- Safe to re-run (idempotent). Run from the Supabase SQL Editor.
-- ==============================================================================

-- ============================================================
-- 0. SCHEMA GUARDS — make sure the legacy tables/columns the
--    wallet functions reference actually exist on ANY database
--    state (idempotent; no-ops when 20260829 already applied).
-- ============================================================
CREATE TABLE IF NOT EXISTS public.user_rewards (
    user_handle          TEXT PRIMARY KEY,
    total_credits        INTEGER NOT NULL DEFAULT 0,
    referred_users_count INTEGER NOT NULL DEFAULT 0,
    referral_earnings    INTEGER NOT NULL DEFAULT 0,
    updated_at           BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS user_handle         TEXT;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS total_credits       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referred_users_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referral_earnings   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS updated_at          BIGINT NOT NULL DEFAULT 0;
-- Backfill handle for rows created before this guard existed.
UPDATE public.user_rewards SET user_handle = lower(COALESCE(user_handle, '')) WHERE user_handle IS NULL OR user_handle = '';
DELETE FROM public.user_rewards WHERE user_handle = '';
ALTER TABLE public.user_rewards ALTER COLUMN user_handle SET NOT NULL;
ALTER TABLE public.user_rewards DROP CONSTRAINT IF EXISTS user_rewards_pkey;
ALTER TABLE public.user_rewards ADD CONSTRAINT user_rewards_pkey PRIMARY KEY (user_handle);

-- Additive guards on OTHER existing tables the wallet RPCs read.
-- (Applying them here means a missing column can never abort a CREATE FUNCTION later.)
CREATE TABLE IF NOT EXISTS public.admin_configs (
    id                 TEXT PRIMARY KEY,
    credits_per_dollar INTEGER NOT NULL DEFAULT 2000
);
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS credits_per_dollar INTEGER NOT NULL DEFAULT 2000;
ALTER TABLE public.app_users    ADD COLUMN IF NOT EXISTS can_process_payouts BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS public.withdrawals (
    id           TEXT PRIMARY KEY,
    user_handle  TEXT NOT NULL DEFAULT '',
    user_email   TEXT NOT NULL DEFAULT '',
    method       TEXT NOT NULL DEFAULT '',
    account_number TEXT NOT NULL DEFAULT '',
    credits_used INTEGER NOT NULL DEFAULT 0,
    amount_usd   NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_bdt   NUMERIC(12,2) NOT NULL DEFAULT 0,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    request_date TEXT NOT NULL DEFAULT '',
    transaction_note TEXT NOT NULL DEFAULT '',
    timestamp    BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS user_handle     TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS user_email      TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS method          TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS account_number  TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS credits_used    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS amount_usd      NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS amount_bdt      NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS status          TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS request_date    TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS transaction_note TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS timestamp       BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW();
CREATE INDEX IF NOT EXISTS idx_withdrawals_user    ON public.withdrawals (user_handle);
CREATE INDEX IF NOT EXISTS idx_withdrawals_status  ON public.withdrawals (status);

-- ============================================================
-- 1. USER REWARDS — SEPARATE BALANCE BUCKETS
-- ============================================================
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referral_credits             INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS watch_credits                INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS challenge_credits            INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS withdrawable_credits         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS pending_withdrawal_credits   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS total_earned_credits         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referral_redeemed_credits    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS watch_redeemed_credits       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS challenge_redeemed_credits   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS pending_watch_credits        INTEGER NOT NULL DEFAULT 0;

-- Guards: negative balances are impossible at the DB level too.
ALTER TABLE public.user_rewards DROP CONSTRAINT IF EXISTS user_rewards_non_negative;
ALTER TABLE public.user_rewards ADD CONSTRAINT user_rewards_non_negative CHECK (
    referral_credits           >= 0 AND
    watch_credits              >= 0 AND
    challenge_credits          >= 0 AND
    withdrawable_credits       >= 0 AND
    pending_withdrawal_credits >= 0 AND
    total_earned_credits       >= 0 AND
    pending_watch_credits      >= 0
);

-- ============================================================
-- 0.5 PRE-FLIGHT VERIFICATION
--     After the guards above, EVERY column the wallet functions
--     reference must exist. If anything is still missing we fail
--     LOUDLY here and name the exact table.column — instead of a
--     cryptic 42703 somewhere deep inside a CREATE FUNCTION.
-- ============================================================
DO $wallet_preflight$
DECLARE
    v_missing TEXT := '';
    r RECORD;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('app_users','uid'), ('app_users','handle'), ('app_users','role'),
            ('app_users','name'), ('app_users','can_process_payouts'),
            ('admin_configs','id'), ('admin_configs','credits_per_dollar'),
            ('user_rewards','user_handle'), ('user_rewards','total_credits'),
            ('user_rewards','referred_users_count'), ('user_rewards','referral_earnings'),
            ('user_rewards','updated_at'),
            ('user_rewards','referral_credits'), ('user_rewards','watch_credits'),
            ('user_rewards','challenge_credits'), ('user_rewards','withdrawable_credits'),
            ('user_rewards','pending_withdrawal_credits'), ('user_rewards','total_earned_credits'),
            ('user_rewards','referral_redeemed_credits'), ('user_rewards','watch_redeemed_credits'),
            ('user_rewards','challenge_redeemed_credits'), ('user_rewards','pending_watch_credits'),
            ('withdrawals','id'), ('withdrawals','user_handle'), ('withdrawals','user_email'),
            ('withdrawals','method'), ('withdrawals','account_number'),
            ('withdrawals','credits_used'), ('withdrawals','amount_usd'), ('withdrawals','amount_bdt'),
            ('withdrawals','status'), ('withdrawals','request_date'),
            ('withdrawals','transaction_note'), ('withdrawals','timestamp'),
            ('withdrawals','created_at'), ('withdrawals','updated_at')
        ) AS t(tbl, col)
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = r.tbl AND column_name = r.col
        ) THEN
            v_missing := v_missing || format('%s.%s;  ', r.tbl, r.col);
        END IF;
    END LOOP;

    IF v_missing <> '' THEN
        RAISE EXCEPTION
            'WALLET MIGRATION PRE-FLIGHT FAILED. Missing required columns: % — run the guards or share this exact message.',
            v_missing USING ERRCODE = 'P0001';
    END IF;
END
$wallet_preflight$;

-- ============================================================
-- 2. WALLET TRANSACTIONS  (immutable audit trail for every balance change)
-- ============================================================
-- These three tables belong to THIS migration only. Recreating them from
-- scratch guarantees the exact DDL below even if a stale/broken variant of
-- the table exists on the live database (no production wallet data exists yet).
DROP TABLE IF EXISTS public.wallet_transactions CASCADE;
CREATE TABLE public.wallet_transactions (
    id              TEXT PRIMARY KEY,
    user_handle     TEXT NOT NULL,
    tx_type         TEXT NOT NULL,                -- REFERRAL / WATCH / CHALLENGE / REDEEM / WITHDRAWAL / WITHDRAWAL_REFUND / MANUAL_ADD / MANUAL_REMOVE / BONUS
    source          TEXT NOT NULL,                -- e.g. REFER_AND_EARN / WATCH_AND_EARN / CHALLENGE / WITHDRAWAL / ADMIN
    coin_amount     INTEGER NOT NULL DEFAULT 0,   -- signed delta (+ earned/redeemed, - withdrawn)
    monetary_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        TEXT NOT NULL DEFAULT 'USD',
    status          TEXT NOT NULL DEFAULT 'COMPLETED', -- COMPLETED / PENDING / PROCESSING / PAID / REJECTED / CANCELLED
    credit_or_debit TEXT NOT NULL DEFAULT 'CREDIT',
    reference       TEXT NOT NULL DEFAULT '',
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_transactions_non_null CHECK (coin_amount <> 0)
);

CREATE INDEX IF NOT EXISTS idx_wallet_tx_user          ON public.wallet_transactions (user_handle, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_status        ON public.wallet_transactions (status);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_type          ON public.wallet_transactions (tx_type);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_created_at    ON public.wallet_transactions (created_at DESC);

ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "wallet_tx_select_own_or_staff" ON public.wallet_transactions;
DROP POLICY IF EXISTS "wallet_tx_no_client_write" ON public.wallet_transactions;
-- Users read their own transactions; staff read all. No client INSERT/UPDATE/DELETE:
-- balance-changing inserts happen ONLY inside SECURITY DEFINER functions.
CREATE POLICY "wallet_tx_select_own_or_staff" ON public.wallet_transactions FOR SELECT TO authenticated
    USING (
        user_handle = (SELECT handle FROM public.app_users WHERE uid = auth.uid()::text)
        OR EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER','MODERATOR'))
    );
CREATE POLICY "wallet_tx_no_client_write" ON public.wallet_transactions FOR ALL TO authenticated
    USING (false) WITH CHECK (false);
-- ============================================================
-- 3. WALLET AUDIT LOG  (administrative wallet/reward actions)
-- ============================================================
DROP TABLE IF EXISTS public.wallet_audit_log CASCADE;
CREATE TABLE public.wallet_audit_log (
    id              TEXT PRIMARY KEY,
    admin_handle    TEXT NOT NULL,
    target_handle   TEXT NOT NULL,
    action          TEXT NOT NULL,                -- MANUAL_ADD / MANUAL_REMOVE / WITHDRAWAL_REJECT / WITHDRAWAL_APPROVE / WITHDRAWAL_PAID / FLAG_SETTLED
    field_name      TEXT NOT NULL DEFAULT '',
    previous_value  TEXT NOT NULL DEFAULT '',
    new_value       TEXT NOT NULL DEFAULT '',
    reference       TEXT NOT NULL DEFAULT '',
    reason          TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wallet_audit_admin   ON public.wallet_audit_log (admin_handle, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_audit_target  ON public.wallet_audit_log (target_handle, created_at DESC);

ALTER TABLE public.wallet_audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "wallet_audit_select_staff" ON public.wallet_audit_log;
DROP POLICY IF EXISTS "wallet_audit_no_client_write" ON public.wallet_audit_log;
CREATE POLICY "wallet_audit_select_staff" ON public.wallet_audit_log FOR SELECT TO authenticated
    USING (EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER','MODERATOR')));
CREATE POLICY "wallet_audit_no_client_write" ON public.wallet_audit_log FOR ALL TO authenticated
    USING (false) WITH CHECK (false);

-- ============================================================
-- 4. FRAUD / SUSPICIOUS ACTIVITY FLAGS
-- ============================================================
DROP TABLE IF EXISTS public.wallet_fraud_flags CASCADE;
CREATE TABLE public.wallet_fraud_flags (
    id              TEXT PRIMARY KEY,
    user_handle     TEXT NOT NULL,
    flag_type       TEXT NOT NULL,                -- HIGH_WATCH_REWARD / REFERRAL_ABUSE / DUPLICATE_CLAIM / SUSPICIOUS_TRANSACTIONS / UNUSUAL_WITHDRAWAL
    severity        TEXT NOT NULL DEFAULT 'LOW',  -- LOW / MEDIUM / HIGH
    description     TEXT NOT NULL DEFAULT '',
    resolved        BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by     TEXT NOT NULL DEFAULT '',
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_fraud_unique_flag UNIQUE (user_handle, flag_type)
);

CREATE INDEX IF NOT EXISTS idx_wallet_fraud_user     ON public.wallet_fraud_flags (user_handle);
CREATE INDEX IF NOT EXISTS idx_wallet_fraud_resolved ON public.wallet_fraud_flags (resolved);

ALTER TABLE public.wallet_fraud_flags ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "wallet_fraud_select_staff" ON public.wallet_fraud_flags;
DROP POLICY IF EXISTS "wallet_fraud_no_client_write" ON public.wallet_fraud_flags;
CREATE POLICY "wallet_fraud_select_staff" ON public.wallet_fraud_flags FOR SELECT TO authenticated
    USING (EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER','MODERATOR')));
CREATE POLICY "wallet_fraud_no_client_write" ON public.wallet_fraud_flags FOR ALL TO authenticated
    USING (false) WITH CHECK (false);

-- ============================================================
-- 5. SHARED SECURITY HELPERS (inside SECURITY DEFINER functions -> auth() works)
-- ============================================================
CREATE OR REPLACE FUNCTION public.wallet_is_staff() RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY INVOKER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER','MODERATOR'));
$$;
GRANT EXECUTE ON FUNCTION public.wallet_is_staff() TO authenticated;

CREATE OR REPLACE FUNCTION public.wallet_is_super_admin() RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY INVOKER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role = 'SUPER_ADMIN');
$$;
GRANT EXECUTE ON FUNCTION public.wallet_is_super_admin() TO authenticated;

CREATE OR REPLACE FUNCTION public.wallet_current_handle() RETURNS TEXT LANGUAGE sql STABLE SECURITY INVOKER SET search_path = public AS $$
    SELECT COALESCE((SELECT handle FROM public.app_users WHERE uid = auth.uid()::text), '');
$$;
GRANT EXECUTE ON FUNCTION public.wallet_current_handle() TO authenticated;

-- Look up credits-per-dollar + USD->BDT from the existing admin_configs row.
CREATE OR REPLACE FUNCTION public.wallet_rate_credits_per_dollar() RETURNS INTEGER LANGUAGE sql STABLE SECURITY INVOKER SET search_path = public AS $$
    SELECT COALESCE((SELECT credits_per_dollar FROM public.admin_configs WHERE id = 'global_config' LIMIT 1), 2000);
$$;
CREATE OR REPLACE FUNCTION public.wallet_rate_usd_to_bdt() RETURNS NUMERIC LANGUAGE sql STABLE SET search_path = public AS $$
    SELECT COALESCE((SELECT credits_per_dollar FROM public.admin_configs WHERE id = 'global_config' LIMIT 1), 2000)::NUMERIC * 0.0005;
$$;
-- ============================================================
-- 6. REDEEM REWARD CREDITS  (move eligible credits -> withdrawable wallet)
--    Prevents double redemption because the source bucket is emptied atomically.
-- ============================================================
CREATE OR REPLACE FUNCTION public.redeem_reward_credits(
    p_user_handle TEXT,
    p_source TEXT            -- 'REFERRAL' | 'WATCH' | 'CHALLENGE'
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_amount INTEGER := 0;
    v_col    TEXT;
    v_cnt    TEXT;
    v_user   TEXT;
BEGIN
    IF p_user_handle IS NULL OR length(p_user_handle) = 0 THEN
        RAISE EXCEPTION 'Missing user handle' USING ERRCODE = '22023';
    END IF;
    v_user := lower(p_user_handle);

    -- Must be redeeming your OWN credits.
    IF public.wallet_current_handle() <> v_user THEN
        RAISE EXCEPTION 'You can only redeem your own credits' USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.user_rewards WHERE user_handle = v_user) THEN
        INSERT INTO public.user_rewards (user_handle, updated_at)
        VALUES (v_user, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT);
    END IF;

    IF p_source NOT IN ('REFERRAL','WATCH','CHALLENGE') THEN
        RAISE EXCEPTION 'Invalid redemption source' USING ERRCODE = '22023';
    END IF;

    CASE p_source
        WHEN 'REFERRAL' THEN v_col := 'referral_credits';  v_cnt := 'referral_redeemed_credits';
        WHEN 'WATCH'    THEN v_col := 'watch_credits';     v_cnt := 'watch_redeemed_credits';
        WHEN 'CHALLENGE' THEN v_col := 'challenge_credits'; v_cnt := 'challenge_redeemed_credits';
    END CASE;

    -- Atomically read + clear the source bucket under a row lock (no double redemption).
    EXECUTE format('SELECT %I FROM public.user_rewards WHERE user_handle = $1 FOR UPDATE', v_col)
        INTO v_amount USING v_user;
    v_amount := COALESCE(v_amount, 0);

    IF v_amount <= 0 THEN
        RAISE EXCEPTION 'No eligible ''%'' credits to redeem', p_source USING ERRCODE = 'P0001';
    END IF;

    EXECUTE format('UPDATE public.user_rewards SET %I = 0, withdrawable_credits = withdrawable_credits + $1, updated_at = (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT WHERE user_handle = $2', v_col)
        USING v_amount, v_user;

    EXECUTE format('UPDATE public.user_rewards SET %I = %I + $1 WHERE user_handle = $2', v_cnt, v_cnt) USING v_amount, v_user;

    INSERT INTO public.wallet_transactions (id, user_handle, tx_type, source, coin_amount, status, credit_or_debit, reference, metadata)
    VALUES ('REDEEM-' || md5(random()::text || clock_timestamp()::text),
            v_user, 'REDEEM', p_source, v_amount, 'COMPLETED', 'CREDIT',
            p_source || '_REDEEMED',
            jsonb_build_object('redeemed_from', p_source));

    RETURN jsonb_build_object(
        'ok', TRUE,
        'source', p_source,
        'redeemed_amount', v_amount,
        'withdrawable_credits', (SELECT withdrawable_credits FROM public.user_rewards WHERE user_handle = v_user)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.redeem_reward_credits(TEXT, TEXT) TO authenticated;
-- ============================================================
-- 7. REQUEST WITHDRAWAL  (atomic reserve + record)
-- ============================================================
CREATE OR REPLACE FUNCTION public.request_wallet_withdrawal(
    p_user_handle TEXT,
    p_email TEXT,
    p_method TEXT,
    p_account_number TEXT,
    p_credits INTEGER,
    p_amount_usd NUMERIC,
    p_amount_bdt NUMERIC,
    p_client_ref TEXT DEFAULT ''
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_user TEXT;
    v_min_usd  NUMERIC;
    v_cpd  INTEGER;
    v_min_credits INTEGER;
    v_bal  INTEGER;
    v_new_id TEXT;
    v_dupe_count INTEGER;
BEGIN
    IF p_user_handle IS NULL OR length(p_user_handle) = 0 THEN RAISE EXCEPTION 'Missing user handle' USING ERRCODE = '22023'; END IF;
    IF p_method IS NULL OR length(p_method) = 0        THEN RAISE EXCEPTION 'Payment method is required' USING ERRCODE = '22023'; END IF;
    IF p_account_number IS NULL OR length(p_account_number) = 0 THEN RAISE EXCEPTION 'Account / wallet number is required' USING ERRCODE = '22023'; END IF;
    v_user := lower(p_user_handle);

    IF public.wallet_current_handle() <> v_user THEN
        RAISE EXCEPTION 'You can only request a withdrawal for your own account' USING ERRCODE = '42501';
    END IF;

    IF COALESCE(p_credits, 0) <= 0 THEN RAISE EXCEPTION 'Withdrawal credits must be positive' USING ERRCODE = '22023'; END IF;

    -- Existing Rules: minimum withdrawal (from admin_configs) converted to credits.
    v_min_usd := COALESCE((SELECT min_withdrawal_usd FROM public.admin_configs WHERE id = 'global_config' LIMIT 1), 1.0);
    v_cpd  := public.wallet_rate_credits_per_dollar();
    v_min_credits := CEIL(v_min_usd * v_cpd);

    -- No duplicate submissions: only one open (PENDING/PROCESSING) withdrawal per user.
    SELECT COUNT(*) INTO v_dupe_count FROM public.withdrawals
        WHERE lower(user_handle) = v_user AND status IN ('PENDING','PROCESSING');
    IF v_dupe_count > 0 THEN
        RAISE EXCEPTION 'You already have a pending withdrawal request. Wait for it to be processed before submitting another.' USING ERRCODE = 'P0001';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.user_rewards WHERE user_handle = v_user) THEN
        INSERT INTO public.user_rewards (user_handle, updated_at) VALUES (v_user, (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT);
    END IF;

    IF p_credits < v_min_credits THEN
        RAISE EXCEPTION 'Minimum withdrawal is % coins (min $% USD)', v_min_credits, v_min_usd USING ERRCODE = 'P0001';
    END IF;

    SELECT withdrawable_credits INTO v_bal FROM public.user_rewards WHERE user_handle = v_user FOR UPDATE;
    IF COALESCE(v_bal, 0) < p_credits THEN
        RAISE EXCEPTION 'Insufficient withdrawable balance. You have % coins, requested %.', v_bal, p_credits USING ERRCODE = 'P0001';
    END IF;

    -- Reserve funds atomically (no negative balances possible).
    UPDATE public.user_rewards
       SET withdrawable_credits = withdrawable_credits - p_credits,
           pending_withdrawal_credits = pending_withdrawal_credits + p_credits,
           updated_at = (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT
     WHERE user_handle = v_user;

    IF p_client_ref IS NOT NULL AND length(p_client_ref) > 0 THEN
        v_new_id := p_client_ref;
    ELSE
        v_new_id := 'WD-' || md5(random()::text || clock_timestamp()::text);
    END IF;

    BEGIN
        INSERT INTO public.withdrawals (id, user_handle, user_email, method, account_number, credits_used,
                                        amount_usd, amount_bdt, status, request_date, transaction_note, timestamp, created_at)
        VALUES (v_new_id, v_user, COALESCE(p_email,''), p_method, p_account_number, p_credits,
                COALESCE(p_amount_usd,0), COALESCE(p_amount_bdt,0), 'PENDING',
                to_char(now(), 'YYYY-MM-DD'),
                'Verification in progress',
                (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT, NOW());
    EXCEPTION WHEN unique_violation THEN
        -- Same id already submitted -> revert the reservation.
        UPDATE public.user_rewards
           SET withdrawable_credits = withdrawable_credits + p_credits,
               pending_withdrawal_credits = pending_withdrawal_credits - p_credits
         WHERE user_handle = v_user;
        RAISE EXCEPTION 'Duplicate withdrawal submission rejected' USING ERRCODE = 'P0001';
    END;

    INSERT INTO public.wallet_transactions (id, user_handle, tx_type, source, coin_amount, monetary_amount, status, credit_or_debit, reference, metadata)
    VALUES ('WITHDRAWAL-' || v_new_id, v_user, 'WITHDRAWAL', 'WITHDRAWAL', -p_credits, COALESCE(p_amount_usd,0),
            'PENDING', 'DEBIT', v_new_id,
            jsonb_build_object('method', p_method, 'account', p_account_number));

    RETURN jsonb_build_object(
        'ok', TRUE,
        'withdrawal_id', v_new_id,
        'credits', p_credits,
        'withdrawable_credits', (SELECT withdrawable_credits FROM public.user_rewards WHERE user_handle = v_user)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.request_wallet_withdrawal(TEXT, TEXT, TEXT, TEXT, INTEGER, NUMERIC, NUMERIC, TEXT) TO authenticated;
-- ============================================================
-- 8. ADMIN MANUAL ADJUSTMENT  (Super Admin only, mandatory reason + audit)
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_adjust_wallet(
    p_target_handle TEXT,
    p_amount INTEGER,
    p_reason TEXT,
    p_type TEXT DEFAULT 'ADD',   -- 'ADD' | 'REMOVE'
    p_source TEXT DEFAULT 'ADMIN'
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_admin TEXT;
    v_user TEXT;
    v_prev INTEGER;
    v_signed INTEGER;
    v_tx_type TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied: Super Admin only' USING ERRCODE = '42501';
    END IF;
    v_admin := public.wallet_current_handle();
    IF COALESCE(p_reason, '') = '' THEN
        RAISE EXCEPTION 'A reason is mandatory for manual wallet adjustments' USING ERRCODE = '22023';
    END IF;
    IF COALESCE(p_amount, 0) = 0 THEN
        RAISE EXCEPTION 'Adjustment amount must be non-zero' USING ERRCODE = '22023';
    END IF;
    v_user := lower(COALESCE(p_target_handle, ''));
    IF v_user = '' THEN RAISE EXCEPTION 'Target user is required' USING ERRCODE = '22023'; END IF;

    IF NOT EXISTS (SELECT 1 FROM public.user_rewards WHERE user_handle = v_user) THEN
        INSERT INTO public.user_rewards (user_handle, updated_at) VALUES (v_user, (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT);
    END IF;

    SELECT withdrawable_credits INTO v_prev FROM public.user_rewards WHERE user_handle = v_user FOR UPDATE;
    v_prev := COALESCE(v_prev, 0);

    IF upper(p_type) = 'REMOVE' THEN
        v_signed := -p_amount;
        v_tx_type := 'MANUAL_REMOVE';
        IF v_prev + v_signed < 0 THEN
            RAISE EXCEPTION 'Cannot remove % coins: balance is only %', p_amount, v_prev USING ERRCODE = 'P0001';
        END IF;
    ELSE
        v_signed := p_amount;
        v_tx_type := 'MANUAL_ADD';
    END IF;

    UPDATE public.user_rewards
       SET withdrawable_credits = withdrawable_credits + v_signed,
           total_earned_credits = total_earned_credits + CASE WHEN v_signed > 0 THEN v_signed ELSE 0 END,
           updated_at = (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT
     WHERE user_handle = v_user;

    INSERT INTO public.wallet_transactions (id, user_handle, tx_type, source, coin_amount, status, credit_or_debit, reference, metadata)
    VALUES ('ADJ-' || md5(random()::text || clock_timestamp()::text), v_user, v_tx_type, COALESCE(p_source,'ADMIN'),
            v_signed, 'COMPLETED', CASE WHEN v_signed > 0 THEN 'CREDIT' ELSE 'DEBIT' END,
            'admin:' || v_admin, jsonb_build_object('reason', p_reason));

    INSERT INTO public.wallet_audit_log (id, admin_handle, target_handle, action, field_name, previous_value, new_value, reason, reference)
    VALUES ('AUD-' || md5(random()::text || clock_timestamp()::text), v_admin, v_user, v_tx_type,
            'withdrawable_credits', v_prev::TEXT, (v_prev + v_signed)::TEXT, p_reason, 'manual_adjustment');

    RETURN jsonb_build_object('ok', TRUE, 'target', v_user, 'delta', v_signed, 'new_balance', v_prev + v_signed);
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_adjust_wallet(TEXT, INTEGER, TEXT, TEXT, TEXT) TO authenticated;
-- ============================================================
-- 9. ADMIN REJECT WITHDRAWAL  (atomic: refund + reversal transaction + audit)
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_reject_withdrawal_v2(
    p_withdrawal_id TEXT,
    p_reason TEXT DEFAULT ''
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_admin TEXT;
    v_target TEXT;
    v_credits INTEGER;
    v_old_status TEXT;
    v_approved BOOLEAN;
BEGIN
    SELECT TRUE INTO v_approved FROM public.app_users u WHERE u.uid = auth.uid()::text
        AND (u.role IN ('SUPER_ADMIN','ADMIN','MANAGER') AND u.can_process_payouts);
    IF v_approved IS NULL AND NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied: payout permission required' USING ERRCODE = '42501';
    END IF;
    v_admin := public.wallet_current_handle();

    SELECT lower(user_handle), credits_used, status
      INTO v_target, v_credits, v_old_status
      FROM public.withdrawals WHERE id = p_withdrawal_id;
    IF v_target IS NULL THEN
        RAISE EXCEPTION 'Withdrawal not found' USING ERRCODE = 'P0002';
    END IF;

    -- Only reject once (idempotent): never silently double-refund.
    IF v_old_status = 'REJECTED' THEN
        RETURN jsonb_build_object('ok', TRUE, 'already_rejected', TRUE, 'withdrawal_id', p_withdrawal_id);
    END IF;

    UPDATE public.withdrawals SET status = 'REJECTED',
           transaction_note = COALESCE(p_reason, transaction_note),
           updated_at = NOW()
     WHERE id = p_withdrawal_id;

    -- Restore balance ONLY for credits actually reserved (>=1) and not yet refunded.
    IF v_old_status IN ('PENDING','PROCESSING') AND COALESCE(v_credits, 0) > 0 THEN
        UPDATE public.user_rewards
           SET pending_withdrawal_credits = pending_withdrawal_credits - v_credits,
               withdrawable_credits = withdrawable_credits + v_credits,
               updated_at = (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT
         WHERE user_handle = v_target;

        INSERT INTO public.wallet_transactions (id, user_handle, tx_type, source, coin_amount, monetary_amount, status, credit_or_debit, reference, metadata)
        VALUES ('REFUND-' || p_withdrawal_id, v_target, 'WITHDRAWAL_REFUND', 'WITHDRAWAL', v_credits,
                (SELECT amount_usd FROM public.withdrawals WHERE id = p_withdrawal_id), 'COMPLETED', 'CREDIT', p_withdrawal_id,
                jsonb_build_object('reason', p_reason, 'admin', v_admin));
    END IF;

    INSERT INTO public.wallet_audit_log (id, admin_handle, target_handle, action, field_name, previous_value, new_value, reason, reference)
    VALUES ('AUD-' || md5(random()::text || clock_timestamp()::text), v_admin, v_target, 'WITHDRAWAL_REJECT',
            'status', v_old_status, 'REJECTED', COALESCE(p_reason,'Rejected by admin'), p_withdrawal_id);

    RETURN jsonb_build_object('ok', TRUE, 'withdrawal_id', p_withdrawal_id, 'refunded_credits', v_credits);
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_reject_withdrawal_v2(TEXT, TEXT) TO authenticated;
-- ============================================================
-- 10. WALLET SUMMARY (single user, used by wallet screen)
-- ============================================================
CREATE OR REPLACE FUNCTION public.wallet_summary(
    p_user_handle TEXT
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_user TEXT;
    v_withdrawable INTEGER := 0;
    v_pending_wd INTEGER := 0;
    v_total_earned INTEGER := 0;
    v_ref INTEGER := 0;
    v_watch INTEGER := 0;
    v_challenge INTEGER := 0;
    v_ref_red INTEGER := 0;
    v_watch_red INTEGER := 0;
    v_chal_red INTEGER := 0;
    v_total INTEGER := 0;
    v_cpd INTEGER;
    v_rate NUMERIC;
    v_pending_wd_usd NUMERIC;
 BEGIN
    v_user := lower(COALESCE(p_user_handle, ''));
    IF v_user = '' THEN RAISE EXCEPTION 'Missing user handle' USING ERRCODE = '22023'; END IF;

    SELECT withdrawable_credits, pending_withdrawal_credits, total_earned_credits,
           referral_credits, watch_credits, challenge_credits,
           referral_redeemed_credits, watch_redeemed_credits, challenge_redeemed_credits, total_credits
      INTO v_withdrawable, v_pending_wd, v_total_earned,
           v_ref, v_watch, v_challenge,
           v_ref_red, v_watch_red, v_chal_red, v_total
      FROM public.user_rewards WHERE user_handle = v_user;

    IF v_withdrawable IS NULL THEN
        INSERT INTO public.user_rewards (user_handle, updated_at) VALUES (v_user, (EXTRACT(EPOCH FROM NOW())*1000)::BIGINT)
        ON CONFLICT (user_handle) DO NOTHING;
        v_withdrawable := 0; v_pending_wd := 0; v_total_earned := 0; v_ref := 0; v_watch := 0;
        v_challenge := 0; v_ref_red := 0; v_watch_red := 0; v_chal_red := 0; v_total := 0;
    END IF;

    v_cpd := public.wallet_rate_credits_per_dollar();
    v_rate := public.wallet_rate_usd_to_bdt();
    v_pending_wd_usd := COALESCE((SELECT SUM(amount_usd) FROM public.withdrawals
                                  WHERE lower(user_handle) = v_user AND status IN ('PENDING','PROCESSING')), 0);

    RETURN jsonb_build_object(
        'user_handle', v_user,
        'withdrawable_credits', v_withdrawable,
        'withdrawable_usd', ROUND((v_withdrawable::NUMERIC / v_cpd), 2),
        'withdrawable_bdt', ROUND((v_withdrawable::NUMERIC / v_cpd) * v_rate, 2),
        'pending_withdrawal_credits', v_pending_wd,
        'pending_withdrawal_usd', ROUND(v_pending_wd_usd, 2),
        'total_earned_credits', v_total_earned,
        'referral_credits', v_ref,
        'watch_credits', v_watch,
        'challenge_credits', v_challenge,
        'referral_redeemed_credits', v_ref_red,
        'watch_redeemed_credits', v_watch_red,
        'challenge_redeemed_credits', v_chal_red,
        'total_credits', v_total,
        'credits_per_dollar', v_cpd,
        'usd_to_bdt', ROUND(v_rate, 4)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.wallet_summary(TEXT) TO authenticated;

-- ============================================================
-- 11. LIST WALLET TRANSACTIONS (own or staff) — access enforced here (SECURITY DEFINER).
-- ============================================================
CREATE OR REPLACE FUNCTION public.list_wallet_transactions(
    p_user_handle TEXT DEFAULT NULL,
    p_limit INTEGER DEFAULT 200
) RETURNS SETOF public.wallet_transactions LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_caller TEXT;
    v_staff BOOLEAN;
    v_user TEXT;
BEGIN
    v_caller := public.wallet_current_handle();
    v_staff  := public.wallet_is_staff();
    IF NOT v_staff THEN
        IF p_user_handle IS NOT NULL AND lower(p_user_handle) <> v_caller THEN
            RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
        END IF;
        v_user := v_caller;
    ELSE
        v_user := lower(COALESCE(p_user_handle, v_caller));
    END IF;
    RETURN QUERY SELECT t.* FROM public.wallet_transactions t
        WHERE (v_user = '' OR lower(t.user_handle) = v_user)
        ORDER BY t.created_at DESC
        LIMIT GREATEST(1, LEAST(500, p_limit));
END;
$$;
GRANT EXECUTE ON FUNCTION public.list_wallet_transactions(TEXT, INTEGER) TO authenticated;
-- ============================================================
-- 12. ADMIN WALLET OVERVIEW (platform-wide summary, payout staff)
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_wallet_overview() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_total_platform INTEGER;
    v_total_earned INTEGER;
    v_total_redeemed INTEGER;
    v_total_withdrawn_usd NUMERIC(12,2);
    v_pending_payout NUMERIC(12,2);
    v_total_paid NUMERIC(12,2);
    v_pending_count INTEGER;
    v_today_earned INTEGER;
    v_yesterday_earned INTEGER;
    v_today_withdrawn_usd NUMERIC(12,2);
    v_yesterday_withdrawn_usd NUMERIC(12,2);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER')) THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    SELECT COALESCE(SUM(withdrawable_credits),0) INTO v_total_platform FROM public.user_rewards;
    SELECT COALESCE(SUM(total_earned_credits),0) INTO v_total_earned FROM public.user_rewards;
    SELECT COALESCE(SUM(referral_redeemed_credits + watch_redeemed_credits + challenge_redeemed_credits),0) INTO v_total_redeemed FROM public.user_rewards;
    SELECT COALESCE(SUM(amount_usd),0) INTO v_total_withdrawn_usd FROM public.withdrawals WHERE status = 'PAID';
    SELECT COALESCE(SUM(amount_usd),0) INTO v_pending_payout FROM public.withdrawals WHERE status IN ('PENDING','PROCESSING');
    SELECT COALESCE(SUM(amount_usd),0) INTO v_total_paid FROM public.withdrawals WHERE status = 'PAID';
    SELECT COUNT(*) INTO v_pending_count FROM public.withdrawals WHERE status IN ('PENDING','PROCESSING');
    SELECT COALESCE(SUM(coin_amount),0) INTO v_today_earned FROM public.wallet_transactions
        WHERE credit_or_debit = 'CREDIT' AND status = 'COMPLETED' AND created_at >= date_trunc('day', NOW());
    SELECT COALESCE(SUM(coin_amount),0) INTO v_yesterday_earned FROM public.wallet_transactions
        WHERE credit_or_debit = 'CREDIT' AND status = 'COMPLETED'
          AND created_at >= date_trunc('day', NOW()) - INTERVAL '1 day'
          AND created_at < date_trunc('day', NOW());
    SELECT COALESCE(SUM(amount_usd),0) INTO v_today_withdrawn_usd FROM public.withdrawals
        WHERE status = 'PAID' AND created_at >= date_trunc('day', NOW());
    SELECT COALESCE(SUM(amount_usd),0) INTO v_yesterday_withdrawn_usd FROM public.withdrawals
        WHERE status = 'PAID'
          AND created_at >= date_trunc('day', NOW()) - INTERVAL '1 day'
          AND created_at < date_trunc('day', NOW());
    RETURN jsonb_build_object(
        'total_platform_coins', v_total_platform,
        'total_coins_earned_by_users', v_total_earned,
        'total_coins_redeemed', v_total_redeemed,
        'total_withdrawn_amount_usd', v_total_withdrawn_usd,
        'pending_payout_amount_usd', v_pending_payout,
        'total_paid_payouts_usd', v_total_paid,
        'pending_payout_count', v_pending_count,
        'today_earned_coins', v_today_earned,
        'yesterday_earned_coins', v_yesterday_earned,
        'today_withdrawn_usd', v_today_withdrawn_usd,
        'yesterday_withdrawn_usd', v_yesterday_withdrawn_usd
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_wallet_overview() TO authenticated;

-- ============================================================
-- 13. ADMIN: USER EARNINGS OVERVIEW
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_user_earnings_overview() RETURNS TABLE(
    user_handle TEXT, name TEXT,
    total_earned INTEGER, referral_credits INTEGER, watch_credits INTEGER, challenge_credits INTEGER,
    redeemed INTEGER, withdrawn INTEGER, withdrawable INTEGER
) LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER')) THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
        SELECT r.user_handle,
               COALESCE((SELECT u.name FROM public.app_users u WHERE lower(u.handle) = r.user_handle LIMIT 1), ''),
               r.total_earned_credits,
               r.referral_credits,
               r.watch_credits,
               r.challenge_credits,
               r.referral_redeemed_credits + r.watch_redeemed_credits + r.challenge_redeemed_credits,
               COALESCE((SELECT SUM(w.credits_used) FROM public.withdrawals w WHERE lower(w.user_handle) = r.user_handle AND w.status = 'PAID'), 0),
               r.withdrawable_credits
        FROM public.user_rewards r
        ORDER BY r.withdrawable_credits DESC;
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_user_earnings_overview() TO authenticated;
-- ============================================================
-- 14. ADMIN: AUDIT LOG + FRAUD FLAGS
-- ============================================================
CREATE OR REPLACE FUNCTION public.admin_audit_log(p_limit INTEGER DEFAULT 200) RETURNS SETOF public.wallet_audit_log
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF NOT (public.wallet_is_staff()) THEN RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501'; END IF;
    RETURN QUERY SELECT l.* FROM public.wallet_audit_log l ORDER BY l.created_at DESC LIMIT GREATEST(1, LEAST(500, p_limit));
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_audit_log(INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION public.admin_fraud_flags(p_include_resolved BOOLEAN DEFAULT FALSE) RETURNS SETOF public.wallet_fraud_flags
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role IN ('SUPER_ADMIN','ADMIN','MANAGER')) THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY SELECT f.* FROM public.wallet_fraud_flags f
        WHERE (p_include_resolved OR f.resolved = FALSE)
        ORDER BY f.created_at DESC;
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_fraud_flags(BOOLEAN) TO authenticated;

-- Super Admin resolves a flag after manual review.
CREATE OR REPLACE FUNCTION public.admin_resolve_fraud_flag(
    p_flag_id TEXT,
    p_reason TEXT DEFAULT ''
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_admin TEXT; v_target TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied: Super Admin only' USING ERRCODE = '42501';
    END IF;
    v_admin := public.wallet_current_handle();
    SELECT user_handle INTO v_target FROM public.wallet_fraud_flags WHERE id = p_flag_id;
    IF v_target IS NULL THEN RAISE EXCEPTION 'Flag not found' USING ERRCODE = 'P0002'; END IF;
    UPDATE public.wallet_fraud_flags SET resolved = TRUE, resolved_by = v_admin, resolved_at = NOW()
        WHERE id = p_flag_id;
    INSERT INTO public.wallet_audit_log (id, admin_handle, target_handle, action, field_name, previous_value, new_value, reason, reference)
    VALUES ('AUD-' || md5(random()::text || clock_timestamp()::text), v_admin, v_target, 'FLAG_SETTLED',
            'resolved','false','true',COALESCE(p_reason,'Reviewed and resolved'),p_flag_id);
    RETURN jsonb_build_object('ok', TRUE, 'flag_id', p_flag_id);
END;
$$;
GRANT EXECUTE ON FUNCTION public.admin_resolve_fraud_flag(TEXT, TEXT) TO authenticated;
-- ============================================================
-- 15. FRAUD DETECTION (Super Admin run; only FLAGS, never deletes users)
-- ============================================================
-- Schema helper: returns TRUE if the given column exists on a public table.
-- Lets the wallet system tolerate legacy tables whose layout differs from repo migrations.
CREATE OR REPLACE FUNCTION public.wallet_has_column(p_table TEXT, p_column TEXT) RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = p_table AND column_name = p_column
    );
$$;

CREATE OR REPLACE FUNCTION public.detect_suspicious_activity() RETURNS SETOF public.wallet_fraud_flags
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_rec RECORD;
    v_flag_id TEXT;
    v_sql TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users u WHERE u.uid = auth.uid()::text AND u.role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied: Super Admin only' USING ERRCODE = '42501';
    END IF;

    -- Helper: does a column exist on a table? (live schema may differ from repo migrations)
    -- We use dynamic SQL below so a legacy table layout can never break creation or the scan.
    -- A) Abnormally high watch reward volume in 1 day.
    --    Adapts to the live reel_activities columns (user_handle / credits_earned / timestamp may vary).
    IF to_regclass('public.reel_activities') IS NOT NULL
       AND public.wallet_has_column('reel_activities', 'user_handle') THEN
        v_sql := 'SELECT user_handle, COUNT(*) AS n'
              || CASE WHEN public.wallet_has_column('reel_activities', 'credits_earned')
                      THEN ', SUM(credits_earned) AS coins' ELSE ', 0 AS coins' END
              || ' FROM public.reel_activities WHERE 1=1 '
              || CASE WHEN public.wallet_has_column('reel_activities', 'timestamp')
                      THEN 'AND timestamp >= ' || (EXTRACT(EPOCH FROM NOW() - interval '1 day') * 1000)::bigint::text
                      ELSE 'AND created_at >= NOW() - interval ''1 day''' END
              || ' GROUP BY user_handle HAVING COUNT(*) > 500';
        FOR v_rec IN EXECUTE v_sql LOOP
            v_flag_id := 'FRAUD-' || md5(v_rec.user_handle::text || 'HIGH_WATCH');
            INSERT INTO public.wallet_fraud_flags (id, user_handle, flag_type, severity, description)
            VALUES (v_flag_id, v_rec.user_handle, 'HIGH_WATCH_REWARD', 'HIGH',
                    format('%s watch events (%s coins) in the last 24h', v_rec.n, v_rec.coins))
            ON CONFLICT (user_handle, flag_type) DO NOTHING;
        END LOOP;
    END IF;

    -- B) Referral abuse: an unusually high number of referred users.
    FOR v_rec IN
        SELECT user_handle, referred_users_count FROM public.user_rewards
        WHERE referred_users_count > 200
    LOOP
        v_flag_id := 'FRAUD-' || md5(v_rec.user_handle::text || 'REFERRAL_ABUSE');
        INSERT INTO public.wallet_fraud_flags (id, user_handle, flag_type, severity, description)
        VALUES (v_flag_id, v_rec.user_handle, 'REFERRAL_ABUSE', 'MEDIUM',
                format('Referred %s users (abnormally high)', v_rec.referred_users_count))
        ON CONFLICT (user_handle, flag_type) DO NOTHING;
    END LOOP;

    -- C) Duplicate reward claims: many identical SIGNED transactions in 1h.
    FOR v_rec IN
        SELECT user_handle, source, coin_amount, COUNT(*) AS n
        FROM public.wallet_transactions
        WHERE created_at >= NOW() - interval '1 hour'
        GROUP BY user_handle, source, coin_amount
        HAVING COUNT(*) >= 25
    LOOP
        v_flag_id := 'FRAUD-' || md5(v_rec.user_handle::text || 'DUPLICATE_CLAIM');
        INSERT INTO public.wallet_fraud_flags (id, user_handle, flag_type, severity, description)
        VALUES (v_flag_id, v_rec.user_handle, 'DUPLICATE_CLAIM', 'MEDIUM',
                format('%s identical transactions of %s coins from %s in 1h', v_rec.n, v_rec.coin_amount, v_rec.source))
        ON CONFLICT (user_handle, flag_type) DO NOTHING;
    END LOOP;

    -- D) Unusual withdrawal activity: many withdrawal requests in 7 days.
    --    Adapts to the live withdrawals table (may predate the user_handle column).
    IF to_regclass('public.withdrawals') IS NOT NULL THEN
        v_sql := 'SELECT '
              || CASE WHEN public.wallet_has_column('withdrawals', 'user_handle') THEN 'user_handle'
                      WHEN public.wallet_has_column('withdrawals', 'user_email') THEN 'user_email AS user_handle'
                      ELSE 'NULL::text AS user_handle' END
              || ', COUNT(*) AS n FROM public.withdrawals WHERE '
              || CASE WHEN public.wallet_has_column('withdrawals', 'created_at')
                      THEN 'created_at >= NOW() - interval ''7 days''' ELSE 'TRUE' END
              || CASE WHEN public.wallet_has_column('withdrawals', 'user_handle')
                         OR public.wallet_has_column('withdrawals', 'user_email')
                      THEN ' GROUP BY 1 HAVING COUNT(*) >= 10'
                      ELSE ' GROUP BY 1 HAVING COUNT(*) >= 10 AND 1=0' END; -- no identity column: skip
        FOR v_rec IN EXECUTE v_sql LOOP
            IF v_rec.user_handle IS NULL THEN CONTINUE; END IF;
            v_flag_id := 'FRAUD-' || md5(v_rec.user_handle::text || 'UNUSUAL_WITHDRAWAL');
            INSERT INTO public.wallet_fraud_flags (id, user_handle, flag_type, severity, description)
            VALUES (v_flag_id, v_rec.user_handle, 'UNUSUAL_WITHDRAWAL', 'HIGH',
                    format('%s withdrawal requests in the last 7 days', v_rec.n))
            ON CONFLICT (user_handle, flag_type) DO NOTHING;
        END LOOP;
    END IF;

    RETURN QUERY SELECT f.* FROM public.wallet_fraud_flags f WHERE f.resolved = FALSE ORDER BY f.created_at DESC;
END;
$$;
GRANT EXECUTE ON FUNCTION public.detect_suspicious_activity() TO authenticated;

-- Refresh PostgREST schema cache so the app sees the new tables/functions immediately.
NOTIFY pgrst, 'reload schema';