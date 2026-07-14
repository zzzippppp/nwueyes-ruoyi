package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.vo.CameraConfigVo;
import com.ruoyi.system.mapper.CameraMapper;
import com.ruoyi.system.service.ICameraService;

@Service
public class CameraServiceImpl implements ICameraService
{
    private static final long DEFAULT_TYPE_ID = 1L;

    @Autowired
    private CameraMapper cameraMapper;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Override
    public List<CameraConfigVo> listMonitorCameras()
    {
        return cameraMapper.selectMonitorList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resolveOrCreateCamera(String serialNo, Integer channelNo, String defaultName)
    {
        if (StringUtils.isEmpty(serialNo))
        {
            throw new IllegalArgumentException("serialNo 不能为空");
        }
        int channel = channelNo == null || channelNo < 1 ? 1 : channelNo.intValue();
        Long existing = cameraMapper.selectIdBySerial(serialNo.trim(), channel);
        if (existing != null)
        {
            return existing;
        }
        String name = StringUtils.isEmpty(defaultName) ? "摄像头-" + serialNo : defaultName;
        String deviceCode = "CAM-" + System.currentTimeMillis();
        cameraMapper.insertCamera(deviceCode, serialNo.trim(), channel, name, DEFAULT_TYPE_ID);
        Long created = cameraMapper.selectIdBySerial(serialNo.trim(), channel);
        if (created == null)
        {
            throw new IllegalStateException("创建摄像头失败");
        }
        Integer lineY = ingestProperties.getReplayLineY();
        String roi = ingestProperties.getReplayRoi();
        if (lineY != null || !StringUtils.isEmpty(roi))
        {
            cameraMapper.updateDoorConfig(created, lineY, roi);
        }
        return created;
    }

    @Override
    public CameraConfigVo getCameraConfig(Long cameraId)
    {
        if (cameraId == null)
        {
            return null;
        }
        return cameraMapper.selectById(cameraId);
    }
}
