package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceVideoClipIngestBo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;

public interface IPresenceVideoClipService
{
    PresenceVideoClipVo ingestClip(PresenceVideoClipIngestBo bo);

    /**
     * 分析后清理：若该片段/场景未产生任何证据行为日志，则删除视频文件、片段记录及分析产物。
     *
     * @return true 表示已删除（无证据），false 表示保留（有证据或未命中删除条件）
     */
    boolean deleteClipIfNoEvidence(Long clipId, String sceneGroupId, String taskId);
}
