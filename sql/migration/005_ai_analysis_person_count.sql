-- AI 分析结果：人数字段（替代危险等级展示）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/005_ai_analysis_person_count.sql

BEGIN;

ALTER TABLE ai_analysis_results
    ADD COLUMN IF NOT EXISTS person_count INTEGER;

COMMIT;
