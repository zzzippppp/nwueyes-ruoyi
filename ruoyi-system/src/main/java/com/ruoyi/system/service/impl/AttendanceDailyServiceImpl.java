package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.vo.AttendanceDailyStatsVo;
import com.ruoyi.system.domain.vo.AttendanceDashboardVo;
import com.ruoyi.system.domain.vo.PersonDailyAttendanceVo;
import com.ruoyi.system.mapper.AttendanceDailyMapper;
import com.ruoyi.system.service.IAttendanceDailyService;
import com.ruoyi.system.support.StatDateRange;

@Service
public class AttendanceDailyServiceImpl implements IAttendanceDailyService
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AttendanceDailyMapper attendanceDailyMapper;

    @Override
    public void onEnter(Long personId, Long cameraId, Long sessionId, java.util.Date eventTime)
    {
        if (personId == null || eventTime == null)
        {
            return;
        }
        LocalDate statDate = eventTime.toInstant().atZone(STAT_ZONE).toLocalDate();
        attendanceDailyMapper.upsertOnEnter(Date.valueOf(statDate), personId, cameraId, eventTime, sessionId);
    }

    @Override
    public void onExit(Long personId, Long sessionId, java.util.Date eventTime, Integer dwellSeconds)
    {
        if (personId == null || eventTime == null)
        {
            return;
        }
        LocalDate statDate = eventTime.toInstant().atZone(STAT_ZONE).toLocalDate();
        attendanceDailyMapper.updateOnExit(Date.valueOf(statDate), personId, eventTime, dwellSeconds);
    }

    @Override
    public List<PersonDailyAttendanceVo> listDailyAttendance(LocalDate statDate, LocalDate beginDate,
            LocalDate endDate, Long cameraId, String personType, String displayName, String employeeNo,
            String attendanceStatus, int limit)
    {
        StatDateRange range = StatDateRange.resolve(statDate, beginDate, endDate);
        int rowLimit = limit > 0 ? Math.min(limit, 500) : 200;
        return attendanceDailyMapper.selectDailyList(Date.valueOf(range.getBeginDate()),
                Date.valueOf(range.getEndDate()), cameraId, personType, displayName, employeeNo, attendanceStatus,
                rowLimit);
    }

    @Override
    public AttendanceDashboardVo getDashboard(LocalDate statDate)
    {
        LocalDate today = statDate != null ? statDate : LocalDate.now(STAT_ZONE);
        Date sqlToday = Date.valueOf(today);
        int registry = attendanceDailyMapper.countRegistryPersons();
        int attended = attendanceDailyMapper.countAttendedToday(sqlToday, false);
        BigDecimal rate = registry <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(attended * 100.0 / registry).setScale(2, RoundingMode.HALF_UP);

        AttendanceDailyStatsVo yesterday = attendanceDailyMapper.selectDailyStats(Date.valueOf(today.minusDays(1)));
        BigDecimal delta = yesterday == null || yesterday.getAttendanceRate() == null
                ? BigDecimal.ZERO
                : rate.subtract(yesterday.getAttendanceRate());

        AttendanceDashboardVo vo = new AttendanceDashboardVo();
        vo.setTotalRegistryCount(registry);
        vo.setAttendedCount(attended);
        vo.setAttendanceRate(rate);
        vo.setAttendanceRateDelta(delta);
        vo.setPresentCount(attendanceDailyMapper.countPresentPersons("registry"));
        vo.setStrangerTotalCount(attendanceDailyMapper.countStrangerPersons());
        vo.setStrangerPresentCount(attendanceDailyMapper.countPresentPersons("stranger"));
        return vo;
    }

    @Override
    public void finalizeDailyStats(LocalDate statDate)
    {
        LocalDate day = statDate != null ? statDate : LocalDate.now(STAT_ZONE).minusDays(1);
        Date sqlDate = Date.valueOf(day);
        int registry = attendanceDailyMapper.countRegistryPersons();
        int attended = attendanceDailyMapper.countAttendedToday(sqlDate, false);
        BigDecimal rate = registry <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(attended * 100.0 / registry).setScale(2, RoundingMode.HALF_UP);
        int strangerTotal = attendanceDailyMapper.countStrangerPersons();
        attendanceDailyMapper.upsertDailyStats(sqlDate, registry, attended, rate, strangerTotal);
    }
}
