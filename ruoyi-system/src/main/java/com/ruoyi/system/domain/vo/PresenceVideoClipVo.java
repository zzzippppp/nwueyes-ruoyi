package com.ruoyi.system.domain.vo;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class PresenceVideoClipVo
{
    private Long id;
    private String clipKey;
    private String clipType;
    private Long sessionId;
    private String sceneGroupId;
    private Long locationId;
    private String trackKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private Double preRollSec;
    private Double postRollSec;
    private String videoUrl;
    private String status;
    private String providerStatus;
    private String providerTaskId;
    private String providerSourceUrl;
    private String providerErrorMessage;
    private String publicVideoUrl;
    private List<AiAnalysisResultVo> analysisResults;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClipKey() { return clipKey; }
    public void setClipKey(String clipKey) { this.clipKey = clipKey; }
    public String getClipType() { return clipType; }
    public void setClipType(String clipType) { this.clipType = clipType; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getSceneGroupId() { return sceneGroupId; }
    public void setSceneGroupId(String sceneGroupId) { this.sceneGroupId = sceneGroupId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getTrackKey() { return trackKey; }
    public void setTrackKey(String trackKey) { this.trackKey = trackKey; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public Double getPreRollSec() { return preRollSec; }
    public void setPreRollSec(Double preRollSec) { this.preRollSec = preRollSec; }
    public Double getPostRollSec() { return postRollSec; }
    public void setPostRollSec(Double postRollSec) { this.postRollSec = postRollSec; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public String getProviderTaskId() { return providerTaskId; }
    public void setProviderTaskId(String providerTaskId) { this.providerTaskId = providerTaskId; }
    public String getProviderSourceUrl() { return providerSourceUrl; }
    public void setProviderSourceUrl(String providerSourceUrl) { this.providerSourceUrl = providerSourceUrl; }
    public String getProviderErrorMessage() { return providerErrorMessage; }
    public void setProviderErrorMessage(String providerErrorMessage) { this.providerErrorMessage = providerErrorMessage; }
    public String getPublicVideoUrl() { return publicVideoUrl; }
    public void setPublicVideoUrl(String publicVideoUrl) { this.publicVideoUrl = publicVideoUrl; }
    public List<AiAnalysisResultVo> getAnalysisResults() { return analysisResults; }
    public void setAnalysisResults(List<AiAnalysisResultVo> analysisResults) { this.analysisResults = analysisResults; }
}
