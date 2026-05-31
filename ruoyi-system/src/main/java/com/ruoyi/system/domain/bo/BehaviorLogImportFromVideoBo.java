package com.ruoyi.system.domain.bo;

/**
 * 从视频分析任务导入行为日志
 */
public class BehaviorLogImportFromVideoBo
{
    private String taskId;

    private Long locationId;

    public String getTaskId()
    {
        return taskId;
    }

    public void setTaskId(String taskId)
    {
        this.taskId = taskId;
    }

    public Long getLocationId()
    {
        return locationId;
    }

    public void setLocationId(Long locationId)
    {
        this.locationId = locationId;
    }
}
