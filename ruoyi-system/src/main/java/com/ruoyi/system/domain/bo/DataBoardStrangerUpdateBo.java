package com.ruoyi.system.domain.bo;

import java.util.ArrayList;
import java.util.List;

/**
 * 陌生人研判编辑 / 合并请求
 */
public class DataBoardStrangerUpdateBo
{
    private String displayName;

    private String employeeNo;

    /** student / staff / stranger */
    private String personType;

    private String gender;

    private String phone;

    private String note;

    private String tagsText;

    /** 人脸去重后关联的其他 track_key */
    private List<String> relatedTrackKeys = new ArrayList<>();

    /** 兼容旧字段 stranger / known */
    private String identityType;

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    public String getPersonType()
    {
        return personType;
    }

    public void setPersonType(String personType)
    {
        this.personType = personType;
    }

    public String getGender()
    {
        return gender;
    }

    public void setGender(String gender)
    {
        this.gender = gender;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getTagsText()
    {
        return tagsText;
    }

    public void setTagsText(String tagsText)
    {
        this.tagsText = tagsText;
    }

    public List<String> getRelatedTrackKeys()
    {
        return relatedTrackKeys;
    }

    public void setRelatedTrackKeys(List<String> relatedTrackKeys)
    {
        this.relatedTrackKeys = relatedTrackKeys;
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
