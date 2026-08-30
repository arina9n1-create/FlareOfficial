-- Clear all existing application data so the project starts with an empty database.
-- This is intentionally destructive and removes every registered Auth account as well.
DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'revenue_adjustments',
        'content_earnings',
        'admob_ad_unit_mapping',
        'admob_revenue_reports',
        'content_ad_impressions',
        'monetization_transactions',
        'revenue_periods',
        'earnings_wallets',
        'monetization_applications',
        'user_monetization_profiles',
        'chat_message_reads',
        'follows',
        'call_signals',
        'comments',
        'notifications',
        'chat_messages',
        'stories',
        'reels',
        'posts',
        'friends',
        'app_users'
    ] LOOP
        IF to_regclass('public.' || table_name) IS NOT NULL THEN
            EXECUTE format('TRUNCATE TABLE public.%I CASCADE', table_name);
        END IF;
    END LOOP;
END $$;

TRUNCATE TABLE auth.users CASCADE;
