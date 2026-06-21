package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceEventIngestBo;
import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;
import com.ruoyi.system.service.IBehaviorLogService;
import com.ruoyi.system.service.IPresenceIngestAsyncService;
import com.ruoyi.system.service.IPresenceTrackService;

@Service
public class PresenceIngestAsyncServiceImpl implements IPresenceIngestAsyncService
{
    private static final Logger log = LoggerFactory.getLogger(PresenceIngestAsyncServiceImpl.class);

    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private final ConcurrentHashMap<String, Long> dedupeAt = new ConcurrentHashMap<>();

    @Autowired
    @Qualifier("presenceIngestExecutor")
    private ThreadPoolTaskExecutor presenceIngestExecutor;

    @Autowired
    private IPresenceTrackService presenceTrackService;

    @Autowired
    private IBehaviorLogService behaviorLogService;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Override
    public void submit(PresenceEventIngestBo bo)
    {
        if (bo == null || StringUtils.isEmpty(bo.getEventType()))
        {
            return;
        }
        String dedupeKey = StringUtils.nvl(bo.getCameraId(), 0L) + ":" + StringUtils.nvl(bo.getTrackKey(), "")
                + ":" + bo.getEventType().toLowerCase();
        long now = System.currentTimeMillis();
        Long last = dedupeAt.get(dedupeKey);
        long dedupeMs = ingestProperties.getLive().getIngestDedupeMs();
        if (last != null && now - last < dedupeMs)
        {
            log.debug("skip duplicate ingest {}", dedupeKey);
            return;
        }
        dedupeAt.put(dedupeKey, now);

        try
        {
            presenceIngestExecutor.execute(() -> processEvent(bo));
        }
        catch (Exception ex)
        {
            log.warn("ingest queue reject track={} event={}: {}", bo.getTrackKey(), bo.getEventType(), ex.getMessage());
        }
    }

    private void processEvent(PresenceEventIngestBo bo)
    {
        try
        {
            Date eventTime = parseEventTime(bo.getEventTime());
            String eventType = bo.getEventType().toLowerCase();
            String quality = StringUtils.nvl(bo.getQualityFlag(), "normal");
            PresenceTrackProcessResultVo result;
            if ("exit".equals(eventType))
            {
                result = presenceTrackService.processExit(bo.getCameraId(), bo.getTrackKey(), eventTime,
                        bo.getFaceImageUrl(), bo.getBodyImageUrl(), quality);
            }
            else
            {
                result = presenceTrackService.processEnter(bo.getCameraId(), bo.getTrackKey(), eventTime,
                        bo.getFaceImageUrl(), bo.getBodyImageUrl(), quality);
            }
            behaviorLogService.recordLiveIngest(bo, eventTime, result);
            if (result.isSkippedDuplicateEnter())
            {
                log.info("async ingest duplicate enter log-only track={} session={} person={}",
                        bo.getTrackKey(), result.getSessionId(), result.getPersonId());
            }
            else if (result.isSkippedOrphanExit())
            {
                log.info("async ingest orphan exit log-only track={}", bo.getTrackKey());
            }
            else
            {
                log.info("async ingest ok track={} event={} session={} person={}",
                        bo.getTrackKey(), eventType, result.getSessionId(), result.getPersonId());
            }
        }
        catch (Exception ex)
        {
            log.error("async ingest failed track={} event={}: {}", bo.getTrackKey(), bo.getEventType(), ex.getMessage(),
                    ex);
        }
    }

    private Date parseEventTime(String raw)
    {
        if (StringUtils.isEmpty(raw))
        {
            return new Date();
        }
        try
        {
            return Date.from(OffsetDateTime.parse(raw).toInstant());
        }
        catch (DateTimeParseException ignored)
        {
        }
        try
        {
            LocalDateTime ldt = LocalDateTime.parse(raw);
            return Date.from(ldt.atZone(STAT_ZONE).toInstant());
        }
        catch (DateTimeParseException ex)
        {
            return new Date();
        }
    }
}
