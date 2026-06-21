package com.ruoyi.system.domain.bo;

/**
 * 人员档案编辑请求
 */
public class DataBoardPersonUpdateBo
{
    private String displayName;

    /** student / staff / stranger */
    private String personType;

    /** 兼容旧字段 */
    private String personKind;

    private String employeeNo;

    private String note;

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getPersonType()
    {
        return personType != null ? personType : personKind;
    }

    public void setPersonType(String personType)
    {
        this.personType = personType;
    }

    public String getPersonKind()
    {
        return getPersonType();
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
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
