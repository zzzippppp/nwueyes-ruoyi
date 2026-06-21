-- 修复：旧版 004_attendance_extend 曾 DROP face_image_url/body_image_url
-- 合并后代码需要 behavior_logs 同时保留 face/body 小图与 snapshot 整帧
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/007_behavior_logs_restore_image_columns.sql

BEGIN;

ALTER TABLE behavior_logs
    ADD COLUMN IF NOT EXISTS face_image_url VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS body_image_url VARCHAR(512) NOT NULL DEFAULT '';

-- 旧数据仅有 snapshot_url 时，回填到 face 列便于列表展示
UPDATE behavior_logs
SET face_image_url = snapshot_url
WHERE (face_image_url = '' OR face_image_url IS NULL)
  AND snapshot_url IS NOT NULL
  AND TRIM(snapshot_url) <> '';

COMMIT;
