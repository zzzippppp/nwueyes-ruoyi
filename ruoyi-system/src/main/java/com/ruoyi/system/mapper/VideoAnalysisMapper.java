package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.AiAnalysisResultVo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;

public interface VideoAnalysisMapper
{
    Long upsertVideoClip(PresenceVideoClipVo clip);

    int updateClipProviderResult(@Param("id") Long id,
            @Param("videoUrl") String videoUrl,
            @Param("status") String status,
            @Param("providerStatus") String providerStatus,
            @Param("providerTaskId") String providerTaskId,
            @Param("providerSourceUrl") String providerSourceUrl,
            @Param("providerErrorMessage") String providerErrorMessage,
            @Param("publicVideoUrl") String publicVideoUrl);

    int updateClipPublicVideoUrl(@Param("id") Long id,
            @Param("publicVideoUrl") String publicVideoUrl,
            @Param("status") String status);

    PresenceVideoClipVo selectClipById(@Param("id") Long id);

    PresenceVideoClipVo selectSceneClipByGroupId(@Param("sceneGroupId") String sceneGroupId);

    List<PresenceVideoClipVo> selectClipsByIds(@Param("ids") List<Long> ids);

    List<PresenceVideoClipVo> selectSceneClips(@Param("sceneGroupIds") List<String> sceneGroupIds);

    List<AiAnalysisResultVo> selectAnalysisByTargets(@Param("targetType") String targetType,
            @Param("targetIds") List<String> targetIds);

    int insertAnalysisPending(@Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("modelKey") String modelKey,
            @Param("modelName") String modelName);

    int updateAnalysisResult(@Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("modelKey") String modelKey,
            @Param("status") String status,
            @Param("summary") String summary,
            @Param("appearance") String appearance,
            @Param("behavior") String behavior,
            @Param("personCount") Integer personCount,
            @Param("rawJson") String rawJson,
            @Param("errorMessage") String errorMessage);

    int updateBehaviorLogClipByTrack(@Param("trackKey") String trackKey,
            @Param("cameraId") Long cameraId,
            @Param("sceneGroupId") String sceneGroupId,
            @Param("clipId") Long clipId,
            @Param("analysisStatus") String analysisStatus,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    int updateBehaviorLogSceneByRange(@Param("cameraId") Long cameraId,
            @Param("sceneGroupId") String sceneGroupId,
            @Param("clipId") Long clipId,
            @Param("analysisStatus") String analysisStatus,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    /** 事件入库后反向查找：同摄像头、时间落在片段窗内的场景视频 */
    PresenceVideoClipVo selectMatchingSceneClip(@Param("cameraId") Long cameraId,
            @Param("eventTime") Date eventTime);

    /** 兼容历史：按 track 反查个人会话片段 */
    PresenceVideoClipVo selectMatchingPersonClip(@Param("cameraId") Long cameraId,
            @Param("trackKey") String trackKey,
            @Param("eventTime") Date eventTime);
}
