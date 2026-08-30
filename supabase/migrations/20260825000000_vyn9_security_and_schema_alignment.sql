ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS time_ago TEXT;
UPDATE public.posts SET time_ago = 'Just now' WHERE time_ago IS NULL;

DELETE FROM public.posts
WHERE post_image_res LIKE 'img_%';

ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS location    TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS effect_name TEXT;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS likes_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS is_liked    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS is_saved    BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS time      TEXT;
ALTER TABLE public.chat_messages ADD COLUMN IF NOT EXISTS reactions TEXT;

ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS username TEXT;
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS time_ago  TEXT;

CREATE TABLE IF NOT EXISTS public.stories (
    id BIGSERIAL PRIMARY KEY,
    username TEXT NOT NULL,
    user_avatar_type TEXT NOT NULL DEFAULT 'default',
    image_res TEXT NOT NULL,
    caption TEXT NOT NULL DEFAULT '',
    is_own BOOLEAN NOT NULL DEFAULT FALSE,
    timestamp BIGINT NOT NULL DEFAULT
        (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS username TEXT NOT NULL DEFAULT '';
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS user_avatar_type TEXT NOT NULL DEFAULT 'default';
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS image_res TEXT NOT NULL DEFAULT '';
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS caption TEXT NOT NULL DEFAULT '';
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS is_own BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS timestamp BIGINT NOT NULL DEFAULT
    (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;

DELETE FROM public.stories
WHERE image_res LIKE 'img_%'
    OR image_res = '';

CREATE TABLE IF NOT EXISTS public.call_signals (
    id              TEXT PRIMARY KEY,
    caller_handle   TEXT NOT NULL,
    caller_name     TEXT,
    caller_avatar   TEXT DEFAULT 'default',
    receiver_handle TEXT NOT NULL,
    call_type       TEXT NOT NULL DEFAULT 'VIDEO',
    status          TEXT NOT NULL DEFAULT 'OFFERING',
    sdp             TEXT,
    timestamp       BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.follows (
    id            BIGSERIAL PRIMARY KEY,
    follower_uid  TEXT NOT NULL,
    following_uid TEXT NOT NULL,
    is_following  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (follower_uid, following_uid)
);
CREATE INDEX IF NOT EXISTS idx_follows_follower  ON public.follows (follower_uid);
CREATE INDEX IF NOT EXISTS idx_follows_following ON public.follows (following_uid);

CREATE TABLE IF NOT EXISTS public.chat_message_reads (
    id         BIGSERIAL PRIMARY KEY,
    message_id TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    read_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (message_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_chat_message_reads_user ON public.chat_message_reads (user_id);

CREATE OR REPLACE FUNCTION public.increment_post_comments(target_post_id BIGINT)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    UPDATE public.posts SET comments_count = COALESCE(comments_count,0) + 1 WHERE id = target_post_id;
END;
$$;

ALTER TABLE public.call_signals       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.follows            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_message_reads ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS call_signals_select_participants ON public.call_signals;
CREATE POLICY call_signals_select_participants ON public.call_signals
    FOR SELECT USING (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS call_signals_insert_self ON public.call_signals;
CREATE POLICY call_signals_insert_self ON public.call_signals
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS call_signals_update_participants ON public.call_signals;
CREATE POLICY call_signals_update_participants ON public.call_signals
    FOR UPDATE USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS follows_select_authenticated ON public.follows;
CREATE POLICY follows_select_authenticated ON public.follows
    FOR SELECT USING (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS follows_insert_own ON public.follows;
CREATE POLICY follows_insert_own ON public.follows
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL AND follower_uid = auth.uid()::text);
DROP POLICY IF EXISTS follows_update_own ON public.follows;
CREATE POLICY follows_update_own ON public.follows
    FOR UPDATE USING (auth.uid() IS NOT NULL AND follower_uid = auth.uid()::text);

DROP POLICY IF EXISTS chat_message_reads_select_own ON public.chat_message_reads;
CREATE POLICY chat_message_reads_select_own ON public.chat_message_reads
    FOR SELECT USING (auth.uid()::text = user_id);
DROP POLICY IF EXISTS chat_message_reads_insert_own ON public.chat_message_reads;
CREATE POLICY chat_message_reads_insert_own ON public.chat_message_reads
    FOR INSERT WITH CHECK (auth.uid()::text = user_id);

CREATE OR REPLACE FUNCTION public.block_client_role_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('request.jwt.claim.role', true) IS NOT DISTINCT FROM 'service_role' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Changing role/permissions requires server-side authorization' USING ERRCODE = '42501';
END;
$$;

DROP TRIGGER IF EXISTS trg_app_users_block_role_change ON public.app_users;
CREATE TRIGGER trg_app_users_block_role_change
    BEFORE UPDATE OF role, can_manage_users, can_delete_posts, can_edit_posts,
                     can_moderate_comments, can_manage_chats, can_manage_monetization,
                     can_manage_rewards, can_clean_storage, is_banned, ban_reason
    ON public.app_users FOR EACH ROW
    EXECUTE FUNCTION public.block_client_role_change();

REVOKE UPDATE (role, can_manage_users, can_delete_posts, can_edit_posts,
               can_moderate_comments, can_manage_chats, can_manage_monetization,
               can_manage_rewards, can_clean_storage, is_banned, ban_reason)
    ON public.app_users FROM anon, authenticated;

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['chat_messages', 'posts', 'reels', 'app_users', 'stories'] LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_publication_tables
            WHERE pubname = 'supabase_realtime'
              AND schemaname = 'public'
              AND tablename = table_name
        ) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', table_name);
        END IF;
    END LOOP;
END $$;

