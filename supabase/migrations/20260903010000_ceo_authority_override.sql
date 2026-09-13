-- =============================================================================
-- CEO AUTHORITY OVERRIDE & NUCLEAR FIREWALL (BOT-KILLER v2)
-- -----------------------------------------------------------------------------
-- 1) FORCE PROMOTES @ceo
-- 2) BLOCKS ANY NEW 'RUNNER' SIGNUPS AT THE AUTH LEVEL (THE FIREWALL)
-- 3) PURGES ALL EXISTING BOTS PERMANENTLY
-- =============================================================================

-- 1) FORCE DROP the trigger to allow changes
DROP TRIGGER IF EXISTS trg_app_users_block_role_change ON public.app_users;

-- 2) Promote @ceo to SUPER_ADMIN
UPDATE public.app_users
SET role = 'SUPER_ADMIN', can_manage_users = true, can_delete_posts = true, can_edit_posts = true,
    can_moderate_comments = true, can_manage_chats = true, can_manage_monetization = true,
    can_manage_rewards = true, can_clean_storage = true
WHERE lower(handle) = 'ceo';

-- 3) Re-create a SMARTER role-change trigger
CREATE OR REPLACE FUNCTION public.block_client_role_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN') THEN RETURN NEW; END IF;
    IF current_setting('request.jwt.claim.role', true) IS NOT DISTINCT FROM 'service_role' THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Changing role/permissions requires Super Admin authorization' USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_app_users_block_role_change
    BEFORE UPDATE OF role, can_manage_users, can_delete_posts, can_edit_posts,
                     can_moderate_comments, can_manage_chats, can_manage_monetization,
                     can_manage_rewards, can_clean_storage, is_banned, ban_reason
    ON public.app_users FOR EACH ROW EXECUTE FUNCTION public.block_client_role_change();

-- -----------------------------------------------------------------------------
-- 4) THE NUCLEAR FIREWALL: Block Signups at the AUTH level
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.trg_auth_firewall_bot_blocker()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- If email or metadata contains 'runner' or 'scan', REJECT the signup entirely.
    IF lower(NEW.email) LIKE '%runner%' OR lower(NEW.email) LIKE '%scan%'
       OR lower(COALESCE(NEW.raw_user_meta_data->>'handle', '')) LIKE '%runner%'
       OR lower(COALESCE(NEW.raw_user_meta_data->>'handle', '')) LIKE '%scan%'
    THEN
        RAISE EXCEPTION 'Signup Blocked: Automated accounts are not allowed on this project' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auth_signup_firewall ON auth.users;
CREATE TRIGGER trg_auth_signup_firewall
    BEFORE INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.trg_auth_firewall_bot_blocker();

-- -----------------------------------------------------------------------------
-- 5) THE PURGE: Destroy existing bots in one shot
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT id FROM auth.users
        WHERE lower(email) LIKE '%runner%'
           OR lower(email) LIKE '%scan%'
           OR lower(COALESCE(raw_user_meta_data->>'handle', '')) LIKE '%runner%'
           OR lower(COALESCE(raw_user_meta_data->>'handle', '')) LIKE '%scan%'
    LOOP
        DELETE FROM public.app_users WHERE uid = r.id::text;
        DELETE FROM auth.users WHERE id = r.id;
    END LOOP;
END $$;

-- 6) HARDEN Mass Delete
CREATE OR REPLACE FUNCTION public.admin_delete_all_except_ceo(ceo_handle TEXT)
RETURNS INT LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_deleted INT; r RECORD; v_ceo_uid TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;
    SELECT uid INTO v_ceo_uid FROM public.app_users WHERE lower(handle) = lower(btrim(ceo_handle));
    v_deleted := 0;
    FOR r IN SELECT id::text as uid FROM auth.users WHERE id::text <> auth.uid()::text AND (v_ceo_uid IS NULL OR id::text <> v_ceo_uid)
    LOOP
        BEGIN
            DELETE FROM public.app_users WHERE uid = r.uid;
            DELETE FROM auth.users WHERE id = r.uid::uuid;
            v_deleted := v_deleted + 1;
        EXCEPTION WHEN OTHERS THEN NULL; END;
    END LOOP;
    RETURN v_deleted;
END;
$$;
