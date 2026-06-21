package com.ruoyi.system.domain.bo;

public class PresenceVideoClipIngestBo
{
    private String clipKey;
    private String clipType;
    private Long sessionId;
    private String sceneGroupId;
    private Long cameraId;
    private String trackKey;
    private String startTime;
    private String endTime;
    private Double preRollSec;
    private Double postRollSec;
    private String videoUrl;
    private String status;
    private String deviceSerial;
    private Integer channelNo;
    private String validCode;
    private Boolean preferLocal;

    public String getClipKey() { return clipKey; }
    public void setClipKey(String clipKey) { this.clipKey = clipKey; }
    public String getClipType() { return clipType; }
    public void setClipType(String clipType) { this.clipType = clipType; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getSceneGroupId() { return sceneGroupId; }
    public void setSceneGroupId(String sceneGroupId) { this.sceneGroupId = sceneGroupId; }
    public Long getCameraId() { return cameraId; }
    public void setCameraId(Long cameraId) { this.cameraId = cameraId; }
    public String getTrackKey() { return trackKey; }
    public void setTrackKey(String trackKey) { this.trackKey = trackKey; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Double getPreRollSec() { return preRollSec; }
    public void setPreRollSec(Double preRollSec) { this.preRollSec = preRollSec; }
    public Double getPostRollSec() { return postRollSec; }
    public void setPostRollSec(Double postRollSec) { this.postRollSec = postRollSec; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeviceSerial() { return deviceSerial; }
    public void setDeviceSerial(String deviceSerial) { this.deviceSerial = deviceSerial; }
    public Integer getChannelNo() { return channelNo; }
    public void setChannelNo(Integer channelNo) { this.channelNo = channelNo; }
    public String getValidCode() { return validCode; }
    public void setValidCode(String validCode) { this.validCode = validCode; }
    public Boolean getPreferLocal() { return preferLocal; }
    public void setPreferLocal(Boolean preferLocal) { this.preferLocal = preferLocal; }
}
