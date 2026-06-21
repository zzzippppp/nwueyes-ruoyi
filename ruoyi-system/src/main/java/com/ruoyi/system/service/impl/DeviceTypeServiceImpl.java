package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.bo.DeviceTypeSaveBo;
import com.ruoyi.system.domain.vo.DeviceTypeVo;
import com.ruoyi.system.mapper.DeviceTypeMapper;
import com.ruoyi.system.service.IDeviceTypeService;

@Service
public class DeviceTypeServiceImpl implements IDeviceTypeService
{
    @Autowired
    private DeviceTypeMapper deviceTypeMapper;

    @Override
    public List<DeviceTypeVo> listDeviceTypes()
    {
        return deviceTypeMapper.selectDeviceTypeList();
    }

    @Override
    public DeviceTypeVo createDeviceType(DeviceTypeSaveBo bo)
    {
        validateSaveBo(bo, true);
        DeviceTypeVo row = new DeviceTypeVo();
        row.setTypeCode(bo.getTypeCode().trim());
        row.setTypeName(bo.getTypeName().trim());
        row.setRemark(StringUtils.nvl(bo.getRemark(), ""));
        deviceTypeMapper.insertDeviceType(row);
        return deviceTypeMapper.selectDeviceTypeById(row.getId());
    }

    @Override
    public boolean updateDeviceType(Long id, DeviceTypeSaveBo bo)
    {
        if (id == null || deviceTypeMapper.selectDeviceTypeById(id) == null)
        {
            return false;
        }
        validateSaveBo(bo, false);
        DeviceTypeVo row = new DeviceTypeVo();
        row.setId(id);
        row.setTypeName(bo.getTypeName().trim());
        row.setRemark(StringUtils.nvl(bo.getRemark(), ""));
        return deviceTypeMapper.updateDeviceType(row) > 0;
    }

    @Override
    public boolean deleteDeviceType(Long id)
    {
        if (id == null || deviceTypeMapper.selectDeviceTypeById(id) == null)
        {
            return false;
        }
        if (deviceTypeMapper.countCameraByTypeId(id) > 0)
        {
            throw new ServiceException("该类型已被摄像头引用，无法删除");
        }
        return deviceTypeMapper.deleteDeviceTypeById(id) > 0;
    }

    private void validateSaveBo(DeviceTypeSaveBo bo, boolean requireCode)
    {
        if (bo == null)
        {
            throw new ServiceException("参数不能为空");
        }
        if (requireCode && StringUtils.isEmpty(bo.getTypeCode()))
        {
            throw new ServiceException("类型编码不能为空");
        }
        if (StringUtils.isEmpty(bo.getTypeName()))
        {
            throw new ServiceException("类型名称不能为空");
        }
    }
}
