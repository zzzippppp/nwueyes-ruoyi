package com.ruoyi.system.domain.vo;

/**
 * 直播抽帧标定结果
 */
public class PresenceLiveProbeVo
{
    private Long cameraId;

    private String rawImageUrl;

    private String overlayImageUrl;

    private Integer width;

    private Integer height;

    private Integer lineY;

    private String roi;

    private String message;

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

    public String getRawImageUrl()
    {
        return rawImageUrl;
    }

    public void setRawImageUrl(String rawImageUrl)
    {
        this.rawImageUrl = rawImageUrl;
    }

    public String getOverlayImageUrl()
    {
        return overlayImageUrl;
    }

    public void setOverlayImageUrl(String overlayImageUrl)
    {
        this.overlayImageUrl = overlayImageUrl;
    }

    public Integer getWidth()
    {
        return width;
    }

    public void setWidth(Integer width)
    {
        this.width = width;
    }

    public Integer getHeight()
    {
        return height;
    }

    public void setHeight(Integer height)
    {
        this.height = height;
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

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
