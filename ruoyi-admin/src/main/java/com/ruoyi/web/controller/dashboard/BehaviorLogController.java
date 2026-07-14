package com.ruoyi.web.controller.dashboard;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.domain.bo.AiAnalysisRunBo;
import com.ruoyi.system.domain.bo.BehaviorLogImportFromVideoBo;
import com.ruoyi.system.service.IBehaviorLogService;
import com.ruoyi.system.service.IVideoAnalysisService;

/**
 * 行为日志（数据看板子模块）
 */
@RestController
@RequestMapping("/dashboard/behavior-log")
public class BehaviorLogController
{
    @Autowired
    private IBehaviorLogService behaviorLogService;

    @Autowired
    private IVideoAnalysisService videoAnalysisService;

    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(value = "statDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate,
            @RequestParam(value = "beginDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate beginDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "cameraId", required = false) Long cameraId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "limit", required = false, defaultValue = "500") Integer limit)
    {
        return AjaxResult.success(behaviorLogService.listBehaviorLogs(statDate, beginDate, endDate, cameraId, eventType,
                beginTime, endTime, limit));
    }

    @PostMapping("/import-from-video")
    public AjaxResult importFromVideo(@RequestBody BehaviorLogImportFromVideoBo bo)
    {
        return AjaxResult.success(behaviorLogService.importFromVideoAnalyze(bo));
    }

    @GetMapping("/analysis/models")
    public AjaxResult analysisModels()
    {
        return AjaxResult.success(videoAnalysisService.listModelOptions());
    }

    @PostMapping("/analysis/run")
    public AjaxResult runAnalysis(@RequestBody AiAnalysisRunBo bo)
    {
        videoAnalysisService.runAnalysis(bo.getTargetType(), bo.getTargetId(), bo.getModelKeys());
        return AjaxResult.success("analysis submitted");
    }

    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable("id") Long id)
    {
        return behaviorLogService.deleteBehaviorLog(id) ? AjaxResult.success() : AjaxResult.error("删除失败，记录可能不存在");
    }
}
