package com.ruoyi.system.domain.bo;

/**
 * 摄像头编辑请求
 */
public class DataBoardCameraUpdateBo
{
    private String deviceName;

    private Boolean isActive;

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public Boolean getIsActive()
    {
        return isActive;
    }

    public void setIsActive(Boolean isActive)
    {
        this.isActive = isActive;
    }
}
