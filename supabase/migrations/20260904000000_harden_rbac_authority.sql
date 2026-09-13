-- =============================================================================
-- RBAC AUTHORITY HARDENING & CEO PROTECTION
-- -----------------------------------------------------------------------------
-- Ensures granular permissions are correctly applied and protected.
-- =============================================================================

-- 1) Promote @ceo to SUPER_ADMIN with all flags true
UPDATE public.app_users
SET role = 'SUPER_ADMIN',
    can_manage_users = true,
    can_delete_posts = true,
    can_edit_posts = true,
    can_moderate_comments = true,
    can_manage_chats = true,
    can_manage_monetization = true,
    can_manage_rewards = true,
    can_clean_storage = true
WHERE lower(handle) = 'ceo';

-- 2) Update promote_user to ensure it updates ALL permission flags
CREATE OR REPLACE FUNCTION public.promote_user(
    target_uid TEXT,
    new_role TEXT,
    new_can_manage_users BOOLEAN,
    new_can_delete_posts BOOLEAN,
    new_can_edit_posts BOOLEAN,
    new_can_moderate_comments BOOLEAN,
    new_can_manage_chats BOOLEAN,
    new_can_manage_monetization BOOLEAN,
    new_can_manage_rewards BOOLEAN,
    new_can_clean_storage BOOLEAN
)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    -- Only allow if the CALLER is a SUPER_ADMIN
    IF NOT EXISTS (
        SELECT 1 FROM public.app_users
        WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN'
    ) THEN
        RAISE EXCEPTION 'Access Denied: Only Super Admins can change roles' USING ERRCODE = '42501';
    END IF;

    -- PROTECT CEO: Nobody can change @ceo's role or permissions
    IF EXISTS (SELECT 1 FROM public.app_users WHERE uid = target_uid AND lower(handle) = 'ceo') THEN
        RAISE EXCEPTION 'Access Denied: The CEO account cannot be modified' USING ERRCODE = '42501';
    END IF;

    -- Safety: Cannot demote self from SUPER_ADMIN
    IF auth.uid()::text = target_uid AND new_role <> 'SUPER_ADMIN' THEN
         RAISE EXCEPTION 'Access Denied: You cannot demote yourself' USING ERRCODE = '42501';
    END IF;

    UPDATE public.app_users
    SET
        role = new_role,
        can_manage_users = new_can_manage_users,
        can_delete_posts = new_can_delete_posts,
        can_edit_posts = new_can_edit_posts,
        can_moderate_comments = new_can_moderate_comments,
        can_manage_chats = new_can_manage_chats,
        can_manage_monetization = new_can_manage_monetization,
        can_manage_rewards = new_can_manage_rewards,
        can_clean_storage = new_can_clean_storage
    WHERE uid = target_uid;
END;
$$;
