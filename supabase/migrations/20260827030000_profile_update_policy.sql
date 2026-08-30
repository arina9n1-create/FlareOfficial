-- Allow an authenticated user to persist only their own profile fields.
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS app_users_select_authenticated ON public.app_users;
CREATE POLICY app_users_select_authenticated ON public.app_users
    FOR SELECT TO authenticated
    USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS app_users_insert_own ON public.app_users;
CREATE POLICY app_users_insert_own ON public.app_users
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = uid);

DROP POLICY IF EXISTS app_users_update_own_profile ON public.app_users;
CREATE POLICY app_users_update_own_profile ON public.app_users
    FOR UPDATE TO authenticated
    USING (auth.uid()::text = uid)
    WITH CHECK (auth.uid()::text = uid);