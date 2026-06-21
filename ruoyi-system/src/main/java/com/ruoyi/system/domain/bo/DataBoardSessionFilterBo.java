package com.ruoyi.system.domain.bo;



/**

 * 停留记录筛选（考勤信息等）

 */

public class DataBoardSessionFilterBo

{

    /** 姓名，模糊匹配 */

    private String displayName;



    /** 学工号，模糊匹配 */

    private String employeeNo;



    /** 人员类型：student / staff / stranger */

    private String personType;



    /** 会话状态：open / closed / absent / present / left */

    private String sessionStatus;



    /** 时段开始 HH:mm（与 endTime 同时传入时启用分钟级单日筛选） */

    private String beginTime;



    /** 时段结束 HH:mm（含该分钟） */

    private String endTime;



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



    public String getSessionStatus()

    {

        return sessionStatus;

    }



    public void setSessionStatus(String sessionStatus)

    {

        this.sessionStatus = sessionStatus;

    }



    public String getBeginTime()

    {

        return beginTime;

    }



    public void setBeginTime(String beginTime)

    {

        this.beginTime = beginTime;

    }



    public String getEndTime()

    {

        return endTime;

    }



    public void setEndTime(String endTime)

    {

        this.endTime = endTime;

    }

}


