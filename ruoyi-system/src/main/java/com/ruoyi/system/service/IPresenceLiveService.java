package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceLiveStartBo;
import com.ruoyi.system.domain.vo.PresenceLiveProbeVo;
import com.ruoyi.system.domain.vo.PresenceLiveTaskVo;

public interface IPresenceLiveService
{
    PresenceLiveTaskVo startLive(PresenceLiveStartBo bo);

    PresenceLiveTaskVo stopLive(String taskId);

    PresenceLiveTaskVo getTask(String taskId);

    /** 返回当前仍在运行/启动中的直播识别任务，无则 null。 */
    PresenceLiveTaskVo getActiveTask();

    /**
     * 拉主码流抽一帧，用于门线/ROI 标定。
     */
    PresenceLiveProbeVo captureProbeFrame(PresenceLiveStartBo bo);
}
