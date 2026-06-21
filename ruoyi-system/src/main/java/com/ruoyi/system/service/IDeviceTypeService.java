package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.bo.DeviceTypeSaveBo;
import com.ruoyi.system.domain.vo.DeviceTypeVo;

public interface IDeviceTypeService
{
    List<DeviceTypeVo> listDeviceTypes();

    DeviceTypeVo createDeviceType(DeviceTypeSaveBo bo);

    boolean updateDeviceType(Long id, DeviceTypeSaveBo bo);

    boolean deleteDeviceType(Long id);
}
