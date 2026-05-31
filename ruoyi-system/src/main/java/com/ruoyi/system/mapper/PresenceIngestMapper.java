package com.ruoyi.system.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.PresenceOpenSessionVo;

public interface PresenceIngestMapper
{
    int existsLocation(@Param("locationId") Long locationId);

    PresenceOpenSessionVo selectOpenByTrack(@Param("locationId") Long locationId, @Param("trackKey") String trackKey);

    PresenceOpenSessionVo selectOpenBySessionId(@Param("sessionId") Long sessionId);

    PresenceOpenSessionVo selectLatestOpenByPerson(@Param("locationId") Long locationId, @Param("personId") Long personId);

    PresenceOpenSessionVo selectLatestOpenByLocation(@Param("locationId") Long locationId);

    Long insertOpenSession(@Param("locationId") Long locationId, @Param("personId") Long personId,
            @Param("trackKey") String trackKey, @Param("eventTime") Date eventTime, @Param("faceImageUrl") String faceImageUrl,
            @Param("bodyImageUrl") String bodyImageUrl, @Param("bestMatchScore") Float bestMatchScore,
            @Param("enterFaceEmbedding") String enterFaceEmbedding,
            @Param("enterBodyEmbedding") String enterBodyEmbedding);

    int touchOpenSession(@Param("sessionId") Long sessionId, @Param("eventTime") Date eventTime, @Param("personId") Long personId,
            @Param("faceImageUrl") String faceImageUrl, @Param("bodyImageUrl") String bodyImageUrl,
            @Param("bestMatchScore") Float bestMatchScore,
            @Param("enterFaceEmbedding") String enterFaceEmbedding,
            @Param("enterBodyEmbedding") String enterBodyEmbedding);

    int closeSession(@Param("sessionId") Long sessionId, @Param("eventTime") Date eventTime, @Param("personId") Long personId,
            @Param("faceImageUrl") String faceImageUrl, @Param("bodyImageUrl") String bodyImageUrl,
            @Param("bestMatchScore") Float bestMatchScore);
}
