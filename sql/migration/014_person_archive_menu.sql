-- 系统管理下新增「人员档案」，置于最上方
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT 99, '人员档案', 1, 0, 'person', 'system/person/index', 'PersonArchive',
       1, 0, 'C', '0', '0', 'system:person:list', 'peoples', 'admin', CURRENT_TIMESTAMP, '人脸库人员档案'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 99);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 9901, '人员查询', 99, 1, '#', '', '', 1, 0, 'F', '0', '0', 'system:person:query', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9901);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 9902, '人员新增', 99, 2, '#', '', '', 1, 0, 'F', '0', '0', 'system:person:add', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9902);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 9903, '人员修改', 99, 3, '#', '', '', 1, 0, 'F', '0', '0', 'system:person:edit', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9903);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 9904, '人员删除', 99, 4, '#', '', '', 1, 0, 'F', '0', '0', 'system:person:remove', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9904);

-- 普通角色（role_id=2）授权；超级管理员通常已放行全部权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 99 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 99);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 9901 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 9901);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 9902 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 9902);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 9903 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 9903);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 9904 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 9904);
