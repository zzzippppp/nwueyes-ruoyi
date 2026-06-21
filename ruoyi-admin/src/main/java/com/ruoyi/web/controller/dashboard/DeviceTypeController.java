package com.ruoyi.web.controller.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.domain.bo.DeviceTypeSaveBo;
import com.ruoyi.system.service.IDeviceTypeService;

@RestController
@RequestMapping("/dashboard/device-type")
public class DeviceTypeController
{
    @Autowired
    private IDeviceTypeService deviceTypeService;

    @GetMapping("/list")
    public AjaxResult list()
    {
        return AjaxResult.success(deviceTypeService.listDeviceTypes());
    }

    @PostMapping
    public AjaxResult create(@RequestBody DeviceTypeSaveBo bo)
    {
        return AjaxResult.success(deviceTypeService.createDeviceType(bo));
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable("id") Long id, @RequestBody DeviceTypeSaveBo bo)
    {
        return deviceTypeService.updateDeviceType(id, bo) ? AjaxResult.success() : AjaxResult.error("更新失败");
    }

    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable("id") Long id)
    {
        return deviceTypeService.deleteDeviceType(id) ? AjaxResult.success() : AjaxResult.error("删除失败");
    }
}
