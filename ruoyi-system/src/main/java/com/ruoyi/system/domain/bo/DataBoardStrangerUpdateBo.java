package com.ruoyi.system.domain.bo;

/**
 * 陌生人研判编辑请求
 */
public class DataBoardStrangerUpdateBo
{
    private String displayName;

    private String tagsText;

    /** stranger 或 known */
    private String identityType;

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

    public String getIdentityType()
    {
        return identityType;
    }

    public void setIdentityType(String identityType)
    {
        this.identityType = identityType;
    }
}
