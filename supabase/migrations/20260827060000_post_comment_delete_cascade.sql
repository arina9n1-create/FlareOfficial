-- Permanently delete comments with their parent post. No soft-delete state is used.

DO $$
DECLARE
    fk RECORD;
    definition TEXT;
BEGIN
    FOR fk IN
        SELECT con.conname AS name,
               con.conrelid::regclass AS child_table,
               pg_get_constraintdef(con.oid) AS definition
        FROM pg_constraint con
        WHERE con.contype = 'f'
          AND con.confrelid = 'public.posts'::regclass
          AND con.conrelid = 'public.comments'::regclass
    LOOP
        definition := regexp_replace(
            fk.definition,
            '\s+ON DELETE\s+(NO ACTION|RESTRICT|CASCADE|SET NULL|SET DEFAULT)',
            '',
            'gi'
        );
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.child_table, fk.name);
        EXECUTE format(
            'ALTER TABLE %s ADD CONSTRAINT %I %s ON DELETE CASCADE',
            fk.child_table,
            fk.name,
            trim(definition)
        );
    END LOOP;
END $$;

DO $$
BEGIN
    IF to_regclass('public.posts') IS NOT NULL
       AND to_regclass('public.comments') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'comments_post_id_fkey'
       ) THEN
        ALTER TABLE public.comments
            ADD CONSTRAINT comments_post_id_fkey
            FOREIGN KEY (post_id) REFERENCES public.posts(id)
            ON DELETE CASCADE;
    END IF;
END $$;