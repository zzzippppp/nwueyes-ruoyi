package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.vo.DeviceTypeVo;

public interface IDeviceTypeService
{
    List<DeviceTypeVo> selectDeviceTypeList(DeviceTypeVo query);

    DeviceTypeVo selectDeviceTypeById(Long id);

    boolean checkTypeCodeUnique(DeviceTypeVo type);

    boolean checkTypeNameUnique(DeviceTypeVo type);

    int insertDeviceType(DeviceTypeVo type);

    int updateDeviceType(DeviceTypeVo type);

    int deleteDeviceTypeByIds(Long[] ids);
}
