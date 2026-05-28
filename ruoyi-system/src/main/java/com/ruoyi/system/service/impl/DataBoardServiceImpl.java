package com.ruoyi.system.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.vo.DataBoardHourlyItemVo;
import com.ruoyi.system.domain.vo.DataBoardLocationItemVo;
import com.ruoyi.system.domain.vo.DataBoardOverviewVo;
import com.ruoyi.system.domain.vo.DataBoardPersonItemVo;
import com.ruoyi.system.domain.vo.DataBoardRecentSessionVo;
import com.ruoyi.system.domain.vo.DataBoardStrangerItemVo;
import com.ruoyi.system.domain.vo.DataBoardSummaryVo;
import com.ruoyi.system.mapper.DataBoardMapper;
import com.ruoyi.system.service.IDataBoardService;

/**
 * 数据看板统计
 */
@Service
public class DataBoardServiceImpl implements IDataBoardService
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int DEFAULT_RECENT_LIMIT = 10;

    private static final int MAX_RECENT_LIMIT = 50;

    @Autowired
    private DataBoardMapper dataBoardMapper;

    @Override
    public DataBoardSummaryVo getSummary(LocalDate statDate, Long locationId, int recentLimit)
    {
        LocalDate queryDate = statDate != null ? statDate : LocalDate.now(STAT_ZONE);
        Date sqlDate = Date.valueOf(queryDate);
        int limit = recentLimit > 0 ? Math.min(recentLimit, MAX_RECENT_LIMIT) : DEFAULT_RECENT_LIMIT;

        DataBoardOverviewVo overview = dataBoardMapper.selectOverview(sqlDate, locationId);
        if (overview == null)
        {
            overview = new DataBoardOverviewVo();
        }

        List<DataBoardHourlyItemVo> hourlyTrend = dataBoardMapper.selectHourlyTrend(sqlDate, locationId);
        List<DataBoardLocationItemVo> byLocation = dataBoardMapper.selectByLocation(sqlDate, locationId);
        List<DataBoardRecentSessionVo> recentSessions = dataBoardMapper.selectRecentSessions(sqlDate, locationId, limit);
        List<DataBoardPersonItemVo> personItems = dataBoardMapper.selectPersonItems(sqlDate, locationId, limit);
        List<DataBoardStrangerItemVo> strangerItems = dataBoardMapper.selectStrangerItems(sqlDate, locationId, limit);

        long closedDwell = nullSafe(overview.getClosedDwellSeconds());
        long openDwell = nullSafe(overview.getOpenDwellSeconds());

        DataBoardSummaryVo summary = new DataBoardSummaryVo();
        summary.setStatDate(queryDate.toString());
        summary.setLocationId(locationId);
        summary.setSessionCount(nullSafe(overview.getSessionCount()));
        summary.setVisitorCount(nullSafe(overview.getVisitorCount()));
        summary.setKnownVisitorCount(nullSafe(overview.getKnownVisitorCount()));
        summary.setStrangerVisitorCount(nullSafe(overview.getStrangerVisitorCount()));
        summary.setOpenSessionCount(nullSafe(overview.getOpenSessionCount()));
        summary.setClosedDwellSeconds(closedDwell);
        summary.setOpenDwellSeconds(openDwell);
        summary.setTotalDwellSeconds(closedDwell + openDwell);
        summary.setHourlyTrend(hourlyTrend);
        summary.setByLocation(byLocation);
        summary.setRecentSessions(recentSessions);
        summary.setPersonItems(personItems);
        summary.setStrangerItems(strangerItems);
        return summary;
    }

    private long nullSafe(Long value)
    {
        return value == null ? 0L : value;
    }
}
