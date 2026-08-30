-- ==============================================================================
-- VYN9 NATIVE MONETIZATION & CREATOR REVENUE ATTRIBUTION SYSTEM
-- SUPABASE / POSTGRESQL AUTHORITATIVE SCHEMA & RLS POLICIES (PHASE 1 + PHASE 2)
-- ==============================================================================

-- 1. Monetization Settings Table
CREATE TABLE IF NOT EXISTS public.monetization_settings (
    id TEXT PRIMARY KEY DEFAULT 'global',
    enable_monetization BOOLEAN NOT NULL DEFAULT TRUE,
    enable_monetization_application BOOLEAN NOT NULL DEFAULT TRUE,
    enable_content_monetization BOOLEAN NOT NULL DEFAULT TRUE,
    enable_post_monetization BOOLEAN NOT NULL DEFAULT TRUE,
    enable_video_monetization BOOLEAN NOT NULL DEFAULT TRUE,
    enable_reels_monetization BOOLEAN NOT NULL DEFAULT TRUE,
    minimum_followers INT NOT NULL DEFAULT 1000,
    minimum_views INT NOT NULL DEFAULT 10000,
    enable_earnings_wallet BOOLEAN NOT NULL DEFAULT TRUE,
    enable_add_fund BOOLEAN NOT NULL DEFAULT TRUE,
    enable_withdraw BOOLEAN NOT NULL DEFAULT TRUE,
    minimum_add_fund NUMERIC(10,2) NOT NULL DEFAULT 5.00,
    maximum_add_fund NUMERIC(10,2) NOT NULL DEFAULT 1000.00,
    minimum_withdrawal NUMERIC(10,2) NOT NULL DEFAULT 10.00,
    maximum_withdrawal NUMERIC(10,2) NOT NULL DEFAULT 5000.00,
    -- Phase 2 Revenue Share & Pool Settings
    creator_revenue_share NUMERIC(5,2) NOT NULL DEFAULT 50.00, -- e.g. 50%
    platform_revenue_share NUMERIC(5,2) NOT NULL DEFAULT 50.00, -- e.g. 50%
    post_revenue_pool_share NUMERIC(5,2) NOT NULL DEFAULT 30.00, -- e.g. 30%
    video_revenue_pool_share NUMERIC(5,2) NOT NULL DEFAULT 40.00, -- e.g. 40%
    reels_revenue_pool_share NUMERIC(5,2) NOT NULL DEFAULT 30.00, -- e.g. 30%
    allow_estimated_earnings BOOLEAN NOT NULL DEFAULT TRUE,
    auto_finalize_revenue BOOLEAN NOT NULL DEFAULT FALSE,
    revenue_currency TEXT NOT NULL DEFAULT 'USD',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by TEXT DEFAULT 'admin',
    CONSTRAINT chk_revenue_share_sum CHECK ((creator_revenue_share + platform_revenue_share) = 100.00),
    CONSTRAINT chk_pool_share_sum CHECK ((post_revenue_pool_share + video_revenue_pool_share + reels_revenue_pool_share) = 100.00)
);

-- 2. User Monetization Profile Table
CREATE TABLE IF NOT EXISTS public.user_monetization_profiles (
    user_id TEXT PRIMARY KEY,
    user_handle TEXT NOT NULL UNIQUE,
    user_name TEXT NOT NULL,
    monetization_status TEXT NOT NULL DEFAULT 'NOT_MONETIZED', -- 'NOT_MONETIZED', 'PENDING', 'APPROVED', 'REJECTED'
    application_status TEXT NOT NULL DEFAULT 'NOT_APPLIED',     -- 'NOT_APPLIED', 'PENDING', 'APPROVED', 'REJECTED'
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    rejection_reason TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Monetization Applications Table
CREATE TABLE IF NOT EXISTS public.monetization_applications (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_handle TEXT NOT NULL,
    user_name TEXT NOT NULL,
    user_avatar_type TEXT DEFAULT 'default',
    follower_count_at_application INT NOT NULL DEFAULT 0,
    view_count_at_application INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    rejection_reason TEXT DEFAULT '',
    reviewed_by TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_monetization_apps_user_status ON public.monetization_applications(user_id, status);

-- 4. Earnings Wallet Table
-- CRITICAL RULE: Add Fund money does NOT increase lifetime_earnings.
-- lifetime_earnings strictly records actual creator monetization earnings.
CREATE TABLE IF NOT EXISTS public.earnings_wallets (
    user_id TEXT PRIMARY KEY,
    available_balance NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    lifetime_earnings NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    total_added_funds NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    total_withdrawn NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Monetization Transactions Table
CREATE TABLE IF NOT EXISTS public.monetization_transactions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_handle TEXT NOT NULL,
    type TEXT NOT NULL, -- 'CREATOR_EARNING', 'ADD_FUND', 'WITHDRAWAL', 'REFUND', 'ADJUSTMENT'
    amount NUMERIC(12,4) NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED', -- 'COMPLETED', 'PENDING', 'FAILED', 'REJECTED'
    reference_id TEXT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_monetization_txns_user ON public.monetization_transactions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_monetization_txns_ref ON public.monetization_transactions(reference_id);

-- 6. Revenue Periods Table
CREATE TABLE IF NOT EXISTS public.revenue_periods (
    id TEXT PRIMARY KEY, -- e.g. 'PERIOD-2026-08'
    name TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'IMPORTED', 'CALCULATING', 'CALCULATED', 'FINALIZED', 'ADJUSTED'
    total_admob_revenue NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    total_creator_pool NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    total_platform_revenue NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    post_pool NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    video_pool NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    reels_pool NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    total_eligible_impressions BIGINT NOT NULL DEFAULT 0,
    post_impressions BIGINT NOT NULL DEFAULT 0,
    video_impressions BIGINT NOT NULL DEFAULT 0,
    reels_impressions BIGINT NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'USD',
    imported_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. AdMob Revenue Reports Table (Official Imported Reporting Data)
CREATE TABLE IF NOT EXISTS public.admob_revenue_reports (
    id TEXT PRIMARY KEY,
    revenue_period_id TEXT NOT NULL REFERENCES public.revenue_periods(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    app_id TEXT NOT NULL DEFAULT 'ca-app-pub-vyn9-prod',
    ad_unit_id TEXT NOT NULL,
    ad_format TEXT NOT NULL, -- 'NATIVE', 'BANNER', 'INTERSTITIAL', 'REWARDED'
    country_code TEXT NOT NULL DEFAULT 'GLOBAL',
    impressions BIGINT NOT NULL DEFAULT 0,
    estimated_earnings NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    currency TEXT NOT NULL DEFAULT 'USD',
    source TEXT NOT NULL DEFAULT 'ADMOB_REPORTING_API',
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_admob_report_dimensions UNIQUE(revenue_period_id, report_date, ad_unit_id, ad_format, country_code)
);

CREATE INDEX IF NOT EXISTS idx_admob_reports_period ON public.admob_revenue_reports(revenue_period_id);

-- 8. Configurable AdMob Ad Unit Mapping Table
CREATE TABLE IF NOT EXISTS public.admob_ad_unit_mapping (
    id TEXT PRIMARY KEY,
    ad_unit_id TEXT NOT NULL UNIQUE,
    placement TEXT NOT NULL, -- 'CONTENT_FEED_NATIVE', 'CONTENT_FEED_BANNER', 'VIDEO_NATIVE', 'VIDEO_BANNER', 'REELS_NATIVE', 'REELS_BANNER'
    content_type TEXT NOT NULL, -- 'POST', 'VIDEO', 'REEL'
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. Content Ad Impressions Table (Recorded Only On Genuine Ad Displays)
CREATE TABLE IF NOT EXISTS public.content_ad_impressions (
    id TEXT PRIMARY KEY,
    content_id TEXT NOT NULL,
    creator_id TEXT NOT NULL,
    content_type TEXT NOT NULL, -- 'POST', 'VIDEO', 'REEL'
    ad_unit_id TEXT NOT NULL,
    placement TEXT NOT NULL,
    session_id TEXT,
    impression_reference TEXT NOT NULL UNIQUE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    date_bucket TEXT NOT NULL, -- 'YYYY-MM'
    country_code TEXT NOT NULL DEFAULT 'GLOBAL',
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    rejection_reason TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_content_impressions_content ON public.content_ad_impressions(content_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_content_impressions_creator ON public.content_ad_impressions(creator_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_content_impressions_bucket ON public.content_ad_impressions(date_bucket, content_type);

-- 10. Content-Level Revenue Attribution Table (Authoritative Calculated Earnings)
CREATE TABLE IF NOT EXISTS public.content_earnings (
    id TEXT PRIMARY KEY, -- e.g. 'EARN-PERIOD-2026-08-POST-101'
    revenue_period_id TEXT NOT NULL REFERENCES public.revenue_periods(id) ON DELETE CASCADE,
    content_id TEXT NOT NULL,
    creator_id TEXT NOT NULL,
    user_handle TEXT NOT NULL,
    content_type TEXT NOT NULL, -- 'POST', 'VIDEO', 'REEL'
    content_title TEXT NOT NULL,
    content_thumbnail_res TEXT DEFAULT 'default',
    content_views BIGINT NOT NULL DEFAULT 0,
    eligible_impressions BIGINT NOT NULL DEFAULT 0,
    total_type_impressions BIGINT NOT NULL DEFAULT 0,
    attribution_share NUMERIC(10,6) NOT NULL DEFAULT 0.000000, -- e.g. 0.054231
    allocated_revenue NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    creator_share NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    platform_share NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    status TEXT NOT NULL DEFAULT 'ESTIMATED', -- 'ESTIMATED', 'FINALIZED', 'ADJUSTED', 'PAID'
    currency TEXT NOT NULL DEFAULT 'USD',
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_content_period_earning UNIQUE(revenue_period_id, content_id)
);

CREATE INDEX IF NOT EXISTS idx_content_earnings_period ON public.content_earnings(revenue_period_id);
CREATE INDEX IF NOT EXISTS idx_content_earnings_creator ON public.content_earnings(creator_id, status);

-- 11. Revenue Adjustments Table (AdMob Audit Reconciliations)
CREATE TABLE IF NOT EXISTS public.revenue_adjustments (
    id TEXT PRIMARY KEY,
    revenue_period_id TEXT NOT NULL REFERENCES public.revenue_periods(id) ON DELETE CASCADE,
    content_earning_id TEXT NOT NULL REFERENCES public.content_earnings(id) ON DELETE CASCADE,
    creator_id TEXT NOT NULL,
    previous_amount NUMERIC(12,4) NOT NULL,
    adjustment_amount NUMERIC(12,4) NOT NULL,
    new_amount NUMERIC(12,4) NOT NULL,
    reason TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'ADMOB_AUDIT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by TEXT NOT NULL DEFAULT 'admin'
);

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

ALTER TABLE public.monetization_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_monetization_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.monetization_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.earnings_wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.monetization_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.revenue_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admob_revenue_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admob_ad_unit_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_ad_impressions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_earnings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.revenue_adjustments ENABLE ROW LEVEL SECURITY;

-- Monetization Settings: Public read, Admin write
CREATE POLICY "Allow public read monetization_settings"
    ON public.monetization_settings FOR SELECT USING (true);

-- User Profiles: Users read own, Admin full access
CREATE POLICY "Allow users read own monetization profile"
    ON public.user_monetization_profiles FOR SELECT
    USING (auth.uid()::text = user_id OR user_handle = current_user);

-- Earnings Wallet: Strict owner isolation
CREATE POLICY "Allow users read own earnings wallet"
    ON public.earnings_wallets FOR SELECT
    USING (auth.uid()::text = user_id);

-- Transactions: Strict owner isolation
CREATE POLICY "Allow users read own transactions"
    ON public.monetization_transactions FOR SELECT
    USING (auth.uid()::text = user_id);

-- Revenue Periods: Public read (creators need to see current period names and status)
CREATE POLICY "Allow public read revenue_periods"
    ON public.revenue_periods FOR SELECT USING (true);

-- Content Earnings: Creators see only their own content earnings
CREATE POLICY "Allow creators read own content earnings"
    ON public.content_earnings FOR SELECT
    USING (auth.uid()::text = creator_id OR user_handle = current_user);
