-- 删除 presence_sessions.enter_face_embedding（出门人脸比对未启用）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/011_drop_enter_face_embedding.sql

BEGIN;

ALTER TABLE presence_sessions DROP COLUMN IF EXISTS enter_face_embedding;

COMMIT;
