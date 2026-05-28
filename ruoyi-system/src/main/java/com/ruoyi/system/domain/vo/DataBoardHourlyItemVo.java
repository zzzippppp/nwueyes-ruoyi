package com.ruoyi.system.domain.vo;

/**
 * 看板按小时统计项
 */
public class DataBoardHourlyItemVo
{
    private Integer hour;

    private Long arrivalCount;

    private Long dwellSeconds;

    public Integer getHour()
    {
        return hour;
    }

    public void setHour(Integer hour)
    {
        this.hour = hour;
    }

    public Long getArrivalCount()
    {
        return arrivalCount;
    }

    public void setArrivalCount(Long arrivalCount)
    {
        this.arrivalCount = arrivalCount;
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
