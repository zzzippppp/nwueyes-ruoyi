package com.ruoyi.system.domain.vo;

/**
 * 门区标定（视频测试与直播识别共用）。
 */
public class PresenceDoorConfigVo
{
    private Integer lineY;

    private String roi;

    private Double yoloConf;

    private Double snapshotWindowSec;

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

    public Double getYoloConf()
    {
        return yoloConf;
    }

    public void setYoloConf(Double yoloConf)
    {
        this.yoloConf = yoloConf;
    }

    public Double getSnapshotWindowSec()
    {
        return snapshotWindowSec;
    }

    public void setSnapshotWindowSec(Double snapshotWindowSec)
    {
        this.snapshotWindowSec = snapshotWindowSec;
    }
}
