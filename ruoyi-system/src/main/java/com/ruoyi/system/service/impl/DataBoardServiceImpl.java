package com.ruoyi.system.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.bo.DataBoardSessionFilterBo;
import com.ruoyi.system.domain.vo.DataBoardAttendanceItemVo;
import com.ruoyi.system.domain.vo.DataBoardHourlyItemVo;
import com.ruoyi.system.domain.vo.DataBoardCameraItemVo;
import com.ruoyi.system.domain.vo.DataBoardOverviewVo;
import com.ruoyi.system.domain.vo.DataBoardPersonItemVo;
import com.ruoyi.system.domain.vo.DataBoardRecentSessionVo;
import com.ruoyi.system.domain.vo.DataBoardStrangerItemVo;
import com.ruoyi.system.domain.vo.DataBoardSummaryVo;
import com.ruoyi.system.mapper.DataBoardMapper;
import com.ruoyi.system.service.IDataBoardService;
import com.ruoyi.system.support.StatDateRange;

/**
 * 数据看板统计
 */
@Service
public class DataBoardServiceImpl implements IDataBoardService
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int DEFAULT_RECENT_LIMIT = 10;

    private static final int MAX_RECENT_LIMIT = 500;

    @Autowired
    private DataBoardMapper dataBoardMapper;

    @Override
    public DataBoardSummaryVo getSummary(LocalDate statDate, LocalDate beginDate, LocalDate endDate, Long cameraId,
            int recentLimit, DataBoardSessionFilterBo sessionFilter)
    {
        StatDateRange range = StatDateRange.resolve(statDate, beginDate, endDate);
        DataBoardSessionFilterBo filter = normalizeSessionFilter(sessionFilter);
        if (hasTimeRange(filter))
        {
            LocalDate day = range.getBeginDate();
            range = StatDateRange.ofSingleDay(day);
        }
        Date sqlBegin = Date.valueOf(range.getBeginDate());
        Date sqlEnd = Date.valueOf(range.getEndDate());
        int limit = recentLimit > 0 ? Math.min(recentLimit, MAX_RECENT_LIMIT) : DEFAULT_RECENT_LIMIT;

        DataBoardOverviewVo overview = dataBoardMapper.selectOverview(sqlBegin, sqlEnd, cameraId, filter);
        if (overview == null)
        {
            overview = new DataBoardOverviewVo();
        }

        List<DataBoardHourlyItemVo> hourlyTrend = dataBoardMapper.selectHourlyTrend(sqlBegin, sqlEnd, cameraId, filter);
        List<DataBoardCameraItemVo> byLocation = dataBoardMapper.selectByCamera(sqlBegin, sqlEnd, cameraId, filter);
        List<DataBoardRecentSessionVo> recentSessions = dataBoardMapper.selectRecentSessions(sqlBegin, sqlEnd,
                cameraId, limit, filter);
        List<DataBoardPersonItemVo> personItems = dataBoardMapper.selectPersonItems(sqlBegin, sqlEnd, cameraId, limit);
        List<DataBoardStrangerItemVo> strangerItems = dataBoardMapper.selectStrangerItems(sqlBegin, sqlEnd, cameraId,
                limit);

        List<DataBoardAttendanceItemVo> attendanceItems = dataBoardMapper.selectAttendanceInfoList(sqlBegin, sqlEnd,
                cameraId, limit, filter);

        long closedDwell = nullSafe(overview.getClosedDwellSeconds());
        long openDwell = nullSafe(overview.getOpenDwellSeconds());

        DataBoardSummaryVo summary = new DataBoardSummaryVo();
        summary.setStatDate(range.getBeginDate().toString());
        summary.setBeginDate(range.getBeginDate().toString());
        summary.setEndDate(range.getEndDate().toString());
        summary.setCameraId(cameraId);
        summary.setSessionCount(nullSafe(overview.getSessionCount()));
        summary.setVisitorCount(nullSafe(overview.getVisitorCount()));
        summary.setKnownVisitorCount(nullSafe(overview.getKnownVisitorCount()));
        summary.setStrangerVisitorCount(nullSafe(overview.getStrangerVisitorCount()));
        summary.setOpenSessionCount(nullSafe(overview.getOpenSessionCount()));
        summary.setClosedDwellSeconds(closedDwell);
        summary.setOpenDwellSeconds(openDwell);
        summary.setTotalDwellSeconds(closedDwell + openDwell);

        LocalDate rateDate = range.getBeginDate();
        long registeredPersonCount = nullSafe(dataBoardMapper.selectRegisteredPersonCount());
        long todayKnownAttendanceCount = nullSafe(
                dataBoardMapper.selectTodayKnownAttendanceCount(Date.valueOf(rateDate), cameraId));
        int attendanceRatePercent = registeredPersonCount > 0
                ? (int) Math.round(todayKnownAttendanceCount * 100.0 / registeredPersonCount)
                : 0;
        summary.setRegisteredPersonCount(registeredPersonCount);
        summary.setTodayKnownAttendanceCount(todayKnownAttendanceCount);
        summary.setAttendanceRatePercent(attendanceRatePercent);

        summary.setHourlyTrend(hourlyTrend);
        summary.setByLocation(byLocation);
        summary.setRecentSessions(recentSessions);
        summary.setPersonItems(personItems);
        summary.setStrangerItems(strangerItems);
        summary.setAttendanceItems(attendanceItems);
        return summary;
    }

    private DataBoardSessionFilterBo normalizeSessionFilter(DataBoardSessionFilterBo sessionFilter)
    {
        if (sessionFilter == null)
        {
            return new DataBoardSessionFilterBo();
        }
        if (!StringUtils.isEmpty(sessionFilter.getDisplayName()))
        {
            sessionFilter.setDisplayName(sessionFilter.getDisplayName().trim());
        }
        if (!StringUtils.isEmpty(sessionFilter.getEmployeeNo()))
        {
            sessionFilter.setEmployeeNo(sessionFilter.getEmployeeNo().trim());
        }
        if (!StringUtils.isEmpty(sessionFilter.getPersonType()))
        {
            sessionFilter.setPersonType(sessionFilter.getPersonType().trim());
        }
        if (!StringUtils.isEmpty(sessionFilter.getSessionStatus()))
        {
            sessionFilter.setSessionStatus(sessionFilter.getSessionStatus().trim());
        }
        normalizeTimeRange(sessionFilter);
        return sessionFilter;
    }

    private void normalizeTimeRange(DataBoardSessionFilterBo sessionFilter)
    {
        if (StringUtils.isEmpty(sessionFilter.getBeginTime()) || StringUtils.isEmpty(sessionFilter.getEndTime()))
        {
            sessionFilter.setBeginTime(null);
            sessionFilter.setEndTime(null);
            return;
        }
        int beginMinutes = parseTimeToMinutes(sessionFilter.getBeginTime(), 0);
        int endMinutes = parseTimeToMinutes(sessionFilter.getEndTime(), 23 * 60 + 59);
        if (beginMinutes > endMinutes)
        {
            int tmp = beginMinutes;
            beginMinutes = endMinutes;
            endMinutes = tmp;
        }
        sessionFilter.setBeginTime(formatMinutesToTime(beginMinutes));
        sessionFilter.setEndTime(formatMinutesToTime(endMinutes));
    }

    private int parseTimeToMinutes(String timeText, int fallback)
    {
        if (StringUtils.isEmpty(timeText))
        {
            return fallback;
        }
        String[] parts = timeText.trim().split(":");
        if (parts.length < 2)
        {
            return fallback;
        }
        try
        {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            hour = Math.max(0, Math.min(23, hour));
            minute = Math.max(0, Math.min(59, minute));
            return hour * 60 + minute;
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private String formatMinutesToTime(int minutes)
    {
        int hour = Math.max(0, Math.min(23, minutes / 60));
        int minute = Math.max(0, Math.min(59, minutes % 60));
        return String.format("%02d:%02d", hour, minute);
    }

    private boolean hasTimeRange(DataBoardSessionFilterBo sessionFilter)
    {
        return sessionFilter != null && !StringUtils.isEmpty(sessionFilter.getBeginTime())
                && !StringUtils.isEmpty(sessionFilter.getEndTime());
    }

    private long nullSafe(Long value)
    {
        return value == null ? 0L : value;
    }
}
