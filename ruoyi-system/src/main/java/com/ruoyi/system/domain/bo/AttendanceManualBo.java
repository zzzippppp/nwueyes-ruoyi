package com.ruoyi.system.domain.bo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 考勤信息手工新增 / 修改
 */
public class AttendanceManualBo
{
    private Long personId;

    private Long cameraId;

    /** absent / present / left */
    private String attendanceStatus;

    /** yyyy-MM-dd，默认当天 */
    private String statDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arrivalAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date departureAt;

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

    public String getAttendanceStatus()
    {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus)
    {
        this.attendanceStatus = attendanceStatus;
    }

    public String getStatDate()
    {
        return statDate;
    }

    public void setStatDate(String statDate)
    {
        this.statDate = statDate;
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
}
