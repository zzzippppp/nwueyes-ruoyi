package com.ruoyi.web.controller.dashboard;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.domain.bo.AttendanceManualBo;
import com.ruoyi.system.service.IAttendanceDailyService;

/**
 * 考勤信息 API（列表 / 大屏 / 手工维护）
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
            @RequestParam(value = "personId", required = false) Long personId,
            @RequestParam(value = "attendanceStatus", required = false) String attendanceStatus,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit)
    {
        return AjaxResult.success(attendanceDailyService.listDailyAttendance(statDate, beginDate, endDate, cameraId,
                personType, displayName, employeeNo, personId, attendanceStatus, limit == null ? 200 : limit));
    }

    @GetMapping("/dashboard")
    public AjaxResult dashboard(
            @RequestParam(value = "statDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate)
    {
        return AjaxResult.success(attendanceDailyService.getDashboard(statDate));
    }

    @PostMapping("/manual")
    public AjaxResult add(@RequestBody AttendanceManualBo bo)
    {
        return attendanceDailyService.saveManualAttendance(bo) ? AjaxResult.success() : AjaxResult.error("新增失败");
    }

    @PutMapping("/manual")
    public AjaxResult edit(@RequestBody AttendanceManualBo bo)
    {
        return attendanceDailyService.saveManualAttendance(bo) ? AjaxResult.success() : AjaxResult.error("修改失败");
    }
}
