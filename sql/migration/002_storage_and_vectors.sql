-- 增量迁移：旧库补向量字段（全新安装请只跑 001，可跳过本文件）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/002_storage_and_vectors.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE behavior_logs
    ADD COLUMN IF NOT EXISTS face_match_score REAL,
    ADD COLUMN IF NOT EXISTS body_match_score REAL,
    ADD COLUMN IF NOT EXISTS quality_flag VARCHAR(16) NOT NULL DEFAULT 'normal';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'behavior_logs_quality_flag_check'
    ) THEN
        ALTER TABLE behavior_logs
            ADD CONSTRAINT behavior_logs_quality_flag_check
            CHECK (quality_flag IN ('normal', 'low', 'missing'));
    END IF;
END $$;

COMMENT ON COLUMN behavior_logs.face_match_score IS '进门人脸匹配分（cosine 相似度，0~1）';
COMMENT ON COLUMN behavior_logs.body_match_score IS '出门体态匹配分（cosine 相似度，0~1）';
COMMENT ON COLUMN behavior_logs.quality_flag IS '抓拍质量：normal=达标, low=次优帧, missing=无图';

CREATE INDEX IF NOT EXISTS idx_behavior_logs_session_id ON behavior_logs (session_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_quality_flag ON behavior_logs (quality_flag);

ALTER TABLE presence_sessions
    ADD COLUMN IF NOT EXISTS enter_face_embedding vector(512),
    ADD COLUMN IF NOT EXISTS enter_body_embedding vector(512);

COMMENT ON COLUMN presence_sessions.enter_face_embedding IS '进门时刻人脸向量，512 维';
COMMENT ON COLUMN presence_sessions.enter_body_embedding IS '进门时刻体态向量，出门比对用，512 维';

CREATE INDEX IF NOT EXISTS idx_ps_enter_body_embedding
    ON presence_sessions USING hnsw (enter_body_embedding vector_cosine_ops)
    WHERE status = 'open'::session_status AND enter_body_embedding IS NOT NULL;

ALTER TABLE body_profiles
    ADD COLUMN IF NOT EXISTS embedding vector(512),
    ADD COLUMN IF NOT EXISTS quality_score REAL;

COMMENT ON COLUMN body_profiles.embedding IS '体态 ReID 向量，512 维（OSNet 等）';

CREATE INDEX IF NOT EXISTS idx_body_profiles_embedding
    ON body_profiles USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

COMMIT;
