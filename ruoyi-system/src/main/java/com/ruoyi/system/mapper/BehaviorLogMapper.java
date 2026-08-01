package com.ruoyi.system.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.BehaviorLogItemVo;

public interface BehaviorLogMapper
{
    List<BehaviorLogItemVo> selectBehaviorLogList(@Param("beginDate") LocalDate beginDate,
            @Param("endDate") LocalDate endDate, @Param("cameraId") Long cameraId,
            @Param("eventType") String eventType, @Param("displayName") String displayName,
            @Param("personType") String personType, @Param("personId") Long personId,
            @Param("beginTime") String beginTime, @Param("endTime") String endTime, @Param("limit") Integer limit);

    int insertBehaviorLog(BehaviorLogItemVo row);

    int countByUniqueKey(@Param("trackKey") String trackKey,
            @Param("eventType") String eventType,
            @Param("eventTime") java.util.Date eventTime);

    int updateBehaviorLogSnapshot(@Param("id") Long id, @Param("snapshotUrl") String snapshotUrl);

    int updateBehaviorLogPresence(@Param("id") Long id,
            @Param("personId") Long personId,
            @Param("sessionId") Long sessionId,
            @Param("faceMatchScore") Float faceMatchScore,
            @Param("bodyMatchScore") Float bodyMatchScore,
            @Param("qualityFlag") String qualityFlag);

    int updateBehaviorLogVideoBind(@Param("id") Long id,
            @Param("sceneGroupId") String sceneGroupId,
            @Param("clipId") Long clipId,
            @Param("analysisStatus") String analysisStatus);

    int updateBehaviorAnalysis(@Param("id") Long id, @Param("behaviorAnalysis") String behaviorAnalysis);

    int deleteById(@Param("id") Long id);
}
