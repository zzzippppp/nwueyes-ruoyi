package com.ruoyi.system.domain.vo;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 行为日志列表项（合并：人脸/体态小图 + 整帧 snapshot + clip/AI + 考勤字段）
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

    private String snapshotUrl;

    private String videoUrl;

    private String personType;

    private String employeeNo;

    private Long cameraId;

    private String deviceName;

    private Long personId;

    private String trackKey;

    private Long sessionId;

    private Float faceMatchScore;

    private Float bodyMatchScore;

    private String qualityFlag;

    private String behaviorAnalysis;

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

    /** 兼容旧前端 */
    public String getPersonKind()
    {
        return personType;
    }

    public void setPersonKind(String personKind)
    {
        this.personType = personKind;
    }

    /** 兼容旧前端 */
    public String getSource()
    {
        return "live";
    }

    public void setSource(String source)
    {
        // no-op
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
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
