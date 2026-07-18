-- local only: replace placeholder serial for monitor screen
UPDATE camera SET
  device_code = 'CAM-206',
  device_name = '智能考勤',
  serial_no = 'BH7367243',
  verify_code = '',
  channel_no = 1,
  online_status = 'online',
  line_y = 658,
  roi = '640,35,1250,680',
  ref_width = 1920,
  ref_height = 1080,
  updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

SELECT id, device_code, device_name, serial_no, verify_code, channel_no, line_y, roi
FROM camera
WHERE id = 1;
