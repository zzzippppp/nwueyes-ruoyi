-- =============================================================================
-- 第二部分：nwueyes 业务表（最终结构，合并 migration 001~011）
-- =============================================================================

-- 清理旧业务表（重复执行时使用）
DROP TABLE IF EXISTS ai_analysis_results CASCADE;
DROP TABLE IF EXISTS presence_video_clips CASCADE;
DROP TABLE IF EXISTS person_daily_attendance CASCADE;
DROP TABLE IF EXISTS attendance_daily_stats CASCADE;
DROP TABLE IF EXISTS behavior_logs CASCADE;
DROP TABLE IF EXISTS presence_sessions CASCADE;
DROP TABLE IF EXISTS body_profiles CASCADE;
DROP TABLE IF EXISTS face_profiles CASCADE;
DROP TABLE IF EXISTS persons CASCADE;
DROP TABLE IF EXISTS camera CASCADE;
DROP TABLE IF EXISTS device_type CASCADE;
DROP TABLE IF EXISTS sys_platform_config CASCADE;

DROP TYPE IF EXISTS attendance_status CASCADE;
DROP TYPE IF EXISTS person_type CASCADE;
DROP TYPE IF EXISTS session_status CASCADE;
DROP TYPE IF EXISTS camera_online_status CASCADE;

-- ---------- 枚举 ----------
CREATE TYPE session_status AS ENUM ('open', 'closed');
CREATE TYPE person_type AS ENUM ('student', 'staff', 'stranger');
CREATE TYPE attendance_status AS ENUM ('present', 'left', 'absent');
CREATE TYPE camera_online_status AS ENUM ('online', 'offline');

-- ---------- device_type / camera ----------
CREATE TABLE device_type (
    id          BIGSERIAL PRIMARY KEY,
    type_code   VARCHAR(16) NOT NULL,
    type_name   VARCHAR(32) NOT NULL,
    remark      VARCHAR(256),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_device_type_code UNIQUE (type_code)
);

INSERT INTO device_type (type_code, type_name, remark)
VALUES ('ipc', '网络摄像机', '默认设备类型');

CREATE TABLE camera (
    id               BIGSERIAL PRIMARY KEY,
    device_code      VARCHAR(32) NOT NULL,
    device_name      VARCHAR(64) NOT NULL,
    type_id          BIGINT REFERENCES device_type(id) ON DELETE SET NULL,
    install_location VARCHAR(128),
    ip_addr          VARCHAR(64),
    channel_no       INTEGER NOT NULL DEFAULT 1,
    serial_no        VARCHAR(64) NOT NULL,
    verify_code      VARCHAR(32) NOT NULL DEFAULT '',
    online_status    camera_online_status NOT NULL DEFAULT 'offline',
    line_y           INTEGER,
    roi              VARCHAR(64),
    ref_width        INTEGER NOT NULL DEFAULT 1920,
    ref_height       INTEGER NOT NULL DEFAULT 1080,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_camera_device_code UNIQUE (device_code),
    CONSTRAINT uk_camera_serial_channel UNIQUE (serial_no, channel_no)
);

CREATE INDEX idx_camera_type ON camera (type_id);

-- ---------- persons ----------
CREATE TABLE persons (
    id              BIGSERIAL PRIMARY KEY,
    display_name    VARCHAR(128) NOT NULL,
    person_type     person_type NOT NULL DEFAULT 'stranger',
    employee_no     VARCHAR(64),
    face_image_url  VARCHAR(512),
    nick_name       VARCHAR(30) NOT NULL DEFAULT '',
    gender          CHAR(1) NOT NULL DEFAULT '0',
    phone           VARCHAR(11) NOT NULL DEFAULT '',
    email           VARCHAR(50) NOT NULL DEFAULT '',
    dept_id         BIGINT REFERENCES sys_dept(dept_id) ON DELETE SET NULL,
    status          CHAR(1) NOT NULL DEFAULT '0',
    avatar_url      VARCHAR(512) NOT NULL DEFAULT '',
    video_url       VARCHAR(512) NOT NULL DEFAULT '',
    note            VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_persons_employee_no
    ON persons (employee_no) WHERE employee_no IS NOT NULL AND employee_no <> '';
CREATE INDEX idx_persons_dept ON persons (dept_id);
CREATE INDEX idx_persons_name ON persons (display_name);

COMMENT ON COLUMN persons.gender IS '0男 1女 2未知';
COMMENT ON COLUMN persons.status IS '名册状态：0正常 1停用';
COMMENT ON COLUMN persons.avatar_url IS '人事头像 URL（可与识别用 face_image_url 不同）';
COMMENT ON COLUMN persons.video_url IS '介绍视频 URL';

-- ---------- 向量档案 ----------
CREATE TABLE face_profiles (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    embedding       vector(512) NOT NULL,
    image_url       VARCHAR(512) NOT NULL,
    quality_score   REAL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_face_profiles_person ON face_profiles (person_id);
CREATE INDEX idx_face_profiles_embedding
    ON face_profiles USING hnsw (embedding vector_cosine_ops);

CREATE TABLE body_profiles (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    image_url       VARCHAR(512) NOT NULL,
    embedding       vector(512),
    quality_score   REAL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN body_profiles.embedding IS '体态 ReID 向量，512 维（OSNet 等）';

CREATE INDEX idx_body_profiles_person ON body_profiles (person_id);
CREATE INDEX idx_body_profiles_embedding
    ON body_profiles USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

-- ---------- presence_sessions ----------
CREATE TABLE presence_sessions (
    id                   BIGSERIAL PRIMARY KEY,
    camera_id            BIGINT NOT NULL REFERENCES camera(id),
    person_id            BIGINT REFERENCES persons(id) ON DELETE CASCADE,
    track_key            VARCHAR(96) NOT NULL,
    arrival_at           TIMESTAMPTZ NOT NULL,
    departure_at         TIMESTAMPTZ,
    dwell_seconds        INTEGER,
    status               session_status NOT NULL DEFAULT 'open',
    best_match_score     REAL,
    face_image_url       VARCHAR(512),
    body_image_url       VARCHAR(512),
    enter_body_embedding vector(512),
    attendance_date      DATE NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN presence_sessions.enter_body_embedding IS '进门时刻体态向量，出门比对用，512 维';

CREATE INDEX idx_ps_camera_time ON presence_sessions (camera_id, arrival_at DESC);
CREATE INDEX idx_ps_open ON presence_sessions (camera_id, status) WHERE status = 'open';
CREATE INDEX idx_ps_person_time ON presence_sessions (person_id, arrival_at DESC) WHERE person_id IS NOT NULL;
CREATE INDEX idx_ps_track ON presence_sessions (track_key, status);
CREATE INDEX idx_ps_enter_body_embedding
    ON presence_sessions USING hnsw (enter_body_embedding vector_cosine_ops)
    WHERE status = 'open' AND enter_body_embedding IS NOT NULL;

-- ---------- behavior_logs ----------
CREATE TABLE behavior_logs (
    id                 BIGSERIAL PRIMARY KEY,
    event_type         VARCHAR(16) NOT NULL CHECK (event_type IN ('enter', 'exit')),
    event_time         TIMESTAMP NOT NULL,
    camera_id          BIGINT NOT NULL REFERENCES camera(id),
    person_id          BIGINT REFERENCES persons(id) ON DELETE CASCADE,
    track_key          VARCHAR(128) NOT NULL,
    session_id         BIGINT REFERENCES presence_sessions(id) ON DELETE CASCADE,
    face_image_url     VARCHAR(512) NOT NULL DEFAULT '',
    body_image_url     VARCHAR(512) NOT NULL DEFAULT '',
    snapshot_url       VARCHAR(512) NOT NULL DEFAULT '',
    video_url          VARCHAR(512),
    video_start_at     TIMESTAMPTZ,
    video_end_at       TIMESTAMPTZ,
    face_match_score   REAL,
    body_match_score   REAL,
    quality_flag       VARCHAR(16) NOT NULL DEFAULT 'normal'
        CHECK (quality_flag IN ('normal', 'low', 'missing')),
    behavior_analysis  TEXT,
    scene_group_id     VARCHAR(96),
    clip_id            BIGINT,
    analysis_status    VARCHAR(32),
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_behavior_log_event UNIQUE (track_key, event_type, event_time)
);

COMMENT ON COLUMN behavior_logs.face_match_score IS '进门人脸匹配分（cosine 相似度，0~1）';
COMMENT ON COLUMN behavior_logs.body_match_score IS '出门体态匹配分（cosine 相似度，0~1）';
COMMENT ON COLUMN behavior_logs.quality_flag IS '抓拍质量：normal=达标, low=次优帧, missing=无图';
COMMENT ON COLUMN behavior_logs.behavior_analysis IS '行为分析：自然语言描述';

CREATE INDEX idx_behavior_logs_event_time ON behavior_logs (event_time DESC);
CREATE INDEX idx_behavior_logs_camera_id ON behavior_logs (camera_id);
CREATE INDEX idx_behavior_logs_person_id ON behavior_logs (person_id);
CREATE INDEX idx_behavior_logs_session_id ON behavior_logs (session_id);
CREATE INDEX idx_behavior_logs_quality_flag ON behavior_logs (quality_flag);
CREATE INDEX idx_behavior_logs_scene_group_id ON behavior_logs (scene_group_id);
CREATE INDEX idx_behavior_logs_clip_id ON behavior_logs (clip_id);
CREATE INDEX idx_behavior_logs_analysis_status ON behavior_logs (analysis_status);

-- ---------- 视频片段 / AI 分析 ----------
CREATE TABLE presence_video_clips (
    id                     BIGSERIAL PRIMARY KEY,
    clip_key               VARCHAR(160) NOT NULL UNIQUE,
    clip_type              VARCHAR(32) NOT NULL CHECK (clip_type IN ('person_session', 'scene_group')),
    session_id             BIGINT REFERENCES presence_sessions(id),
    scene_group_id         VARCHAR(96),
    camera_id              BIGINT NOT NULL REFERENCES camera(id),
    track_key              VARCHAR(128),
    start_time             TIMESTAMP NOT NULL,
    end_time               TIMESTAMP NOT NULL,
    pre_roll_sec           REAL NOT NULL DEFAULT 3,
    post_roll_sec          REAL NOT NULL DEFAULT 3,
    video_url              VARCHAR(512) NOT NULL,
    status                 VARCHAR(32) NOT NULL DEFAULT 'ready',
    provider_status        VARCHAR(48),
    provider_task_id       VARCHAR(128),
    provider_source_url    TEXT,
    provider_error_message TEXT,
    public_video_url       VARCHAR(1024),
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_presence_video_clips_session ON presence_video_clips (session_id);
CREATE INDEX idx_presence_video_clips_scene ON presence_video_clips (scene_group_id);
CREATE INDEX idx_presence_video_clips_time ON presence_video_clips (start_time DESC, end_time DESC);

ALTER TABLE behavior_logs
    ADD CONSTRAINT behavior_logs_clip_id_fkey
    FOREIGN KEY (clip_id) REFERENCES presence_video_clips(id);

CREATE TABLE ai_analysis_results (
    id            BIGSERIAL PRIMARY KEY,
    target_type   VARCHAR(32) NOT NULL CHECK (target_type IN ('clip', 'scene_group')),
    target_id     VARCHAR(96) NOT NULL,
    model_key     VARCHAR(96) NOT NULL,
    model_name    VARCHAR(128) NOT NULL,
    status        VARCHAR(32) NOT NULL CHECK (status IN ('pending', 'success', 'failed', 'skipped')),
    summary       TEXT,
    appearance    TEXT,
    behavior      TEXT,
    risk_level    VARCHAR(32),
    raw_json      TEXT,
    error_message TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ai_analysis_target_model UNIQUE (target_type, target_id, model_key)
);

CREATE INDEX idx_ai_analysis_target ON ai_analysis_results (target_type, target_id);
CREATE INDEX idx_ai_analysis_status ON ai_analysis_results (status);

-- ---------- 考勤日汇总 ----------
CREATE TABLE person_daily_attendance (
    id                  BIGSERIAL PRIMARY KEY,
    stat_date           DATE NOT NULL,
    person_id           BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    camera_id           BIGINT REFERENCES camera(id) ON DELETE SET NULL,
    first_enter_at      TIMESTAMPTZ,
    last_exit_at        TIMESTAMPTZ,
    total_dwell_seconds INTEGER NOT NULL DEFAULT 0,
    enter_count         INTEGER NOT NULL DEFAULT 0,
    attendance_status   attendance_status NOT NULL DEFAULT 'absent',
    is_attended         BOOLEAN NOT NULL DEFAULT FALSE,
    current_session_id  BIGINT REFERENCES presence_sessions(id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_person_daily_attendance UNIQUE (stat_date, person_id)
);

CREATE INDEX idx_pda_stat_first_enter
    ON person_daily_attendance (stat_date, first_enter_at DESC NULLS LAST);
CREATE INDEX idx_pda_stat_person ON person_daily_attendance (stat_date, person_id);

CREATE TABLE attendance_daily_stats (
    id                      BIGSERIAL PRIMARY KEY,
    stat_date               DATE UNIQUE NOT NULL,
    total_registry_count    INTEGER NOT NULL,
    attended_count          INTEGER NOT NULL,
    attendance_rate         NUMERIC(5,2) NOT NULL,
    stranger_total_count    INTEGER NOT NULL DEFAULT 0,
    computed_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- 平台门户配置 ----------
CREATE TABLE sys_platform_config (
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

CREATE INDEX idx_sys_platform_config_group ON sys_platform_config (group_name, sort_order);

-- ---------- sys_user 学工号索引 ----------
COMMENT ON COLUMN sys_user.work_no IS '学工号';
CREATE UNIQUE INDEX idx_sys_user_work_no
    ON sys_user (work_no)
    WHERE work_no IS NOT NULL AND work_no <> '' AND del_flag = '0';

-- ---------- 默认摄像头 id=1（请改 serial_no）----------
INSERT INTO camera (
    id, device_code, device_name, type_id, install_location, channel_no, serial_no,
    online_status, line_y, roi
)
VALUES (
    1, 'CAM-001', '默认监控点位',
    (SELECT id FROM device_type WHERE type_code = 'ipc' LIMIT 1),
    '请在数据看板或本表修改', 1, 'CHANGE_ME_DEVICE_SERIAL', 'online',
    658, '640,35,1250,680'
);

SELECT setval(pg_get_serial_sequence('camera', 'id'), GREATEST((SELECT MAX(id) FROM camera), 1));
