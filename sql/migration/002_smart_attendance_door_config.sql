-- 智能考勤摄像头（camera.id=2068, serial BH7367243）门线标定 + 局域网拉流信息
-- 蓝框：门框 ROI [438,418,555,847]  红框：门线上沿 [408,854,592,899]（0~1000 → 1920×1080）

UPDATE camera
SET line_y = 923,
    roi = '842,450,1066,915',
    ref_width = 1920,
    ref_height = 1080,
    ip_addr = '192.168.3.49',
    verify_code = 'HLVLXG',
    updated_at = NOW()
WHERE id = 2068;
