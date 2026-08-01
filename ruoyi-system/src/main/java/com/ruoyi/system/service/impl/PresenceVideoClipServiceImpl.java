package com.ruoyi.system.service.impl;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceReplayStartBo;
import com.ruoyi.system.domain.bo.PresenceVideoClipIngestBo;
import com.ruoyi.system.domain.vo.EzvizPlaybackClipVo;
import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;
import com.ruoyi.system.mapper.VideoAnalysisMapper;
import com.ruoyi.system.service.IEzvizPlaybackService;
import com.ruoyi.system.service.IPresenceReplayService;
import com.ruoyi.system.service.IPresenceVideoClipService;
import com.ruoyi.system.service.IVideoAnalysisService;
import com.ruoyi.system.storage.PresenceStoragePaths;

@Service
public class PresenceVideoClipServiceImpl implements IPresenceVideoClipService
{
    private static final Logger log = LoggerFactory.getLogger(PresenceVideoClipServiceImpl.class);

    private static final ZoneId STAT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String CLIP_PERSON_SESSION = "person_session";
    private static final String CLIP_SCENE_GROUP = "scene_group";

    @Autowired
    private VideoAnalysisMapper videoAnalysisMapper;

    @Autowired
    private IVideoAnalysisService videoAnalysisService;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private IEzvizPlaybackService ezvizPlaybackService;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Autowired
    @Lazy
    private IPresenceReplayService presenceReplayService;

    @Autowired
    @Qualifier("presenceIngestExecutor")
    private ThreadPoolTaskExecutor presenceIngestExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresenceVideoClipVo ingestClip(PresenceVideoClipIngestBo bo)
    {
        validate(bo);
        Date startTime = parseTime(bo.getStartTime());
        Date endTime = parseTime(bo.getEndTime());
        PresenceVideoClipVo clip = new PresenceVideoClipVo();
        clip.setClipKey(StringUtils.nvl(bo.getClipKey(), buildClipKey(bo)));
        clip.setClipType(normalizeClipType(bo.getClipType()));
        clip.setSessionId(bo.getSessionId());
        clip.setSceneGroupId(bo.getSceneGroupId());
        clip.setCameraId(bo.getCameraId());
        clip.setTrackKey(bo.getTrackKey());
        clip.setStartTime(startTime);
        clip.setEndTime(endTime);
        clip.setPreRollSec(bo.getPreRollSec() == null ? ingestProperties.getClip().getPreRollSec() : bo.getPreRollSec());
        clip.setPostRollSec(bo.getPostRollSec() == null ? ingestProperties.getClip().getPostRollSec() : bo.getPostRollSec());
        clip.setVideoUrl(StringUtils.nvl(bo.getVideoUrl(), ""));
        clip.setStatus(resolveInitialStatus(bo, clip.getVideoUrl()));
        clip.setProviderStatus(resolveInitialProviderStatus(bo, clip.getVideoUrl()));
        clip.setProviderSourceUrl(StringUtils.nvl(bo.getVideoUrl(), ""));

        Long clipId = videoAnalysisMapper.upsertVideoClip(clip);
        clip.setId(clipId);

        try
        {
            if (CLIP_PERSON_SESSION.equals(clip.getClipType()) && !StringUtils.isEmpty(clip.getTrackKey()))
            {
                videoAnalysisMapper.updateBehaviorLogClipByTrack(clip.getTrackKey(), clip.getCameraId(),
                        clip.getSceneGroupId(), clipId, "pending", clip.getStartTime(), clip.getEndTime());
            }
            if (CLIP_SCENE_GROUP.equals(clip.getClipType()) && !StringUtils.isEmpty(clip.getSceneGroupId()))
            {
                videoAnalysisMapper.updateBehaviorLogSceneByRange(clip.getCameraId(), clip.getSceneGroupId(),
                        clipId, "pending", clip.getStartTime(), clip.getEndTime());
            }
        }
        catch (Exception ex)
        {
            // 片段本身已落库；关联行为日志失败不应整单回滚（历史上曾因 source 字段不存在导致录像“有文件无库”）
            log.warn("link behavior_logs for clipId={} failed: {}", clipId, ex.getMessage());
        }

        PresenceVideoClipVo persisted = videoAnalysisMapper.selectClipById(clipId);
        boolean hasLocalVideo = isLocalClipUrl(clip.getVideoUrl());
        if (!hasLocalVideo && StringUtils.isNotEmpty(bo.getDeviceSerial()))
        {
            executeAfterCommit(() -> resolvePlaybackAndUpdate(clipId, bo, startTime, endTime));
        }
        else
        {
            if (shouldAutoAnalyzeYolo(persisted))
            {
                executeAfterCommit(() -> enqueueYoloAnalyze(persisted));
            }
            else if (ingestProperties.getAnalysis() != null && ingestProperties.getAnalysis().isAutoRun())
            {
                executeAfterCommit(() -> videoAnalysisService.submitClipAnalysis(persisted));
            }
        }
        return persisted;
    }

    private boolean shouldAutoAnalyzeYolo(PresenceVideoClipVo clip)
    {
        if (clip == null || ingestProperties.getClip() == null || !ingestProperties.getClip().isAutoAnalyzeYolo())
        {
            return false;
        }
        if (!CLIP_SCENE_GROUP.equals(clip.getClipType()))
        {
            return false;
        }
        return isLocalClipUrl(clip.getVideoUrl());
    }

    private void enqueueYoloAnalyze(PresenceVideoClipVo clip)
    {
        try
        {
            Optional<Path> localFile = storagePaths.resolveClipUrlToFile(clip.getVideoUrl());
            if (localFile.isEmpty())
            {
                log.warn("auto YOLO analyze skipped: clip file missing clipId={} url={}",
                        clip.getId(), clip.getVideoUrl());
                return;
            }
            PresenceReplayStartBo bo = new PresenceReplayStartBo();
            bo.setVideoPath(localFile.get().toAbsolutePath().normalize().toString());
            bo.setCameraId(clip.getCameraId());
            bo.setClipId(clip.getId());
            bo.setSceneGroupId(clip.getSceneGroupId());
            bo.setVideoBaseTime(clip.getStartTime());
            bo.setAutoImportBehaviorLogs(ingestProperties.getClip().isAutoImportBehaviorLogs());
            PresenceReplayTaskVo task = presenceReplayService.startAnalyze(bo);
            log.info("auto YOLO analyze queued clipId={} scene={} taskId={}",
                    clip.getId(), clip.getSceneGroupId(), task == null ? "-" : task.getTaskId());
        }
        catch (Exception ex)
        {
            log.warn("auto YOLO analyze failed clipId={}: {}", clip.getId(), ex.getMessage());
        }
    }

    private boolean isLocalClipUrl(String videoUrl)
    {
        if (StringUtils.isEmpty(videoUrl))
        {
            return false;
        }
        String path = videoUrl.trim().replace("\\", "/");
        return path.startsWith("/dashboard/storage/file/clip/");
    }

    private void validate(PresenceVideoClipIngestBo bo)
    {
        if (bo == null)
        {
            throw new IllegalArgumentException("clip payload cannot be empty");
        }
        if (bo.getCameraId() == null)
        {
            throw new IllegalArgumentException("cameraId cannot be empty");
        }
        if (StringUtils.isEmpty(bo.getVideoUrl()) && StringUtils.isEmpty(bo.getDeviceSerial()))
        {
            throw new IllegalArgumentException("videoUrl/deviceSerial cannot both be empty");
        }
        if (StringUtils.isEmpty(bo.getStartTime()) || StringUtils.isEmpty(bo.getEndTime()))
        {
            throw new IllegalArgumentException("startTime/endTime cannot be empty");
        }
    }

    private void resolvePlaybackAndUpdate(Long clipId, PresenceVideoClipIngestBo bo, Date startTime, Date endTime)
    {
        try
        {
            // 默认直接使用萤石公网回放地址进行模型分析；仅在明确要求时才下载本地副本。
            boolean preferLocal = Boolean.TRUE.equals(bo.getPreferLocal());
            EzvizPlaybackClipVo playback = ezvizPlaybackService.resolvePlaybackClip(bo.getDeviceSerial(), bo.getChannelNo(),
                    bo.getValidCode(), startTime, endTime, preferLocal);
            String videoUrl = resolveVideoUrl(bo, playback);
            String status = resolveStatus(bo, playback, videoUrl);
            videoAnalysisMapper.updateClipProviderResult(clipId, videoUrl, status,
                    playback == null ? null : playback.getProviderStatus(),
                    playback == null ? null : playback.getProviderTaskId(),
                    playback == null ? null : playback.getProviderSourceUrl(),
                    playback == null ? null : playback.getErrorMessage(),
                    resolvePublicVideoUrl(playback, videoUrl));
            PresenceVideoClipVo refreshed = videoAnalysisMapper.selectClipById(clipId);
            if (shouldAutoAnalyzeYolo(refreshed))
            {
                enqueueYoloAnalyze(refreshed);
            }
            else if (ingestProperties.getAnalysis() != null && ingestProperties.getAnalysis().isAutoRun())
            {
                videoAnalysisService.submitClipAnalysis(refreshed);
            }
        }
        catch (Exception ex)
        {
            log.warn("ezviz playback resolve failed clipId={}: {}", clipId, ex.getMessage());
            videoAnalysisMapper.updateClipProviderResult(clipId, "", "failed",
                    "failed", null, null, ex.getMessage(), null);
        }
    }

    private void executeAfterCommit(Runnable task)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    presenceIngestExecutor.execute(task);
                }
            });
            return;
        }
        presenceIngestExecutor.execute(task);
    }

    private String resolveVideoUrl(PresenceVideoClipIngestBo bo, EzvizPlaybackClipVo playback)
    {
        if (playback != null && StringUtils.isNotEmpty(playback.getDownloadedLocalUrl()))
        {
            return playback.getDownloadedLocalUrl();
        }
        if (playback != null && StringUtils.isNotEmpty(playback.getPlaybackUrl()))
        {
            return playback.getPlaybackUrl();
        }
        return StringUtils.nvl(bo.getVideoUrl(), "");
    }

    private String resolvePublicVideoUrl(EzvizPlaybackClipVo playback, String videoUrl)
    {
        if (playback == null || StringUtils.isEmpty(videoUrl))
        {
            return "";
        }
        String providerStatus = StringUtils.nvl(playback.getProviderStatus(), "");
        if ("ezviz_playback_only".equals(providerStatus) && isHttpUrl(videoUrl))
        {
            return videoUrl;
        }
        return "";
    }

    private boolean isHttpUrl(String url)
    {
        if (StringUtils.isEmpty(url))
        {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String resolveStatus(PresenceVideoClipIngestBo bo, EzvizPlaybackClipVo playback, String videoUrl)
    {
        if (playback != null && StringUtils.isNotEmpty(playback.getProviderStatus())
                && !"failed".equals(playback.getProviderStatus()))
        {
            return playback.getProviderStatus();
        }
        if (StringUtils.isNotEmpty(videoUrl))
        {
            return StringUtils.nvl(bo.getStatus(), "ready");
        }
        return "failed";
    }

    private String resolveInitialStatus(PresenceVideoClipIngestBo bo, String videoUrl)
    {
        if (StringUtils.isNotEmpty(videoUrl))
        {
            return StringUtils.nvl(bo.getStatus(), "ready");
        }
        if (StringUtils.isNotEmpty(bo.getDeviceSerial()))
        {
            return "ezviz_task_processing";
        }
        return "pending_playback";
    }

    private String resolveInitialProviderStatus(PresenceVideoClipIngestBo bo, String videoUrl)
    {
        if (StringUtils.isNotEmpty(videoUrl))
        {
            return "local_ffmpeg_recorded";
        }
        if (StringUtils.isNotEmpty(bo.getDeviceSerial()))
        {
            return "ezviz_task_processing";
        }
        return "failed";
    }

    private String normalizeClipType(String raw)
    {
        String value = StringUtils.nvl(raw, CLIP_PERSON_SESSION).toLowerCase();
        if (CLIP_PERSON_SESSION.equals(value) || CLIP_SCENE_GROUP.equals(value))
        {
            return value;
        }
        throw new IllegalArgumentException("unsupported clipType: " + raw);
    }

    private String buildClipKey(PresenceVideoClipIngestBo bo)
    {
        return StringUtils.nvl(bo.getClipType(), CLIP_PERSON_SESSION) + ":"
                + StringUtils.nvl(bo.getSceneGroupId(), "scene") + ":"
                + StringUtils.nvl(bo.getTrackKey(), "group") + ":"
                + StringUtils.nvl(bo.getStartTime(), "");
    }

    private Date parseTime(String raw)
    {
        try
        {
            return Date.from(OffsetDateTime.parse(raw).toInstant());
        }
        catch (DateTimeParseException ignored)
        {
        }
        try
        {
            return Date.from(LocalDateTime.parse(raw).atZone(STAT_ZONE).toInstant());
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalArgumentException("invalid time: " + raw);
        }
    }
}
