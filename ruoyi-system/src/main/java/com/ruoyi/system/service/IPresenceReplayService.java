package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.bo.PresenceReplayStartBo;
import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;

public interface IPresenceReplayService
{
    PresenceReplayTaskVo startReplay(PresenceReplayStartBo bo);

    /** YOLO+ByteTrack 分析（不入库） */
    PresenceReplayTaskVo startAnalyze(PresenceReplayStartBo bo);

    PresenceReplayTaskVo getTask(String taskId);

    /** 对分析任务的源视频做 AI 样貌/行为理解（异步）；结果写入 resultJson.aiAnalysis */
    Map<String, Object> submitAiAnalysisForTask(String taskId, List<String> modelKeys);

    /**
     * 同步确保任务级 AI 分析已写入 resultJson.aiAnalysis（导入行为日志前调用）。
     * 已成功则直接返回；进行中会短暂等待；否则同步跑一遍。
     */
    Map<String, Object> ensureAiAnalysisForTask(String taskId);
}
