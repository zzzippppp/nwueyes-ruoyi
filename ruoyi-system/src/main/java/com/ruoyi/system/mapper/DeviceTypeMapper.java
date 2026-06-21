package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.DeviceTypeVo;

public interface DeviceTypeMapper
{
    List<DeviceTypeVo> selectDeviceTypeList();

    DeviceTypeVo selectDeviceTypeById(@Param("id") Long id);

    int insertDeviceType(DeviceTypeVo row);

    int updateDeviceType(DeviceTypeVo row);

    int deleteDeviceTypeById(@Param("id") Long id);

    int countCameraByTypeId(@Param("typeId") Long typeId);
}
