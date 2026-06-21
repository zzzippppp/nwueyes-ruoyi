-- 行为日志演示数据（B 方案：demo 来源，不写入 presence_sessions）
-- 用法: psql "postgresql://postgres:root123@localhost:5432/nwueyes" -f ruoyi/sql/seed_behavior_log_demo.sql

BEGIN;

DO $cfg$
BEGIN
    PERFORM set_config('app.demo_face_img', '/dashboard/data-board/file/face/face_850cbff6b83b4cdea2161333f6a41f62.jpg', true);
    PERFORM set_config('app.demo_body_img', '/dashboard/data-board/file/face/face_850cbff6b83b4cdea2161333f6a41f62.jpg', true);
END $cfg$;

DELETE FROM behavior_logs WHERE source = 'demo';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM camera WHERE id = 1) THEN
        RAISE EXCEPTION 'camera 表无 id=1，请先配置摄像头';
    END IF;
END $$;

INSERT INTO behavior_logs (
    display_name, event_type, event_time,
    face_image_url, body_image_url,
    camera_id, person_id, track_key, session_id, person_kind, source, created_at
)
SELECT
    CASE WHEN p.id IS NOT NULL AND p.person_kind = 'known' THEN p.display_name ELSE '未登记-' || v.track_key END,
    v.event_type,
    v.event_time,
    current_setting('app.demo_face_img'),
    current_setting('app.demo_body_img'),
    1,
    p.id,
    v.track_key,
    NULL,
    CASE WHEN p.id IS NOT NULL AND p.person_kind = 'known' THEN 'known' ELSE 'stranger' END,
    'demo',
    NOW()
FROM (VALUES
    ('demo_known_001', '张三', 'enter', NOW() - INTERVAL '4 hours'),
    ('demo_known_001', '张三', 'exit',  NOW() - INTERVAL '2 hours'),
    ('demo_known_002', '李四', 'enter', NOW() - INTERVAL '3 hours'),
    ('demo_known_002', '李四', 'exit',  NOW() - INTERVAL '1 hour'),
    ('demo_known_003', '王五', 'enter', NOW() - INTERVAL '45 minutes'),
    ('demo_stranger_001', '未登记访客A', 'enter', NOW() - INTERVAL '6 hours'),
    ('demo_stranger_001', '未登记访客A', 'exit',  NOW() - INTERVAL '5 hours'),
    ('demo_anon_001', NULL, 'enter', NOW() - INTERVAL '8 hours'),
    ('demo_anon_001', NULL, 'exit',  NOW() - INTERVAL '7 hours'),
    ('demo_anon_002', NULL, 'enter', NOW() - INTERVAL '90 minutes')
) AS v(track_key, person_name, event_type, event_time)
LEFT JOIN persons p ON p.display_name = v.person_name AND p.note = 'demo_seed';

COMMIT;

SELECT 'behavior_logs(demo)' AS item, COUNT(*)::text AS cnt FROM behavior_logs WHERE source = 'demo';
