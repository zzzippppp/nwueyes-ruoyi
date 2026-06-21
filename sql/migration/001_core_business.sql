-- nwueyes 核心业务表（监控点位、人员档案、停留会话、行为日志）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/001_core_business.sql
-- 前置: 已执行 sql/ry_20260417.sql（若依 sys_* + CREATE EXTENSION vector）

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

-- ========== 枚举 ==========
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'person_kind') THEN
        CREATE TYPE person_kind AS ENUM ('known', 'stranger');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'session_status') THEN
        CREATE TYPE session_status AS ENUM ('open', 'closed');
    END IF;
END $$;

-- ========== device_type / camera 监控设备 ==========
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'camera_online_status') THEN
        CREATE TYPE camera_online_status AS ENUM ('online', 'offline');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS device_type (
    id          BIGSERIAL PRIMARY KEY,
    type_code   VARCHAR(16) NOT NULL,
    type_name   VARCHAR(32) NOT NULL,
    remark      VARCHAR(256),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_device_type_code UNIQUE (type_code)
);

INSERT INTO device_type (type_code, type_name, remark)
VALUES ('ipc', '网络摄像机', '默认设备类型')
ON CONFLICT (type_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS camera (
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

CREATE INDEX IF NOT EXISTS idx_camera_type ON camera (type_id);

-- ========== persons 人员档案 ==========
CREATE TABLE IF NOT EXISTS persons (
    id              BIGSERIAL PRIMARY KEY,
    display_name    VARCHAR(128) NOT NULL,
    person_kind     person_kind NOT NULL DEFAULT 'stranger',
    note            VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_persons_kind ON persons (person_kind);

-- ========== face_profiles / body_profiles 向量档案 ==========
CREATE TABLE IF NOT EXISTS face_profiles (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    embedding       vector(512) NOT NULL,
    image_url       VARCHAR(512) NOT NULL,
    quality_score   REAL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_face_profiles_person ON face_profiles (person_id);
CREATE INDEX IF NOT EXISTS idx_face_profiles_embedding
    ON face_profiles USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS body_profiles (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    image_url       VARCHAR(512) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    embedding       vector(512),
    quality_score   REAL
);

COMMENT ON COLUMN body_profiles.embedding IS '体态 ReID 向量，512 维（OSNet 等）';

CREATE INDEX IF NOT EXISTS idx_body_profiles_person ON body_profiles (person_id);
CREATE INDEX IF NOT EXISTS idx_body_profiles_embedding
    ON body_profiles USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

-- ========== presence_sessions 停留会话（看板「在场中」）==========
CREATE TABLE IF NOT EXISTS presence_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    camera_id         BIGINT NOT NULL REFERENCES camera(id),
    person_id           BIGINT REFERENCES persons(id) ON DELETE SET NULL,
    track_key           VARCHAR(96) NOT NULL,
    arrival_at          TIMESTAMPTZ NOT NULL,
    last_seen_at        TIMESTAMPTZ NOT NULL,
    departure_at        TIMESTAMPTZ,
    dwell_seconds       INTEGER,
    status              session_status NOT NULL DEFAULT 'open',
    best_match_score    REAL,
    face_image_url      VARCHAR(512),
    body_image_url      VARCHAR(512),
    enter_body_embedding vector(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN presence_sessions.enter_body_embedding IS '进门时刻体态向量，出门比对用，512 维';

CREATE INDEX IF NOT EXISTS idx_ps_location_time ON presence_sessions (camera_id, arrival_at DESC);
CREATE INDEX IF NOT EXISTS idx_ps_open ON presence_sessions (camera_id, status)
    WHERE status = 'open';
CREATE INDEX IF NOT EXISTS idx_ps_person_time ON presence_sessions (person_id, arrival_at DESC)
    WHERE person_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ps_track ON presence_sessions (track_key, status);
CREATE INDEX IF NOT EXISTS idx_ps_enter_body_embedding
    ON presence_sessions USING hnsw (enter_body_embedding vector_cosine_ops)
    WHERE status = 'open' AND enter_body_embedding IS NOT NULL;

-- ========== behavior_logs 进/出门行为流水（行为日志页）==========
-- 用途：每次 enter/exit 事件一条只读流水，含抓拍图 URL、匹配分、质量标记；
--       与 presence_sessions 分离：session 管「在场状态」，behavior_logs 管「审计/追溯」。
CREATE TABLE IF NOT EXISTS behavior_logs (
    id              BIGSERIAL PRIMARY KEY,
    display_name    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(16) NOT NULL CHECK (event_type IN ('enter', 'exit')),
    event_time      TIMESTAMP NOT NULL,
    face_image_url  VARCHAR(512) NOT NULL DEFAULT '',
    body_image_url  VARCHAR(512) NOT NULL DEFAULT '',
    camera_id     BIGINT NOT NULL REFERENCES camera(id),
    person_id       BIGINT REFERENCES persons(id) ON DELETE SET NULL,
    track_key       VARCHAR(128) NOT NULL,
    session_id      BIGINT REFERENCES presence_sessions(id) ON DELETE SET NULL,
    person_kind     VARCHAR(32) NOT NULL CHECK (person_kind IN ('known', 'stranger', 'unknown')),
    source          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    face_match_score REAL,
    body_match_score REAL,
    quality_flag    VARCHAR(16) NOT NULL DEFAULT 'normal'
        CHECK (quality_flag IN ('normal', 'low', 'missing')),
    CONSTRAINT uk_behavior_log_event UNIQUE (track_key, event_type, event_time, source)
);

COMMENT ON COLUMN behavior_logs.face_match_score IS '进门人脸匹配分（cosine 相似度，0~1）';
COMMENT ON COLUMN behavior_logs.body_match_score IS '出门体态匹配分（cosine 相似度，0~1）';
COMMENT ON COLUMN behavior_logs.quality_flag IS '抓拍质量：normal=达标, low=次优帧, missing=无图';

CREATE INDEX IF NOT EXISTS idx_behavior_logs_event_time ON behavior_logs (event_time DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_camera_id ON behavior_logs (camera_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_person_id ON behavior_logs (person_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_session_id ON behavior_logs (session_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_quality_flag ON behavior_logs (quality_flag);

-- ========== 默认摄像头 id=1（请改 serial_no）==========
INSERT INTO camera (id, device_code, device_name, type_id, install_location, channel_no, serial_no, online_status)
SELECT 1, 'CAM-001', '默认监控点位', (SELECT id FROM device_type WHERE type_code = 'ipc' LIMIT 1),
       '请在数据看板或本表修改', 1, 'CHANGE_ME_DEVICE_SERIAL', 'online'
WHERE NOT EXISTS (SELECT 1 FROM camera WHERE id = 1);

SELECT setval(
    pg_get_serial_sequence('camera', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM camera), 1)
);

COMMIT;
