package com.ruoyi.system.domain.vo;

/**
 * 摄像头设备配置（含门线标定）
 */
public class CameraConfigVo
{
    private Long id;

    private String deviceCode;

    private String deviceName;

    private Long typeId;

    private String installLocation;

    private String ipAddr;

    private String serialNo;

    private Integer channelNo;

    private String verifyCode;

    private String onlineStatus;

    private Integer lineY;

    private String roi;

    private Integer refWidth;

    private Integer refHeight;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getDeviceCode()
    {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode)
    {
        this.deviceCode = deviceCode;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public Long getTypeId()
    {
        return typeId;
    }

    public void setTypeId(Long typeId)
    {
        this.typeId = typeId;
    }

    public String getInstallLocation()
    {
        return installLocation;
    }

    public void setInstallLocation(String installLocation)
    {
        this.installLocation = installLocation;
    }

    public String getIpAddr()
    {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr)
    {
        this.ipAddr = ipAddr;
    }

    /** 萤石 API 兼容别名 */
    public String getDeviceSerial()
    {
        return serialNo;
    }

    public void setDeviceSerial(String deviceSerial)
    {
        this.serialNo = deviceSerial;
    }

    public String getSerialNo()
    {
        return serialNo;
    }

    public void setSerialNo(String serialNo)
    {
        this.serialNo = serialNo;
    }

    public Integer getChannelNo()
    {
        return channelNo;
    }

    public void setChannelNo(Integer channelNo)
    {
        this.channelNo = channelNo;
    }

    public String getVerifyCode()
    {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode)
    {
        this.verifyCode = verifyCode;
    }

    public String getOnlineStatus()
    {
        return onlineStatus;
    }

    public void setOnlineStatus(String onlineStatus)
    {
        this.onlineStatus = onlineStatus;
    }

    public Integer getLineY()
    {
        return lineY;
    }

    public void setLineY(Integer lineY)
    {
        this.lineY = lineY;
    }

    public String getRoi()
    {
        return roi;
    }

    public void setRoi(String roi)
    {
        this.roi = roi;
    }

    public Integer getRefWidth()
    {
        return refWidth;
    }

    public void setRefWidth(Integer refWidth)
    {
        this.refWidth = refWidth;
    }

    public Integer getRefHeight()
    {
        return refHeight;
    }

    public void setRefHeight(Integer refHeight)
    {
        this.refHeight = refHeight;
    }
}
