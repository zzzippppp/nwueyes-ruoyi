package com.ruoyi.system.domain.bo;

/**
 * 摄像头门框/门线标定参数。
 */
public class CameraDoorConfigBo
{
    private Long cameraId;

    private Integer lineY;

    private String roi;

    private Integer refWidth;

    private Integer refHeight;

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
}
