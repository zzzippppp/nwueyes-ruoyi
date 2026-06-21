package com.ruoyi.system.domain.vo;

import java.math.BigDecimal;

/**
 * 日终考勤统计快照（attendance_daily_stats）
 */
public class AttendanceDailyStatsVo
{
    private String statDate;

    private Integer totalRegistryCount;

    private Integer attendedCount;

    private BigDecimal attendanceRate;

    private Integer strangerTotalCount;

    public String getStatDate()
    {
        return statDate;
    }

    public void setStatDate(String statDate)
    {
        this.statDate = statDate;
    }

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

    public Integer getStrangerTotalCount()
    {
        return strangerTotalCount;
    }

    public void setStrangerTotalCount(Integer strangerTotalCount)
    {
        this.strangerTotalCount = strangerTotalCount;
    }
}
