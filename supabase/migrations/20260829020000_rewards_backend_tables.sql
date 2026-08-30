-- VYN9 Rewards Backend Tables (fixed order: tables first, then policies)
-- Tables used by the Admin Panel & Reward system:
--   withdrawals   -> coin payout requests (Admin approve/reject)
--   admin_configs -> global reward config (Rules / Rates / Gateways)
--   user_rewards  -> per-user coin wallet (refund target)
--   refund_withdrawal RPC -> credits coins back when an admin rejects a payout
--
-- Safe to re-run. Run this in the Supabase SQL Editor.

-- ============================================================
-- 1. WITHDRAWALS TABLE + MISSING COLUMN FIX
-- ============================================================
CREATE TABLE IF NOT EXISTS public.withdrawals (
    id               TEXT PRIMARY KEY,
    user_handle      TEXT NOT NULL DEFAULT '',
    user_email       TEXT NOT NULL DEFAULT '',
    method           TEXT NOT NULL DEFAULT '',
    account_number   TEXT NOT NULL DEFAULT '',
    credits_used     INTEGER NOT NULL DEFAULT 0,
    amount_usd       NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_bdt       NUMERIC(12,2) NOT NULL DEFAULT 0,
    status           TEXT NOT NULL DEFAULT 'PENDING',
    request_date     TEXT NOT NULL DEFAULT '',
    transaction_note TEXT NOT NULL DEFAULT '',
    timestamp        BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.withdrawals ALTER COLUMN id TYPE text USING id::text;

ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS user_handle      TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS user_email       TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS method           TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS account_number   TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS credits_used     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS amount_usd       NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS amount_bdt       NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS status           TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS request_date     TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS transaction_note TEXT NOT NULL DEFAULT '';
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS timestamp        BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.withdrawals ADD COLUMN IF NOT EXISTS created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE public.withdrawals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "withdrawals_select_own_or_staff" ON public.withdrawals;
DROP POLICY IF EXISTS "withdrawals_insert_own_or_staff" ON public.withdrawals;
DROP POLICY IF EXISTS "withdrawals_update_staff" ON public.withdrawals;

CREATE POLICY "withdrawals_select_own_or_staff"
    ON public.withdrawals FOR SELECT
    TO authenticated
    USING (
        user_handle = (SELECT handle FROM public.app_users WHERE uid = auth.uid()::text)
        OR EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'MODERATOR')
        )
    );

CREATE POLICY "withdrawals_insert_own_or_staff"
    ON public.withdrawals FOR INSERT
    TO authenticated
    WITH CHECK (
        user_handle = (SELECT handle FROM public.app_users WHERE uid = auth.uid()::text)
        OR EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
        )
    );

CREATE POLICY "withdrawals_update_staff"
    ON public.withdrawals FOR UPDATE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
        )
    );

-- ============================================================
-- 2. ADMIN CONFIGS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.admin_configs (
    id                          TEXT PRIMARY KEY,
    credits_per_reel            INTEGER NOT NULL DEFAULT 2,
    required_reel_watch_seconds INTEGER NOT NULL DEFAULT 10,
    credits_per_dollar          INTEGER NOT NULL DEFAULT 2000,
    referral_bonus_credits      INTEGER NOT NULL DEFAULT 500,
    min_withdrawal_usd          NUMERIC(12,2) NOT NULL DEFAULT 1.0,
    is_bkash_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    is_nagad_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    is_rocket_enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    is_binance_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    is_paypal_enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    notice_message              TEXT NOT NULL DEFAULT '',
    updated_at                  BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS credits_per_reel            INTEGER NOT NULL DEFAULT 2;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS required_reel_watch_seconds INTEGER NOT NULL DEFAULT 10;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS credits_per_dollar          INTEGER NOT NULL DEFAULT 2000;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS referral_bonus_credits      INTEGER NOT NULL DEFAULT 500;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS min_withdrawal_usd          NUMERIC(12,2) NOT NULL DEFAULT 1.0;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS is_bkash_enabled            BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS is_nagad_enabled            BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS is_rocket_enabled           BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS is_binance_enabled          BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS is_paypal_enabled           BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS notice_message              TEXT NOT NULL DEFAULT '';
ALTER TABLE public.admin_configs ADD COLUMN IF NOT EXISTS updated_at                  BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.admin_configs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_configs_select_all" ON public.admin_configs;
DROP POLICY IF EXISTS "admin_configs_write_staff" ON public.admin_configs;
DROP POLICY IF EXISTS "admin_configs_update_staff" ON public.admin_configs;

CREATE POLICY "admin_configs_select_all"
    ON public.admin_configs FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "admin_configs_write_staff"
    ON public.admin_configs FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
        )
    );

CREATE POLICY "admin_configs_update_staff"
    ON public.admin_configs FOR UPDATE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
        )
    );

-- ============================================================
-- 3. USER REWARDS (coin wallet)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.user_rewards (
    user_handle           TEXT PRIMARY KEY,
    total_credits         INTEGER NOT NULL DEFAULT 0,
    today_reels_watched   INTEGER NOT NULL DEFAULT 0,
    today_reels_uploaded  INTEGER NOT NULL DEFAULT 0,
    total_reels_watched   INTEGER NOT NULL DEFAULT 0,
    total_reels_uploaded  INTEGER NOT NULL DEFAULT 0,
    referral_code         TEXT NOT NULL DEFAULT '',
    referred_users_count  INTEGER NOT NULL DEFAULT 0,
    referral_earnings     INTEGER NOT NULL DEFAULT 0,
    updated_at            BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS total_credits        INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS today_reels_watched  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS today_reels_uploaded INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS total_reels_watched  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS total_reels_uploaded INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referral_code        TEXT NOT NULL DEFAULT '';
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referred_users_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS referral_earnings    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.user_rewards ADD COLUMN IF NOT EXISTS updated_at           BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.user_rewards ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_rewards_select_own_or_staff" ON public.user_rewards;
DROP POLICY IF EXISTS "user_rewards_write_own" ON public.user_rewards;
DROP POLICY IF EXISTS "user_rewards_update_own_or_staff" ON public.user_rewards;

CREATE POLICY "user_rewards_select_own_or_staff"
    ON public.user_rewards FOR SELECT
    TO authenticated
    USING (
        user_handle = (SELECT handle FROM public.app_users WHERE uid = auth.uid()::text)
        OR EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
        )
    );

-- Users upsert their own wallet row (wallet sync)
CREATE POLICY "user_rewards_write_own"
    ON public.user_rewards FOR INSERT
    TO authenticated
    WITH CHECK (
        user_handle = (SELECT handle FROM public.app_users WHERE uid = auth.uid()::text)
    );

CREATE POLICY "user_rewards_update_own_or_staff"
    ON public.user_rewards FOR UPDATE
    TO authenticated
    USING (
        user_handle = (SELECT handle FROM public.app_users WHERE uid = auth.uid()::text)
        OR EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.uid = auth.uid()::text
              AND u.role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
        )
    );

-- ============================================================
-- 4. REFUND RPC — credits coins back when an admin rejects a payout
--    SECURITY DEFINER so the refund works regardless of RLS on user_rewards
-- ============================================================
CREATE OR REPLACE FUNCTION public.refund_withdrawal(
    p_user_handle TEXT,
    p_credits INTEGER
)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF p_credits IS NULL OR p_credits <= 0 THEN
        RETURN;
    END IF;

    INSERT INTO public.user_rewards (user_handle, total_credits, updated_at)
    VALUES (lower(p_user_handle), p_credits, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT)
    ON CONFLICT (user_handle) DO UPDATE
        SET total_credits = public.user_rewards.total_credits + EXCLUDED.total_credits,
            updated_at = EXCLUDED.updated_at;
END;
$$;

GRANT EXECUTE ON FUNCTION public.refund_withdrawal(TEXT, INTEGER) TO authenticated;

-- Refresh PostgREST schema cache so the app sees the new tables immediately
NOTIFY pgrst, 'reload schema';