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
import com.ruoyi.system.domain.vo.LanPreviewVo;
import com.ruoyi.system.domain.vo.PresenceLiveTaskVo;
import com.ruoyi.system.service.IEzvizScreenService;
import com.ruoyi.system.service.ILanPreviewService;
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

    @Autowired
    private ILanPreviewService lanPreviewService;

    /**
     * 获取监控大屏播放配置（摄像头列表等）
     */
    @GetMapping("/config")
    public AjaxResult getConfig()
    {
        return AjaxResult.success(ezvizScreenService.getScreenConfig());
    }

    /**
     * 启动局域网 RTSP 预览（经 go2rtc 转 WebRTC，不走萤石公网）
     */
    @PostMapping("/preview/start")
    public AjaxResult startLanPreview(@RequestBody PresenceLiveStartBo bo)
    {
        LanPreviewVo preview = lanPreviewService.startPreview(bo);
        return AjaxResult.success(preview);
    }

    /**
     * 停止局域网预览并注销 go2rtc 流
     */
    @PostMapping("/preview/stop/{cameraId}")
    public AjaxResult stopLanPreview(@PathVariable("cameraId") Long cameraId)
    {
        lanPreviewService.stopPreview(cameraId);
        return AjaxResult.success();
    }

    /**
     * 启动直播识别（局域网 RTSP + YOLO + 异步 ingest）。
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
        return AjaxResult.success(task);
    }

    /**
     * 拉主码流抽一帧，用于门线标定。
     */
    @PostMapping("/probe-frame")
    public AjaxResult captureProbeFrame(@RequestBody PresenceLiveStartBo bo)
    {
        return AjaxResult.success(presenceLiveService.captureProbeFrame(bo));
    }
}
