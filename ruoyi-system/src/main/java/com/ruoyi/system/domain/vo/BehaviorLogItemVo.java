package com.ruoyi.system.domain.vo;

import java.util.Date;
import java.util.List;
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

    private String faceImageUrl;

    private String bodyImageUrl;

    private Long locationId;

    private String locationName;

    private Long personId;

    private String trackKey;

    private Long sessionId;

    private String personKind;

    private String source;

    private Float faceMatchScore;

    private Float bodyMatchScore;

    private String qualityFlag;

    private String sceneGroupId;

    private Long clipId;

    private String analysisStatus;

    private PresenceVideoClipVo clip;

    private PresenceVideoClipVo sceneClip;

    private List<AiAnalysisResultVo> analysisResults;

    private List<AiAnalysisResultVo> sceneAnalysisResults;

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

    public String getPersonKind()
    {
        return personKind;
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
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

    public String getSceneGroupId()
    {
        return sceneGroupId;
    }

    public void setSceneGroupId(String sceneGroupId)
    {
        this.sceneGroupId = sceneGroupId;
    }

    public Long getClipId()
    {
        return clipId;
    }

    public void setClipId(Long clipId)
    {
        this.clipId = clipId;
    }

    public String getAnalysisStatus()
    {
        return analysisStatus;
    }

    public void setAnalysisStatus(String analysisStatus)
    {
        this.analysisStatus = analysisStatus;
    }

    public PresenceVideoClipVo getClip()
    {
        return clip;
    }

    public void setClip(PresenceVideoClipVo clip)
    {
        this.clip = clip;
    }

    public PresenceVideoClipVo getSceneClip()
    {
        return sceneClip;
    }

    public void setSceneClip(PresenceVideoClipVo sceneClip)
    {
        this.sceneClip = sceneClip;
    }

    public List<AiAnalysisResultVo> getAnalysisResults()
    {
        return analysisResults;
    }

    public void setAnalysisResults(List<AiAnalysisResultVo> analysisResults)
    {
        this.analysisResults = analysisResults;
    }

    public List<AiAnalysisResultVo> getSceneAnalysisResults()
    {
        return sceneAnalysisResults;
    }

    public void setSceneAnalysisResults(List<AiAnalysisResultVo> sceneAnalysisResults)
    {
        this.sceneAnalysisResults = sceneAnalysisResults;
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
