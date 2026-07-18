package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.DeviceTypeVo;

public interface DeviceTypeMapper
{
    List<DeviceTypeVo> selectDeviceTypeList(DeviceTypeVo query);

    DeviceTypeVo selectDeviceTypeById(@Param("id") Long id);

    DeviceTypeVo checkTypeCodeUnique(@Param("typeCode") String typeCode);

    DeviceTypeVo checkTypeNameUnique(@Param("typeName") String typeName);

    int insertDeviceType(DeviceTypeVo row);

    int updateDeviceType(DeviceTypeVo row);

    int deleteDeviceTypeByIds(@Param("ids") Long[] ids);

    int countCameraByTypeId(@Param("typeId") Long typeId);

    int countCameraByTypeIds(@Param("ids") Long[] ids);
}
