package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceReplayStartBo;
import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;

public interface IPresenceReplayService
{
    PresenceReplayTaskVo startReplay(PresenceReplayStartBo bo);

    /** YOLO+ByteTrack 分析（不入库） */
    PresenceReplayTaskVo startAnalyze(PresenceReplayStartBo bo);

    PresenceReplayTaskVo getTask(String taskId);
}
