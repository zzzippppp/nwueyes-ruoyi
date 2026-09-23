package com.ruoyi.system.service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.bo.AttendanceManualBo;
import com.ruoyi.system.domain.vo.AttendanceDashboardVo;
import com.ruoyi.system.domain.vo.PersonDailyAttendanceVo;

public interface IAttendanceDailyService
{
    void onEnter(Long personId, Long cameraId, Long sessionId, Date eventTime);

    void onExit(Long personId, Long sessionId, Date eventTime, Integer dwellSeconds);

    /**
     * 兜底签退：关闭所有仍 open 的停留会话，并把对应当日考勤置为已离场。
     *
     * @param closeTime 统一的离场时间（如当日 23:59:59）
     * @return 关闭的会话数
     */
    int autoCloseOpenSessions(Date closeTime);

    List<PersonDailyAttendanceVo> listDailyAttendance(LocalDate statDate, LocalDate beginDate, LocalDate endDate,
            Long cameraId, String personType, String displayName, String employeeNo, Long personId,
            String attendanceStatus, int limit);

    AttendanceDashboardVo getDashboard(LocalDate statDate);

    void finalizeDailyStats(LocalDate statDate);

    /** 手工新增 / 修改考勤（含在场状态） */
    boolean saveManualAttendance(AttendanceManualBo bo);
}
