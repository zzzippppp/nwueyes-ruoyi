-- 数据看板演示数据：人员档案(含人脸)、停留记录、陌生人研判
-- 不修改 camera；可重复执行前先清理同批演示数据
-- 用法: psql "postgresql://postgres:root123@localhost:5432/nwueyes" -f ruoyi/sql/seed_data_board_demo.sql

BEGIN;

DO $cfg$
BEGIN
    PERFORM set_config('app.demo_face_img', '/dashboard/data-board/file/face/face_850cbff6b83b4cdea2161333f6a41f62.jpg', true);
END $cfg$;

DELETE FROM presence_sessions
WHERE track_key LIKE 'demo_%';

DELETE FROM face_profiles
WHERE person_id IN (SELECT id FROM persons WHERE note = 'demo_seed');

DELETE FROM persons
WHERE note = 'demo_seed';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM camera WHERE id = 1) THEN
        RAISE EXCEPTION 'camera 表无 id=1，请先配置摄像头后再执行本脚本';
    END IF;
END $$;

INSERT INTO persons (display_name, person_type, note, created_at, updated_at)
VALUES
    ('张三', 'staff', 'demo_seed', NOW(), NOW()),
    ('李四', 'student', 'demo_seed', NOW(), NOW()),
    ('王五', 'student', 'demo_seed', NOW(), NOW()),
    ('赵六', 'staff', 'demo_seed', NOW(), NOW()),
    ('孙八', 'student', 'demo_seed', NOW(), NOW()),
    ('未登记访客A', 'stranger', 'demo_seed', NOW(), NOW()),
    ('未登记访客B', 'stranger', 'demo_seed', NOW(), NOW());

INSERT INTO face_profiles (person_id, embedding, image_url, created_at)
SELECT
    p.id,
    array_fill(0.0::real, ARRAY[512])::vector(512),
    current_setting('app.demo_face_img'),
    NOW()
FROM persons p
WHERE p.note = 'demo_seed';

INSERT INTO presence_sessions (
    camera_id, person_id, track_key,
    arrival_at, departure_at, dwell_seconds,
    status, face_image_url, attendance_date, created_at, updated_at
)
SELECT
    1,
    p.id,
    v.track_key,
    v.arrival_at,
    v.departure_at,
    v.dwell_seconds,
    v.status::session_status,
    current_setting('app.demo_face_img'),
    (v.arrival_at AT TIME ZONE 'Asia/Shanghai')::date,
    NOW(),
    NOW()
FROM (VALUES
    ('demo_known_001', '张三', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '2 hours', 7200, 'closed'),
    ('demo_known_002', '李四', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '1 hour', 7200, 'closed'),
    ('demo_known_003', '王五', NOW() - INTERVAL '45 minutes', NULL, NULL, 'open'),
    ('demo_known_004', '赵六', NOW() - INTERVAL '1 day 5 hours', NOW() - INTERVAL '1 day 3 hours', 7200, 'closed'),
    ('demo_stranger_001', '未登记访客A', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '5 hours', 3600, 'closed'),
    ('demo_stranger_002', '未登记访客B', NOW() - INTERVAL '2 hours', NULL, NULL, 'open'),
    ('demo_anon_001', NULL, NOW() - INTERVAL '8 hours', NOW() - INTERVAL '7 hours', 3600, 'closed'),
    ('demo_anon_002', NULL, NOW() - INTERVAL '90 minutes', NULL, NULL, 'open'),
    ('demo_anon_003', NULL, NOW() - INTERVAL '5 hours', NOW() - INTERVAL '4 hours', 3600, 'closed')
) AS v(track_key, person_name, arrival_at, departure_at, dwell_seconds, status)
LEFT JOIN persons p ON p.display_name = v.person_name AND p.note = 'demo_seed';

COMMIT;

SELECT 'persons(demo)' AS item, COUNT(*)::text AS cnt FROM persons WHERE note = 'demo_seed'
UNION ALL
SELECT 'face_profiles(demo)', COUNT(*)::text FROM face_profiles fp JOIN persons p ON p.id = fp.person_id WHERE p.note = 'demo_seed'
UNION ALL
SELECT 'presence_sessions(demo)', COUNT(*)::text FROM presence_sessions WHERE track_key LIKE 'demo_%';
