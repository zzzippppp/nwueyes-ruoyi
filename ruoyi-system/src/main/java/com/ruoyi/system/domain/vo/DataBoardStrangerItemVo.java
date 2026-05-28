package com.ruoyi.system.domain.vo;

/**
 * 看板陌生人研判行
 */
public class DataBoardStrangerItemVo
{
    private String trackKey;

    private String displayName;

    private String tagsText;

    private String faceImageUrl;

    private Long mergedPersonId;

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getTagsText()
    {
        return tagsText;
    }

    public void setTagsText(String tagsText)
    {
        this.tagsText = tagsText;
    }

    public String getFaceImageUrl()
    {
        return faceImageUrl;
    }

    public void setFaceImageUrl(String faceImageUrl)
    {
        this.faceImageUrl = faceImageUrl;
    }

    public Long getMergedPersonId()
    {
        return mergedPersonId;
    }

    public void setMergedPersonId(Long mergedPersonId)
    {
        this.mergedPersonId = mergedPersonId;
    }
}
