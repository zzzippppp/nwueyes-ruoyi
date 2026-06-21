package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.bo.DataBoardSessionFilterBo;
import com.ruoyi.system.domain.vo.DataBoardAttendanceItemVo;
import com.ruoyi.system.domain.vo.DataBoardHourlyItemVo;
import com.ruoyi.system.domain.vo.DataBoardCameraItemVo;
import com.ruoyi.system.domain.vo.DataBoardOverviewVo;
import com.ruoyi.system.domain.vo.DataBoardPersonItemVo;
import com.ruoyi.system.domain.vo.DataBoardRecentSessionVo;
import com.ruoyi.system.domain.vo.DataBoardStrangerItemVo;

/**
 * 数据看板统计
 */
public interface DataBoardMapper
{
    DataBoardOverviewVo selectOverview(@Param("beginDate") Date beginDate, @Param("endDate") Date endDate,
            @Param("cameraId") Long cameraId, @Param("filter") DataBoardSessionFilterBo filter);

    List<DataBoardHourlyItemVo> selectHourlyTrend(@Param("beginDate") Date beginDate, @Param("endDate") Date endDate,
            @Param("cameraId") Long cameraId, @Param("filter") DataBoardSessionFilterBo filter);

    List<DataBoardCameraItemVo> selectByCamera(@Param("beginDate") Date beginDate, @Param("endDate") Date endDate,
            @Param("cameraId") Long cameraId, @Param("filter") DataBoardSessionFilterBo filter);

    List<DataBoardRecentSessionVo> selectRecentSessions(@Param("beginDate") Date beginDate,
            @Param("endDate") Date endDate, @Param("cameraId") Long cameraId, @Param("limit") int limit,
            @Param("filter") DataBoardSessionFilterBo filter);

    List<DataBoardAttendanceItemVo> selectAttendanceInfoList(@Param("beginDate") Date beginDate,
            @Param("endDate") Date endDate, @Param("cameraId") Long cameraId, @Param("limit") int limit,
            @Param("filter") DataBoardSessionFilterBo filter);

    List<DataBoardPersonItemVo> selectPersonItems(@Param("beginDate") Date beginDate, @Param("endDate") Date endDate,
            @Param("cameraId") Long cameraId, @Param("limit") int limit);

    List<DataBoardStrangerItemVo> selectStrangerItems(@Param("beginDate") Date beginDate,
            @Param("endDate") Date endDate, @Param("cameraId") Long cameraId, @Param("limit") int limit);

    int updatePerson(@Param("personId") Long personId, @Param("displayName") String displayName,
            @Param("personType") String personType, @Param("employeeNo") String employeeNo,
            @Param("note") String note);

    Long selectPersonByEmployeeNo(@Param("employeeNo") String employeeNo);

    int reassignSessionsPerson(@Param("fromPersonId") Long fromPersonId, @Param("toPersonId") Long toPersonId);

    int reassignBehaviorLogsPerson(@Param("fromPersonId") Long fromPersonId, @Param("toPersonId") Long toPersonId);

    int reassignDailyPerson(@Param("fromPersonId") Long fromPersonId, @Param("toPersonId") Long toPersonId);

    int reassignFaceProfilesPerson(@Param("fromPersonId") Long fromPersonId, @Param("toPersonId") Long toPersonId);

    int reassignBodyProfilesPerson(@Param("fromPersonId") Long fromPersonId, @Param("toPersonId") Long toPersonId);

    int deletePerson(@Param("personId") Long personId);

    int updateSessionStatus(@Param("sessionId") Long sessionId, @Param("status") String status);

    int deleteSession(@Param("sessionId") Long sessionId);

    int deleteBehaviorLogsByTrackKey(@Param("trackKey") String trackKey);

    int deleteStrangerByTrackKey(@Param("trackKey") String trackKey);

    int bindTrackToPerson(@Param("trackKey") String trackKey, @Param("personId") Long personId);

    Long selectPersonIdByTrackKey(@Param("trackKey") String trackKey);

    int insertPerson(@Param("displayName") String displayName, @Param("personType") String personType,
            @Param("employeeNo") String employeeNo, @Param("note") String note);

    Long selectLastPersonId();

    int insertFaceProfile(@Param("personId") Long personId, @Param("imageUrl") String imageUrl);

    int updateCamera(@Param("cameraId") Long cameraId, @Param("deviceName") String deviceName,
            @Param("isActive") Boolean isActive);

    Long selectRegisteredPersonCount();

    Long selectTodayKnownAttendanceCount(@Param("today") Date today, @Param("cameraId") Long cameraId);
}
