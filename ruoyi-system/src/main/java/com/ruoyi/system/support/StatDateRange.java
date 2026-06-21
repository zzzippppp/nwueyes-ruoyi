package com.ruoyi.system.support;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 统计日期范围（含单日兼容）
 */
public final class StatDateRange
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private final LocalDate beginDate;

    private final LocalDate endDate;

    private StatDateRange(LocalDate beginDate, LocalDate endDate)
    {
        this.beginDate = beginDate;
        this.endDate = endDate;
    }

    public static StatDateRange resolve(LocalDate statDate, LocalDate beginDate, LocalDate endDate)
    {
        if (beginDate != null || endDate != null)
        {
            LocalDate begin = beginDate != null ? beginDate : endDate;
            LocalDate end = endDate != null ? endDate : beginDate;
            if (begin.isAfter(end))
            {
                LocalDate tmp = begin;
                begin = end;
                end = tmp;
            }
            return new StatDateRange(begin, end);
        }
        if (statDate != null)
        {
            return new StatDateRange(statDate, statDate);
        }
        LocalDate today = LocalDate.now(STAT_ZONE);
        return new StatDateRange(today, today);
    }

    public static StatDateRange ofSingleDay(LocalDate day)
    {
        return new StatDateRange(day, day);
    }

    public LocalDate getBeginDate()
    {
        return beginDate;
    }

    public LocalDate getEndDate()
    {
        return endDate;
    }
}
