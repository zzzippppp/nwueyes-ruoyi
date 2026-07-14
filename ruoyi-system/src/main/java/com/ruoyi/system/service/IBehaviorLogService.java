package com.ruoyi.system.service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.bo.BehaviorLogImportFromVideoBo;
import com.ruoyi.system.domain.bo.PresenceEventIngestBo;
import com.ruoyi.system.domain.vo.BehaviorLogImportResultVo;
import com.ruoyi.system.domain.vo.BehaviorLogItemVo;
import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;

public interface IBehaviorLogService
{
    List<BehaviorLogItemVo> listBehaviorLogs(LocalDate statDate, LocalDate beginDate, LocalDate endDate,
            Long cameraId, String eventType, String beginTime, String endTime, Integer limit);

    BehaviorLogImportResultVo importFromVideoAnalyze(BehaviorLogImportFromVideoBo bo);

    /**
     * 直播/实时 ingest 成功后写入行为日志（source=live）。
     */
    void recordLiveIngest(PresenceEventIngestBo bo, Date eventTime, PresenceTrackProcessResultVo processed);

    boolean deleteBehaviorLog(Long id);
}
