package com.ruoyi.system.task;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.system.service.IAttendanceDailyService;

/**
 * 日终考勤快照（较昨日对比用）+ 零点兜底签退
 */
@Component
public class AttendanceDailyFinalizeTask
{
    private static final Logger log = LoggerFactory.getLogger(AttendanceDailyFinalizeTask.class);

    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private IAttendanceDailyService attendanceDailyService;

    /**
     * 每天零点：兜底签退。把仍在场（open session）的所有人关闭会话并置为已离场，
     * 离场时间记为「前一天 23:59:59」，考勤归属到进门那天，防止「永远在场」。
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void autoSignOutAtMidnight()
    {
        // 前一天 23:59:59（Asia/Shanghai）
        Date cutoff = Date.from(LocalDate.now(STAT_ZONE).atStartOfDay(STAT_ZONE).minusSeconds(1).toInstant());
        try
        {
            int closed = attendanceDailyService.autoCloseOpenSessions(cutoff);
            log.info("零点兜底签退完成：关闭 open 会话 {} 个，离场时间={}", closed, cutoff);
        }
        catch (Exception ex)
        {
            log.error("零点兜底签退失败: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void finalizeYesterday()
    {
        attendanceDailyService.finalizeDailyStats(LocalDate.now(STAT_ZONE).minusDays(1));
    }
}
