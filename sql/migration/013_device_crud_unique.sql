-- 设备信息 / 设备类型唯一约束 + 按钮权限
-- 用法: psql -U postgres -d nwueyes -f sql/migration/013_device_crud_unique.sql
-- 不破坏已有摄像头 BH7367243 / BK4225491

BEGIN;

-- camera: 设备名称唯一；序列号唯一（业务要求不允许重复序列号）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_camera_device_name'
  ) THEN
    ALTER TABLE camera ADD CONSTRAINT uk_camera_device_name UNIQUE (device_name);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_camera_serial_no'
  ) THEN
    ALTER TABLE camera ADD CONSTRAINT uk_camera_serial_no UNIQUE (serial_no);
  END IF;
END $$;

-- device_type: 类型名称唯一（类型编码已有 uk_device_type_code）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_device_type_name'
  ) THEN
    ALTER TABLE device_type ADD CONSTRAINT uk_device_type_name UNIQUE (type_name);
  END IF;
END $$;

-- 设备信息按钮权限（挂在 menu_id=802 下）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8021, '设备信息查询', 802, 1, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-info:query', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8021);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8022, '设备信息新增', 802, 2, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-info:add', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8022);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8023, '设备信息修改', 802, 3, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-info:edit', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8023);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8024, '设备信息删除', 802, 4, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-info:remove', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8024);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8025, '设备信息导出', 802, 5, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-info:export', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8025);

-- 设备类型按钮权限（挂在 menu_id=803 下）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8031, '设备类型查询', 803, 1, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-type:query', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8031);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8032, '设备类型新增', 803, 2, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-type:add', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8032);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8033, '设备类型修改', 803, 3, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-type:edit', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8033);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8034, '设备类型删除', 803, 4, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-type:remove', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8034);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 8035, '设备类型导出', 803, 5, '#', '', '', 1, 0, 'F', '0', '0', 'dashboard:device-type:export', '#', 'admin', CURRENT_TIMESTAMP, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 8035);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id FROM (VALUES (8021),(8022),(8023),(8024),(8025),(8031),(8032),(8033),(8034),(8035)) AS m(menu_id)
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.menu_id);

COMMIT;
