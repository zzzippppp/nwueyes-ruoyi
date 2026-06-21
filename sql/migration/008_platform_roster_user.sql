-- 平台配置表 + persons 人事字段扩展 + sys_user 学工号
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/008_platform_roster_user.sql
-- 前置: 005_attendance_extend.sql（person_type 枚举）

BEGIN;

-- ========== sys_platform_config 平台/门户配置 ==========
CREATE TABLE IF NOT EXISTS sys_platform_config (
    config_id    BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL,
    config_value TEXT,
    config_type  VARCHAR(50) NOT NULL DEFAULT 'text',
    group_name   VARCHAR(50) NOT NULL,
    config_desc  VARCHAR(200),
    sort_order   INTEGER NOT NULL DEFAULT 0,
    create_by    VARCHAR(64) DEFAULT '',
    create_time  TIMESTAMP,
    update_by    VARCHAR(64) DEFAULT '',
    update_time  TIMESTAMP,
    remark       VARCHAR(500),
    CONSTRAINT uk_sys_platform_config_key UNIQUE (config_key)
);

COMMENT ON TABLE sys_platform_config IS '平台配置表（主题、版权、登录页等门户展示项）';
COMMENT ON COLUMN sys_platform_config.config_type IS 'text/textarea/image/upload';
COMMENT ON COLUMN sys_platform_config.group_name IS 'basic/copyright/auth/contact/theme';

CREATE INDEX IF NOT EXISTS idx_sys_platform_config_group
    ON sys_platform_config (group_name, sort_order);

-- ========== persons 人事字段（原 attendance_person 已合并）==========
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

-- ========== sys_user 增加学工号（保留 dept_id 供若依部门树使用）==========
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS work_no VARCHAR(30) NOT NULL DEFAULT '';

COMMENT ON COLUMN sys_user.work_no IS '学工号';

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_user_work_no
    ON sys_user (work_no)
    WHERE work_no IS NOT NULL AND work_no <> '' AND del_flag = '0';

COMMIT;
