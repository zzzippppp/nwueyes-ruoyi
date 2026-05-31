package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceEventIngestBo;
import com.ruoyi.system.domain.vo.PresenceIngestResultVo;

public interface IPresenceIngestService
{
    PresenceIngestResultVo ingest(PresenceEventIngestBo bo);
}
