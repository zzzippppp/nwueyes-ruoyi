package com.ruoyi.web.controller.dashboard;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.IAttendanceDailyService;

/**
 * 考勤信息 API（列表 / 大屏）
 */
@RestController
@RequestMapping("/dashboard/attendance")
public class AttendanceController
{
    @Autowired
    private IAttendanceDailyService attendanceDailyService;

    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(value = "statDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate,
            @RequestParam(value = "beginDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate beginDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "cameraId", required = false) Long cameraId,
            @RequestParam(value = "personType", required = false) String personType,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "employeeNo", required = false) String employeeNo,
            @RequestParam(value = "attendanceStatus", required = false) String attendanceStatus,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit)
    {
        return AjaxResult.success(attendanceDailyService.listDailyAttendance(statDate, beginDate, endDate, cameraId,
                personType, displayName, employeeNo, attendanceStatus, limit == null ? 200 : limit));
    }

    @GetMapping("/dashboard")
    public AjaxResult dashboard(
            @RequestParam(value = "statDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate)
    {
        return AjaxResult.success(attendanceDailyService.getDashboard(statDate));
    }
}
