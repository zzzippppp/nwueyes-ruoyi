-- 行为日志 / 考勤管理菜单（表结构见 sql/migration/001_core_business.sql）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/behavior_log.sql
-- 全新安装后建议再执行 sql/migration/008_menu_restructure.sql

BEGIN;

UPDATE sys_menu
SET menu_name = '考勤管理',
    parent_id = 0,
    order_num = 2,
    path = 'attendance',
    component = NULL,
    route_name = 'Attendance',
    menu_type = 'M',
    visible = '0',
    status = '0',
    icon = 'peoples',
    remark = '考勤管理目录'
WHERE menu_id = 6;

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

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 601 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 601);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 602 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 602);

COMMIT;
