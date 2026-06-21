package com.ruoyi.system.domain.vo;

import java.math.BigDecimal;

/**
 * 考勤大屏实时指标
 */
public class AttendanceDashboardVo
{
    private Integer totalRegistryCount;

    private Integer attendedCount;

    private BigDecimal attendanceRate;

    private BigDecimal attendanceRateDelta;

    private Integer presentCount;

    private Integer strangerTotalCount;

    private Integer strangerPresentCount;

    public Integer getTotalRegistryCount()
    {
        return totalRegistryCount;
    }

    public void setTotalRegistryCount(Integer totalRegistryCount)
    {
        this.totalRegistryCount = totalRegistryCount;
    }

    public Integer getAttendedCount()
    {
        return attendedCount;
    }

    public void setAttendedCount(Integer attendedCount)
    {
        this.attendedCount = attendedCount;
    }

    public BigDecimal getAttendanceRate()
    {
        return attendanceRate;
    }

    public void setAttendanceRate(BigDecimal attendanceRate)
    {
        this.attendanceRate = attendanceRate;
    }

    public BigDecimal getAttendanceRateDelta()
    {
        return attendanceRateDelta;
    }

    public void setAttendanceRateDelta(BigDecimal attendanceRateDelta)
    {
        this.attendanceRateDelta = attendanceRateDelta;
    }

    public Integer getPresentCount()
    {
        return presentCount;
    }

    public void setPresentCount(Integer presentCount)
    {
        this.presentCount = presentCount;
    }

    public Integer getStrangerTotalCount()
    {
        return strangerTotalCount;
    }

    public void setStrangerTotalCount(Integer strangerTotalCount)
    {
        this.strangerTotalCount = strangerTotalCount;
    }

    public Integer getStrangerPresentCount()
    {
        return strangerPresentCount;
    }

    public void setStrangerPresentCount(Integer strangerPresentCount)
    {
        this.strangerPresentCount = strangerPresentCount;
    }
}
