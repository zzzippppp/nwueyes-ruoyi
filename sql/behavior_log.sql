-- 行为日志表 + 数据看板菜单结构调整
-- 用法: psql "postgresql://postgres:root123@localhost:5432/nwueyes" -f ruoyi/sql/behavior_log.sql

BEGIN;

-- ========== 行为日志表 ==========
CREATE TABLE IF NOT EXISTS behavior_logs (
    id              BIGSERIAL PRIMARY KEY,
    display_name    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(16) NOT NULL CHECK (event_type IN ('enter', 'exit')),
    event_time      TIMESTAMP NOT NULL,
    face_image_url  VARCHAR(512) NOT NULL DEFAULT '',
    body_image_url  VARCHAR(512) NOT NULL DEFAULT '',
    location_id     BIGINT NOT NULL REFERENCES locations(id),
    person_id       BIGINT REFERENCES persons(id),
    track_key       VARCHAR(128) NOT NULL,
    session_id      BIGINT REFERENCES presence_sessions(id),
    person_kind     VARCHAR(32) NOT NULL CHECK (person_kind IN ('known', 'stranger', 'unknown')),
    source          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_behavior_log_event UNIQUE (track_key, event_type, event_time, source)
);

CREATE INDEX IF NOT EXISTS idx_behavior_logs_event_time ON behavior_logs (event_time DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_location_id ON behavior_logs (location_id);
CREATE INDEX IF NOT EXISTS idx_behavior_logs_person_id ON behavior_logs (person_id);

-- ========== 菜单：数据看板改为目录，原页面与行为日志为子菜单 ==========
UPDATE sys_menu
SET menu_name = '数据看板',
    parent_id = 0,
    order_num = 2,
    path = 'data_board',
    component = NULL,
    route_name = 'DataBoard',
    menu_type = 'M',
    visible = '0',
    status = '0',
    icon = 'chart',
    remark = '数据看板目录'
WHERE menu_id = 6;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 601, '数据看板', 6, 1, 'index', 'dashboard/data_board/index', 'DataBoardIndex',
       1, 0, 'C', '0', '0', 'dashboard:data-board:list', 'chart', 'admin', CURRENT_TIMESTAMP, '数据看板主页'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 601);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 602, '行为日志', 6, 2, 'behavior_log', 'dashboard/behavior_log/index', 'BehaviorLog',
       1, 0, 'C', '0', '0', 'dashboard:behavior-log:list', 'documentation', 'admin', CURRENT_TIMESTAMP, '进出门行为日志'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 602);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 601 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 601);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 602 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 602);

COMMIT;
