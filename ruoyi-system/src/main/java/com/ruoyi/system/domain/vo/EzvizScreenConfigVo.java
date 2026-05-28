package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控大屏萤石配置
 * 
 * @author ruoyi
 */
public class EzvizScreenConfigVo
{
    /**
     * 萤石 accessToken
     */
    private String accessToken;

    /**
     * 默认通道号
     */
    private Integer defaultChannelNo;

    /**
     * 设备列表
     */
    private List<EzvizDeviceVo> devices = new ArrayList<EzvizDeviceVo>();

    public String getAccessToken()
    {
        return accessToken;
    }

    public void setAccessToken(String accessToken)
    {
        this.accessToken = accessToken;
    }

    public Integer getDefaultChannelNo()
    {
        return defaultChannelNo;
    }

    public void setDefaultChannelNo(Integer defaultChannelNo)
    {
        this.defaultChannelNo = defaultChannelNo;
    }

    public List<EzvizDeviceVo> getDevices()
    {
        return devices;
    }

    public void setDevices(List<EzvizDeviceVo> devices)
    {
        this.devices = devices;
    }
}
