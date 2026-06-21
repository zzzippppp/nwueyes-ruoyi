package com.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 考勤信息列表项（按人员聚合）
 */
public class DataBoardAttendanceItemVo
{
    private Long personId;

    private String displayName;

    private String employeeNo;

    /** student / staff / stranger */
    private String personKind;

    private String locationName;

    /** absent / present / left */
    private String attendanceStatus;

    private Long passageCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arrivalAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date departureAt;

    private Long dwellSeconds;

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

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    public String getPersonKind()
    {
        return personKind;
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public String getLocationName()
    {
        return locationName;
    }

    public void setLocationName(String locationName)
    {
        this.locationName = locationName;
    }

    public String getAttendanceStatus()
    {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus)
    {
        this.attendanceStatus = attendanceStatus;
    }

    public Long getPassageCount()
    {
        return passageCount;
    }

    public void setPassageCount(Long passageCount)
    {
        this.passageCount = passageCount;
    }

    public Date getArrivalAt()
    {
        return arrivalAt;
    }

    public void setArrivalAt(Date arrivalAt)
    {
        this.arrivalAt = arrivalAt;
    }

    public Date getDepartureAt()
    {
        return departureAt;
    }

    public void setDepartureAt(Date departureAt)
    {
        this.departureAt = departureAt;
    }

    public Long getDwellSeconds()
    {
        return dwellSeconds;
    }

    public void setDwellSeconds(Long dwellSeconds)
    {
        this.dwellSeconds = dwellSeconds;
    }
}
