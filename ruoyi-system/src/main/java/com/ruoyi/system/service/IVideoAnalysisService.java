package com.ruoyi.system.service;

import java.nio.file.Path;
import java.util.List;
import com.ruoyi.system.domain.vo.AiAnalysisResultVo;
import com.ruoyi.system.domain.vo.AiModelOptionVo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;

public interface IVideoAnalysisService
{
    void submitClipAnalysis(PresenceVideoClipVo clip);

    void submitClipAnalysis(PresenceVideoClipVo clip, List<String> modelKeys);

    List<AiModelOptionVo> listModelOptions();

    void runAnalysis(String targetType, String targetId, List<String> modelKeys);

    /** 对本地视频文件同步调用已启用模型（视频测试页等） */
    List<AiAnalysisResultVo> analyzeLocalVideo(Path videoFile, List<String> modelKeys);

    /**
     * 对本地视频调用模型；eventTimesSec 为 YOLO 过线事件秒数（可空）。
     * 有事件则围着事件抽帧，无事件则抽视频中段 60%。
     */
    List<AiAnalysisResultVo> analyzeLocalVideo(Path videoFile, List<String> modelKeys, List<Double> eventTimesSec);
}
