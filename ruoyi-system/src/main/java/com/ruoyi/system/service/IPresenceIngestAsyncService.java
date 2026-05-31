package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceEventIngestBo;

public interface IPresenceIngestAsyncService
{
    /**
     * 异步入队处理过线事件，队列满时丢弃最旧任务。
     */
    void submit(PresenceEventIngestBo bo);
}
