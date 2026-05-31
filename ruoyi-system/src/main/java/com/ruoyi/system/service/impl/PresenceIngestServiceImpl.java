package com.ruoyi.system.service.impl;



import java.time.LocalDateTime;

import java.time.OffsetDateTime;

import java.time.ZoneId;

import java.time.format.DateTimeParseException;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.ruoyi.common.utils.StringUtils;

import com.ruoyi.system.domain.bo.PresenceEventIngestBo;

import com.ruoyi.system.domain.vo.PresenceIngestResultVo;

import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;

import com.ruoyi.system.service.IBehaviorLogService;
import com.ruoyi.system.service.IPresenceIngestService;

import com.ruoyi.system.service.IPresenceTrackService;



@Service

public class PresenceIngestServiceImpl implements IPresenceIngestService

{

    private static final String EVENT_ENTER = "enter";



    private static final String EVENT_EXIT = "exit";



    private static final String EVENT_HEARTBEAT = "heartbeat";



    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");



    @Autowired

    private IPresenceTrackService presenceTrackService;

    @Autowired

    private IBehaviorLogService behaviorLogService;



    @Override

    public PresenceIngestResultVo ingest(PresenceEventIngestBo bo)

    {

        String eventType = normalizeEventType(bo.getEventType());

        if (bo.getLocationId() == null)

        {

            throw new IllegalArgumentException("locationId 不能为空");

        }

        if (!EVENT_HEARTBEAT.equals(eventType) && StringUtils.isEmpty(bo.getTrackKey()) && bo.getSessionId() == null)

        {

            throw new IllegalArgumentException("trackKey/sessionId 至少提供一个");

        }



        Date eventTime = parseEventTime(bo.getEventTime());

        String trackKey = StringUtils.nvl(bo.getTrackKey(), "ingest_" + bo.getSessionId());



        PresenceTrackProcessResultVo processed;

        if (EVENT_EXIT.equals(eventType))

        {

            processed = presenceTrackService.processExit(bo.getLocationId(), trackKey, eventTime,

                    bo.getFaceImageUrl(), bo.getBodyImageUrl(), null);

        }

        else

        {

            processed = presenceTrackService.processEnter(bo.getLocationId(), trackKey, eventTime,

                    bo.getFaceImageUrl(), bo.getBodyImageUrl(), null);

        }



        PresenceIngestResultVo vo = new PresenceIngestResultVo();

        vo.setSessionId(processed.getSessionId());

        vo.setStatus(processed.getSessionStatus());

        vo.setEventType(eventType);

        if (EVENT_ENTER.equals(eventType) || EVENT_EXIT.equals(eventType))
        {
            behaviorLogService.recordLiveIngest(bo, eventTime, processed);
        }

        return vo;

    }



    private String normalizeEventType(String raw)

    {

        String eventType = StringUtils.isEmpty(raw) ? EVENT_HEARTBEAT : raw.toLowerCase();

        if (EVENT_ENTER.equals(eventType) || EVENT_EXIT.equals(eventType) || EVENT_HEARTBEAT.equals(eventType))

        {

            return eventType;

        }

        throw new IllegalArgumentException("不支持的 eventType: " + raw);

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

            throw new IllegalArgumentException("eventTime 格式错误，建议 ISO8601: " + raw);

        }

    }

}

