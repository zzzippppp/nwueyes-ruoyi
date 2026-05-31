package com.ruoyi.system.domain.vo;

/**
 * 直播识别任务状态（复用回放任务字段结构）。
 */
public class PresenceLiveTaskVo extends PresenceReplayTaskVo
{
    private String streamMode;

    private String streamProtocol;

    private String deviceSerial;

    public String getStreamMode()
    {
        return streamMode;
    }

    public void setStreamMode(String streamMode)
    {
        this.streamMode = streamMode;
    }

    public String getStreamProtocol()
    {
        return streamProtocol;
    }

    public void setStreamProtocol(String streamProtocol)
    {
        this.streamProtocol = streamProtocol;
    }

    public String getDeviceSerial()
    {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial)
    {
        this.deviceSerial = deviceSerial;
    }
}
