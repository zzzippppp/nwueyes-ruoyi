package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频分析过线事件与人脸/体态库匹配结果。
 */
public class AnalyzeEventMatchResultVo
{
    private String taskId;

    private Integer eventCount;

    private Integer matchedCount;

    private List<AnalyzeEventMatchItemVo> events = new ArrayList<>();

    public String getTaskId()
    {
        return taskId;
    }

    public void setTaskId(String taskId)
    {
        this.taskId = taskId;
    }

    public Integer getEventCount()
    {
        return eventCount;
    }

    public void setEventCount(Integer eventCount)
    {
        this.eventCount = eventCount;
    }

    public Integer getMatchedCount()
    {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount)
    {
        this.matchedCount = matchedCount;
    }

    public List<AnalyzeEventMatchItemVo> getEvents()
    {
        return events;
    }

    public void setEvents(List<AnalyzeEventMatchItemVo> events)
    {
        this.events = events;
    }
}
