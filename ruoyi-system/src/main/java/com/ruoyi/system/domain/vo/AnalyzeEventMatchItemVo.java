package com.ruoyi.system.domain.vo;

/**
 * 视频分析单条过线事件的库内匹配预览。
 */
public class AnalyzeEventMatchItemVo
{
    private Integer frame;

    private Double timeSec;

    private Integer trackId;

    private String trackKey;

    private String eventType;

    private String faceImageUrl;

    private String bodyImageUrl;

    private String snapshotUrl;

    private String qualityFlag;

    private String displayName;

    private Long personId;

    private String personKind;

    private boolean matched;

    private Float faceMatchScore;

    private Float bodyMatchScore;

    public Integer getFrame()
    {
        return frame;
    }

    public void setFrame(Integer frame)
    {
        this.frame = frame;
    }

    public Double getTimeSec()
    {
        return timeSec;
    }

    public void setTimeSec(Double timeSec)
    {
        this.timeSec = timeSec;
    }

    public Integer getTrackId()
    {
        return trackId;
    }

    public void setTrackId(Integer trackId)
    {
        this.trackId = trackId;
    }

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
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

    public String getQualityFlag()
    {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag)
    {
        this.qualityFlag = qualityFlag;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public String getPersonKind()
    {
        return personKind;
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public boolean isMatched()
    {
        return matched;
    }

    public void setMatched(boolean matched)
    {
        this.matched = matched;
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
}
