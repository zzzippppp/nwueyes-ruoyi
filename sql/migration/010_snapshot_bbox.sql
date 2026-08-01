-- 行为日志监控画面人物框（归一化坐标 JSON）
ALTER TABLE behavior_logs
    ADD COLUMN IF NOT EXISTS snapshot_bbox TEXT;
