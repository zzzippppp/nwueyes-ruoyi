-- 清理冗余字段、合并 attendance_person 到 persons、删除 body_reid_profiles
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/010_persons_cleanup_merge.sql

BEGIN;

-- ========== 删除无用列 ==========
ALTER TABLE persons DROP COLUMN IF EXISTS tags;
DROP INDEX IF EXISTS idx_persons_tags;

ALTER TABLE face_profiles DROP COLUMN IF EXISTS library_file;
ALTER TABLE face_profiles DROP COLUMN IF EXISTS is_primary;

ALTER TABLE body_profiles DROP COLUMN IF EXISTS library_file;
ALTER TABLE body_profiles DROP COLUMN IF EXISTS is_primary;

ALTER TABLE presence_sessions DROP COLUMN IF EXISTS id_state;

DROP TABLE IF EXISTS body_reid_profiles CASCADE;

-- ========== persons 扩展人事字段（原 attendance_person）==========
ALTER TABLE persons ADD COLUMN IF NOT EXISTS nick_name VARCHAR(30) NOT NULL DEFAULT '';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS gender CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS phone VARCHAR(11) NOT NULL DEFAULT '';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS email VARCHAR(50) NOT NULL DEFAULT '';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS dept_id BIGINT REFERENCES sys_dept(dept_id) ON DELETE SET NULL;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS status CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS video_url VARCHAR(512) NOT NULL DEFAULT '';

COMMENT ON COLUMN persons.nick_name IS '昵称';
COMMENT ON COLUMN persons.gender IS '0男 1女 2未知';
COMMENT ON COLUMN persons.status IS '名册状态：0正常 1停用';
COMMENT ON COLUMN persons.avatar_url IS '人事头像 URL（可与识别用 face_image_url 不同）';
COMMENT ON COLUMN persons.video_url IS '介绍视频 URL';

CREATE INDEX IF NOT EXISTS idx_persons_dept ON persons (dept_id);
CREATE INDEX IF NOT EXISTS idx_persons_name ON persons (display_name);

-- ========== 迁移 attendance_person 数据（按学工号合并）==========
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'attendance_person'
    ) THEN
        UPDATE persons p
        SET nick_name = COALESCE(NULLIF(ap.nick_name, ''), p.nick_name),
            display_name = COALESCE(NULLIF(ap.person_name, ''), p.display_name),
            gender = ap.gender,
            phone = ap.phone,
            email = ap.email,
            dept_id = ap.dept_id,
            status = ap.status,
            avatar_url = CASE WHEN ap.avatar_url <> '' THEN ap.avatar_url ELSE p.avatar_url END,
            video_url = CASE WHEN ap.video_url <> '' THEN ap.video_url ELSE p.video_url END,
            employee_no = COALESCE(p.employee_no, ap.employee_no),
            updated_at = NOW()
        FROM attendance_person ap
        WHERE ap.employee_no IS NOT NULL AND ap.employee_no <> ''
          AND p.employee_no = ap.employee_no;

        INSERT INTO persons (
            display_name, nick_name, employee_no, gender, phone, email, dept_id,
            status, avatar_url, video_url, person_type, created_at, updated_at
        )
        SELECT
            ap.person_name,
            ap.nick_name,
            ap.employee_no,
            ap.gender,
            ap.phone,
            ap.email,
            ap.dept_id,
            ap.status,
            ap.avatar_url,
            ap.video_url,
            'staff'::person_type,
            COALESCE(ap.create_time, NOW()),
            COALESCE(ap.update_time, NOW())
        FROM attendance_person ap
        WHERE ap.employee_no IS NULL OR ap.employee_no = ''
           OR NOT EXISTS (
               SELECT 1 FROM persons p WHERE p.employee_no = ap.employee_no
           );

        DROP TABLE attendance_person CASCADE;
    END IF;
END $$;

COMMIT;
