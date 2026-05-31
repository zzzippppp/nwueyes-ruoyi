-- 行为日志菜单（表结构见 sql/migration/001_core_business.sql）
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/behavior_log.sql

BEGIN;

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
