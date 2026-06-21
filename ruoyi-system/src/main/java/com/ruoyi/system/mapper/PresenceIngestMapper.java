package com.ruoyi.system.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.PresenceOpenSessionVo;

public interface PresenceIngestMapper
{
    int existsLocation(@Param("cameraId") Long cameraId);

    PresenceOpenSessionVo selectOpenByTrack(@Param("cameraId") Long cameraId, @Param("trackKey") String trackKey);

    PresenceOpenSessionVo selectOpenBySessionId(@Param("sessionId") Long sessionId);

    PresenceOpenSessionVo selectLatestOpenByPerson(@Param("cameraId") Long cameraId, @Param("personId") Long personId);

    PresenceOpenSessionVo selectLatestOpenByLocation(@Param("cameraId") Long cameraId);

    Long insertOpenSession(@Param("cameraId") Long cameraId, @Param("personId") Long personId,
            @Param("trackKey") String trackKey, @Param("eventTime") Date eventTime,
            @Param("bestMatchScore") Float bestMatchScore,
            @Param("enterBodyEmbedding") String enterBodyEmbedding);

    int closeSession(@Param("sessionId") Long sessionId, @Param("eventTime") Date eventTime, @Param("personId") Long personId,
            @Param("bestMatchScore") Float bestMatchScore);
}
