package com.ruoyi.system.domain.bo;

import java.util.Date;

/**
 * 录像回放 / YOLO 分析启动参数
 */
public class PresenceReplayStartBo
{
    /** uploadPath 下相对路径；与 videoPath 二选一 */
    private String uploadedFileName;

    /** 本地视频绝对路径（场景片等） */
    private String videoPath;

    private Long cameraId;

    private Integer lineY;

    private String roi;

    /** 门线/ROI 标定参考宽度；空则 Python 侧默认 1920 */
    private Integer refWidth;

    /** 门线/ROI 标定参考高度；空则 Python 侧默认 1080 */
    private Integer refHeight;

    private String debugOut;

    /** 分析成功后是否自动导入行为日志（场景片 AB 流水线） */
    private Boolean autoImportBehaviorLogs;

    /** 导入时绑定的片段 ID */
    private Long clipId;

    /** 导入时绑定的场景组 ID */
    private String sceneGroupId;

    /** 事件时间基准（通常为片段 startTime）；空则从文件名/任务开始时间推断 */
    private Date videoBaseTime;

    public String getUploadedFileName()
    {
        return uploadedFileName;
    }

    public void setUploadedFileName(String uploadedFileName)
    {
        this.uploadedFileName = uploadedFileName;
    }

    public String getVideoPath()
    {
        return videoPath;
    }

    public void setVideoPath(String videoPath)
    {
        this.videoPath = videoPath;
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

    public String getDebugOut()
    {
        return debugOut;
    }

    public void setDebugOut(String debugOut)
    {
        this.debugOut = debugOut;
    }

    public Boolean getAutoImportBehaviorLogs()
    {
        return autoImportBehaviorLogs;
    }

    public void setAutoImportBehaviorLogs(Boolean autoImportBehaviorLogs)
    {
        this.autoImportBehaviorLogs = autoImportBehaviorLogs;
    }

    public Long getClipId()
    {
        return clipId;
    }

    public void setClipId(Long clipId)
    {
        this.clipId = clipId;
    }

    public String getSceneGroupId()
    {
        return sceneGroupId;
    }

    public void setSceneGroupId(String sceneGroupId)
    {
        this.sceneGroupId = sceneGroupId;
    }

    public Date getVideoBaseTime()
    {
        return videoBaseTime;
    }

    public void setVideoBaseTime(Date videoBaseTime)
    {
        this.videoBaseTime = videoBaseTime;
    }
}
