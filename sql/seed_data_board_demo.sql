-- 数据看板演示数据：人员档案(含人脸)、停留记录、陌生人研判
-- 不修改 locations；可重复执行前先清理同批演示数据
-- 用法: psql "postgresql://postgres:root123@localhost:5432/nwueyes" -f ruoyi/sql/seed_data_board_demo.sql

BEGIN;

-- 与现有人脸文件一致（face_library/face_850cbff6....jpg）
-- 若文件不存在，人员档案缩略图会加载失败，可先上传一张人脸或改此路径
DO $cfg$
BEGIN
    PERFORM set_config('app.demo_face_img', '/dashboard/data-board/file/face/face_850cbff6b83b4cdea2161333f6a41f62.jpg', true);
END $cfg$;

-- 清理本脚本写入的演示数据（保留原有 zp 等业务数据）
DELETE FROM presence_sessions
WHERE track_key LIKE 'demo_%';

DELETE FROM face_profiles
WHERE person_id IN (SELECT id FROM persons WHERE note = 'demo_seed');

DELETE FROM persons
WHERE note = 'demo_seed';

-- 需至少有一个监控点位（不新增 locations，仅用已有 id=1）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM locations WHERE id = 1) THEN
        RAISE EXCEPTION 'locations 表无 id=1，请先配置监控点位后再执行本脚本';
    END IF;
END $$;

-- ========== 人员档案（已知 5 + 陌生人档案 2）==========
INSERT INTO persons (display_name, person_kind, tags, note, created_at, updated_at)
VALUES
    ('张三', 'known', ARRAY['教职工'], 'demo_seed', NOW(), NOW()),
    ('李四', 'known', ARRAY['学生'], 'demo_seed', NOW(), NOW()),
    ('王五', 'known', ARRAY['访客'], 'demo_seed', NOW(), NOW()),
    ('赵六', 'known', ARRAY['教职工', '门禁'], 'demo_seed', NOW(), NOW()),
    ('孙八', 'known', ARRAY['学生'], 'demo_seed', NOW(), NOW()),
    ('未登记访客A', 'stranger', ARRAY['待研判'], 'demo_seed', NOW(), NOW()),
    ('未登记访客B', 'stranger', ARRAY['高频出现'], 'demo_seed', NOW(), NOW());

INSERT INTO face_profiles (person_id, embedding, image_url, is_primary, created_at)
SELECT
    p.id,
    array_fill(0.0::real, ARRAY[512])::vector(512),
    current_setting('app.demo_face_img'),
    TRUE,
    NOW()
FROM persons p
WHERE p.note = 'demo_seed';

-- ========== 停留记录 + 陌生人轨迹（共用 presence_sessions）==========
INSERT INTO presence_sessions (
    location_id, person_id, track_key,
    arrival_at, last_seen_at, departure_at, dwell_seconds,
    status, face_image_url, created_at, updated_at
)
SELECT
    1,
    p.id,
    v.track_key,
    v.arrival_at,
    v.last_seen_at,
    v.departure_at,
    v.dwell_seconds,
    v.status::session_status,
    current_setting('app.demo_face_img'),
    NOW(),
    NOW()
FROM (VALUES
    -- 已知人员 · 今日已离开
    ('demo_known_001', '张三', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', 7200, 'closed'),
    ('demo_known_002', '李四', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', 7200, 'closed'),
    -- 已知人员 · 今日仍在场
    ('demo_known_003', '王五', NOW() - INTERVAL '45 minutes', NOW() - INTERVAL '2 minutes', NULL, NULL, 'open'),
    -- 已知人员 · 昨日记录（仅停留 Tab 选昨日可见）
    ('demo_known_004', '赵六', NOW() - INTERVAL '1 day 5 hours', NOW() - INTERVAL '1 day 3 hours', NOW() - INTERVAL '1 day 3 hours', 7200, 'closed'),
    -- 绑定 stranger 档案的轨迹（陌生人研判 + 停留）
    ('demo_stranger_001', '未登记访客A', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours', 3600, 'closed'),
    ('demo_stranger_002', '未登记访客B', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '20 minutes', NULL, NULL, 'open'),
    -- 纯匿名轨迹（仅 person_id 为空，陌生人研判）
    ('demo_anon_001', NULL, NOW() - INTERVAL '8 hours', NOW() - INTERVAL '7 hours', NOW() - INTERVAL '7 hours', 3600, 'closed'),
    ('demo_anon_002', NULL, NOW() - INTERVAL '90 minutes', NOW() - INTERVAL '30 minutes', NULL, NULL, 'open'),
    ('demo_anon_003', NULL, NOW() - INTERVAL '5 hours', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours', 3600, 'closed')
) AS v(track_key, person_name, arrival_at, last_seen_at, departure_at, dwell_seconds, status)
LEFT JOIN persons p ON p.display_name = v.person_name AND p.note = 'demo_seed';

COMMIT;

-- 汇总
SELECT 'persons(demo)' AS item, COUNT(*)::text AS cnt FROM persons WHERE note = 'demo_seed'
UNION ALL
SELECT 'face_profiles(demo)', COUNT(*)::text FROM face_profiles fp JOIN persons p ON p.id = fp.person_id WHERE p.note = 'demo_seed'
UNION ALL
SELECT 'presence_sessions(demo)', COUNT(*)::text FROM presence_sessions WHERE track_key LIKE 'demo_%';
