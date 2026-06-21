package com.ruoyi.system.domain.bo;

/**
 * 陌生人转在案 / 合并请求
 */
public class DataBoardStrangerUpdateBo
{
    private String displayName;

    private String employeeNo;

    /** student 或 staff */
    private String personType;

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
}
