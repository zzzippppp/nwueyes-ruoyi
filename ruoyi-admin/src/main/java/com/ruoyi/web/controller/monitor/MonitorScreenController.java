package com.ruoyi.web.controller.monitor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.domain.bo.PresenceLiveStartBo;
import com.ruoyi.system.domain.vo.PresenceLiveTaskVo;
import com.ruoyi.system.service.IEzvizScreenService;
import com.ruoyi.system.service.IPresenceLiveService;

/**
 * 监控大屏
 */
@RestController
@RequestMapping("/monitor/screen")
public class MonitorScreenController
{
    @Autowired
    private IEzvizScreenService ezvizScreenService;

    @Autowired
    private IPresenceLiveService presenceLiveService;

    /**
     * 获取监控大屏播放配置
     */
    @GetMapping("/config")
    public AjaxResult getConfig()
    {
        return AjaxResult.success(ezvizScreenService.getScreenConfig());
    }

    /**
     * 启动萤石直播识别（拉流 + YOLO + 异步 ingest）。
     */
    @PostMapping("/live/start")
    public AjaxResult startLiveRecognize(@RequestBody PresenceLiveStartBo bo)
    {
        PresenceLiveTaskVo task = presenceLiveService.startLive(bo);
        return AjaxResult.success(task);
    }

    /**
     * 停止直播识别任务。
     */
    @PostMapping("/live/stop/{taskId}")
    public AjaxResult stopLiveRecognize(@PathVariable("taskId") String taskId)
    {
        PresenceLiveTaskVo task = presenceLiveService.stopLive(taskId);
        return AjaxResult.success(task);
    }

    /**
     * 查询当前仍在运行的直播识别任务（页面跳转后恢复状态用）。
     */
    @GetMapping("/live/active")
    public AjaxResult activeLiveRecognize()
    {
        return AjaxResult.success(presenceLiveService.getActiveTask());
    }

    /**
     * 查询直播识别任务状态。
     */
    @GetMapping("/live/status/{taskId}")
    public AjaxResult liveRecognizeStatus(@PathVariable("taskId") String taskId)
    {
        PresenceLiveTaskVo task = presenceLiveService.getTask(taskId);
        if (task == null)
        {
            return AjaxResult.error("任务不存在: " + taskId);
        }
        return AjaxResult.success(task);
    }
}
