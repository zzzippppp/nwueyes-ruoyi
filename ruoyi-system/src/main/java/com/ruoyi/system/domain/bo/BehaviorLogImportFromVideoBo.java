package com.ruoyi.system.domain.bo;

import java.util.Date;

/**
 * 从视频分析任务导入行为日志
 */
public class BehaviorLogImportFromVideoBo
{
    private String taskId;

    private Long cameraId;

    /** 绑定视频片段 */
    private Long clipId;

    /** 绑定场景组 */
    private String sceneGroupId;

    /** 事件时间基准（片段起始时间） */
    private Date videoBaseTime;

    /** 无过线事件时返回空结果而非抛错（自动流水线用） */
    private boolean allowEmptyEvents;

    public String getTaskId()
    {
        return taskId;
    }

    public void setTaskId(String taskId)
    {
        this.taskId = taskId;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
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

    public boolean isAllowEmptyEvents()
    {
        return allowEmptyEvents;
    }

    public void setAllowEmptyEvents(boolean allowEmptyEvents)
    {
        this.allowEmptyEvents = allowEmptyEvents;
    }

    /** 兼容前端旧字段 locationId */
    public void setLocationId(Long locationId)
    {
        this.cameraId = locationId;
    }
}
