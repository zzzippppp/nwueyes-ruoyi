package com.ruoyi.system.domain.bo;

/**
 * 录像回放测试启动参数
 */
public class PresenceReplayStartBo
{
    private String uploadedFileName;

    private Long locationId;

    private Integer lineY;

    private String roi;

    private String debugOut;

    public String getUploadedFileName()
    {
        return uploadedFileName;
    }

    public void setUploadedFileName(String uploadedFileName)
    {
        this.uploadedFileName = uploadedFileName;
    }

    public Long getLocationId()
    {
        return locationId;
    }

    public void setLocationId(Long locationId)
    {
        this.locationId = locationId;
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

    public String getDebugOut()
    {
        return debugOut;
    }

    public void setDebugOut(String debugOut)
    {
        this.debugOut = debugOut;
    }
}
