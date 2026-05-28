package com.ruoyi.system.domain.vo;

/**
 * 萤石设备简要信息
 * 
 * @author ruoyi
 */
public class EzvizDeviceVo
{
    /**
     * 设备序列号
     */
    private String deviceSerial;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 通道号
     */
    private Integer channelNo;

    /**
     * 在线状态
     */
    private String status;

    /**
     * 是否加密
     */
    private Boolean encrypt;

    public String getDeviceSerial()
    {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial)
    {
        this.deviceSerial = deviceSerial;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public Integer getChannelNo()
    {
        return channelNo;
    }

    public void setChannelNo(Integer channelNo)
    {
        this.channelNo = channelNo;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Boolean getEncrypt()
    {
        return encrypt;
    }

    public void setEncrypt(Boolean encrypt)
    {
        this.encrypt = encrypt;
    }
}
