package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.vo.AiModelOptionVo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;

public interface IVideoAnalysisService
{
    void submitClipAnalysis(PresenceVideoClipVo clip);

    void submitClipAnalysis(PresenceVideoClipVo clip, List<String> modelKeys);

    List<AiModelOptionVo> listModelOptions();

    void runAnalysis(String targetType, String targetId, List<String> modelKeys);
}
