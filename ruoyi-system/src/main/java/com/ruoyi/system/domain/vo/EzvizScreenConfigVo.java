package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.ruoyi.system.domain.vo.CameraConfigVo;

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

    /**
     * 数据库已配置的识别摄像头（含门线/ROI）
     */
    private List<CameraConfigVo> cameras = new ArrayList<CameraConfigVo>();

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

    public List<CameraConfigVo> getCameras()
    {
        return cameras;
    }

    public void setCameras(List<CameraConfigVo> cameras)
    {
        this.cameras = cameras;
    }
}
