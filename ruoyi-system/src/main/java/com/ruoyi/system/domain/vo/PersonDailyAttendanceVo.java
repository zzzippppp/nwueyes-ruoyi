package com.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class PersonDailyAttendanceVo
{
    private Long id;

    private String statDate;

    private Long personId;

    private String displayName;

    private String employeeNo;

    private String personType;

    private Long cameraId;

    private String deviceName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstEnterAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastExitAt;

    private Integer totalDwellSeconds;

    private Integer enterCount;

    private String attendanceStatus;

    private Boolean isAttended;

    private Long currentSessionId;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getStatDate()
    {
        return statDate;
    }

    public void setStatDate(String statDate)
    {
        this.statDate = statDate;
    }

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

    public String getPersonType()
    {
        return personType;
    }

    public void setPersonType(String personType)
    {
        this.personType = personType;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public Date getFirstEnterAt()
    {
        return firstEnterAt;
    }

    public void setFirstEnterAt(Date firstEnterAt)
    {
        this.firstEnterAt = firstEnterAt;
    }

    public Date getLastExitAt()
    {
        return lastExitAt;
    }

    public void setLastExitAt(Date lastExitAt)
    {
        this.lastExitAt = lastExitAt;
    }

    public Integer getTotalDwellSeconds()
    {
        return totalDwellSeconds;
    }

    public void setTotalDwellSeconds(Integer totalDwellSeconds)
    {
        this.totalDwellSeconds = totalDwellSeconds;
    }

    public Integer getEnterCount()
    {
        return enterCount;
    }

    public void setEnterCount(Integer enterCount)
    {
        this.enterCount = enterCount;
    }

    public String getAttendanceStatus()
    {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus)
    {
        this.attendanceStatus = attendanceStatus;
    }

    public Boolean getIsAttended()
    {
        return isAttended;
    }

    public void setIsAttended(Boolean isAttended)
    {
        this.isAttended = isAttended;
    }

    public Long getCurrentSessionId()
    {
        return currentSessionId;
    }

    public void setCurrentSessionId(Long currentSessionId)
    {
        this.currentSessionId = currentSessionId;
    }
}
