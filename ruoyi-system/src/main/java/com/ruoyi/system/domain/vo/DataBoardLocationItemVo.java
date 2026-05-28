package com.ruoyi.system.domain.vo;

/**
 * 看板按地点统计项
 */
public class DataBoardLocationItemVo
{
    private Long locationId;

    private String locationName;

    private String deviceSerial;

    private Integer channelNo;

    private Long sessionCount;

    private Long dwellSeconds;

    private Long openCount;

    public Long getLocationId()
    {
        return locationId;
    }

    public void setLocationId(Long locationId)
    {
        this.locationId = locationId;
    }

    public String getLocationName()
    {
        return locationName;
    }

    public void setLocationName(String locationName)
    {
        this.locationName = locationName;
    }

    public String getDeviceSerial()
    {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial)
    {
        this.deviceSerial = deviceSerial;
    }

    public Integer getChannelNo()
    {
        return channelNo;
    }

    public void setChannelNo(Integer channelNo)
    {
        this.channelNo = channelNo;
    }

    public Long getSessionCount()
    {
        return sessionCount;
    }

    public void setSessionCount(Long sessionCount)
    {
        this.sessionCount = sessionCount;
    }

    public Long getDwellSeconds()
    {
        return dwellSeconds;
    }

    public void setDwellSeconds(Long dwellSeconds)
    {
        this.dwellSeconds = dwellSeconds;
    }

    public Long getOpenCount()
    {
        return openCount;
    }

    public void setOpenCount(Long openCount)
    {
        this.openCount = openCount;
    }
}
