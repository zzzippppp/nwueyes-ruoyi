package com.ruoyi.system.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.vo.LocationConfigVo;
import com.ruoyi.system.mapper.LocationMapper;
import com.ruoyi.system.service.ILocationService;

@Service
public class LocationServiceImpl implements ILocationService
{
    @Autowired
    private LocationMapper locationMapper;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resolveOrCreateLocation(String deviceSerial, Integer channelNo, String defaultName)
    {
        if (StringUtils.isEmpty(deviceSerial))
        {
            throw new IllegalArgumentException("deviceSerial 不能为空");
        }
        int channel = channelNo == null || channelNo < 1 ? 1 : channelNo.intValue();
        Long existing = locationMapper.selectIdByDevice(deviceSerial.trim(), channel);
        if (existing != null)
        {
            return existing;
        }
        String name = StringUtils.isEmpty(defaultName) ? "监控点位-" + deviceSerial : defaultName;
        locationMapper.insertLocation(deviceSerial.trim(), channel, name);
        Long created = locationMapper.selectIdByDevice(deviceSerial.trim(), channel);
        if (created == null)
        {
            throw new IllegalStateException("创建监控点位失败");
        }
        Integer lineY = ingestProperties.getReplayLineY();
        String roi = ingestProperties.getReplayRoi();
        if (lineY != null || !StringUtils.isEmpty(roi))
        {
            locationMapper.updateDoorConfig(created, lineY, roi);
        }
        return created;
    }

    @Override
    public LocationConfigVo getLocationConfig(Long locationId)
    {
        if (locationId == null)
        {
            return null;
        }
        return locationMapper.selectById(locationId);
    }
}
