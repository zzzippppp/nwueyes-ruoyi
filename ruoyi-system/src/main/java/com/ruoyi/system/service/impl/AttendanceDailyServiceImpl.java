package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.bo.AttendanceManualBo;
import com.ruoyi.system.domain.vo.AttendanceDailyStatsVo;
import com.ruoyi.system.domain.vo.AttendanceDashboardVo;
import com.ruoyi.system.domain.vo.PersonDailyAttendanceVo;
import com.ruoyi.system.domain.vo.PresenceOpenSessionVo;
import com.ruoyi.system.mapper.AttendanceDailyMapper;
import com.ruoyi.system.mapper.PresenceIngestMapper;
import com.ruoyi.system.service.IAttendanceDailyService;
import com.ruoyi.system.support.StatDateRange;

@Service
public class AttendanceDailyServiceImpl implements IAttendanceDailyService
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AttendanceDailyMapper attendanceDailyMapper;

    @Autowired
    private PresenceIngestMapper presenceIngestMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public int autoCloseOpenSessions(java.util.Date closeTime)
    {
        java.util.Date cutoff = closeTime != null ? closeTime : new java.util.Date();
        List<PresenceOpenSessionVo> opens = presenceIngestMapper.selectAllOpen();
        if (opens == null || opens.isEmpty())
        {
            return 0;
        }
        int closed = 0;
        for (PresenceOpenSessionVo open : opens)
        {
            if (open.getSessionId() == null)
            {
                continue;
            }
            // 截止时间之后才进来的会话（如刚跨零点进门）不动
            if (open.getArrivalAt() != null && cutoff.before(open.getArrivalAt()))
            {
                continue;
            }
            int updated = presenceIngestMapper.closeSession(open.getSessionId(), cutoff, open.getPersonId(), null);
            if (updated <= 0)
            {
                continue;
            }
            closed++;
            if (open.getPersonId() != null)
            {
                // 考勤归属到「进门那天」，离场时间用截止时间
                LocalDate statDate = open.getArrivalAt() != null
                        ? open.getArrivalAt().toInstant().atZone(STAT_ZONE).toLocalDate()
                        : cutoff.toInstant().atZone(STAT_ZONE).toLocalDate();
                int dwell = computeDwellSeconds(open.getArrivalAt(), cutoff);
                attendanceDailyMapper.updateOnExit(Date.valueOf(statDate), open.getPersonId(), cutoff, dwell);
            }
        }
        return closed;
    }

    private int computeDwellSeconds(java.util.Date arrivalAt, java.util.Date departureAt)
    {
        if (arrivalAt == null || departureAt == null)
        {
            return 0;
        }
        long seconds = (departureAt.getTime() - arrivalAt.getTime()) / 1000L;
        return (int) Math.max(0L, seconds);
    }

    @Override
    public List<PersonDailyAttendanceVo> listDailyAttendance(LocalDate statDate, LocalDate beginDate,
            LocalDate endDate, Long cameraId, String personType, String displayName, String employeeNo,
            Long personId, String attendanceStatus, int limit)
    {
        StatDateRange range = StatDateRange.resolve(statDate, beginDate, endDate);
        int rowLimit = limit > 0 ? Math.min(limit, 500) : 200;
        return attendanceDailyMapper.selectDailyList(Date.valueOf(range.getBeginDate()),
                Date.valueOf(range.getEndDate()), cameraId, personType, displayName, employeeNo, personId,
                attendanceStatus, rowLimit);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveManualAttendance(AttendanceManualBo bo)
    {
        if (bo == null || bo.getPersonId() == null)
        {
            throw new ServiceException("人员不能为空");
        }
        String status = normalizeStatus(bo.getAttendanceStatus());
        LocalDate day = resolveStatDate(bo.getStatDate());
        Date sqlDate = Date.valueOf(day);
        java.util.Date now = new java.util.Date();
        java.util.Date arrivalAt = bo.getArrivalAt() != null ? bo.getArrivalAt() : now;
        java.util.Date departureAt = bo.getDepartureAt() != null ? bo.getDepartureAt() : now;

        if ("absent".equals(status))
        {
            // 删除当天重叠会话；并清掉仍 open 的会话，避免列表按会话重算回 present
            presenceIngestMapper.deleteSessionsByPersonDate(bo.getPersonId(), sqlDate);
            presenceIngestMapper.closeOpenSessionsByPerson(bo.getPersonId(), now);
            presenceIngestMapper.deleteSessionsByPersonDate(bo.getPersonId(), sqlDate);
            attendanceDailyMapper.upsertManual(sqlDate, bo.getPersonId(), bo.getCameraId(), null, null, 0, 0,
                    "absent", Boolean.FALSE, null);
            return true;
        }

        if (bo.getCameraId() == null)
        {
            PresenceOpenSessionVo open = presenceIngestMapper.selectAnyOpenByPerson(bo.getPersonId());
            if (open != null && open.getCameraId() != null)
            {
                bo.setCameraId(open.getCameraId());
            }
        }
        if (bo.getCameraId() == null)
        {
            throw new ServiceException("地点/设备不能为空");
        }
        if (presenceIngestMapper.existsLocation(bo.getCameraId()) <= 0)
        {
            throw new ServiceException("地点/设备不存在");
        }

        if ("present".equals(status))
        {
            PresenceOpenSessionVo open = presenceIngestMapper.selectAnyOpenByPerson(bo.getPersonId());
            Long sessionId;
            if (open != null)
            {
                sessionId = open.getSessionId();
                if (bo.getCameraId() != null && !bo.getCameraId().equals(open.getCameraId()))
                {
                    // 已有在场会话时保持原会话，仅刷新日考勤状态
                    bo.setCameraId(open.getCameraId());
                }
                arrivalAt = open.getArrivalAt() != null ? open.getArrivalAt() : arrivalAt;
            }
            else
            {
                String trackKey = "manual_" + IdUtils.fastSimpleUUID();
                sessionId = presenceIngestMapper.insertOpenSession(bo.getCameraId(), bo.getPersonId(), trackKey,
                        arrivalAt, null, null);
            }
            attendanceDailyMapper.upsertManual(sqlDate, bo.getPersonId(), bo.getCameraId(), arrivalAt, null, 0, 1,
                    "present", Boolean.TRUE, sessionId);
            return sessionId != null;
        }

        // left
        if (departureAt.before(arrivalAt))
        {
            throw new ServiceException("离开时间不能早于到达时间");
        }
        PresenceOpenSessionVo open = presenceIngestMapper.selectAnyOpenByPerson(bo.getPersonId());
        Long sessionId = null;
        int dwell = (int) Math.max(0L, (departureAt.getTime() - arrivalAt.getTime()) / 1000L);
        if (open != null)
        {
            sessionId = open.getSessionId();
            presenceIngestMapper.closeSession(sessionId, departureAt, bo.getPersonId(), null);
            if (open.getArrivalAt() != null)
            {
                arrivalAt = open.getArrivalAt();
                dwell = (int) Math.max(0L, (departureAt.getTime() - arrivalAt.getTime()) / 1000L);
            }
        }
        else
        {
            String trackKey = "manual_" + IdUtils.fastSimpleUUID();
            sessionId = presenceIngestMapper.insertOpenSession(bo.getCameraId(), bo.getPersonId(), trackKey,
                    arrivalAt, null, null);
            presenceIngestMapper.closeSession(sessionId, departureAt, bo.getPersonId(), null);
        }
        presenceIngestMapper.closeOpenSessionsByPerson(bo.getPersonId(), departureAt);
        attendanceDailyMapper.upsertManual(sqlDate, bo.getPersonId(), bo.getCameraId(), arrivalAt, departureAt, dwell,
                1, "left", Boolean.TRUE, null);
        return true;
    }

    private String normalizeStatus(String raw)
    {
        if (StringUtils.isEmpty(raw))
        {
            throw new ServiceException("在场状态不能为空");
        }
        String status = raw.trim().toLowerCase(Locale.ROOT);
        if ("open".equals(status))
        {
            return "present";
        }
        if ("closed".equals(status))
        {
            return "left";
        }
        if (!"absent".equals(status) && !"present".equals(status) && !"left".equals(status))
        {
            throw new ServiceException("在场状态无效，仅支持：未出勤 / 在场中 / 已离场");
        }
        return status;
    }

    private LocalDate resolveStatDate(String raw)
    {
        if (StringUtils.isEmpty(raw))
        {
            return LocalDate.now(STAT_ZONE);
        }
        return LocalDate.parse(raw.trim());
    }
}
