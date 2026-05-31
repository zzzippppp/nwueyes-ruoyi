package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 分析任务抓拍向量批量结果。
 */
public class AnalyzeEmbedResultVo
{
    private String taskId;

    private Integer trackCount;

    private Integer faceOkCount;

    private Integer bodyOkCount;

    private List<CaptureTrackEmbedVo> tracks = new ArrayList<>();

    public String getTaskId()
    {
        return taskId;
    }

    public void setTaskId(String taskId)
    {
        this.taskId = taskId;
    }

    public Integer getTrackCount()
    {
        return trackCount;
    }

    public void setTrackCount(Integer trackCount)
    {
        this.trackCount = trackCount;
    }

    public Integer getFaceOkCount()
    {
        return faceOkCount;
    }

    public void setFaceOkCount(Integer faceOkCount)
    {
        this.faceOkCount = faceOkCount;
    }

    public Integer getBodyOkCount()
    {
        return bodyOkCount;
    }

    public void setBodyOkCount(Integer bodyOkCount)
    {
        this.bodyOkCount = bodyOkCount;
    }

    public List<CaptureTrackEmbedVo> getTracks()
    {
        return tracks;
    }

    public void setTracks(List<CaptureTrackEmbedVo> tracks)
    {
        this.tracks = tracks;
    }
}
