-- 清空人员相关业务数据（保留视频片段 presence_video_clips 与摄像头等）
-- 覆盖：人员档案、考勤信息、考勤/行为日志、陌生人研判（来自 presence_sessions）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/scripts/cleanup_nwueyes_data.sql

BEGIN;

-- 保留视频片段索引：先断开与 session 的外键，避免删 session 失败
UPDATE presence_video_clips SET session_id = NULL WHERE session_id IS NOT NULL;

DELETE FROM behavior_logs;
DELETE FROM person_daily_attendance;
DELETE FROM attendance_daily_stats;
DELETE FROM presence_sessions;
DELETE FROM face_profiles;
DELETE FROM body_profiles;
DELETE FROM persons;

-- 重置自增，便于从 1 开始
SELECT setval(pg_get_serial_sequence('behavior_logs', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('presence_sessions', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('person_daily_attendance', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('attendance_daily_stats', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('persons', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('face_profiles', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('body_profiles', 'id'), 1, false);

COMMIT;
