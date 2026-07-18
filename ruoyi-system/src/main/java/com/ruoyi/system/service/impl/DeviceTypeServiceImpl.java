package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.DeviceTypeVo;
import com.ruoyi.system.mapper.DeviceTypeMapper;
import com.ruoyi.system.service.IDeviceTypeService;

@Service
public class DeviceTypeServiceImpl implements IDeviceTypeService
{
    @Autowired
    private DeviceTypeMapper deviceTypeMapper;

    @Override
    public List<DeviceTypeVo> selectDeviceTypeList(DeviceTypeVo query)
    {
        return deviceTypeMapper.selectDeviceTypeList(query);
    }

    @Override
    public DeviceTypeVo selectDeviceTypeById(Long id)
    {
        return deviceTypeMapper.selectDeviceTypeById(id);
    }

    @Override
    public boolean checkTypeCodeUnique(DeviceTypeVo type)
    {
        Long id = type.getId() == null ? -1L : type.getId();
        DeviceTypeVo existing = deviceTypeMapper.checkTypeCodeUnique(type.getTypeCode());
        if (StringUtils.isNotNull(existing) && existing.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public boolean checkTypeNameUnique(DeviceTypeVo type)
    {
        Long id = type.getId() == null ? -1L : type.getId();
        DeviceTypeVo existing = deviceTypeMapper.checkTypeNameUnique(type.getTypeName());
        if (StringUtils.isNotNull(existing) && existing.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public int insertDeviceType(DeviceTypeVo type)
    {
        normalize(type);
        if (type.getRemark() == null)
        {
            type.setRemark("");
        }
        return deviceTypeMapper.insertDeviceType(type);
    }

    @Override
    public int updateDeviceType(DeviceTypeVo type)
    {
        normalize(type);
        if (type.getRemark() == null)
        {
            type.setRemark("");
        }
        return deviceTypeMapper.updateDeviceType(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeviceTypeByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        if (deviceTypeMapper.countCameraByTypeIds(ids) > 0)
        {
            throw new ServiceException("所选类型已被摄像头引用，无法删除");
        }
        return deviceTypeMapper.deleteDeviceTypeByIds(ids);
    }

    private void normalize(DeviceTypeVo type)
    {
        if (type.getTypeCode() != null)
        {
            type.setTypeCode(type.getTypeCode().trim());
        }
        if (type.getTypeName() != null)
        {
            type.setTypeName(type.getTypeName().trim());
        }
    }
}
