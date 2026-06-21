-- 用 device_type + camera 替代 locations，业务表 location_id 改为 camera_id
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/009_camera_replace_locations.sql

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'camera_online_status') THEN
        CREATE TYPE camera_online_status AS ENUM ('online', 'offline');
    END IF;
END $$;

-- ========== device_type ==========
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

-- ========== camera ==========
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

-- 从 locations 迁移（保留 id，便于外键平移）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'locations'
    ) THEN
        INSERT INTO camera (
            id, device_code, device_name, type_id, install_location, channel_no, serial_no,
            online_status, line_y, roi, ref_width, ref_height, created_at, updated_at
        )
        SELECT
            l.id,
            'CAM-' || LPAD(l.id::text, 3, '0'),
            l.name,
            (SELECT id FROM device_type WHERE type_code = 'ipc' LIMIT 1),
            l.site_name,
            l.channel_no,
            l.device_serial,
            CASE WHEN l.is_active THEN 'online'::camera_online_status ELSE 'offline'::camera_online_status END,
            l.line_y,
            l.roi,
            COALESCE(l.ref_width, 1920),
            COALESCE(l.ref_height, 1080),
            l.created_at,
            COALESCE(l.created_at, NOW())
        FROM locations l
        ON CONFLICT (id) DO NOTHING;

        PERFORM setval(
            pg_get_serial_sequence('camera', 'id'),
            GREATEST((SELECT COALESCE(MAX(id), 1) FROM camera), 1)
        );
    END IF;
END $$;

-- ========== 外键列 location_id → camera_id ==========
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'behavior_logs' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE behavior_logs RENAME COLUMN location_id TO camera_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'presence_sessions' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE presence_sessions RENAME COLUMN location_id TO camera_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'person_daily_attendance' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE person_daily_attendance RENAME COLUMN location_id TO camera_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'presence_video_clips'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'presence_video_clips' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE presence_video_clips RENAME COLUMN location_id TO camera_id;
    END IF;
END $$;

-- 删除旧 FK，建立指向 camera 的 FK
DO $$
DECLARE cname text;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'locations') THEN
        FOR cname IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'behavior_logs' AND con.contype = 'f'
              AND con.confrelid = 'locations'::regclass
        LOOP
            EXECUTE format('ALTER TABLE behavior_logs DROP CONSTRAINT %I', cname);
        END LOOP;

        FOR cname IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'presence_sessions' AND con.contype = 'f'
              AND con.confrelid = 'locations'::regclass
        LOOP
            EXECUTE format('ALTER TABLE presence_sessions DROP CONSTRAINT %I', cname);
        END LOOP;

        FOR cname IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'person_daily_attendance' AND con.contype = 'f'
              AND con.confrelid = 'locations'::regclass
        LOOP
            EXECUTE format('ALTER TABLE person_daily_attendance DROP CONSTRAINT %I', cname);
        END LOOP;

        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'presence_video_clips') THEN
            FOR cname IN
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                WHERE rel.relname = 'presence_video_clips' AND con.contype = 'f'
                  AND con.confrelid = 'locations'::regclass
            LOOP
                EXECUTE format('ALTER TABLE presence_video_clips DROP CONSTRAINT %I', cname);
            END LOOP;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'behavior_logs_camera_id_fkey'
    ) THEN
        ALTER TABLE behavior_logs
            ADD CONSTRAINT behavior_logs_camera_id_fkey
            FOREIGN KEY (camera_id) REFERENCES camera(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'presence_sessions_camera_id_fkey'
    ) THEN
        ALTER TABLE presence_sessions
            ADD CONSTRAINT presence_sessions_camera_id_fkey
            FOREIGN KEY (camera_id) REFERENCES camera(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'person_daily_attendance_camera_id_fkey'
    ) THEN
        ALTER TABLE person_daily_attendance
            ADD CONSTRAINT person_daily_attendance_camera_id_fkey
            FOREIGN KEY (camera_id) REFERENCES camera(id) ON DELETE SET NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'presence_video_clips')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'presence_video_clips_camera_id_fkey') THEN
        ALTER TABLE presence_video_clips
            ADD CONSTRAINT presence_video_clips_camera_id_fkey
            FOREIGN KEY (camera_id) REFERENCES camera(id);
    END IF;
END $$;

DROP TABLE IF EXISTS locations CASCADE;

COMMIT;
