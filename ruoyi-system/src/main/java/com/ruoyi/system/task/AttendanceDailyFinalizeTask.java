package com.ruoyi.system.task;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.system.service.IAttendanceDailyService;

/**
 * 日终考勤快照（较昨日对比用）
 */
@Component
public class AttendanceDailyFinalizeTask
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private IAttendanceDailyService attendanceDailyService;

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void finalizeYesterday()
    {
        attendanceDailyService.finalizeDailyStats(LocalDate.now(STAT_ZONE).minusDays(1));
    }
}
