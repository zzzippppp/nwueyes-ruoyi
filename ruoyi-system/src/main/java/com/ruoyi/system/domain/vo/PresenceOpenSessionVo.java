package com.ruoyi.system.domain.vo;

import java.util.Date;

/**
 * 在场会话（匹配/更新用）
 */
public class PresenceOpenSessionVo
{
    private Long sessionId;

    private Long cameraId;

    private Long personId;

    private String trackKey;

    private Date arrivalAt;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public Date getArrivalAt()
    {
        return arrivalAt;
    }

    public void setArrivalAt(Date arrivalAt)
    {
        this.arrivalAt = arrivalAt;
    }
}
