-- 修复删除人员/会话时被 behavior_logs 外键拦截的问题
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/003_fk_on_delete_set_null.sql

BEGIN;

-- 确保可置空（老库若 person_id NOT NULL 会导致 SET NULL 失败）
ALTER TABLE presence_sessions ALTER COLUMN person_id DROP NOT NULL;
ALTER TABLE behavior_logs ALTER COLUMN person_id DROP NOT NULL;
ALTER TABLE behavior_logs ALTER COLUMN session_id DROP NOT NULL;

-- presence_sessions.person_id -> persons
DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'presence_sessions'
      AND con.contype = 'f'
      AND con.confrelid = 'persons'::regclass
    LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE presence_sessions DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE presence_sessions
    ADD CONSTRAINT presence_sessions_person_id_fkey
    FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE SET NULL;

-- behavior_logs.person_id -> persons
DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'behavior_logs'
      AND con.contype = 'f'
      AND con.confrelid = 'persons'::regclass
    LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE behavior_logs DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE behavior_logs
    ADD CONSTRAINT behavior_logs_person_id_fkey
    FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE SET NULL;

-- behavior_logs.session_id -> presence_sessions
DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'behavior_logs'
      AND con.contype = 'f'
      AND con.confrelid = 'presence_sessions'::regclass
    LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE behavior_logs DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE behavior_logs
    ADD CONSTRAINT behavior_logs_session_id_fkey
    FOREIGN KEY (session_id) REFERENCES presence_sessions(id) ON DELETE SET NULL;

COMMIT;
