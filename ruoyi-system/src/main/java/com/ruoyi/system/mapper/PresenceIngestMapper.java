package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.PresenceOpenSessionVo;

public interface PresenceIngestMapper
{
    int existsLocation(@Param("cameraId") Long cameraId);

    /** 所有仍处于 open 的停留会话（兜底签退用） */
    List<PresenceOpenSessionVo> selectAllOpen();

    PresenceOpenSessionVo selectOpenByTrack(@Param("cameraId") Long cameraId, @Param("trackKey") String trackKey);

    PresenceOpenSessionVo selectOpenBySessionId(@Param("sessionId") Long sessionId);

    PresenceOpenSessionVo selectLatestOpenByPerson(@Param("cameraId") Long cameraId, @Param("personId") Long personId);

    PresenceOpenSessionVo selectAnyOpenByPerson(@Param("personId") Long personId);

    PresenceOpenSessionVo selectLatestOpenByLocation(@Param("cameraId") Long cameraId);

    Long insertOpenSession(@Param("cameraId") Long cameraId, @Param("personId") Long personId,
            @Param("trackKey") String trackKey, @Param("eventTime") Date eventTime,
            @Param("bestMatchScore") Float bestMatchScore,
            @Param("enterBodyEmbedding") String enterBodyEmbedding);

    int closeSession(@Param("sessionId") Long sessionId, @Param("eventTime") Date eventTime, @Param("personId") Long personId,
            @Param("bestMatchScore") Float bestMatchScore);

    int closeOpenSessionsByPerson(@Param("personId") Long personId, @Param("eventTime") Date eventTime);

    int deleteSessionsByPersonDate(@Param("personId") Long personId, @Param("attendanceDate") Date attendanceDate);

    int reopenSession(@Param("sessionId") Long sessionId);
}
