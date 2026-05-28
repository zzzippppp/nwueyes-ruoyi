package com.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 看板人员档案行
 */
public class DataBoardPersonItemVo
{
    private Long personId;

    private String displayName;

    private String personKind;

    private String tagsText;

    private String note;

    private String faceImageUrl;

    private String bodyImageUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSeenAt;

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getPersonKind()
    {
        return personKind;
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public String getTagsText()
    {
        return tagsText;
    }

    public void setTagsText(String tagsText)
    {
        this.tagsText = tagsText;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
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

    public Date getLastSeenAt()
    {
        return lastSeenAt;
    }

    public void setLastSeenAt(Date lastSeenAt)
    {
        this.lastSeenAt = lastSeenAt;
    }
}
