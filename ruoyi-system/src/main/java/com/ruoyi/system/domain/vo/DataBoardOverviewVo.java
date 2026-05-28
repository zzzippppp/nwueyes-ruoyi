package com.ruoyi.system.domain.vo;

/**
 * 看板概览计数（Mapper 中间结果）
 */
public class DataBoardOverviewVo
{
    private Long sessionCount;

    private Long visitorCount;

    private Long knownVisitorCount;

    private Long strangerVisitorCount;

    private Long openSessionCount;

    private Long closedDwellSeconds;

    private Long openDwellSeconds;

    public Long getSessionCount()
    {
        return sessionCount;
    }

    public void setSessionCount(Long sessionCount)
    {
        this.sessionCount = sessionCount;
    }

    public Long getVisitorCount()
    {
        return visitorCount;
    }

    public void setVisitorCount(Long visitorCount)
    {
        this.visitorCount = visitorCount;
    }

    public Long getKnownVisitorCount()
    {
        return knownVisitorCount;
    }

    public void setKnownVisitorCount(Long knownVisitorCount)
    {
        this.knownVisitorCount = knownVisitorCount;
    }

    public Long getStrangerVisitorCount()
    {
        return strangerVisitorCount;
    }

    public void setStrangerVisitorCount(Long strangerVisitorCount)
    {
        this.strangerVisitorCount = strangerVisitorCount;
    }

    public Long getOpenSessionCount()
    {
        return openSessionCount;
    }

    public void setOpenSessionCount(Long openSessionCount)
    {
        this.openSessionCount = openSessionCount;
    }

    public Long getClosedDwellSeconds()
    {
        return closedDwellSeconds;
    }

    public void setClosedDwellSeconds(Long closedDwellSeconds)
    {
        this.closedDwellSeconds = closedDwellSeconds;
    }

    public Long getOpenDwellSeconds()
    {
        return openDwellSeconds;
    }

    public void setOpenDwellSeconds(Long openDwellSeconds)
    {
        this.openDwellSeconds = openDwellSeconds;
    }
}
