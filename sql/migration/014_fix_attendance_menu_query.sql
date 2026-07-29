-- 修复考勤管理子菜单：清除误写入 query 的旧 routeName，避免前端 JSON.parse 失败导致侧边栏只显示陌生人研判
-- 用法: psql -U postgres -d nwueyes -f sql/migration/014_fix_attendance_menu_query.sql

BEGIN;

UPDATE sys_menu
SET menu_name = '考勤管理',
    path = 'attendance',
    component = NULL,
    query = NULL,
    route_name = 'Attendance',
    visible = '0',
    status = '0',
    icon = 'peoples'
WHERE menu_id = 6;

UPDATE sys_menu
SET menu_name = '考勤信息',
    parent_id = 6,
    order_num = 1,
    path = 'info',
    component = 'dashboard/attendance_info/index',
    query = NULL,
    route_name = 'AttendanceInfo',
    menu_type = 'C',
    visible = '0',
    status = '0',
    perms = 'dashboard:data-board:list',
    icon = 'chart'
WHERE menu_id = 601;

UPDATE sys_menu
SET menu_name = '考勤日志',
    parent_id = 6,
    order_num = 2,
    path = 'log',
    component = 'dashboard/behavior_log/index',
    query = NULL,
    route_name = 'AttendanceLog',
    menu_type = 'C',
    visible = '0',
    status = '0',
    perms = 'dashboard:behavior-log:list',
    icon = 'documentation'
WHERE menu_id = 602;

UPDATE sys_menu
SET menu_name = '陌生人研判',
    parent_id = 6,
    order_num = 3,
    path = 'stranger',
    component = 'dashboard/stranger_review/index',
    query = NULL,
    route_name = 'StrangerReview',
    menu_type = 'C',
    visible = '0',
    status = '0',
    perms = 'dashboard:stranger-review:list',
    icon = 'user'
WHERE menu_id = 603;

-- 若缺菜单则补齐
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 601, '考勤信息', 6, 1, 'info', 'dashboard/attendance_info/index', 'AttendanceInfo',
       1, 0, 'C', '0', '0', 'dashboard:data-board:list', 'chart', 'admin', CURRENT_TIMESTAMP, '考勤信息与人员档案'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 601);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 602, '考勤日志', 6, 2, 'log', 'dashboard/behavior_log/index', 'AttendanceLog',
       1, 0, 'C', '0', '0', 'dashboard:behavior-log:list', 'documentation', 'admin', CURRENT_TIMESTAMP, '进出门考勤日志'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 602);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 603, '陌生人研判', 6, 3, 'stranger', 'dashboard/stranger_review/index', 'StrangerReview',
       1, 0, 'C', '0', '0', 'dashboard:stranger-review:list', 'user', 'admin', CURRENT_TIMESTAMP, '陌生人身份研判'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 603);

-- 普通角色授权（若缺失）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 6 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 6);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 601 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 601);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 602 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 602);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 603 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 603);

COMMIT;
