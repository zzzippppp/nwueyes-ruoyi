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
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.DeviceInfo;
import com.ruoyi.system.service.ICameraService;
import com.ruoyi.system.service.IEzvizScreenService;

@RestController
@RequestMapping("/dashboard/device-info")
public class DeviceInfoController extends BaseController
{
    @Autowired
    private ICameraService cameraService;

    @Autowired
    private IEzvizScreenService ezvizScreenService;

    @PreAuthorize("@ss.hasPermi('dashboard:device-info:list')")
    @GetMapping("/list")
    public TableDataInfo list(DeviceInfo query)
    {
        startPage();
        List<DeviceInfo> list = cameraService.selectDeviceInfoList(query);
        return getDataTable(list);
    }

    @Log(title = "设备信息", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('dashboard:device-info:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, DeviceInfo query)
    {
        List<DeviceInfo> list = cameraService.selectDeviceInfoList(query);
        ExcelUtil<DeviceInfo> util = new ExcelUtil<DeviceInfo>(DeviceInfo.class);
        util.exportExcel(response, list, "设备信息数据");
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-info:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(cameraService.selectDeviceInfoById(id));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-info:add')")
    @Log(title = "设备信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DeviceInfo info)
    {
        if (!cameraService.checkDeviceNameUnique(info))
        {
            return error("新增设备'" + info.getDeviceName() + "'失败，设备名称已存在");
        }
        if (!cameraService.checkSerialNoUnique(info))
        {
            return error("新增设备'" + info.getDeviceName() + "'失败，设备序列号已存在");
        }
        if (info.getDeviceCode() != null && !info.getDeviceCode().isEmpty()
                && !cameraService.checkDeviceCodeUnique(info))
        {
            return error("新增设备'" + info.getDeviceName() + "'失败，设备编码已存在");
        }
        // 通过萤石开放平台校验：序列号必须对应已绑定到当前账号的真实设备
        ezvizScreenService.assertDeviceBound(info.getSerialNo(), info.getVerifyCode());
        return toAjax(cameraService.insertDeviceInfo(info));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-info:edit')")
    @Log(title = "设备信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DeviceInfo info)
    {
        if (!cameraService.checkDeviceNameUnique(info))
        {
            return error("修改设备'" + info.getDeviceName() + "'失败，设备名称已存在");
        }
        if (!cameraService.checkSerialNoUnique(info))
        {
            return error("修改设备'" + info.getDeviceName() + "'失败，设备序列号已存在");
        }
        if (info.getDeviceCode() != null && !info.getDeviceCode().isEmpty()
                && !cameraService.checkDeviceCodeUnique(info))
        {
            return error("修改设备'" + info.getDeviceName() + "'失败，设备编码已存在");
        }
        // 序列号变更时再次走萤石校验，避免改成假数据
        if (isSerialNoChanged(info))
        {
            ezvizScreenService.assertDeviceBound(info.getSerialNo(), info.getVerifyCode());
        }
        return toAjax(cameraService.updateDeviceInfo(info));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:device-info:remove')")
    @Log(title = "设备信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(cameraService.deleteDeviceInfoByIds(ids));
    }

    private boolean isSerialNoChanged(DeviceInfo info)
    {
        if (info.getId() == null)
        {
            return true;
        }
        DeviceInfo existing = cameraService.selectDeviceInfoById(info.getId());
        if (existing == null)
        {
            return true;
        }
        String oldSerial = StringUtils.nvl(existing.getSerialNo(), "").trim();
        String newSerial = StringUtils.nvl(info.getSerialNo(), "").trim();
        return !oldSerial.equalsIgnoreCase(newSerial);
    }
}
