-- behavior_logs 增加「行为分析」自然语言字段
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/006_behavior_analysis.sql

BEGIN;

ALTER TABLE behavior_logs
    ADD COLUMN IF NOT EXISTS behavior_analysis TEXT;

COMMENT ON COLUMN behavior_logs.behavior_analysis IS '行为分析：自然语言描述（如场景解读、异常说明等）';

COMMIT;
