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

-- ========== locations 监控点位 ==========
CREATE TABLE IF NOT EXISTS locations (
    id              BIGSERIAL PRIMARY KEY,
    device_serial   VARCHAR(32) NOT NULL,
    channel_no      SMALLINT NOT NULL DEFAULT 1,
    name            VARCHAR(128) NOT NULL,
    site_name       VARCHAR(128),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT locations_device_serial_channel_no_key UNIQUE (device_serial, channel_no)
);

-- ========== persons 人员档案 ==========
CREATE TABLE IF NOT EXISTS persons (
    id              BIGSERIAL PRIMARY KEY,
    display_name    VARCHAR(128) NOT NULL,
    person_kind     person_kind NOT NULL DEFAULT 'stranger',
    tags            TEXT[] NOT NULL DEFAULT '{}',
    note            VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_persons_kind ON persons (person_kind);
CREATE INDEX IF NOT EXISTS idx_persons_tags ON persons USING gin (tags);

-- ========== face_profiles / body_profiles 向量档案 ==========
CREATE TABLE IF NOT EXISTS face_profiles (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    embedding       vector(512) NOT NULL,
    image_url       VARCHAR(512) NOT NULL,
    library_file    VARCHAR(255),
    is_primary      BOOLEAN NOT NULL DEFAULT TRUE,
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
    library_file    VARCHAR(255),
    is_primary      BOOLEAN NOT NULL DEFAULT TRUE,
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
    location_id         BIGINT NOT NULL REFERENCES locations(id),
    person_id           BIGINT REFERENCES persons(id) ON DELETE SET NULL,
    track_key           VARCHAR(96) NOT NULL,
    arrival_at          TIMESTAMPTZ NOT NULL,
    last_seen_at        TIMESTAMPTZ NOT NULL,
    departure_at        TIMESTAMPTZ,
    dwell_seconds       INTEGER,
    status              session_status NOT NULL DEFAULT 'open',
    id_state            VARCHAR(32),
    best_match_score    REAL,
    face_image_url      VARCHAR(512),
    body_image_url      VARCHAR(512),
    enter_face_embedding vector(512),
    enter_body_embedding vector(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN presence_sessions.enter_face_embedding IS '进门时刻人脸向量，512 维';
COMMENT ON COLUMN presence_sessions.enter_body_embedding IS '进门时刻体态向量，出门比对用，512 维';

CREATE INDEX IF NOT EXISTS idx_ps_location_time ON presence_sessions (location_id, arrival_at DESC);
CREATE INDEX IF NOT EXISTS idx_ps_open ON presence_sessions (location_id, status)
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
    location_id     BIGINT NOT NULL REFERENCES locations(id),
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
CREATE INDEX IF NOT EXISTS idx_behavior_logs_location_id ON behavior_logs (location_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_person_id ON behavior_logs (person_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_session_id ON behavior_logs (session_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_quality_flag ON behavior_logs (quality_flag);

-- ========== 默认监控点位 id=1（请改 device_serial）==========
INSERT INTO locations (id, device_serial, channel_no, name, site_name, is_active)
SELECT 1, 'CHANGE_ME_DEVICE_SERIAL', 1, '默认监控点位', '请在数据看板或本表修改', TRUE
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE id = 1);

SELECT setval(
    pg_get_serial_sequence('locations', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM locations), 1)
);

COMMIT;
