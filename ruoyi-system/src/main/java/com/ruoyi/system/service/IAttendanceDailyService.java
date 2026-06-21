package com.ruoyi.system.service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.vo.AttendanceDashboardVo;
import com.ruoyi.system.domain.vo.PersonDailyAttendanceVo;

public interface IAttendanceDailyService
{
    void onEnter(Long personId, Long cameraId, Long sessionId, Date eventTime);

    void onExit(Long personId, Long sessionId, Date eventTime, Integer dwellSeconds);

    List<PersonDailyAttendanceVo> listDailyAttendance(LocalDate statDate, LocalDate beginDate, LocalDate endDate,
            Long cameraId, String personType, String displayName, String employeeNo, String attendanceStatus,
            int limit);

    AttendanceDashboardVo getDashboard(LocalDate statDate);

    void finalizeDailyStats(LocalDate statDate);
}
