package com.ruoyi.system.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.bo.DataBoardSessionFilterBo;
import com.ruoyi.system.domain.vo.DataBoardAttendanceItemVo;
import com.ruoyi.system.domain.vo.DataBoardHourlyItemVo;
import com.ruoyi.system.domain.vo.DataBoardCameraItemVo;
import com.ruoyi.system.domain.vo.DataBoardOverviewVo;
import com.ruoyi.system.domain.vo.DataBoardPersonItemVo;
import com.ruoyi.system.domain.vo.DataBoardPersonSearchVo;
import com.ruoyi.system.domain.vo.DataBoardRecentSessionVo;
import com.ruoyi.system.domain.vo.DataBoardStrangerItemVo;
import com.ruoyi.system.domain.vo.DataBoardSummaryVo;
import com.ruoyi.system.mapper.DataBoardMapper;
import com.ruoyi.system.service.IDataBoardService;
import com.ruoyi.system.support.StatDateRange;

/**
 * 数据看板统计
 */
@Service
public class DataBoardServiceImpl implements IDataBoardService
{
    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int DEFAULT_RECENT_LIMIT = 10;

    private static final int MAX_RECENT_LIMIT = 500;

    @Value("${presence.ingest.faceMatchThreshold:0.35}")
    private double faceMatchThreshold;

    @Autowired
    private DataBoardMapper dataBoardMapper;

    @Override
    public DataBoardSummaryVo getSummary(LocalDate statDate, LocalDate beginDate, LocalDate endDate, Long cameraId,
            int recentLimit, DataBoardSessionFilterBo sessionFilter)
    {
        StatDateRange range = StatDateRange.resolve(statDate, beginDate, endDate);
        DataBoardSessionFilterBo filter = normalizeSessionFilter(sessionFilter);
        if (hasTimeRange(filter))
        {
            LocalDate day = range.getBeginDate();
            range = StatDateRange.ofSingleDay(day);
        }
        Date sqlBegin = Date.valueOf(range.getBeginDate());
        Date sqlEnd = Date.valueOf(range.getEndDate());
        int limit = recentLimit > 0 ? Math.min(recentLimit, MAX_RECENT_LIMIT) : DEFAULT_RECENT_LIMIT;

        DataBoardOverviewVo overview = dataBoardMapper.selectOverview(sqlBegin, sqlEnd, cameraId, filter);
        if (overview == null)
        {
            overview = new DataBoardOverviewVo();
        }

        List<DataBoardHourlyItemVo> hourlyTrend = dataBoardMapper.selectHourlyTrend(sqlBegin, sqlEnd, cameraId, filter);
        List<DataBoardCameraItemVo> byLocation = dataBoardMapper.selectByCamera(sqlBegin, sqlEnd, cameraId, filter);
        List<DataBoardRecentSessionVo> recentSessions = dataBoardMapper.selectRecentSessions(sqlBegin, sqlEnd,
                cameraId, limit, filter);
        List<DataBoardPersonItemVo> personItems = dataBoardMapper.selectPersonItems(sqlBegin, sqlEnd, cameraId, limit);
        List<DataBoardStrangerItemVo> strangerItems = groupStrangersByFace(sqlBegin, sqlEnd, cameraId, limit);

        List<DataBoardAttendanceItemVo> attendanceItems = dataBoardMapper.selectAttendanceInfoList(sqlBegin, sqlEnd,
                cameraId, limit, filter);

        long closedDwell = nullSafe(overview.getClosedDwellSeconds());
        long openDwell = nullSafe(overview.getOpenDwellSeconds());

        DataBoardSummaryVo summary = new DataBoardSummaryVo();
        summary.setStatDate(range.getBeginDate().toString());
        summary.setBeginDate(range.getBeginDate().toString());
        summary.setEndDate(range.getEndDate().toString());
        summary.setCameraId(cameraId);
        summary.setSessionCount(nullSafe(overview.getSessionCount()));
        summary.setVisitorCount(nullSafe(overview.getVisitorCount()));
        summary.setKnownVisitorCount(nullSafe(overview.getKnownVisitorCount()));
        summary.setStrangerVisitorCount(nullSafe(overview.getStrangerVisitorCount()));
        summary.setOpenSessionCount(nullSafe(overview.getOpenSessionCount()));
        summary.setClosedDwellSeconds(closedDwell);
        summary.setOpenDwellSeconds(openDwell);
        summary.setTotalDwellSeconds(closedDwell + openDwell);

        LocalDate rateDate = range.getBeginDate();
        long registeredPersonCount = nullSafe(dataBoardMapper.selectRegisteredPersonCount());
        long todayKnownAttendanceCount = nullSafe(
                dataBoardMapper.selectTodayKnownAttendanceCount(Date.valueOf(rateDate), cameraId));
        int attendanceRatePercent = registeredPersonCount > 0
                ? (int) Math.round(todayKnownAttendanceCount * 100.0 / registeredPersonCount)
                : 0;
        summary.setRegisteredPersonCount(registeredPersonCount);
        summary.setTodayKnownAttendanceCount(todayKnownAttendanceCount);
        summary.setAttendanceRatePercent(attendanceRatePercent);

        summary.setHourlyTrend(hourlyTrend);
        summary.setByLocation(byLocation);
        summary.setRecentSessions(recentSessions);
        summary.setPersonItems(personItems);
        summary.setStrangerItems(strangerItems);
        summary.setAttendanceItems(attendanceItems);
        return summary;
    }

    @Override
    public List<DataBoardPersonSearchVo> searchPersons(String keyword)
    {
        return dataBoardMapper.searchPersons(keyword);
    }

    private DataBoardSessionFilterBo normalizeSessionFilter(DataBoardSessionFilterBo sessionFilter)
    {
        if (sessionFilter == null)
        {
            return new DataBoardSessionFilterBo();
        }
        if (!StringUtils.isEmpty(sessionFilter.getDisplayName()))
        {
            sessionFilter.setDisplayName(sessionFilter.getDisplayName().trim());
        }
        if (!StringUtils.isEmpty(sessionFilter.getEmployeeNo()))
        {
            sessionFilter.setEmployeeNo(sessionFilter.getEmployeeNo().trim());
        }
        if (!StringUtils.isEmpty(sessionFilter.getPersonType()))
        {
            sessionFilter.setPersonType(sessionFilter.getPersonType().trim());
        }
        if (!StringUtils.isEmpty(sessionFilter.getSessionStatus()))
        {
            sessionFilter.setSessionStatus(sessionFilter.getSessionStatus().trim());
        }
        normalizeTimeRange(sessionFilter);
        return sessionFilter;
    }

    private void normalizeTimeRange(DataBoardSessionFilterBo sessionFilter)
    {
        if (StringUtils.isEmpty(sessionFilter.getBeginTime()) || StringUtils.isEmpty(sessionFilter.getEndTime()))
        {
            sessionFilter.setBeginTime(null);
            sessionFilter.setEndTime(null);
            return;
        }
        int beginMinutes = parseTimeToMinutes(sessionFilter.getBeginTime(), 0);
        int endMinutes = parseTimeToMinutes(sessionFilter.getEndTime(), 23 * 60 + 59);
        if (beginMinutes > endMinutes)
        {
            int tmp = beginMinutes;
            beginMinutes = endMinutes;
            endMinutes = tmp;
        }
        sessionFilter.setBeginTime(formatMinutesToTime(beginMinutes));
        sessionFilter.setEndTime(formatMinutesToTime(endMinutes));
    }

    private int parseTimeToMinutes(String timeText, int fallback)
    {
        if (StringUtils.isEmpty(timeText))
        {
            return fallback;
        }
        String[] parts = timeText.trim().split(":");
        if (parts.length < 2)
        {
            return fallback;
        }
        try
        {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            hour = Math.max(0, Math.min(23, hour));
            minute = Math.max(0, Math.min(59, minute));
            return hour * 60 + minute;
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private String formatMinutesToTime(int minutes)
    {
        int hour = Math.max(0, Math.min(23, minutes / 60));
        int minute = Math.max(0, Math.min(59, minutes % 60));
        return String.format("%02d:%02d", hour, minute);
    }

    private boolean hasTimeRange(DataBoardSessionFilterBo sessionFilter)
    {
        return sessionFilter != null && !StringUtils.isEmpty(sessionFilter.getBeginTime())
                && !StringUtils.isEmpty(sessionFilter.getEndTime());
    }

    /**
     * 查询陌生人列表并按人脸向量相似度去重分组
     */
    private List<DataBoardStrangerItemVo> groupStrangersByFace(Date sqlBegin, Date sqlEnd, Long cameraId, int limit)
    {
        List<DataBoardStrangerItemVo> rawItems = dataBoardMapper.selectStrangerItems(sqlBegin, sqlEnd, cameraId, limit);
        if (rawItems == null || rawItems.isEmpty())
        {
            return rawItems;
        }

        Map<String, float[]> embedMap = loadStrangerEmbeddings(sqlBegin, sqlEnd);
        if (embedMap.isEmpty())
        {
            for (DataBoardStrangerItemVo item : rawItems)
            {
                item.setSessionCount(1);
                item.getRelatedTrackKeys().add(item.getTrackKey());
            }
            return rawItems;
        }

        for (DataBoardStrangerItemVo item : rawItems)
        {
            float[] emb = embedMap.get(item.getTrackKey());
            item.setFaceEmbedding(emb);
        }

        List<DataBoardStrangerItemVo> grouped = new ArrayList<>();
        boolean[] merged = new boolean[rawItems.size()];

        for (int i = 0; i < rawItems.size(); i++)
        {
            if (merged[i])
            {
                continue;
            }
            DataBoardStrangerItemVo rep = rawItems.get(i);
            rep.setSessionCount(1);
            rep.getRelatedTrackKeys().add(rep.getTrackKey());

            if (rep.getFaceEmbedding() != null)
            {
                for (int j = i + 1; j < rawItems.size(); j++)
                {
                    if (merged[j])
                    {
                        continue;
                    }
                    DataBoardStrangerItemVo other = rawItems.get(j);
                    if (other.getFaceEmbedding() != null
                            && cosineSimilarity(rep.getFaceEmbedding(), other.getFaceEmbedding()) > faceMatchThreshold)
                    {
                        rep.setSessionCount(rep.getSessionCount() + 1);
                        rep.getRelatedTrackKeys().add(other.getTrackKey());
                        merged[j] = true;
                    }
                }
            }
            grouped.add(rep);
        }
        return grouped;
    }

    private Map<String, float[]> loadStrangerEmbeddings(Date sqlBegin, Date sqlEnd)
    {
        Map<String, float[]> map = new HashMap<>();
        List<Map<String, Object>> rows = dataBoardMapper.selectStrangerFaceEmbeddings(sqlBegin, sqlEnd);
        if (rows == null)
        {
            return map;
        }
        for (Map<String, Object> row : rows)
        {
            String trackKey = (String) row.get("track_key");
            String embText = (String) row.get("embedding_text");
            if (trackKey != null && embText != null && !map.containsKey(trackKey))
            {
                float[] vec = parseVector(embText);
                if (vec != null)
                {
                    map.put(trackKey, vec);
                }
            }
        }
        return map;
    }

    private static float[] parseVector(String text)
    {
        if (text == null || text.length() < 3)
        {
            return null;
        }
        String inner = text;
        if (inner.startsWith("["))
        {
            inner = inner.substring(1, inner.length() - 1);
        }
        String[] parts = inner.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }

    private static double cosineSimilarity(float[] a, float[] b)
    {
        if (a.length != b.length)
        {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    private long nullSafe(Long value)
    {
        return value == null ? 0L : value;
    }
}
