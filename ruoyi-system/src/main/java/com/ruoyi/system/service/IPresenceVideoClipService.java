package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceVideoClipIngestBo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;

public interface IPresenceVideoClipService
{
    PresenceVideoClipVo ingestClip(PresenceVideoClipIngestBo bo);
}
