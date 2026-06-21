package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据看板汇总
 */
public class DataBoardSummaryVo
{
    /** 统计日期 yyyy-MM-dd（兼容旧字段，等同 beginDate） */
    private String statDate;

    /** 统计开始日期 yyyy-MM-dd */
    private String beginDate;

    /** 统计结束日期 yyyy-MM-dd */
    private String endDate;

    private Long cameraId;

    /** 当日开始会话数 */
    private Long sessionCount;

    /** 访客人数（含未知访客） */
    private Long visitorCount;

    private Long knownVisitorCount;

    private Long strangerVisitorCount;

    /** 当前在场会话数（open，实时） */
    private Long openSessionCount;

    /** 已离开停留秒数（当日到达且 closed） */
    private Long closedDwellSeconds;

    /** 在场中预估停留秒数（当日到达且 open） */
    private Long openDwellSeconds;

    /** 总停留 = closed + open */
    private Long totalDwellSeconds;

    /** 在案总人数（student + staff，不含陌生人） */
    private Long registeredPersonCount;

    /** 今日出勤人数（去重，不含陌生人） */
    private Long todayKnownAttendanceCount;

    /** 出勤率 0–100 */
    private Integer attendanceRatePercent;

    private List<DataBoardHourlyItemVo> hourlyTrend = new ArrayList<>();

    private List<DataBoardCameraItemVo> byLocation = new ArrayList<>();

    private List<DataBoardRecentSessionVo> recentSessions = new ArrayList<>();

    private List<DataBoardPersonItemVo> personItems = new ArrayList<>();

    private List<DataBoardStrangerItemVo> strangerItems = new ArrayList<>();

    private List<DataBoardAttendanceItemVo> attendanceItems = new ArrayList<>();

    public String getStatDate()
    {
        return statDate;
    }

    public void setStatDate(String statDate)
    {
        this.statDate = statDate;
    }

    public String getBeginDate()
    {
        return beginDate;
    }

    public void setBeginDate(String beginDate)
    {
        this.beginDate = beginDate;
    }

    public String getEndDate()
    {
        return endDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }

    public Long getCameraId()
    {
        return cameraId;
    }

    public void setCameraId(Long cameraId)
    {
        this.cameraId = cameraId;
    }

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

    public Long getTotalDwellSeconds()
    {
        return totalDwellSeconds;
    }

    public void setTotalDwellSeconds(Long totalDwellSeconds)
    {
        this.totalDwellSeconds = totalDwellSeconds;
    }

    public Long getRegisteredPersonCount()
    {
        return registeredPersonCount;
    }

    public void setRegisteredPersonCount(Long registeredPersonCount)
    {
        this.registeredPersonCount = registeredPersonCount;
    }

    public Long getTodayKnownAttendanceCount()
    {
        return todayKnownAttendanceCount;
    }

    public void setTodayKnownAttendanceCount(Long todayKnownAttendanceCount)
    {
        this.todayKnownAttendanceCount = todayKnownAttendanceCount;
    }

    public Integer getAttendanceRatePercent()
    {
        return attendanceRatePercent;
    }

    public void setAttendanceRatePercent(Integer attendanceRatePercent)
    {
        this.attendanceRatePercent = attendanceRatePercent;
    }

    public List<DataBoardHourlyItemVo> getHourlyTrend()
    {
        return hourlyTrend;
    }

    public void setHourlyTrend(List<DataBoardHourlyItemVo> hourlyTrend)
    {
        this.hourlyTrend = hourlyTrend;
    }

    public List<DataBoardCameraItemVo> getByLocation()
    {
        return byLocation;
    }

    public void setByLocation(List<DataBoardCameraItemVo> byLocation)
    {
        this.byLocation = byLocation;
    }

    public List<DataBoardRecentSessionVo> getRecentSessions()
    {
        return recentSessions;
    }

    public void setRecentSessions(List<DataBoardRecentSessionVo> recentSessions)
    {
        this.recentSessions = recentSessions;
    }

    public List<DataBoardPersonItemVo> getPersonItems()
    {
        return personItems;
    }

    public void setPersonItems(List<DataBoardPersonItemVo> personItems)
    {
        this.personItems = personItems;
    }

    public List<DataBoardStrangerItemVo> getStrangerItems()
    {
        return strangerItems;
    }

    public void setStrangerItems(List<DataBoardStrangerItemVo> strangerItems)
    {
        this.strangerItems = strangerItems;
    }

    public List<DataBoardAttendanceItemVo> getAttendanceItems()
    {
        return attendanceItems;
    }

    public void setAttendanceItems(List<DataBoardAttendanceItemVo> attendanceItems)
    {
        this.attendanceItems = attendanceItems;
    }
}
