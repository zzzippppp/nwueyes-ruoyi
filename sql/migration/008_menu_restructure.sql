-- 菜单重组：考勤管理 / 设备管理 / 智能问答
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/008_menu_restructure.sql

BEGIN;

-- ========== 考勤管理（原数据看板目录）==========
UPDATE sys_menu
SET menu_name = '考勤管理',
    path = 'attendance',
    route_name = 'Attendance',
    icon = 'peoples',
    remark = '考勤管理目录'
WHERE menu_id = 6;

UPDATE sys_menu
SET menu_name = '考勤信息',
    path = 'info',
    component = 'dashboard/attendance_info/index',
    query = NULL,
    route_name = 'AttendanceInfo',
    order_num = 1,
    icon = 'chart',
    remark = '考勤信息与人员档案'
WHERE menu_id = 601;

UPDATE sys_menu
SET menu_name = '考勤日志',
    path = 'log',
    component = 'dashboard/behavior_log/index',
    query = NULL,
    route_name = 'AttendanceLog',
    order_num = 2,
    icon = 'documentation',
    remark = '进出门考勤日志'
WHERE menu_id = 602;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 603, '陌生人研判', 6, 3, 'stranger', 'dashboard/stranger_review/index', 'StrangerReview',
       1, 0, 'C', '0', '0', 'dashboard:stranger-review:list', 'user', 'admin', CURRENT_TIMESTAMP, '陌生人身份研判'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 603);

-- ========== 隐藏原顶级监控大屏（视频检测保留，012 归入设备管理）==========
UPDATE sys_menu SET visible = '1', order_num = 99 WHERE menu_id = 5;

-- ========== 设备管理 ==========
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 8, '设备管理', 0, 3, 'device', NULL, 'Device', 1, 0, 'M', '0', '0', NULL, 'monitor', 'admin', CURRENT_TIMESTAMP, '设备管理目录'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 801, '监控大屏', 8, 1, 'monitor_screen', 'dashboard/monitor_screen/index', 'DeviceMonitorScreen',
       1, 0, 'C', '0', '0', 'monitor:screen:list', 'monitor', 'admin', CURRENT_TIMESTAMP, '萤石监控大屏与识别'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 801);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 802, '设备信息', 8, 2, 'info', 'dashboard/device_info/index', 'DeviceInfo',
       1, 0, 'C', '0', '0', 'dashboard:device-info:list', 'server', 'admin', CURRENT_TIMESTAMP, '监控设备信息维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 802);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 803, '设备类型', 8, 3, 'type', 'dashboard/device_type/index', 'DeviceType',
       1, 0, 'C', '0', '0', 'dashboard:device-type:list', 'component', 'admin', CURRENT_TIMESTAMP, '设备类型字典'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 803);

-- ========== 智能问答 ==========
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 9, '智能问答', 0, 4, 'ai_chat', 'dashboard/ai_chat/index', 'AiChat',
       1, 0, 'C', '0', '0', 'dashboard:ai-chat:list', 'message', 'admin', CURRENT_TIMESTAMP, '大模型智能问答'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9);

-- ========== 管理员角色授权 ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 603 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 603);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 8 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 8);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 801 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 801);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 802 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 802);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 803 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 803);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 9 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 9);

COMMIT;
