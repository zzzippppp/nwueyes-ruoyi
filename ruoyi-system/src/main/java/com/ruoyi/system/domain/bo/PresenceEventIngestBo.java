package com.ruoyi.system.domain.bo;

/**
 * 识别侧上报事件
 */
public class PresenceEventIngestBo
{
    private String eventType;

    private Long locationId;

    private String trackKey;

    private Long personId;

    private Long sessionId;

    private String eventTime;

    private String faceImageUrl;

    private String bodyImageUrl;

    private Float bestMatchScore;

    /** 为 true 时仅入队异步处理，立即返回 */
    private Boolean async;

    private String qualityFlag;

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public Long getLocationId()
    {
        return locationId;
    }

    public void setLocationId(Long locationId)
    {
        this.locationId = locationId;
    }

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getEventTime()
    {
        return eventTime;
    }

    public void setEventTime(String eventTime)
    {
        this.eventTime = eventTime;
    }

    public String getFaceImageUrl()
    {
        return faceImageUrl;
    }

    public void setFaceImageUrl(String faceImageUrl)
    {
        this.faceImageUrl = faceImageUrl;
    }

    public String getBodyImageUrl()
    {
        return bodyImageUrl;
    }

    public void setBodyImageUrl(String bodyImageUrl)
    {
        this.bodyImageUrl = bodyImageUrl;
    }

    public Float getBestMatchScore()
    {
        return bestMatchScore;
    }

    public void setBestMatchScore(Float bestMatchScore)
    {
        this.bestMatchScore = bestMatchScore;
    }

    public Boolean getAsync()
    {
        return async;
    }

    public void setAsync(Boolean async)
    {
        this.async = async;
    }

    public String getQualityFlag()
    {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag)
    {
        this.qualityFlag = qualityFlag;
    }
}
