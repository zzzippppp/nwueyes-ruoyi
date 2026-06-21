package com.ruoyi.system.domain.vo;

/**
 * 看板按摄像头统计项
 */
public class DataBoardCameraItemVo
{
    private Long cameraId;

    private String deviceName;

    private String deviceSerial;

    private Integer channelNo;

    private Long sessionCount;

    private Long dwellSeconds;

    private Long openCount;

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
