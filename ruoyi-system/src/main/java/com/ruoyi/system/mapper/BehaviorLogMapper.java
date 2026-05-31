package com.ruoyi.system.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.BehaviorLogItemVo;

public interface BehaviorLogMapper
{
    List<BehaviorLogItemVo> selectBehaviorLogList(@Param("statDate") LocalDate statDate,
            @Param("locationId") Long locationId,
            @Param("eventType") String eventType,
            @Param("limit") Integer limit);

    int insertBehaviorLog(BehaviorLogItemVo row);

    int countByUniqueKey(@Param("trackKey") String trackKey,
            @Param("eventType") String eventType,
            @Param("eventTime") java.util.Date eventTime,
            @Param("source") String source);

    int updateBehaviorLogImages(@Param("id") Long id,
            @Param("faceImageUrl") String faceImageUrl,
            @Param("bodyImageUrl") String bodyImageUrl);

    int updateBehaviorLogPresence(@Param("id") Long id,
            @Param("displayName") String displayName,
            @Param("personId") Long personId,
            @Param("sessionId") Long sessionId,
            @Param("personKind") String personKind,
            @Param("faceMatchScore") Float faceMatchScore,
            @Param("bodyMatchScore") Float bodyMatchScore,
            @Param("qualityFlag") String qualityFlag);
}
