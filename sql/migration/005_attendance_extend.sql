-- 考勤模块扩展：person_type、locations 门线、behavior_logs snapshot/视频、日汇总表
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/005_attendance_extend.sql
-- 前置: 001_core_business.sql、003_fk_on_delete_set_null.sql、004_video_clips_and_ai_analysis.sql

BEGIN;

-- ========== 新枚举 person_type ==========
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'person_type') THEN
        CREATE TYPE person_type AS ENUM ('student', 'staff', 'stranger');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'attendance_status') THEN
        CREATE TYPE attendance_status AS ENUM ('present', 'left', 'absent');
    END IF;
END $$;

-- ========== camera：门线/ROI ==========
ALTER TABLE camera
    ADD COLUMN IF NOT EXISTS line_y INTEGER,
    ADD COLUMN IF NOT EXISTS roi VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ref_width INTEGER NOT NULL DEFAULT 1920,
    ADD COLUMN IF NOT EXISTS ref_height INTEGER NOT NULL DEFAULT 1080;

UPDATE camera
SET line_y = COALESCE(line_y, 658),
    roi = COALESCE(roi, '640,35,1250,680')
WHERE id = 1;

-- ========== persons：person_type + employee_no ==========
ALTER TABLE persons ADD COLUMN IF NOT EXISTS employee_no VARCHAR(64);
ALTER TABLE persons ADD COLUMN IF NOT EXISTS person_type person_type;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS face_image_url VARCHAR(512);

UPDATE persons
SET person_type = CASE
    WHEN person_kind::text = 'known' THEN 'student'::person_type
    WHEN person_kind::text = 'stranger' THEN 'stranger'::person_type
    ELSE 'stranger'::person_type
END
WHERE person_type IS NULL;

ALTER TABLE persons ALTER COLUMN person_type SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_persons_employee_no
    ON persons (employee_no) WHERE employee_no IS NOT NULL AND employee_no <> '';

ALTER TABLE persons DROP COLUMN IF EXISTS person_kind;
DROP TYPE IF EXISTS person_kind;

-- ========== presence_sessions：考勤日，去掉 last_seen_at ==========
ALTER TABLE presence_sessions
    ADD COLUMN IF NOT EXISTS attendance_date DATE;

UPDATE presence_sessions
SET attendance_date = (arrival_at AT TIME ZONE 'Asia/Shanghai')::date
WHERE attendance_date IS NULL;

ALTER TABLE presence_sessions ALTER COLUMN attendance_date SET NOT NULL;

ALTER TABLE presence_sessions DROP COLUMN IF EXISTS last_seen_at;

-- ========== behavior_logs：snapshot_url / video（保留 face/body 小图列）==========
ALTER TABLE behavior_logs
    ADD COLUMN IF NOT EXISTS face_image_url VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS body_image_url VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS snapshot_url VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS video_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS video_start_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS video_end_at TIMESTAMPTZ;

UPDATE behavior_logs
SET snapshot_url = COALESCE(
    NULLIF(TRIM(face_image_url), ''),
    NULLIF(TRIM(body_image_url), ''),
    ''
)
WHERE snapshot_url = '' OR snapshot_url IS NULL;

ALTER TABLE behavior_logs DROP CONSTRAINT IF EXISTS uk_behavior_log_event;
ALTER TABLE behavior_logs ADD CONSTRAINT uk_behavior_log_event
    UNIQUE (track_key, event_type, event_time);

ALTER TABLE behavior_logs DROP COLUMN IF EXISTS source;
ALTER TABLE behavior_logs DROP COLUMN IF EXISTS display_name;
ALTER TABLE behavior_logs DROP COLUMN IF EXISTS person_kind;

-- ========== 外键改为 CASCADE（删人员级联删 session/log/daily）==========
ALTER TABLE presence_sessions ALTER COLUMN person_id DROP NOT NULL;
ALTER TABLE behavior_logs ALTER COLUMN person_id DROP NOT NULL;
ALTER TABLE behavior_logs ALTER COLUMN session_id DROP NOT NULL;

DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'presence_sessions' AND con.contype = 'f'
      AND con.confrelid = 'persons'::regclass LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE presence_sessions DROP CONSTRAINT %I', cname);
    END IF;
END $$;
ALTER TABLE presence_sessions
    ADD CONSTRAINT presence_sessions_person_id_fkey
    FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE;

DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'behavior_logs' AND con.contype = 'f'
      AND con.confrelid = 'persons'::regclass LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE behavior_logs DROP CONSTRAINT %I', cname);
    END IF;
END $$;
ALTER TABLE behavior_logs
    ADD CONSTRAINT behavior_logs_person_id_fkey
    FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE;

DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'behavior_logs' AND con.contype = 'f'
      AND con.confrelid = 'presence_sessions'::regclass LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE behavior_logs DROP CONSTRAINT %I', cname);
    END IF;
END $$;
ALTER TABLE behavior_logs
    ADD CONSTRAINT behavior_logs_session_id_fkey
    FOREIGN KEY (session_id) REFERENCES presence_sessions(id) ON DELETE CASCADE;

-- ========== person_daily_attendance ==========
CREATE TABLE IF NOT EXISTS person_daily_attendance (
    id                  BIGSERIAL PRIMARY KEY,
    stat_date           DATE NOT NULL,
    person_id           BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    camera_id         BIGINT REFERENCES camera(id) ON DELETE SET NULL,
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

CREATE INDEX IF NOT EXISTS idx_pda_stat_first_enter
    ON person_daily_attendance (stat_date, first_enter_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_pda_stat_person
    ON person_daily_attendance (stat_date, person_id);

-- ========== attendance_daily_stats ==========
CREATE TABLE IF NOT EXISTS attendance_daily_stats (
    id                      BIGSERIAL PRIMARY KEY,
    stat_date               DATE UNIQUE NOT NULL,
    total_registry_count    INTEGER NOT NULL,
    attended_count          INTEGER NOT NULL,
    attendance_rate         NUMERIC(5,2) NOT NULL,
    stranger_total_count    INTEGER NOT NULL DEFAULT 0,
    computed_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ========== 回填 person_daily_attendance（历史 enter 日志 + sessions）==========
INSERT INTO person_daily_attendance (
    stat_date, person_id, camera_id, first_enter_at, last_exit_at,
    total_dwell_seconds, enter_count, attendance_status, is_attended, current_session_id, updated_at
)
SELECT
    d.stat_date,
    d.person_id,
    d.camera_id,
    d.first_enter_at,
    d.last_exit_at,
    COALESCE(d.total_dwell, 0),
    COALESCE(d.enter_cnt, 0),
    CASE
        WHEN EXISTS (
            SELECT 1 FROM presence_sessions ps
            WHERE ps.person_id = d.person_id AND ps.status = 'open'
              AND (ps.arrival_at AT TIME ZONE 'Asia/Shanghai')::date = d.stat_date
        ) THEN 'present'::attendance_status
        WHEN COALESCE(d.enter_cnt, 0) > 0 THEN 'left'::attendance_status
        ELSE 'absent'::attendance_status
    END,
    COALESCE(d.enter_cnt, 0) > 0,
    (
        SELECT ps.id FROM presence_sessions ps
        WHERE ps.person_id = d.person_id AND ps.status = 'open'
          AND (ps.arrival_at AT TIME ZONE 'Asia/Shanghai')::date = d.stat_date
        ORDER BY ps.arrival_at DESC LIMIT 1
    ),
    NOW()
FROM (
    SELECT
        (bl.event_time AT TIME ZONE 'Asia/Shanghai')::date AS stat_date,
        bl.person_id,
        MIN(bl.camera_id) AS camera_id,
        MIN(bl.event_time) FILTER (WHERE bl.event_type = 'enter') AS first_enter_at,
        MAX(bl.event_time) FILTER (WHERE bl.event_type = 'exit') AS last_exit_at,
        COALESCE(SUM(ps.dwell_seconds) FILTER (WHERE ps.status = 'closed'), 0) AS total_dwell,
        COUNT(*) FILTER (WHERE bl.event_type = 'enter') AS enter_cnt
    FROM behavior_logs bl
    LEFT JOIN presence_sessions ps ON ps.id = bl.session_id
    WHERE bl.person_id IS NOT NULL
    GROUP BY (bl.event_time AT TIME ZONE 'Asia/Shanghai')::date, bl.person_id
) d
ON CONFLICT (stat_date, person_id) DO UPDATE SET
    camera_id = EXCLUDED.camera_id,
    first_enter_at = LEAST(person_daily_attendance.first_enter_at, EXCLUDED.first_enter_at),
    last_exit_at = GREATEST(person_daily_attendance.last_exit_at, EXCLUDED.last_exit_at),
    total_dwell_seconds = EXCLUDED.total_dwell_seconds,
    enter_count = EXCLUDED.enter_count,
    attendance_status = EXCLUDED.attendance_status,
    is_attended = EXCLUDED.is_attended,
    current_session_id = EXCLUDED.current_session_id,
    updated_at = NOW();

-- ========== 回填 attendance_daily_stats（按 person_daily_attendance 聚合）==========
INSERT INTO attendance_daily_stats (
    stat_date, total_registry_count, attended_count, attendance_rate, stranger_total_count, computed_at
)
SELECT
    pda.stat_date,
    GREATEST(
        (SELECT COUNT(*) FROM persons p WHERE p.person_type IN ('student', 'staff')),
        1
    ) AS total_registry,
    COUNT(DISTINCT pda.person_id) FILTER (
        WHERE pda.is_attended AND p.person_type IN ('student', 'staff')
    ) AS attended,
    CASE
        WHEN (SELECT COUNT(*) FROM persons p WHERE p.person_type IN ('student', 'staff')) = 0 THEN 0
        ELSE ROUND(
            100.0 * COUNT(DISTINCT pda.person_id) FILTER (
                WHERE pda.is_attended AND p.person_type IN ('student', 'staff')
            ) / (SELECT COUNT(*) FROM persons p WHERE p.person_type IN ('student', 'staff')),
            2
        )
    END,
    (SELECT COUNT(*) FROM persons p WHERE p.person_type = 'stranger'),
    NOW()
FROM person_daily_attendance pda
JOIN persons p ON p.id = pda.person_id
GROUP BY pda.stat_date
ON CONFLICT (stat_date) DO NOTHING;

COMMIT;
