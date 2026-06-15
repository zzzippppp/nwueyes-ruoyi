-- 人员会话视频片段、多人场景组与多模型分析结果
-- 用法: psql -U postgres -d nwueyes -f sql/migration/003_video_clips_and_ai_analysis.sql

BEGIN;

CREATE TABLE IF NOT EXISTS presence_video_clips (
    id              BIGSERIAL PRIMARY KEY,
    clip_key        VARCHAR(160) NOT NULL UNIQUE,
    clip_type       VARCHAR(32) NOT NULL CHECK (clip_type IN ('person_session', 'scene_group')),
    session_id      BIGINT REFERENCES presence_sessions(id),
    scene_group_id  VARCHAR(96),
    location_id     BIGINT NOT NULL REFERENCES locations(id),
    track_key       VARCHAR(128),
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP NOT NULL,
    pre_roll_sec    REAL NOT NULL DEFAULT 3,
    post_roll_sec   REAL NOT NULL DEFAULT 3,
    video_url       VARCHAR(512) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ready',
    provider_status VARCHAR(48),
    provider_task_id VARCHAR(128),
    provider_source_url TEXT,
    provider_error_message TEXT,
    public_video_url VARCHAR(1024),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE presence_video_clips
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(48),
    ADD COLUMN IF NOT EXISTS provider_task_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_source_url TEXT,
    ADD COLUMN IF NOT EXISTS provider_error_message TEXT,
    ADD COLUMN IF NOT EXISTS public_video_url VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_presence_video_clips_session ON presence_video_clips (session_id);
CREATE INDEX IF NOT EXISTS idx_presence_video_clips_scene ON presence_video_clips (scene_group_id);
CREATE INDEX IF NOT EXISTS idx_presence_video_clips_time ON presence_video_clips (start_time DESC, end_time DESC);

CREATE TABLE IF NOT EXISTS ai_analysis_results (
    id              BIGSERIAL PRIMARY KEY,
    target_type     VARCHAR(32) NOT NULL CHECK (target_type IN ('clip', 'scene_group')),
    target_id       VARCHAR(96) NOT NULL,
    model_key       VARCHAR(96) NOT NULL,
    model_name      VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL CHECK (status IN ('pending', 'success', 'failed', 'skipped')),
    summary         TEXT,
    appearance      TEXT,
    behavior        TEXT,
    risk_level      VARCHAR(32),
    raw_json        TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ai_analysis_target_model UNIQUE (target_type, target_id, model_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_analysis_target ON ai_analysis_results (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_ai_analysis_status ON ai_analysis_results (status);

ALTER TABLE behavior_logs
    ADD COLUMN IF NOT EXISTS scene_group_id VARCHAR(96),
    ADD COLUMN IF NOT EXISTS clip_id BIGINT REFERENCES presence_video_clips(id),
    ADD COLUMN IF NOT EXISTS analysis_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_behavior_logs_scene_group_id ON behavior_logs (scene_group_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_clip_id ON behavior_logs (clip_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_analysis_status ON behavior_logs (analysis_status);

COMMIT;
