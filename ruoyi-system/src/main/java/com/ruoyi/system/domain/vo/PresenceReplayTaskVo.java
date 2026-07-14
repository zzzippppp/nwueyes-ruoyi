package com.ruoyi.system.domain.vo;

/**
 * 回放任务状态
 */
public class PresenceReplayTaskVo
{
    private String taskId;

    private String status;

    private Integer exitCode;

    private String startedAt;

    private String finishedAt;

    private String message;

    private String logTail;

    /** YOLO 分析结果 JSON（仅 analyze 任务） */
    private String resultJson;

    /** 分析/回放关联的摄像头 ID */
    private Long cameraId;

    public String getTaskId()
    {
        return taskId;
    }

    public void setTaskId(String taskId)
    {
        this.taskId = taskId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getExitCode()
    {
        return exitCode;
    }

    public void setExitCode(Integer exitCode)
    {
        this.exitCode = exitCode;
    }

    public String getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(String startedAt)
    {
        this.startedAt = startedAt;
    }

    public String getFinishedAt()
    {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt)
    {
        this.finishedAt = finishedAt;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getLogTail()
    {
        return logTail;
    }

    public void setLogTail(String logTail)
    {
        this.logTail = logTail;
    }

    public String getResultJson()
    {
        return resultJson;
    }

    public void setResultJson(String resultJson)
    {
        this.resultJson = resultJson;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }
}
