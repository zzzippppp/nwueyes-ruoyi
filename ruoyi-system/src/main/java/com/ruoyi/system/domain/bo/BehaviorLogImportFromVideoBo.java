package com.ruoyi.system.domain.bo;

/**
 * 从视频分析任务导入行为日志
 */
public class BehaviorLogImportFromVideoBo
{
    private String taskId;

    private Long cameraId;

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

    /** 兼容前端旧字段 locationId */
    public void setLocationId(Long locationId)
    {
        this.cameraId = locationId;
    }
}
