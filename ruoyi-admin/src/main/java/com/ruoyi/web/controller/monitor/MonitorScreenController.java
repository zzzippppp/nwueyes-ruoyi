package com.ruoyi.web.controller.monitor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.IEzvizScreenService;

/**
 * 监控大屏
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/monitor/screen")
public class MonitorScreenController
{
    @Autowired
    private IEzvizScreenService ezvizScreenService;

    /**
     * 获取监控大屏播放配置
     * 
     * @return 播放配置
     */
    @GetMapping("/config")
    public AjaxResult getConfig()
    {
        return AjaxResult.success(ezvizScreenService.getScreenConfig());
    }
}
