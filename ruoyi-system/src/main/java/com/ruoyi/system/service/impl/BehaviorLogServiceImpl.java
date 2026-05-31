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

import java.util.HashMap;

import java.util.Iterator;

import java.util.List;

import java.util.Map;

import java.util.Optional;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.ruoyi.common.utils.StringUtils;

import com.ruoyi.system.config.PresenceIngestProperties;

import com.ruoyi.system.domain.bo.BehaviorLogImportFromVideoBo;

import com.ruoyi.system.domain.bo.PresenceEventIngestBo;

import com.ruoyi.system.domain.vo.BehaviorLogImportResultVo;

import com.ruoyi.system.domain.vo.BehaviorLogItemVo;

import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;

import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;

import com.ruoyi.system.mapper.BehaviorLogMapper;

import com.ruoyi.system.service.IBehaviorLogService;

import com.ruoyi.system.service.IPresenceReplayService;

import com.ruoyi.system.service.IPresenceTrackService;

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

    private IPresenceReplayService presenceReplayService;



    @Autowired

    private PresenceIngestProperties ingestProperties;



    @Autowired

    private PresenceStoragePaths storagePaths;



    @Autowired

    private IPresenceTrackService presenceTrackService;



    @Override

    public List<BehaviorLogItemVo> listBehaviorLogs(LocalDate statDate, Long locationId, String eventType, Integer limit)

    {

        return behaviorLogMapper.selectBehaviorLogList(statDate, locationId, eventType,

                limit == null ? 500 : limit);

    }



    @Override

    public void recordLiveIngest(PresenceEventIngestBo bo, Date eventTime, PresenceTrackProcessResultVo processed)

    {

        if (bo == null || processed == null || eventTime == null)

        {

            return;

        }

        String eventType = StringUtils.nvl(bo.getEventType(), "").toLowerCase();

        if (!"enter".equals(eventType) && !"exit".equals(eventType))

        {

            return;

        }

        String trackKey = StringUtils.nvl(bo.getTrackKey(), "");

        if (StringUtils.isEmpty(trackKey) || bo.getLocationId() == null)

        {

            return;

        }

        if (behaviorLogMapper.countByUniqueKey(trackKey, eventType, eventTime, SOURCE_LIVE) > 0)

        {

            return;

        }

        String displayName = StringUtils.nvl(processed.getDisplayName(), "未登记-" + trackKey);

        String personKind = normalizeBehaviorPersonKind(processed.getPersonKind());

        String qualityFlag = StringUtils.nvl(bo.getQualityFlag(), resolveQualityFlag(bo.getFaceImageUrl(), bo.getBodyImageUrl()));



        BehaviorLogItemVo row = new BehaviorLogItemVo();

        row.setDisplayName(displayName);

        row.setEventType(eventType);

        row.setEventTime(eventTime);

        row.setFaceImageUrl(StringUtils.nvl(bo.getFaceImageUrl(), ""));

        row.setBodyImageUrl(StringUtils.nvl(bo.getBodyImageUrl(), ""));

        row.setLocationId(bo.getLocationId());

        row.setPersonId(processed.getPersonId());

        row.setTrackKey(trackKey);

        row.setSessionId(processed.getSessionId());

        row.setPersonKind(personKind);

        row.setSource(SOURCE_LIVE);

        behaviorLogMapper.insertBehaviorLog(row);



        if (row.getId() != null)

        {

            behaviorLogMapper.updateBehaviorLogPresence(row.getId(), displayName, processed.getPersonId(),

                    processed.getSessionId(), personKind, processed.getFaceMatchScore(),

                    processed.getBodyMatchScore(), qualityFlag);

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

        if (StringUtils.isEmpty(faceImageUrl) && StringUtils.isEmpty(bodyImageUrl))

        {

            return "missing";

        }

        if (StringUtils.isEmpty(faceImageUrl) && !StringUtils.isEmpty(bodyImageUrl))

        {

            return "low";

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



        Long locationId = bo.getLocationId() == null ? ingestProperties.getDefaultLocationId() : bo.getLocationId();

        JsonNode root;

        try

        {

            root = objectMapper.readTree(task.getResultJson());

        }

        catch (Exception ex)

        {

            throw new IllegalStateException("解析分析结果失败: " + ex.getMessage());

        }



        String sourceVideo = root.path("sourceVideo").asText("");

        JsonNode eventsNode = root.path("events");

        if (!eventsNode.isArray() || eventsNode.isEmpty())

        {

            throw new IllegalStateException("分析结果中没有过线事件");

        }



        Map<Integer, SnapshotUrls> trackSnapshots = loadTrackSnapshots(bo.getTaskId());

        if (trackSnapshots.isEmpty())

        {

            trackSnapshots = loadCaptureTracksFromResult(root);

        }

        LocalDateTime videoBaseTime = resolveVideoBaseTime(sourceVideo, task.getStartedAt());



        int inserted = 0;

        int skipped = 0;

        int sessionSkipped = 0;

        int duplicateEnterSkipped = 0;

        for (Iterator<JsonNode> it = eventsNode.elements(); it.hasNext();)

        {

            JsonNode event = it.next();

            String eventType = event.path("eventType").asText("");

            if (!"enter".equals(eventType) && !"exit".equals(eventType))

            {

                skipped++;

                continue;

            }

            int trackId = event.path("trackId").asInt(0);

            String trackKey = TRACK_PREFIX + trackId;

            double timeSec = event.path("timeSec").asDouble(0.0);

            LocalDateTime eventTime = videoBaseTime.plusNanos((long) (timeSec * 1_000_000_000L));

            Date eventDate = Date.from(eventTime.atZone(ZoneId.systemDefault()).toInstant());



            if (behaviorLogMapper.countByUniqueKey(trackKey, eventType, eventDate, SOURCE_VIDEO_TEST) > 0)

            {

                skipped++;

                continue;

            }



            SnapshotUrls urls = trackSnapshots.getOrDefault(trackId, SnapshotUrls.empty());

            String qualityFlag = resolveQualityFlag(urls.faceUrl, urls.bodyUrl);

            if ("enter".equals(eventType))

            {

                PresenceTrackProcessResultVo processed;

                try

                {

                    processed = presenceTrackService.processEnter(locationId, trackKey, eventDate,

                            StringUtils.nvl(urls.faceUrl, ""), StringUtils.nvl(urls.bodyUrl, ""), qualityFlag);

                }

                catch (Exception ex)

                {

                    log.warn("视频导入进门 session 处理失败 track={}: {}", trackKey, ex.getMessage());

                    sessionSkipped++;

                    continue;

                }

                if (processed.isSkippedDuplicateEnter())

                {

                    duplicateEnterSkipped++;

                    continue;

                }

                BehaviorLogItemVo row = new BehaviorLogItemVo();

                row.setDisplayName(StringUtils.nvl(processed.getDisplayName(), "未登记-" + trackKey));

                row.setEventType(eventType);

                row.setEventTime(eventDate);

                row.setFaceImageUrl(StringUtils.nvl(urls.faceUrl, ""));

                row.setBodyImageUrl(StringUtils.nvl(urls.bodyUrl, ""));

                row.setLocationId(locationId);

                row.setPersonId(processed.getPersonId());

                row.setTrackKey(trackKey);

                row.setSessionId(processed.getSessionId());

                row.setPersonKind(normalizeBehaviorPersonKind(processed.getPersonKind()));

                row.setSource(SOURCE_VIDEO_TEST);

                behaviorLogMapper.insertBehaviorLog(row);

                promoteLogImages(row, eventTime.toLocalDate());

                if (row.getId() != null)

                {

                    behaviorLogMapper.updateBehaviorLogPresence(row.getId(), row.getDisplayName(), processed.getPersonId(),

                            processed.getSessionId(), row.getPersonKind(), processed.getFaceMatchScore(),

                            processed.getBodyMatchScore(), StringUtils.nvl(processed.getQualityFlag(), qualityFlag));

                }

                inserted++;

                continue;

            }



            BehaviorLogItemVo row = new BehaviorLogItemVo();

            row.setDisplayName("未登记-" + trackKey);

            row.setEventType(eventType);

            row.setEventTime(eventDate);

            row.setFaceImageUrl(StringUtils.nvl(urls.faceUrl, ""));

            row.setBodyImageUrl(StringUtils.nvl(urls.bodyUrl, ""));

            row.setLocationId(locationId);

            row.setPersonId(null);

            row.setTrackKey(trackKey);

            row.setSessionId(null);

            row.setPersonKind("stranger");

            row.setSource(SOURCE_VIDEO_TEST);

            behaviorLogMapper.insertBehaviorLog(row);

            promoteLogImages(row, eventTime.toLocalDate());

            if (!applyExitPresencePipeline(row, locationId, trackKey, eventDate, urls))

            {

                sessionSkipped++;

                behaviorLogMapper.updateBehaviorLogPresence(row.getId(), row.getDisplayName(), null, null,

                        row.getPersonKind(), null, null, qualityFlag);

            }

            inserted++;

        }



        BehaviorLogImportResultVo result = new BehaviorLogImportResultVo();

        result.setInsertedCount(inserted);

        result.setSkippedCount(skipped);

        result.setMessage(String.format(

                "已写入 %d 条行为日志（跳过 %d 条，session 未配对 %d 条，重复进门抑制 %d 条）；抓拍轨迹 %d 条",

                inserted, skipped, sessionSkipped, duplicateEnterSkipped, trackSnapshots.size()));

        return result;

    }



    private boolean applyExitPresencePipeline(BehaviorLogItemVo row, Long locationId, String trackKey,
            Date eventDate, SnapshotUrls urls)
    {
        if (row.getId() == null)
        {
            return false;
        }
        String qualityFlag = resolveQualityFlag(urls.faceUrl, urls.bodyUrl);
        PresenceTrackProcessResultVo processed;
        try
        {
            processed = presenceTrackService.processExit(locationId, trackKey, eventDate,
                    row.getFaceImageUrl(), row.getBodyImageUrl(), qualityFlag);
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
        behaviorLogMapper.updateBehaviorLogPresence(row.getId(), displayName, processed.getPersonId(),
                processed.getSessionId(), personKind, processed.getFaceMatchScore(),
                processed.getBodyMatchScore(), resolvedQuality);
        row.setDisplayName(displayName);
        row.setPersonId(processed.getPersonId());
        row.setSessionId(processed.getSessionId());
        row.setPersonKind(personKind);
        row.setFaceMatchScore(processed.getFaceMatchScore());
        row.setBodyMatchScore(processed.getBodyMatchScore());
        row.setQualityFlag(resolvedQuality);
        return true;
    }

    private String resolveQualityFlag(SnapshotUrls urls)
    {
        boolean hasFace = !StringUtils.isEmpty(urls.faceUrl);
        boolean hasBody = !StringUtils.isEmpty(urls.bodyUrl);
        if (!hasFace && !hasBody)
        {
            return "missing";
        }
        if (!hasFace && hasBody)
        {
            return "low";
        }
        return "normal";
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



    private void promoteLogImages(BehaviorLogItemVo row, LocalDate eventDate)

    {

        if (row.getId() == null)

        {

            return;

        }

        try

        {

            String faceUrl = promoteLogImage(row.getFaceImageUrl(), eventDate, row.getId(), true);

            String bodyUrl = promoteLogImage(row.getBodyImageUrl(), eventDate, row.getId(), false);

            if (!faceUrl.equals(StringUtils.nvl(row.getFaceImageUrl(), ""))

                    || !bodyUrl.equals(StringUtils.nvl(row.getBodyImageUrl(), "")))

            {

                behaviorLogMapper.updateBehaviorLogImages(row.getId(), faceUrl, bodyUrl);

                row.setFaceImageUrl(faceUrl);

                row.setBodyImageUrl(bodyUrl);

            }

        }

        catch (Exception ex)

        {

            throw new IllegalStateException("行为日志图片落盘失败: " + ex.getMessage(), ex);

        }

    }



    private String promoteLogImage(String sourceUrl, LocalDate eventDate, long logId, boolean face)

            throws java.io.IOException

    {

        if (StringUtils.isEmpty(sourceUrl))

        {

            return "";

        }

        Optional<Path> sourceOpt = storagePaths.resolveImageUrlToFile(sourceUrl);

        if (sourceOpt.isEmpty())

        {

            return sourceUrl;

        }

        Path source = sourceOpt.get();

        if (face)

        {

            return storagePaths.promoteToLogFace(source, eventDate, logId);

        }

        return storagePaths.promoteToLogBody(source, eventDate, logId);

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


