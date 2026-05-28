package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.DataBoardHourlyItemVo;
import com.ruoyi.system.domain.vo.DataBoardLocationItemVo;
import com.ruoyi.system.domain.vo.DataBoardOverviewVo;
import com.ruoyi.system.domain.vo.DataBoardPersonItemVo;
import com.ruoyi.system.domain.vo.DataBoardRecentSessionVo;
import com.ruoyi.system.domain.vo.DataBoardStrangerItemVo;

/**
 * 数据看板统计
 */
public interface DataBoardMapper
{
    DataBoardOverviewVo selectOverview(@Param("statDate") Date statDate, @Param("locationId") Long locationId);

    List<DataBoardHourlyItemVo> selectHourlyTrend(@Param("statDate") Date statDate, @Param("locationId") Long locationId);

    List<DataBoardLocationItemVo> selectByLocation(@Param("statDate") Date statDate, @Param("locationId") Long locationId);

    List<DataBoardRecentSessionVo> selectRecentSessions(@Param("statDate") Date statDate,
            @Param("locationId") Long locationId, @Param("limit") int limit);

    List<DataBoardPersonItemVo> selectPersonItems(@Param("statDate") Date statDate, @Param("locationId") Long locationId,
            @Param("limit") int limit);

    List<DataBoardStrangerItemVo> selectStrangerItems(@Param("statDate") Date statDate,
            @Param("locationId") Long locationId, @Param("limit") int limit);

    int updatePerson(@Param("personId") Long personId, @Param("displayName") String displayName,
            @Param("personKind") String personKind, @Param("tagsText") String tagsText, @Param("note") String note);

    int deletePerson(@Param("personId") Long personId);

    int updateSessionStatus(@Param("sessionId") Long sessionId, @Param("status") String status);

    int deleteSession(@Param("sessionId") Long sessionId);

    int deleteStrangerByTrackKey(@Param("trackKey") String trackKey);

    int clearStrangerPerson(@Param("trackKey") String trackKey);

    int bindTrackToPerson(@Param("trackKey") String trackKey, @Param("personId") Long personId);

    int insertPerson(@Param("displayName") String displayName, @Param("personKind") String personKind,
            @Param("tagsText") String tagsText, @Param("note") String note);

    Long selectLastPersonId();

    int insertFaceProfile(@Param("personId") Long personId, @Param("imageUrl") String imageUrl);

    int updateLocation(@Param("locationId") Long locationId, @Param("locationName") String locationName,
            @Param("isActive") Boolean isActive);
}
