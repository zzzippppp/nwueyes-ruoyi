-- 门内/门外摄像头角色
--   door     = 门内（现有已标定、朝门外拍那台）：只记进门 enter + 路过 pass，丢弃出门 exit
--   exterior = 门外：不受门线约束，捕到人脸即与库中在场者比对，命中则记离场（关 session + 签退）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/010_camera_role.sql

BEGIN;

ALTER TABLE camera ADD COLUMN IF NOT EXISTS camera_role VARCHAR(16) NOT NULL DEFAULT 'door';

COMMENT ON COLUMN camera.camera_role IS '摄像头角色: door=门内(记进门/路过,丢弃出门), exterior=门外(人脸比对在场者记离场)';

-- 现有已标定门内摄像头（id=2068）保持默认 door 即可。
-- 门外摄像头请按实际 id 设为 exterior，例如：
-- UPDATE camera SET camera_role = 'exterior' WHERE id = <门外摄像头id>;

COMMIT;
