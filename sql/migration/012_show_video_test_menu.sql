-- 恢复「视频检测」菜单（008 曾隐藏 menu_id=7），归入设备管理
-- 用法: psql -U postgres -d nwueyes -f ruoyi/sql/migration/012_show_video_test_menu.sql

BEGIN;

UPDATE sys_menu
SET menu_name = '视频检测',
    parent_id = 8,
    order_num = 4,
    path = 'video_test',
    component = 'dashboard/video_test/index',
    route_name = 'VideoTest',
    visible = '0',
    status = '0',
    perms = 'dashboard:video-test:list',
    icon = 'video',
    remark = '上传视频离线 YOLO 检测与过线标定'
WHERE menu_id = 7;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 7
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 7);

COMMIT;
