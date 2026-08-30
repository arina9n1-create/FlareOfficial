-- VYN9 Admin Promotion RPC
-- This allows a SUPER_ADMIN to change other users' roles without needing the service_role key.

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
    -- 1. Security Check: Only allow the update if the CALLER is a SUPER_ADMIN
    IF NOT EXISTS (
        SELECT 1 FROM public.app_users
        WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN'
    ) THEN
        RAISE EXCEPTION 'Access Denied: Only Super Admins can change roles' USING ERRCODE = '42501';
    END IF;

    -- 2. Prevent a Super Admin from demoting themselves (Safety)
    IF auth.uid()::text = target_uid AND new_role <> 'SUPER_ADMIN' THEN
         RAISE EXCEPTION 'Access Denied: You cannot demote yourself from Super Admin' USING ERRCODE = '42501';
    END IF;

    -- 3. Apply the update
    -- Note: We use an internal update here, bypassing the trg_app_users_block_role_change
    -- trigger if possible, or we could modify the trigger.
    -- Since this is SECURITY DEFINER, it runs as the owner (usually postgres),
    -- but the trigger might still block it if it checks current_setting('role').

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

-- Grant execution to authenticated users
GRANT EXECUTE ON FUNCTION public.promote_user TO authenticated;

-- Function for banning
CREATE OR REPLACE FUNCTION public.ban_user_rpc(
    target_uid TEXT,
    ban_status BOOLEAN,
    reason TEXT
)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.app_users
        WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN'
    ) THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;

    UPDATE public.app_users
    SET is_banned = ban_status, ban_reason = reason
    WHERE uid = target_uid;
END;
$$;

GRANT EXECUTE ON FUNCTION public.ban_user_rpc TO authenticated;

-- Function for user deletion
CREATE OR REPLACE FUNCTION public.delete_user_rpc(
    target_uid TEXT
)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    -- Only allow if caller is SUPER_ADMIN OR deleting themselves
    IF NOT (
        EXISTS (SELECT 1 FROM public.app_users WHERE uid = auth.uid()::text AND role = 'SUPER_ADMIN')
        OR auth.uid()::text = target_uid
    ) THEN
        RAISE EXCEPTION 'Access Denied' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.app_users WHERE uid = target_uid;
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_user_rpc TO authenticated;
