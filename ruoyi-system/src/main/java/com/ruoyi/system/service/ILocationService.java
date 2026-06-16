package com.ruoyi.system.service;

import com.ruoyi.system.domain.vo.LocationConfigVo;

public interface ILocationService
{
    /**
     * 方案 B：按设备号+通道查找点位，不存在则创建。
     */
    Long resolveOrCreateLocation(String deviceSerial, Integer channelNo, String defaultName);

    LocationConfigVo getLocationConfig(Long locationId);
}
