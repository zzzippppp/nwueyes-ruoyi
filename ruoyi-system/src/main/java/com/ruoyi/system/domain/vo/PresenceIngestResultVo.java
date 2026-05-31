package com.ruoyi.system.domain.vo;

/**
 * 识别事件入库结果
 */
public class PresenceIngestResultVo
{
    private Long sessionId;

    private String status;

    private String eventType;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }
}
