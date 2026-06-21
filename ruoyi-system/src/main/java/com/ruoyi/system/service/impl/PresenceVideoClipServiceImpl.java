package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceVideoClipIngestBo;
import com.ruoyi.system.domain.vo.EzvizPlaybackClipVo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;
import com.ruoyi.system.mapper.VideoAnalysisMapper;
import com.ruoyi.system.service.IEzvizPlaybackService;
import com.ruoyi.system.service.IPresenceVideoClipService;
import com.ruoyi.system.service.IVideoAnalysisService;

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

        if (CLIP_PERSON_SESSION.equals(clip.getClipType()) && !StringUtils.isEmpty(clip.getTrackKey()))
        {
            videoAnalysisMapper.updateBehaviorLogClipByTrack(clip.getTrackKey(), clip.getCameraId(),
                    clip.getSceneGroupId(), clipId, "pending", clip.getStartTime(), clip.getEndTime());
        }
        if (CLIP_SCENE_GROUP.equals(clip.getClipType()) && !StringUtils.isEmpty(clip.getSceneGroupId()))
        {
            videoAnalysisMapper.updateBehaviorLogSceneByRange(clip.getCameraId(), clip.getSceneGroupId(),
                    "pending", clip.getStartTime(), clip.getEndTime());
        }

        PresenceVideoClipVo persisted = videoAnalysisMapper.selectClipById(clipId);
        if (StringUtils.isNotEmpty(bo.getDeviceSerial()))
        {
            executeAfterCommit(() -> resolvePlaybackAndUpdate(clipId, bo, startTime, endTime));
        }
        else if (ingestProperties.getAnalysis() != null && ingestProperties.getAnalysis().isAutoRun())
        {
            executeAfterCommit(() -> videoAnalysisService.submitClipAnalysis(persisted));
        }
        return persisted;
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
            if (ingestProperties.getAnalysis() != null && ingestProperties.getAnalysis().isAutoRun())
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
        if (StringUtils.isNotEmpty(bo.getDeviceSerial()))
        {
            return "ezviz_task_processing";
        }
        if (StringUtils.isNotEmpty(videoUrl))
        {
            return StringUtils.nvl(bo.getStatus(), "ready");
        }
        return "pending_playback";
    }

    private String resolveInitialProviderStatus(PresenceVideoClipIngestBo bo, String videoUrl)
    {
        if (StringUtils.isNotEmpty(bo.getDeviceSerial()))
        {
            return "ezviz_task_processing";
        }
        if (StringUtils.isNotEmpty(videoUrl))
        {
            return "fallback_local_recorded";
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
