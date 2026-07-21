package com.ruoyi.web.controller.dashboard;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.vo.DeviceTypeVo;
import com.ruoyi.system.service.IDeviceTypeService;

@RestController
@RequestMapping("/dashboard/device-type")
public class DeviceTypeController extends BaseController
{
    @Autowired
    private IDeviceTypeService deviceTypeService;

    @PreAuthorize("@ss.hasPermi('dashboard:device-type:list')")
    @GetMapping("/list")
    public TableDataInfo list(DeviceTypeVo query)
    {
        startPage();
        List<DeviceTypeVo> list = deviceTypeService.selectDeviceTypeList(query);
        return getDataTable(list);
    }

    /** 下拉选项（设备信息表单用，不分页） */
    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        return success(deviceTypeService.selectDeviceTypeList(new DeviceTypeVo()));
    }

    @Log(title = "设备类型", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('dashboard:device-type:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, DeviceTypeVo query)
    {
        List<DeviceTypeVo> list = deviceTypeService.selectDeviceTypeList(query);
        ExcelUtil<DeviceTypeVo> util = new ExcelUtil<DeviceTypeVo>(DeviceTypeVo.class);
        util.exportExcel(response, list, "设备类型数据");
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-type:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(deviceTypeService.selectDeviceTypeById(id));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-type:add')")
    @Log(title = "设备类型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DeviceTypeVo type)
    {
        if (!deviceTypeService.checkTypeCodeUnique(type))
        {
            return error("新增类型'" + type.getTypeName() + "'失败，类型编码已存在");
        }
        if (!deviceTypeService.checkTypeNameUnique(type))
        {
            return error("新增类型'" + type.getTypeName() + "'失败，类型名称已存在");
        }
        return toAjax(deviceTypeService.insertDeviceType(type));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-type:edit')")
    @Log(title = "设备类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DeviceTypeVo type)
    {
        if (!deviceTypeService.checkTypeCodeUnique(type))
        {
            return error("修改类型'" + type.getTypeName() + "'失败，类型编码已存在");
        }
        if (!deviceTypeService.checkTypeNameUnique(type))
        {
            return error("修改类型'" + type.getTypeName() + "'失败，类型名称已存在");
        }
        return toAjax(deviceTypeService.updateDeviceType(type));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-type:remove')")
    @Log(title = "设备类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(deviceTypeService.deleteDeviceTypeByIds(ids));
    }
}
