package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.DeviceInfo;
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

    @Override
    public List<DeviceInfo> selectDeviceInfoList(DeviceInfo query)
    {
        return cameraMapper.selectDeviceInfoList(query);
    }

    @Override
    public DeviceInfo selectDeviceInfoById(Long id)
    {
        return cameraMapper.selectDeviceInfoById(id);
    }

    @Override
    public boolean checkDeviceNameUnique(DeviceInfo info)
    {
        Long id = info.getId() == null ? -1L : info.getId();
        DeviceInfo existing = cameraMapper.checkDeviceNameUnique(info.getDeviceName());
        if (StringUtils.isNotNull(existing) && existing.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public boolean checkSerialNoUnique(DeviceInfo info)
    {
        Long id = info.getId() == null ? -1L : info.getId();
        DeviceInfo existing = cameraMapper.checkSerialNoUnique(info.getSerialNo());
        if (StringUtils.isNotNull(existing) && existing.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public boolean checkDeviceCodeUnique(DeviceInfo info)
    {
        Long id = info.getId() == null ? -1L : info.getId();
        DeviceInfo existing = cameraMapper.checkDeviceCodeUnique(info.getDeviceCode());
        if (StringUtils.isNotNull(existing) && existing.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public int insertDeviceInfo(DeviceInfo info)
    {
        normalize(info);
        if (StringUtils.isEmpty(info.getDeviceCode()))
        {
            info.setDeviceCode("CAM-" + System.currentTimeMillis());
        }
        if (info.getChannelNo() == null || info.getChannelNo() < 1)
        {
            info.setChannelNo(1);
        }
        if (StringUtils.isEmpty(info.getOnlineStatus()))
        {
            info.setOnlineStatus("offline");
        }
        if (info.getTypeId() == null)
        {
            info.setTypeId(DEFAULT_TYPE_ID);
        }
        if (info.getRefWidth() == null)
        {
            info.setRefWidth(1920);
        }
        if (info.getRefHeight() == null)
        {
            info.setRefHeight(1080);
        }
        return cameraMapper.insertDeviceInfo(info);
    }

    @Override
    public int updateDeviceInfo(DeviceInfo info)
    {
        normalize(info);
        if (info.getChannelNo() == null || info.getChannelNo() < 1)
        {
            info.setChannelNo(1);
        }
        if (StringUtils.isEmpty(info.getOnlineStatus()))
        {
            info.setOnlineStatus("offline");
        }
        return cameraMapper.updateDeviceInfo(info);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeviceInfoByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        if (cameraMapper.countBehaviorByCameraIds(ids) > 0 || cameraMapper.countSessionByCameraIds(ids) > 0)
        {
            throw new ServiceException("所选设备已被考勤/行为日志引用，无法删除");
        }
        return cameraMapper.deleteDeviceInfoByIds(ids);
    }

    private void normalize(DeviceInfo info)
    {
        if (info.getDeviceName() != null)
        {
            info.setDeviceName(info.getDeviceName().trim());
        }
        if (info.getSerialNo() != null)
        {
            info.setSerialNo(info.getSerialNo().trim().toUpperCase());
        }
        if (info.getDeviceCode() != null)
        {
            info.setDeviceCode(info.getDeviceCode().trim());
        }
        if (info.getVerifyCode() == null)
        {
            info.setVerifyCode("");
        }
    }
}
