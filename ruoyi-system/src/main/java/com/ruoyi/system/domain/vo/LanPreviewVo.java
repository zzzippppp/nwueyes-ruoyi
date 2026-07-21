package com.ruoyi.system.domain.vo;

/**
 * 局域网 RTSP 预览（经 go2rtc 转 WebRTC）响应
 */
public class LanPreviewVo
{
    private Long cameraId;

    private String streamName;

    /** go2rtc stream.html 页面地址，供 iframe 嵌入 */
    private String previewUrl;

    private String previewMode;

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

    public String getStreamName()
    {
        return streamName;
    }

    public void setStreamName(String streamName)
    {
        this.streamName = streamName;
    }

    public String getPreviewUrl()
    {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl)
    {
        this.previewUrl = previewUrl;
    }

    public String getPreviewMode()
    {
        return previewMode;
    }

    public void setPreviewMode(String previewMode)
    {
        this.previewMode = previewMode;
    }
}
