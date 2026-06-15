package com.ruoyi.web.controller.ingest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceEventIngestBo;
import com.ruoyi.system.domain.bo.PresenceVideoClipIngestBo;
import com.ruoyi.system.domain.bo.PresenceReplayStartBo;
import com.ruoyi.system.domain.vo.PresenceIngestResultVo;
import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;
import com.ruoyi.system.service.IPresenceEmbedService;
import com.ruoyi.system.service.IPresenceIngestAsyncService;
import com.ruoyi.system.service.IPresenceIngestService;
import com.ruoyi.system.service.IPresenceVideoClipService;
import com.ruoyi.system.service.IPresenceReplayService;
import com.ruoyi.system.domain.vo.AnalyzeEmbedResultVo;
import com.ruoyi.system.domain.vo.PresenceDoorConfigVo;
import java.util.HashMap;
import java.util.Map;

/**
 * 算法识别事件接入
 */
@RestController
@RequestMapping("/ingest/presence")
public class PresenceIngestController
{
    private static final String INGEST_KEY_HEADER = "X-Ingest-Key";

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private IPresenceIngestService presenceIngestService;

    @Autowired
    private IPresenceReplayService presenceReplayService;

    @Autowired
    private IPresenceEmbedService presenceEmbedService;

    @Autowired
    private IPresenceIngestAsyncService presenceIngestAsyncService;

    @Autowired
    private IPresenceVideoClipService presenceVideoClipService;

    @PostMapping("/event")
    @Anonymous
    public AjaxResult ingest(@RequestBody PresenceEventIngestBo bo,
            @RequestHeader(value = INGEST_KEY_HEADER, required = false) String ingestKey)
    {
        if (!ingestProperties.isEnabled())
        {
            return AjaxResult.error("presence ingest 未启用");
        }
        if (!StringUtils.isEmpty(ingestProperties.getApiKey()) && !ingestProperties.getApiKey().equals(ingestKey))
        {
            return AjaxResult.error("ingest key 无效");
        }
        if (bo.getLocationId() == null)
        {
            bo.setLocationId(ingestProperties.getDefaultLocationId());
        }

        if (Boolean.TRUE.equals(bo.getAsync()))
        {
            presenceIngestAsyncService.submit(bo);
            Map<String, Object> accepted = new HashMap<>();
            accepted.put("accepted", true);
            accepted.put("trackKey", bo.getTrackKey());
            accepted.put("eventType", bo.getEventType());
            return AjaxResult.success(accepted);
        }

        PresenceIngestResultVo result = presenceIngestService.ingest(bo);
        return AjaxResult.success(result);
    }

    @PostMapping("/clip")
    @Anonymous
    public AjaxResult ingestClip(@RequestBody PresenceVideoClipIngestBo bo,
            @RequestHeader(value = INGEST_KEY_HEADER, required = false) String ingestKey)
    {
        if (!ingestProperties.isEnabled())
        {
            return AjaxResult.error("presence ingest disabled");
        }
        if (!StringUtils.isEmpty(ingestProperties.getApiKey()) && !ingestProperties.getApiKey().equals(ingestKey))
        {
            return AjaxResult.error("ingest key invalid");
        }
        if (bo.getLocationId() == null)
        {
            bo.setLocationId(ingestProperties.getDefaultLocationId());
        }
        return AjaxResult.success(presenceVideoClipService.ingestClip(bo));
    }

    @GetMapping("/door-config")
    public AjaxResult doorConfig()
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        PresenceDoorConfigVo vo = new PresenceDoorConfigVo();
        vo.setLineY(ingestProperties.getReplayLineY());
        vo.setRoi(ingestProperties.getReplayRoi());
        vo.setYoloConf(live == null ? null : live.getYoloConf());
        vo.setSnapshotWindowSec(ingestProperties.getSnapshotWindowSec());
        vo.setEnterFaceHuntMaxSec(live == null ? null : live.getEnterFaceHuntMaxSec());
        vo.setEnterFaceGraceSec(live == null ? null : live.getEnterFaceGraceSec());
        return AjaxResult.success(vo);
    }

    @PostMapping("/replay/start")
    public AjaxResult startReplay(@RequestBody PresenceReplayStartBo bo)
    {
        PresenceReplayTaskVo task = presenceReplayService.startReplay(bo);
        return AjaxResult.success(task);
    }

    @PostMapping("/analyze/start")
    public AjaxResult startAnalyze(@RequestBody PresenceReplayStartBo bo)
    {
        PresenceReplayTaskVo task = presenceReplayService.startAnalyze(bo);
        return AjaxResult.success(task);
    }

    @GetMapping("/replay/status/{taskId}")
    public AjaxResult replayStatus(@PathVariable("taskId") String taskId)
    {
        PresenceReplayTaskVo task = presenceReplayService.getTask(taskId);
        if (task == null)
        {
            return AjaxResult.error("任务不存在: " + taskId);
        }
        return AjaxResult.success(task);
    }

    /**
     * 对分析任务抓拍图抽取人脸/体态 512 维向量（视频测试页展示用）。
     */
    @PostMapping("/analyze/embed/{taskId}")
    public AjaxResult embedAnalyzeCaptures(@PathVariable("taskId") String taskId)
    {
        AnalyzeEmbedResultVo result = presenceEmbedService.embedAnalyzeCaptures(taskId);
        return AjaxResult.success(result);
    }
}
