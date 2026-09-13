-- =====================================================================
-- Fix: "permission denied for table personal_id_conversation_members"
-- Storage policies on personal-id-media queried that table directly as
-- `authenticated`, which has all grants REVOKED. All member checks now
-- go through the SECURITY DEFINER function pn_is_member(uuid).
-- =====================================================================

CREATE OR REPLACE FUNCTION public.pn_is_member(p_conversation_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE me public.personal_ids;
BEGIN
    IF auth.uid() IS NULL THEN RETURN FALSE; END IF;
    me := public.pn_me();
    IF me.id IS NULL THEN RETURN FALSE; END IF;
    RETURN EXISTS (
        SELECT 1 FROM public.personal_id_conversation_members
        WHERE conversation_id = p_conversation_id AND personal_id = me.id
    );
END;
$fn$;

REVOKE ALL ON FUNCTION public.pn_is_member(uuid) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.pn_is_member(uuid) TO authenticated;

DROP POLICY IF EXISTS "personal_id_media_select_member" ON storage.objects;
CREATE POLICY "personal_id_media_select_member"
    ON storage.objects FOR SELECT USING (
        bucket_id = 'personal-id-media'
        AND auth.role() = 'authenticated'
        AND public.pn_is_member(((storage.foldername(name))[1])::uuid)
    );

DROP POLICY IF EXISTS "personal_id_media_insert_member" ON storage.objects;
CREATE POLICY "personal_id_media_insert_member"
    ON storage.objects FOR INSERT WITH CHECK (
        bucket_id = 'personal-id-media'
        AND auth.role() = 'authenticated'
        AND public.pn_is_member(((storage.foldername(name))[1])::uuid)
    );
