package com.ruoyi.system.service.impl;



import java.io.File;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.time.LocalDate;

import java.time.LocalDateTime;

import java.time.ZoneId;

import java.time.format.DateTimeFormatter;

import java.util.Date;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.HashSet;

import java.util.Iterator;

import java.util.List;

import java.util.Map;

import java.util.Optional;

import java.util.Set;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.annotation.Lazy;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.ruoyi.common.utils.StringUtils;

import com.ruoyi.system.config.PresenceIngestProperties;

import com.ruoyi.system.domain.bo.BehaviorLogImportFromVideoBo;

import com.ruoyi.system.domain.bo.PresenceEventIngestBo;

import com.ruoyi.system.domain.vo.AnalyzeEmbedResultVo;

import com.ruoyi.system.domain.vo.BehaviorLogImportResultVo;

import com.ruoyi.system.domain.vo.BehaviorLogItemVo;

import com.ruoyi.system.domain.vo.AiAnalysisResultVo;

import com.ruoyi.system.domain.vo.CameraConfigVo;

import com.ruoyi.system.domain.vo.CaptureTrackEmbedVo;

import com.ruoyi.system.domain.vo.EmbeddingVectorVo;

import com.ruoyi.system.domain.vo.PresenceVideoClipVo;

import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;

import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;

import com.ruoyi.system.mapper.BehaviorLogMapper;

import com.ruoyi.system.mapper.VideoAnalysisMapper;

import com.ruoyi.system.service.IBehaviorLogService;

import com.ruoyi.system.service.ICameraService;

import com.ruoyi.system.service.IPresenceEmbedService;

import com.ruoyi.system.service.IPresenceReplayService;

import com.ruoyi.system.service.IPresenceTrackService;

import com.ruoyi.system.support.StatDateRange;

import com.ruoyi.system.support.VideoEventImportFilter;

import com.ruoyi.system.storage.PresenceStoragePaths;



@Service

public class BehaviorLogServiceImpl implements IBehaviorLogService

{

    private static final Logger log = LoggerFactory.getLogger(BehaviorLogServiceImpl.class);

    private static final Pattern UPLOAD_TIME_PATTERN = Pattern.compile("_(\\d{14})");

    private static final DateTimeFormatter UPLOAD_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String SOURCE_VIDEO_TEST = "video_test";

    private static final String SOURCE_LIVE = "live";

    private static final String TRACK_PREFIX = "yolo_";



    private final ObjectMapper objectMapper = new ObjectMapper();



    @Autowired

    private BehaviorLogMapper behaviorLogMapper;

    @Autowired

    private VideoAnalysisMapper videoAnalysisMapper;



    @Autowired

    @Lazy

    private IPresenceReplayService presenceReplayService;



    @Autowired

    private PresenceIngestProperties ingestProperties;



    @Autowired

    private PresenceStoragePaths storagePaths;



    @Autowired

    private IPresenceTrackService presenceTrackService;



    @Autowired

    private IPresenceEmbedService presenceEmbedService;

    @Autowired

    private ICameraService cameraService;



    @Override

    public List<BehaviorLogItemVo> listBehaviorLogs(LocalDate statDate, LocalDate beginDate, LocalDate endDate,
            Long cameraId, String eventType, String displayName, String personType, Long personId,
            String beginTime, String endTime, Integer limit)
    {
        StatDateRange range = StatDateRange.resolve(statDate, beginDate, endDate);
        String[] normalizedTimes = normalizeTimeRange(beginTime, endTime);
        if (normalizedTimes[0] != null && normalizedTimes[1] != null)
        {
            range = StatDateRange.ofSingleDay(range.getBeginDate());
        }
        List<BehaviorLogItemVo> rows = behaviorLogMapper.selectBehaviorLogList(range.getBeginDate(), range.getEndDate(),
                cameraId, eventType, displayName, personType, personId,
                normalizedTimes[0], normalizedTimes[1], limit == null ? 500 : limit);

        enrichVideoAnalysis(rows);

        return rows;

    }

    private String[] normalizeTimeRange(String beginTime, String endTime)
    {
        if (StringUtils.isEmpty(beginTime) || StringUtils.isEmpty(endTime))
        {
            return new String[] { null, null };
        }
        int beginMinutes = parseTimeToMinutes(beginTime, 0);
        int endMinutes = parseTimeToMinutes(endTime, 23 * 60 + 59);
        if (beginMinutes > endMinutes)
        {
            int tmp = beginMinutes;
            beginMinutes = endMinutes;
            endMinutes = tmp;
        }
        return new String[] { formatMinutesToTime(beginMinutes), formatMinutesToTime(endMinutes) };
    }

    private int parseTimeToMinutes(String timeText, int fallback)
    {
        if (StringUtils.isEmpty(timeText))
        {
            return fallback;
        }
        String[] parts = timeText.trim().split(":");
        if (parts.length < 2)
        {
            return fallback;
        }
        try
        {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            hour = Math.max(0, Math.min(23, hour));
            minute = Math.max(0, Math.min(59, minute));
            return hour * 60 + minute;
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private String formatMinutesToTime(int minutes)
    {
        int hour = Math.max(0, Math.min(23, minutes / 60));
        int minute = Math.max(0, Math.min(59, minutes % 60));
        return String.format("%02d:%02d", hour, minute);
    }

    private void enrichVideoAnalysis(List<BehaviorLogItemVo> rows)

    {

        if (rows == null || rows.isEmpty())

        {

            return;

        }

        Set<Long> clipIds = new HashSet<>();

        Set<String> sceneGroupIds = new HashSet<>();

        for (BehaviorLogItemVo row : rows)

        {

            if (row.getClipId() != null)

            {

                clipIds.add(row.getClipId());

            }

            if (!StringUtils.isEmpty(row.getSceneGroupId()))

            {

                sceneGroupIds.add(row.getSceneGroupId());

            }

        }

        Map<Long, PresenceVideoClipVo> clipMap = new HashMap<>();

        if (!clipIds.isEmpty())

        {

            for (PresenceVideoClipVo clip : videoAnalysisMapper.selectClipsByIds(new ArrayList<>(clipIds)))

            {

                clipMap.put(clip.getId(), clip);

            }

        }

        Map<String, PresenceVideoClipVo> sceneClipMap = new HashMap<>();

        if (!sceneGroupIds.isEmpty())

        {

            for (PresenceVideoClipVo clip : videoAnalysisMapper.selectSceneClips(new ArrayList<>(sceneGroupIds)))

            {

                sceneClipMap.put(clip.getSceneGroupId(), clip);

            }

        }

        Map<String, List<AiAnalysisResultVo>> clipAnalysis = loadAnalysis("clip", clipIds);

        Map<String, List<AiAnalysisResultVo>> sceneAnalysis = loadAnalysis("scene_group", sceneGroupIds);

        for (BehaviorLogItemVo row : rows)

        {

            if (row.getClipId() != null)

            {

                PresenceVideoClipVo clip = clipMap.get(row.getClipId());

                row.setClip(clip);

                List<AiAnalysisResultVo> results = clipAnalysis.get(String.valueOf(row.getClipId()));

                row.setAnalysisResults(results);

            }

            if (!StringUtils.isEmpty(row.getSceneGroupId()))

            {

                PresenceVideoClipVo sceneClip = sceneClipMap.get(row.getSceneGroupId());

                row.setSceneClip(sceneClip);

                row.setSceneAnalysisResults(sceneAnalysis.get(row.getSceneGroupId()));

            }

            row.setAnalysisStatus(resolveAnalysisStatus(row));

        }

    }

    private Map<String, List<AiAnalysisResultVo>> loadAnalysis(String targetType, Set<?> rawIds)

    {

        Map<String, List<AiAnalysisResultVo>> map = new HashMap<>();

        if (rawIds == null || rawIds.isEmpty())

        {

            return map;

        }

        List<String> targetIds = new ArrayList<>();

        for (Object id : rawIds)

        {

            if (id != null)

            {

                targetIds.add(String.valueOf(id));

            }

        }

        if (targetIds.isEmpty())

        {

            return map;

        }

        for (AiAnalysisResultVo result : videoAnalysisMapper.selectAnalysisByTargets(targetType, targetIds))

        {

            map.computeIfAbsent(result.getTargetId(), k -> new ArrayList<>()).add(result);

        }

        return map;

    }

    private String resolveAnalysisStatus(BehaviorLogItemVo row)

    {

        List<AiAnalysisResultVo> personal = row.getAnalysisResults();

        List<AiAnalysisResultVo> scene = row.getSceneAnalysisResults();

        boolean hasPersonal = personal != null && !personal.isEmpty();

        boolean hasScene = scene != null && !scene.isEmpty();

        if (!hasPersonal && !hasScene)

        {

            // 导入时任务级 AI 已写入 behavior_analysis，应视为已完成，避免一直显示「分析中」

            if (!StringUtils.isEmpty(row.getBehaviorAnalysis()))

            {

                return "success";

            }

            return StringUtils.nvl(row.getAnalysisStatus(), row.getClipId() == null ? "none" : "pending");

        }

        if (hasStatus(personal, "success") || hasStatus(scene, "success"))

        {

            if (hasStatus(personal, "failed") || hasStatus(scene, "failed"))

            {

                return "partial";

            }

            return "success";

        }

        if (hasStatus(personal, "pending") || hasStatus(scene, "pending"))

        {

            // 正式分析结果尚未落库，但导入摘要已有 → 仍按完成展示

            if (!StringUtils.isEmpty(row.getBehaviorAnalysis()))

            {

                return "success";

            }

            return "pending";

        }

        if (hasStatus(personal, "skipped") || hasStatus(scene, "skipped"))

        {

            return "skipped";

        }

        if (!StringUtils.isEmpty(row.getBehaviorAnalysis()))

        {

            return "success";

        }

        return "failed";

    }

    private boolean hasStatus(List<AiAnalysisResultVo> results, String status)

    {

        if (results == null)

        {

            return false;

        }

        for (AiAnalysisResultVo result : results)

        {

            if (status.equals(result.getStatus()))

            {

                return true;

            }

        }

        return false;

    }

    @Override
    public boolean deleteBehaviorLog(Long id)
    {
        return id != null && behaviorLogMapper.deleteById(id) > 0;
    }



    @Override
    public void recordLiveIngest(PresenceEventIngestBo bo, Date eventTime, PresenceTrackProcessResultVo processed)
    {
        if (bo == null || processed == null || eventTime == null)
        {
            return;
        }
        if (processed.isSkippedDuplicateEnter())
        {
            return;
        }
        String eventType = StringUtils.nvl(bo.getEventType(), "").toLowerCase();
        if (!"enter".equals(eventType) && !"exit".equals(eventType) && !"pass".equals(eventType))
        {
            return;
        }
        String trackKey = StringUtils.nvl(bo.getTrackKey(), "");
        if (StringUtils.isEmpty(trackKey) || bo.getCameraId() == null)
        {
            return;
        }
        if (behaviorLogMapper.countByUniqueKey(trackKey, eventType, eventTime) > 0)
        {
            return;
        }

        String faceImageUrl = StringUtils.nvl(bo.getFaceImageUrl(), "");
        String bodyImageUrl = StringUtils.nvl(bo.getBodyImageUrl(), "");
        String snapshotUrl = StringUtils.nvl(bo.getSnapshotUrl(), "");
        String qualityFlag = StringUtils.nvl(bo.getQualityFlag(),
                resolveQualityFlag(faceImageUrl, bodyImageUrl));

        BehaviorLogItemVo row = new BehaviorLogItemVo();
        row.setEventType(eventType);
        row.setEventTime(eventTime);
        row.setFaceImageUrl(faceImageUrl);
        row.setBodyImageUrl(bodyImageUrl);
        row.setSnapshotUrl(snapshotUrl);
        row.setSnapshotBbox(readSnapshotBboxJson(bo.getSnapshotBbox()));
        row.setCameraId(bo.getCameraId());
        row.setPersonId(processed.getPersonId());
        row.setTrackKey(trackKey);
        row.setSessionId(processed.getSessionId());
        row.setBehaviorAnalysis(bo.getBehaviorAnalysis());
        behaviorLogMapper.insertBehaviorLog(row);

        if (row.getId() != null)
        {
            behaviorLogMapper.updateBehaviorLogPresence(row.getId(), processed.getPersonId(),
                    processed.getSessionId(), processed.getFaceMatchScore(),
                    processed.getBodyMatchScore(), qualityFlag);
            promoteSnapshot(row, eventTime.toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
            // 录像常先于追脸结束入库：事件落库后再反向挂上场景视频
            bindExistingClipToBehaviorLog(row.getId(), bo.getCameraId(), trackKey, eventTime);
        }
    }

    /**
     * 行为日志入库后，按摄像头 + 时间窗回挂已存在的场景录像（兼容历史个人会话片段）。
     */
    private void bindExistingClipToBehaviorLog(Long logId, Long cameraId, String trackKey, Date eventTime)
    {
        if (logId == null || cameraId == null || eventTime == null)
        {
            return;
        }
        try
        {
            PresenceVideoClipVo scene = videoAnalysisMapper.selectMatchingSceneClip(cameraId, eventTime);
            if (scene != null)
            {
                behaviorLogMapper.updateBehaviorLogVideoBind(logId, scene.getSceneGroupId(), scene.getId(), "pending");
                return;
            }
            if (!StringUtils.isEmpty(trackKey))
            {
                PresenceVideoClipVo person = videoAnalysisMapper.selectMatchingPersonClip(cameraId, trackKey, eventTime);
                if (person != null)
                {
                    behaviorLogMapper.updateBehaviorLogVideoBind(logId, person.getSceneGroupId(), person.getId(),
                            "pending");
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("bind clip to behavior log failed logId={}: {}", logId, ex.getMessage());
        }
    }

    private void promoteSnapshot(BehaviorLogItemVo row, LocalDate eventDate)
    {
        if (row.getId() == null || StringUtils.isEmpty(row.getSnapshotUrl()))
        {
            return;
        }
        try
        {
            java.util.Optional<Path> sourceOpt = storagePaths.resolveImageUrlToFile(row.getSnapshotUrl());
            if (sourceOpt.isEmpty())
            {
                return;
            }
            String url = storagePaths.promoteToSnapshot(sourceOpt.get(), eventDate, row.getId());
            if (!StringUtils.isEmpty(url))
            {
                behaviorLogMapper.updateBehaviorLogSnapshot(row.getId(), url);
                row.setSnapshotUrl(url);
            }
        }
        catch (Exception ex)
        {
            log.warn("snapshot promote failed logId={}: {}", row.getId(), ex.getMessage());
        }
    }



    private String normalizeBehaviorPersonKind(String personKind)

    {

        if (StringUtils.isEmpty(personKind))

        {

            return "unknown";

        }

        String normalized = personKind.toLowerCase();

        if ("known".equals(normalized) || "stranger".equals(normalized) || "unknown".equals(normalized))

        {

            return normalized;

        }

        return "unknown";

    }



    private String resolveQualityFlag(String faceImageUrl, String bodyImageUrl)

    {

        // 体态已废除：质量只看是否有人脸图

        if (StringUtils.isEmpty(faceImageUrl))

        {

            return "missing";

        }

        return "normal";

    }



    @Override

    public BehaviorLogImportResultVo importFromVideoAnalyze(BehaviorLogImportFromVideoBo bo)

    {

        if (StringUtils.isEmpty(bo.getTaskId()))

        {

            throw new IllegalArgumentException("taskId 不能为空");

        }

        PresenceReplayTaskVo task = presenceReplayService.getTask(bo.getTaskId());

        if (task == null)

        {

            throw new IllegalArgumentException("分析任务不存在: " + bo.getTaskId());

        }

        if (!"success".equals(task.getStatus()))

        {

            throw new IllegalStateException("分析任务未成功完成，无法写入行为日志");

        }

        if (StringUtils.isEmpty(task.getResultJson()))

        {

            throw new IllegalStateException("分析结果为空");

        }



        Long cameraId = bo.getCameraId();
        if (cameraId == null && task.getCameraId() != null)
        {
            cameraId = task.getCameraId();
        }

        JsonNode root;

        try

        {

            root = objectMapper.readTree(task.getResultJson());

        }

        catch (Exception ex)

        {

            throw new IllegalStateException("解析分析结果失败: " + ex.getMessage());

        }

        if (cameraId == null && root.has("cameraId") && !root.get("cameraId").isNull())
        {
            cameraId = root.get("cameraId").asLong();
        }
        if (cameraId == null)
        {
            cameraId = ingestProperties.getDefaultCameraId();
        }



        // 导入前尽量同步拿到任务级 AI（自动流水线已跑过则直接复用）
        try
        {
            presenceReplayService.ensureAiAnalysisForTask(bo.getTaskId());
            PresenceReplayTaskVo refreshed = presenceReplayService.getTask(bo.getTaskId());
            if (refreshed != null && !StringUtils.isEmpty(refreshed.getResultJson()))
            {
                root = objectMapper.readTree(refreshed.getResultJson());
            }
        }
        catch (Exception ex)
        {
            log.warn("导入前 AI 分析不可用 taskId={}: {}", bo.getTaskId(), ex.getMessage());
        }

        String aiSummary = VideoEventImportFilter.readAiSummary(root);
        Integer aiPersonCount = VideoEventImportFilter.readAiPersonCount(root);

        String sourceVideo = root.path("sourceVideo").asText("");

        JsonNode eventsNode = root.path("events");

        if (!eventsNode.isArray() || eventsNode.isEmpty())

        {

            if (bo.isAllowEmptyEvents())
            {
                BehaviorLogImportResultVo empty = new BehaviorLogImportResultVo();
                empty.setInsertedCount(0);
                empty.setSkippedCount(0);
                empty.setMessage("分析结果中没有过线事件，已跳过导入");
                return empty;
            }

            throw new IllegalStateException("分析结果中没有过线事件");

        }



        Map<Integer, SnapshotUrls> trackSnapshots = loadTrackSnapshots(bo.getTaskId());

        if (trackSnapshots.isEmpty())

        {

            trackSnapshots = loadCaptureTracksFromResult(root);

        }

        LocalDateTime videoBaseTime;
        if (bo.getVideoBaseTime() != null)
        {
            videoBaseTime = LocalDateTime.ofInstant(bo.getVideoBaseTime().toInstant(), ZoneId.systemDefault());
        }
        else
        {
            videoBaseTime = resolveVideoBaseTime(sourceVideo, task.getStartedAt());
        }

        // 门外摄像头：不看门线，逐脸与库中在场者比对，命中即记离场（不走 enter/exit/pass 事件流）
        String cameraRole = resolveCameraRole(cameraId);
        if ("exterior".equals(cameraRole))
        {
            return importExteriorExit(bo, cameraId, root, videoBaseTime, aiSummary);
        }

        List<JsonNode> rawEvents = new ArrayList<>();
        for (Iterator<JsonNode> it = eventsNode.elements(); it.hasNext();)
        {
            rawEvents.add(it.next());
        }

        Map<Integer, List<Double>> faceEmb = new HashMap<>();
        try
        {
            // 只做人脸：体态向量已废除
            AnalyzeEmbedResultVo embedResult = presenceEmbedService.embedAnalyzeCaptures(bo.getTaskId(), false);
            if (embedResult != null && embedResult.getTracks() != null)
            {
                for (CaptureTrackEmbedVo row : embedResult.getTracks())
                {
                    if (row == null || row.getTrackId() == null)
                    {
                        continue;
                    }
                    EmbeddingVectorVo fe = row.getFaceEmbedding();
                    if (fe != null && Boolean.TRUE.equals(fe.getOk()) && fe.getEmbedding() != null)
                    {
                        faceEmb.put(row.getTrackId(), fe.getEmbedding());
                    }
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("导入前向量抽取失败，将仅按 trackId 合并 taskId={}: {}", bo.getTaskId(), ex.getMessage());
        }

        double faceTh = ingestProperties.getFaceMatchThreshold() == null ? 0.35
                : ingestProperties.getFaceMatchThreshold();
        VideoEventImportFilter.FilterResult filtered = VideoEventImportFilter.filter(
                rawEvents, faceEmb, faceTh, aiPersonCount);
        log.info("视频导入事件过滤 taskId={} raw={} kept={} droppedPass={} personGroups={} aiPersonCount={}",
                bo.getTaskId(), filtered.getRawCount(), filtered.getEvents().size(),
                filtered.getDroppedPassCount(), filtered.getPersonGroupCount(), aiPersonCount);

        int inserted = 0;

        int skipped = 0;

        int sessionSkipped = 0;

        int duplicateEnterSkipped = 0;

        for (JsonNode event : filtered.getEvents())

        {

            if (event == null || event.isNull())
            {
                skipped++;
                continue;
            }

            String eventType = event.path("eventType").asText("");

            if (!"enter".equals(eventType) && !"exit".equals(eventType) && !"pass".equals(eventType))

            {

                skipped++;

                continue;

            }

            if ("exit".equals(eventType))
            {
                // 门内(door)摄像头不记录出门：离场统一由门外摄像头人脸比对判定
                skipped++;
                continue;
            }

            int trackId = event.path("trackId").asInt(0);

            // 优先用分析脚本写出的全局唯一 trackKey（含 taskId），避免跨片段 ByteTrack id 从 1 重号
            // 撞成同一个 yolo_1，进而被 selectOpenByTrack 误判为同一人
            String trackKey = event.path("trackKey").asText("");
            if (StringUtils.isEmpty(trackKey))
            {
                trackKey = bo.getTaskId() + "_" + TRACK_PREFIX + trackId;
            }

            double timeSec = event.path("timeSec").asDouble(0.0);

            LocalDateTime eventTime = videoBaseTime.plusNanos((long) (timeSec * 1_000_000_000L));

            Date eventDate = Date.from(eventTime.atZone(ZoneId.systemDefault()).toInstant());



            if (behaviorLogMapper.countByUniqueKey(trackKey, eventType, eventDate) > 0)

            {

                skipped++;

                continue;

            }



            SnapshotUrls urls = resolveEventSnapshots(event, trackId, trackSnapshots);

            String qualityFlag = event.path("qualityFlag").asText("");
            if (StringUtils.isEmpty(qualityFlag))
            {
                qualityFlag = resolveQualityFlag(urls.faceUrl, urls.bodyUrl);
            }

            if ("enter".equals(eventType))

            {

                PresenceTrackProcessResultVo processed = null;

                try

                {

                    processed = presenceTrackService.processEnter(cameraId, trackKey, eventDate,

                            StringUtils.nvl(urls.faceUrl, ""), StringUtils.nvl(urls.bodyUrl, ""), qualityFlag);

                    if (processed.isSkippedDuplicateEnter())

                    {

                        duplicateEnterSkipped++;
                        // 重复进门抑制：证据仍入库，但绝不复用已有 open session 的身份（否则多人会被标成 YOLO_1）
                        processed = null;

                    }
                    else if (processed.getSessionId() == null)
                    {
                        // 无可用人脸：不建档、不开 session，也不写进门日志
                        skipped++;
                        continue;
                    }

                }

                catch (Exception ex)

                {

                    log.warn("视频导入进门 session 处理失败 track={}: {}", trackKey, ex.getMessage());

                    sessionSkipped++;

                }

                BehaviorLogItemVo row = buildVideoImportRow(eventType, eventDate, event, urls, qualityFlag,
                        cameraId, trackKey, processed, aiSummary, aiPersonCount);

                behaviorLogMapper.insertBehaviorLog(row);

                promoteSnapshot(row, eventTime.toLocalDate());

                bindImportedLogToClip(row.getId(), bo, aiSummary);

                if (row.getId() != null && processed != null)

                {

                    behaviorLogMapper.updateBehaviorLogPresence(row.getId(), processed.getPersonId(),

                            processed.getSessionId(), processed.getFaceMatchScore(),

                            processed.getBodyMatchScore(), StringUtils.nvl(processed.getQualityFlag(), qualityFlag));

                }

                inserted++;

                continue;

            }

            if ("pass".equals(eventType))
            {
                // 路过：只写行为日志与证据图，不走进/出门 session 配对
                BehaviorLogItemVo row = buildVideoImportRow(eventType, eventDate, event, urls, qualityFlag,
                        cameraId, trackKey, null, aiSummary, aiPersonCount);
                behaviorLogMapper.insertBehaviorLog(row);
                promoteSnapshot(row, eventTime.toLocalDate());
                bindImportedLogToClip(row.getId(), bo, aiSummary);
                inserted++;
                continue;
            }



            BehaviorLogItemVo row = buildVideoImportRow(eventType, eventDate, event, urls, qualityFlag,
                    cameraId, trackKey, null, aiSummary, aiPersonCount);
            behaviorLogMapper.insertBehaviorLog(row);
            promoteSnapshot(row, eventTime.toLocalDate());
            bindImportedLogToClip(row.getId(), bo, aiSummary);
            if (!applyExitPresencePipeline(row, cameraId, trackKey, eventDate, urls, qualityFlag))
            {
                sessionSkipped++;
                if (row.getId() != null)
                {
                    behaviorLogMapper.updateBehaviorLogPresence(row.getId(), null, null,
                            null, null, qualityFlag);
                }
            }

            inserted++;

        }



        BehaviorLogImportResultVo result = new BehaviorLogImportResultVo();

        result.setInsertedCount(inserted);

        result.setSkippedCount(skipped);

        result.setMessage(String.format(

                "已写入 %d 条行为日志（原始事件 %d，过滤后 %d，丢弃路过 %d，同人组 %d，跳过重复 %d，session 异常 %d，重复进门抑制 %d）",

                inserted, filtered.getRawCount(), filtered.getEvents().size(), filtered.getDroppedPassCount(),
                filtered.getPersonGroupCount(), skipped, sessionSkipped, duplicateEnterSkipped));

        return result;

    }



    private String resolveCameraRole(Long cameraId)
    {
        if (cameraId == null)
        {
            return "door";
        }
        try
        {
            CameraConfigVo cfg = cameraService.getCameraConfig(cameraId);
            if (cfg != null && !StringUtils.isEmpty(cfg.getCameraRole()))
            {
                return cfg.getCameraRole();
            }
        }
        catch (Exception ex)
        {
            log.warn("解析摄像头角色失败 cameraId={}: {}", cameraId, ex.getMessage());
        }
        return "door";
    }

    /**
     * 门外摄像头离场导入：逐轨迹最佳脸与人脸库比对，命中且该人当前在场 → 关 session + 签退 + 写离场日志。
     * 未成脸、未命中、命中但不在场者一律跳过（不建档、不记录）。
     */
    private BehaviorLogImportResultVo importExteriorExit(BehaviorLogImportFromVideoBo bo, Long cameraId,
            JsonNode root, LocalDateTime videoBaseTime, String aiSummary)
    {
        AnalyzeEmbedResultVo emb;
        try
        {
            emb = presenceEmbedService.embedAnalyzeCaptures(bo.getTaskId());
        }
        catch (Exception ex)
        {
            BehaviorLogImportResultVo empty = new BehaviorLogImportResultVo();
            empty.setInsertedCount(0);
            empty.setSkippedCount(0);
            empty.setMessage("门外分析无抓拍人脸，跳过：" + ex.getMessage());
            return empty;
        }

        // trackId -> 离场时间 / 证据快照 / 人体框（与进门同一套 snapshotBbox 叠框）
        Map<Integer, Double> lastTimeByTrack = new HashMap<>();
        Map<Integer, String> snapByTrack = new HashMap<>();
        Map<Integer, String> bboxByTrack = new HashMap<>();
        collectExteriorTrackEvidence(root.path("events"), lastTimeByTrack, snapByTrack, bboxByTrack);
        collectExteriorTrackEvidence(root.path("tracks"), lastTimeByTrack, snapByTrack, bboxByTrack);

        int inserted = 0;
        int skipped = 0;
        Set<Long> exitedPersons = new HashSet<>();
        List<CaptureTrackEmbedVo> tracks = emb == null ? null : emb.getTracks();
        if (tracks != null)
        {
            for (CaptureTrackEmbedVo t : tracks)
            {
                EmbeddingVectorVo fe = t.getFaceEmbedding();
                if (fe == null || !Boolean.TRUE.equals(fe.getOk()) || fe.getEmbedding() == null)
                {
                    skipped++;
                    continue;
                }
                double sec = t.getTrackId() == null ? 0.0 : lastTimeByTrack.getOrDefault(t.getTrackId(), 0.0);
                LocalDateTime eventTime = videoBaseTime.plusNanos((long) (sec * 1_000_000_000L));
                Date eventDate = Date.from(eventTime.atZone(ZoneId.systemDefault()).toInstant());

                PresenceTrackProcessResultVo processed = presenceTrackService.processExitByFace(
                        cameraId, fe.getEmbedding(), eventDate);
                if (processed == null || processed.isSkippedOrphanExit() || processed.getSessionId() == null
                        || processed.getPersonId() == null)
                {
                    skipped++;
                    continue;
                }
                if (!exitedPersons.add(processed.getPersonId()))
                {
                    // 同一片段同一人只记一次离场
                    continue;
                }
                String trackKey = StringUtils.isEmpty(t.getTrackKey())
                        ? bo.getTaskId() + "_" + TRACK_PREFIX + t.getTrackId()
                        : t.getTrackKey();
                if (behaviorLogMapper.countByUniqueKey(trackKey, "exit", eventDate) > 0)
                {
                    skipped++;
                    continue;
                }
                String snapshotUrl = t.getTrackId() == null ? "" : snapByTrack.getOrDefault(t.getTrackId(), "");
                String snapshotBbox = t.getTrackId() == null ? null : bboxByTrack.get(t.getTrackId());
                String qualityFlag = StringUtils.nvl(processed.getQualityFlag(), "normal");

                BehaviorLogItemVo row = new BehaviorLogItemVo();
                row.setEventType("exit");
                row.setEventTime(eventDate);
                row.setFaceImageUrl(StringUtils.nvl(t.getFaceImageUrl(), ""));
                row.setBodyImageUrl(StringUtils.nvl(t.getBodyImageUrl(), ""));
                row.setSnapshotUrl(StringUtils.nvl(snapshotUrl, ""));
                row.setSnapshotBbox(snapshotBbox);
                row.setCameraId(cameraId);
                row.setTrackKey(trackKey);
                row.setPersonId(processed.getPersonId());
                row.setSessionId(processed.getSessionId());
                row.setQualityFlag(qualityFlag);
                if (!StringUtils.isEmpty(aiSummary))
                {
                    row.setBehaviorAnalysis(aiSummary);
                }
                behaviorLogMapper.insertBehaviorLog(row);
                promoteSnapshot(row, eventTime.toLocalDate());
                bindImportedLogToClip(row.getId(), bo, aiSummary);
                if (row.getId() != null)
                {
                    behaviorLogMapper.updateBehaviorLogPresence(row.getId(), processed.getPersonId(),
                            processed.getSessionId(), processed.getFaceMatchScore(), null, qualityFlag);
                }
                inserted++;
            }
        }

        BehaviorLogImportResultVo result = new BehaviorLogImportResultVo();
        result.setInsertedCount(inserted);
        result.setSkippedCount(skipped);
        result.setMessage(String.format("门外离场导入：写入 %d 条离场，跳过 %d（未成脸/未命中在场者）", inserted, skipped));
        return result;
    }

    private void collectExteriorTrackEvidence(JsonNode node, Map<Integer, Double> lastTimeByTrack,
            Map<Integer, String> snapByTrack, Map<Integer, String> bboxByTrack)
    {
        if (node == null || !node.isArray())
        {
            return;
        }
        for (JsonNode t : node)
        {
            int tid = t.path("trackId").asInt(-1);
            if (tid < 0)
            {
                continue;
            }
            if (t.hasNonNull("lastTimeSec"))
            {
                lastTimeByTrack.put(tid, t.path("lastTimeSec").asDouble(0.0));
            }
            else if (t.hasNonNull("timeSec") && !lastTimeByTrack.containsKey(tid))
            {
                lastTimeByTrack.put(tid, t.path("timeSec").asDouble(0.0));
            }
            String snap = t.path("snapshotUrl").asText("");
            if (!StringUtils.isEmpty(snap))
            {
                snapByTrack.put(tid, snap);
            }
            String bbox = readEventSnapshotBbox(t);
            if (!StringUtils.isEmpty(bbox))
            {
                bboxByTrack.put(tid, bbox);
            }
        }
    }

    private BehaviorLogItemVo buildVideoImportRow(String eventType, Date eventDate, JsonNode event,
            SnapshotUrls urls, String qualityFlag, Long cameraId, String trackKey,
            PresenceTrackProcessResultVo processed, String aiSummary, Integer aiPersonCount)
    {
        BehaviorLogItemVo row = new BehaviorLogItemVo();
        row.setEventType(eventType);
        row.setEventTime(eventDate);
        row.setFaceImageUrl(StringUtils.nvl(urls.faceUrl, ""));
        row.setBodyImageUrl(StringUtils.nvl(urls.bodyUrl, ""));
        row.setSnapshotUrl(StringUtils.nvl(event.path("snapshotUrl").asText(""), ""));
        row.setCameraId(cameraId);
        row.setTrackKey(trackKey);
        row.setQualityFlag(qualityFlag);
        if (!StringUtils.isEmpty(aiSummary))
        {
            row.setBehaviorAnalysis(aiSummary);
        }
        row.setPersonCount(aiPersonCount);
        row.setSnapshotBbox(readEventSnapshotBbox(event));
        if (processed != null)
        {
            row.setPersonId(processed.getPersonId());
            row.setSessionId(processed.getSessionId());
            row.setFaceMatchScore(processed.getFaceMatchScore());
            row.setBodyMatchScore(processed.getBodyMatchScore());
        }
        return row;
    }

    private String readSnapshotBboxJson(JsonNode bbox)
    {
        if (bbox == null || bbox.isNull())
        {
            return null;
        }
        try
        {
            return objectMapper.writeValueAsString(bbox);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private String readEventSnapshotBbox(JsonNode event)
    {
        if (event == null || event.isNull())
        {
            return null;
        }
        return readSnapshotBboxJson(event.path("snapshotBbox"));
    }

    private void bindImportedLogToClip(Long logId, BehaviorLogImportFromVideoBo bo, String aiSummary)
    {
        if (logId == null || bo == null)
        {
            return;
        }
        if (bo.getClipId() == null && StringUtils.isEmpty(bo.getSceneGroupId()))
        {
            return;
        }
        // 导入前已完成任务级 AI 时，直接标记 success，避免界面一直「分析中」
        String analysisStatus = StringUtils.isEmpty(aiSummary) ? "pending" : "success";
        try
        {
            behaviorLogMapper.updateBehaviorLogVideoBind(logId, bo.getSceneGroupId(), bo.getClipId(), analysisStatus);
        }
        catch (Exception ex)
        {
            log.warn("bind clip for imported logId={} failed: {}", logId, ex.getMessage());
        }
    }

    private boolean applyExitPresencePipeline(BehaviorLogItemVo row, Long cameraId, String trackKey,
            Date eventDate, SnapshotUrls urls, String qualityFlag)
    {
        if (row.getId() == null)
        {
            return false;
        }
        PresenceTrackProcessResultVo processed;
        try
        {
            processed = presenceTrackService.processExit(cameraId, trackKey, eventDate,
                    StringUtils.nvl(urls.faceUrl, ""), StringUtils.nvl(urls.bodyUrl, ""), qualityFlag);
        }
        catch (Exception ex)
        {
            log.warn("行为日志已写入，session 配对跳过 track={} event=exit: {}", trackKey, ex.getMessage());
            return false;
        }

        if (processed.isSkippedOrphanExit())
        {
            log.info("orphan exit behavior log only track={}", trackKey);
            return false;
        }

        String displayName = StringUtils.nvl(processed.getDisplayName(), row.getDisplayName());
        String personKind = StringUtils.nvl(processed.getPersonKind(), row.getPersonKind());
        String resolvedQuality = StringUtils.nvl(processed.getQualityFlag(), qualityFlag);
        behaviorLogMapper.updateBehaviorLogPresence(row.getId(), processed.getPersonId(),
                processed.getSessionId(), processed.getFaceMatchScore(),
                processed.getBodyMatchScore(), resolvedQuality);
        row.setPersonId(processed.getPersonId());
        row.setSessionId(processed.getSessionId());
        row.setFaceMatchScore(processed.getFaceMatchScore());
        row.setBodyMatchScore(processed.getBodyMatchScore());
        row.setQualityFlag(resolvedQuality);
        return true;
    }

    private SnapshotUrls resolveEventSnapshots(JsonNode event, int trackId, Map<Integer, SnapshotUrls> trackSnapshots)
    {
        String faceUrl = event.path("faceImageUrl").asText("");
        String bodyUrl = event.path("bodyImageUrl").asText("");
        if (!StringUtils.isEmpty(faceUrl) || !StringUtils.isEmpty(bodyUrl))
        {
            return new SnapshotUrls(faceUrl, bodyUrl);
        }
        return trackSnapshots.getOrDefault(trackId, SnapshotUrls.empty());
    }

    private Map<Integer, SnapshotUrls> loadCaptureTracksFromResult(JsonNode root)

    {

        Map<Integer, SnapshotUrls> map = new HashMap<>();

        JsonNode tracks = root.path("captureTracks");

        if (!tracks.isArray())

        {

            return map;

        }

        for (JsonNode track : tracks)

        {

            int trackId = track.path("trackId").asInt(0);

            map.put(trackId, new SnapshotUrls(

                    track.path("faceImageUrl").asText(""),

                    track.path("bodyImageUrl").asText("")));

        }

        return map;

    }



    private Map<Integer, SnapshotUrls> loadTrackSnapshots(String taskId)

    {

        Map<Integer, SnapshotUrls> map = new HashMap<>();

        File manifestFile = new File(ingestProperties.resolveStorageRoot(), "capture_manifest/" + taskId + ".json");

        if (!manifestFile.exists())

        {

            return map;

        }

        try

        {

            JsonNode root = objectMapper.readTree(Files.readString(manifestFile.toPath(), StandardCharsets.UTF_8));

            JsonNode tracks = root.path("tracks");

            if (!tracks.isArray())

            {

                return map;

            }

            for (JsonNode track : tracks)

            {

                int trackId = track.path("trackId").asInt(0);

                map.put(trackId, new SnapshotUrls(

                        track.path("faceImageUrl").asText(""),

                        track.path("bodyImageUrl").asText("")));

            }

        }

        catch (Exception ex)

        {

            throw new IllegalStateException("读取抓拍清单失败: " + ex.getMessage(), ex);

        }

        return map;

    }



    private LocalDateTime resolveVideoBaseTime(String sourceVideo, String taskStartedAt)

    {

        Matcher matcher = UPLOAD_TIME_PATTERN.matcher(sourceVideo == null ? "" : sourceVideo);

        if (matcher.find())

        {

            return LocalDateTime.parse(matcher.group(1), UPLOAD_TIME_FMT);

        }

        if (!StringUtils.isEmpty(taskStartedAt))

        {

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            return LocalDateTime.parse(taskStartedAt, fmt);

        }

        return LocalDateTime.now();

    }



    private static class SnapshotUrls

    {

        private final String faceUrl;

        private final String bodyUrl;



        private SnapshotUrls(String faceUrl, String bodyUrl)

        {

            this.faceUrl = faceUrl;

            this.bodyUrl = bodyUrl;

        }



        private static SnapshotUrls empty()

        {

            return new SnapshotUrls("", "");

        }

    }

}


