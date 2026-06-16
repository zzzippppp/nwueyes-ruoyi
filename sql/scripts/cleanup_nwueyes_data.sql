-- 清空 nwueyes 业务数据（保留 locations id=1 监控点位）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/scripts/cleanup_nwueyes_data.sql

BEGIN;

DELETE FROM behavior_logs;
DELETE FROM presence_sessions;
DELETE FROM face_profiles;
DELETE FROM body_profiles;
DELETE FROM persons;

-- 重置自增（可选，便于从 1 开始）
SELECT setval(pg_get_serial_sequence('behavior_logs', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('presence_sessions', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('persons', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('face_profiles', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('body_profiles', 'id'), 1, false);

COMMIT;
