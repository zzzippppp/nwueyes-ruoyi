package com.ruoyi.system.domain.bo;

/**
 * 人员档案编辑请求
 */
public class DataBoardPersonUpdateBo
{
    private String displayName;

    private String personKind;

    private String tagsText;

    private String note;

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
}
