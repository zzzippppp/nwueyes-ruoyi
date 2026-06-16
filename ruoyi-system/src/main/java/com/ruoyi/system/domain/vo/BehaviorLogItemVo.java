package com.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 行为日志列表项
 */
public class BehaviorLogItemVo
{
    private Long id;

    private String displayName;

    private String eventType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date eventTime;

    private String snapshotUrl;

    private String videoUrl;

    private String personType;

    private String employeeNo;

    private Long locationId;

    private String locationName;

    private Long personId;

    private String trackKey;

    private Long sessionId;

    private Float faceMatchScore;

    private Float bodyMatchScore;

    private String qualityFlag;

    /** 行为分析（自然语言） */
    private String behaviorAnalysis;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public Date getEventTime()
    {
        return eventTime;
    }

    public void setEventTime(Date eventTime)
    {
        this.eventTime = eventTime;
    }

    public String getSnapshotUrl()
    {
        return snapshotUrl;
    }

    public void setSnapshotUrl(String snapshotUrl)
    {
        this.snapshotUrl = snapshotUrl;
    }

    public String getVideoUrl()
    {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl)
    {
        this.videoUrl = videoUrl;
    }

    public String getPersonType()
    {
        return personType;
    }

    public void setPersonType(String personType)
    {
        this.personType = personType;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    /** @deprecated 兼容旧前端，等同 snapshotUrl */
    public String getFaceImageUrl()
    {
        return snapshotUrl;
    }

    public void setFaceImageUrl(String faceImageUrl)
    {
        this.snapshotUrl = faceImageUrl;
    }

    /** @deprecated 不再使用 */
    public String getBodyImageUrl()
    {
        return "";
    }

    public void setBodyImageUrl(String bodyImageUrl)
    {
        // no-op
    }

    public Long getLocationId()
    {
        return locationId;
    }

    public void setLocationId(Long locationId)
    {
        this.locationId = locationId;
    }

    public String getLocationName()
    {
        return locationName;
    }

    public void setLocationName(String locationName)
    {
        this.locationName = locationName;
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

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    /** @deprecated 使用 personType */
    public String getPersonKind()
    {
        return personType;
    }

    public void setPersonKind(String personKind)
    {
        this.personType = personKind;
    }

    /** @deprecated 已移除 source 字段 */
    public String getSource()
    {
        return "live";
    }

    public void setSource(String source)
    {
        // no-op
    }

    public Float getFaceMatchScore()
    {
        return faceMatchScore;
    }

    public void setFaceMatchScore(Float faceMatchScore)
    {
        this.faceMatchScore = faceMatchScore;
    }

    public Float getBodyMatchScore()
    {
        return bodyMatchScore;
    }

    public void setBodyMatchScore(Float bodyMatchScore)
    {
        this.bodyMatchScore = bodyMatchScore;
    }

    public String getQualityFlag()
    {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag)
    {
        this.qualityFlag = qualityFlag;
    }

    public String getBehaviorAnalysis()
    {
        return behaviorAnalysis;
    }

    public void setBehaviorAnalysis(String behaviorAnalysis)
    {
        this.behaviorAnalysis = behaviorAnalysis;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }
}
