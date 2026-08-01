package com.ruoyi.system.domain.bo;

/**
 * 直播识别启动参数
 */
public class PresenceLiveStartBo
{
    public static final String STREAM_LAN_RTSP = "lan_rtsp";

    public static final String STREAM_CLOUD_HLS = "cloud_hls";

    private String deviceSerial;

    private Integer channelNo;

    /** lan_rtsp | cloud_hls */
    private String streamMode;

    private Long cameraId;

    private Integer lineY;

    private String roi;

    /** 门线/ROI 标定参考宽度；空则 Python 侧默认 1920 */
    private Integer refWidth;

    /** 门线/ROI 标定参考高度；空则 Python 侧默认 1080 */
    private Integer refHeight;

    private String validCode;

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

    public String getStreamMode()
    {
        return streamMode;
    }

    public void setStreamMode(String streamMode)
    {
        this.streamMode = streamMode;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
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

    public String getValidCode()
    {
        return validCode;
    }

    public void setValidCode(String validCode)
    {
        this.validCode = validCode;
    }
}
