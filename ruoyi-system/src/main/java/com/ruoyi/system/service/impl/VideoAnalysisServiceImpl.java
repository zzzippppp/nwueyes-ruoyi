package com.ruoyi.system.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.vo.AiAnalysisResultVo;
import com.ruoyi.system.domain.vo.AiModelOptionVo;
import com.ruoyi.system.domain.vo.PresenceVideoClipVo;
import com.ruoyi.system.mapper.VideoAnalysisMapper;
import com.ruoyi.system.service.IOssUploadService;
import com.ruoyi.system.service.IVideoAnalysisService;
import com.ruoyi.system.storage.PresenceStoragePaths;

@Service
public class VideoAnalysisServiceImpl implements IVideoAnalysisService
{
    private static final Logger log = LoggerFactory.getLogger(VideoAnalysisServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private VideoAnalysisMapper videoAnalysisMapper;

    @Autowired
    private IOssUploadService ossUploadService;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Autowired
    @Qualifier("presenceIngestExecutor")
    private ThreadPoolTaskExecutor presenceIngestExecutor;

    @Override
    public void submitClipAnalysis(PresenceVideoClipVo clip)
    {
        submitClipAnalysis(clip, null);
    }

    @Override
    public void submitClipAnalysis(PresenceVideoClipVo clip, List<String> modelKeys)
    {
        if (clip == null || clip.getId() == null)
        {
            return;
        }
        presenceIngestExecutor.execute(() -> analyzeClip(clip, modelKeys));
    }

    @Override
    public List<AiModelOptionVo> listModelOptions()
    {
        List<AiModelOptionVo> options = new ArrayList<>();
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || analysis.getModels() == null)
        {
            return options;
        }
        for (PresenceIngestProperties.Model model : analysis.getModels())
        {
            if (model == null)
            {
                continue;
            }
            AiModelOptionVo vo = new AiModelOptionVo();
            vo.setModelKey(StringUtils.nvl(model.getModelKey(), model.getModelName()));
            vo.setModelName(StringUtils.nvl(model.getModelName(), vo.getModelKey()));
            vo.setEnabled(model.isEnabled());
            options.add(vo);
        }
        return options;
    }

    @Override
    public void runAnalysis(String targetType, String targetId, List<String> modelKeys)
    {
        if (StringUtils.isEmpty(targetType) || StringUtils.isEmpty(targetId))
        {
            throw new IllegalArgumentException("targetType/targetId cannot be empty");
        }
        PresenceVideoClipVo clip;
        if ("scene_group".equals(targetType))
        {
            clip = videoAnalysisMapper.selectSceneClipByGroupId(targetId);
        }
        else
        {
            clip = videoAnalysisMapper.selectClipById(Long.valueOf(targetId));
        }
        if (clip == null)
        {
            throw new IllegalArgumentException("video clip not found: " + targetType + "/" + targetId);
        }
        submitClipAnalysis(clip, modelKeys);
    }

    @Override
    public List<AiAnalysisResultVo> analyzeLocalVideo(Path videoFile, List<String> modelKeys)
    {
        return analyzeLocalVideo(videoFile, modelKeys, null);
    }

    @Override
    public List<AiAnalysisResultVo> analyzeLocalVideo(Path videoFile, List<String> modelKeys, List<Double> eventTimesSec)
    {
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || !analysis.isEnabled() || analysis.getModels() == null || analysis.getModels().isEmpty())
        {
            throw new IllegalStateException("AI 分析未启用或未配置模型，请在 application-local.yml 配置 presence.ingest.analysis");
        }
        if (videoFile == null || !Files.isRegularFile(videoFile))
        {
            throw new IllegalArgumentException("视频文件不存在: " + videoFile);
        }

        Set<String> selected = normalizeSelectedModelKeys(modelKeys);
        List<AiAnalysisResultVo> results = new ArrayList<>();
        for (PresenceIngestProperties.Model model : analysis.getModels())
        {
            if (model == null || !model.isEnabled())
            {
                continue;
            }
            String modelKey = StringUtils.nvl(model.getModelKey(), model.getModelName());
            if (!selected.isEmpty() && !selected.contains(modelKey))
            {
                continue;
            }
            AiAnalysisResultVo vo = new AiAnalysisResultVo();
            vo.setTargetType("analyze_task");
            vo.setModelKey(modelKey);
            vo.setModelName(StringUtils.nvl(model.getModelName(), modelKey));
            try
            {
                Map<String, String> parsed = callModelForLocalFile(videoFile, model, analysis, analysis.getPrompt(),
                        eventTimesSec);
                vo.setStatus("success");
                vo.setSummary(parsed.get("summary"));
                vo.setAppearance(parsed.get("appearance"));
                vo.setBehavior(parsed.get("behavior"));
                vo.setPersonCount(parsePersonCountValue(parsed.get("personCount")));
                vo.setRawJson(parsed.get("rawJson"));
            }
            catch (Exception ex)
            {
                log.warn("AI local video analysis failed model={}: {}", modelKey, formatAnalysisError(ex), ex);
                vo.setStatus("failed");
                vo.setSummary("");
                vo.setAppearance("");
                vo.setBehavior("");
                vo.setPersonCount(null);
                vo.setRawJson("{}");
                vo.setErrorMessage(formatAnalysisError(ex));
            }
            results.add(vo);
        }
        if (results.isEmpty())
        {
            throw new IllegalStateException("没有可用的启用模型，请检查 presence.ingest.analysis.models");
        }
        return results;
    }

    private void analyzeClip(PresenceVideoClipVo clip, List<String> modelKeys)
    {
        String targetType = "scene_group".equals(clip.getClipType()) ? "scene_group" : "clip";
        String targetId = "scene_group".equals(targetType) ? clip.getSceneGroupId() : String.valueOf(clip.getId());
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || !analysis.isEnabled() || analysis.getModels() == null || analysis.getModels().isEmpty())
        {
            videoAnalysisMapper.insertAnalysisPending(targetType, targetId, "not_configured", "Not configured");
            videoAnalysisMapper.updateAnalysisResult(targetType, targetId, "not_configured", "skipped",
                    "AI analysis is not enabled.", "", "", null, "{}", "No enabled AI model configured.");
            return;
        }

        Set<String> selected = normalizeSelectedModelKeys(modelKeys);
        for (PresenceIngestProperties.Model model : analysis.getModels())
        {
            if (model == null || !model.isEnabled())
            {
                continue;
            }
            String modelKey = StringUtils.nvl(model.getModelKey(), model.getModelName());
            if (!selected.isEmpty() && !selected.contains(modelKey))
            {
                continue;
            }
            String modelName = StringUtils.nvl(model.getModelName(), modelKey);
            videoAnalysisMapper.insertAnalysisPending(targetType, targetId, modelKey, modelName);
            try
            {
                Map<String, String> parsed = callModel(clip, model, analysis);
                videoAnalysisMapper.updateAnalysisResult(targetType, targetId, modelKey, "success",
                        parsed.get("summary"), parsed.get("appearance"), parsed.get("behavior"),
                        parsePersonCountValue(parsed.get("personCount")), parsed.get("rawJson"), null);
            }
            catch (Exception ex)
            {
                String err = formatAnalysisError(ex);
                log.warn("AI analysis failed clip={} model={}: {}", clip.getId(), modelKey, err, ex);
                videoAnalysisMapper.updateAnalysisResult(targetType, targetId, modelKey, "failed",
                        "", "", "", null, "{}", err);
            }
        }
    }

    private Set<String> normalizeSelectedModelKeys(List<String> modelKeys)
    {
        Set<String> selected = new HashSet<>();
        if (modelKeys == null)
        {
            return selected;
        }
        for (String modelKey : modelKeys)
        {
            if (!StringUtils.isEmpty(modelKey))
            {
                selected.add(modelKey);
            }
        }
        return selected;
    }

    private Map<String, String> callModel(PresenceVideoClipVo clip, PresenceIngestProperties.Model model,
            PresenceIngestProperties.AiAnalysis analysis) throws Exception
    {
        String publicUrl = StringUtils.nvl(clip.getPublicVideoUrl(), "");
        if (isHttpUrl(publicUrl))
        {
            return callModelWithContent(model, analysis, buildPrompt(clip, analysis),
                    List.of(buildVideoUrlContent(publicUrl, analysis.getVideoFps())));
        }
        Path localFile = resolveLocalClipFile(clip);
        if (localFile != null)
        {
            return callModelForLocalFile(localFile, model, analysis, buildPrompt(clip, analysis));
        }
        String videoUrl = resolveModelVideoUrl(clip);
        return callModelWithContent(model, analysis, buildPrompt(clip, analysis),
                List.of(buildVideoUrlContent(videoUrl, analysis.getVideoFps())));
    }

    private Map<String, String> callModelForLocalFile(Path videoFile, PresenceIngestProperties.Model model,
            PresenceIngestProperties.AiAnalysis analysis) throws Exception
    {
        return callModelForLocalFile(videoFile, model, analysis, analysis.getPrompt(), null);
    }

    private Map<String, String> callModelForLocalFile(Path videoFile, PresenceIngestProperties.Model model,
            PresenceIngestProperties.AiAnalysis analysis, String prompt) throws Exception
    {
        return callModelForLocalFile(videoFile, model, analysis, prompt, null);
    }

    private Map<String, String> callModelForLocalFile(Path videoFile, PresenceIngestProperties.Model model,
            PresenceIngestProperties.AiAnalysis analysis, String prompt, List<Double> eventTimesSec) throws Exception
    {
        List<Map<String, Object>> mediaContent = buildLocalMediaContent(videoFile, analysis, eventTimesSec);
        return callModelWithContent(model, analysis, prompt, mediaContent);
    }

    private Map<String, String> callModelWithContent(PresenceIngestProperties.Model model,
            PresenceIngestProperties.AiAnalysis analysis, String prompt,
            List<Map<String, Object>> mediaContent) throws Exception
    {
        if (StringUtils.isEmpty(model.getBaseUrl()))
        {
            throw new IllegalArgumentException("baseUrl is empty");
        }
        if (StringUtils.isEmpty(model.getApiKey()))
        {
            throw new IllegalArgumentException("apiKey is empty，请配置 DASHSCOPE_API_KEY 或 models[].apiKey");
        }
        if (mediaContent == null || mediaContent.isEmpty())
        {
            throw new IllegalArgumentException("mediaContent is empty，无法调用视觉模型");
        }
        String promptText = StringUtils.isEmpty(prompt) ? "请分析这段监控视频。" : prompt;
        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", promptText));
        content.addAll(mediaContent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model.getModelName());
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(Map.of("role", "user", "content", content)));

        String json = objectMapper.writeValueAsString(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(model.getBaseUrl()))
                .timeout(Duration.ofSeconds(Math.max(30, analysis.getTimeoutSec())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + model.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IllegalStateException("model HTTP " + response.statusCode() + ": " + truncate(response.body(), 800));
        }
        return parseModelResponse(response.body());
    }

    private List<Map<String, Object>> buildLocalMediaContent(Path videoFile, PresenceIngestProperties.AiAnalysis analysis)
            throws Exception
    {
        return buildLocalMediaContent(videoFile, analysis, null);
    }

    private List<Map<String, Object>> buildLocalMediaContent(Path videoFile, PresenceIngestProperties.AiAnalysis analysis,
            List<Double> eventTimesSec) throws Exception
    {
        PresenceIngestProperties.Oss oss = analysis.getOss();
        if (oss != null && oss.isEnabled())
        {
            try
            {
                String objectKey = "local/" + videoFile.getFileName();
                String uploaded = ossUploadService.uploadClip(videoFile, objectKey);
                return List.of(buildVideoUrlContent(uploaded, analysis.getVideoFps()));
            }
            catch (Exception ex)
            {
                log.warn("OSS upload failed, fallback to local frames: {}", ex.getMessage());
            }
        }

        long size = Files.size(videoFile);
        // 默认优先抽帧：整段 base64 上传慢且易超时；仅很小的片段才直传
        long maxBase64 = Math.max(256L * 1024L, analysis.getMaxBase64Bytes());
        if (size > 0 && size <= maxBase64)
        {
            String mime = guessVideoMime(videoFile);
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(videoFile));
            return List.of(buildVideoUrlContent(dataUrl, analysis.getVideoFps()));
        }

        List<Double> timestamps = planFrameTimestamps(videoFile, analysis, eventTimesSec);
        List<String> frameUrls = extractFrameDataUrlsAt(videoFile, analysis, timestamps);
        if (frameUrls.isEmpty())
        {
            throw new IllegalStateException("无法从本地视频抽取帧，且文件过大无法 base64 直传: " + videoFile);
        }
        double span = timestamps.size() <= 1 ? 1.0
                : Math.max(0.5, timestamps.get(timestamps.size() - 1) - timestamps.get(0));
        double approxFps = Math.max(0.1, (timestamps.size() - 1) / span);
        Map<String, Object> videoPart = new HashMap<>();
        videoPart.put("type", "video");
        videoPart.put("video", frameUrls);
        videoPart.put("fps", approxFps);
        log.info("AI frame extract strategy={} frames={} times={}",
                (eventTimesSec != null && !eventTimesSec.isEmpty()) ? "event" : "middle60",
                frameUrls.size(), timestamps);
        return List.of(videoPart);
    }

    private Map<String, Object> buildVideoUrlContent(String url, double fps)
    {
        Map<String, Object> part = new HashMap<>();
        part.put("type", "video_url");
        part.put("video_url", Map.of("url", url));
        part.put("fps", Math.max(0.1, fps <= 0 ? 2.0 : fps));
        return part;
    }

    /**
     * 有 YOLO 事件：围着事件 ±padding 抽帧并补齐到 maxFrames；
     * 无事件：在视频中段 middleWindowRatio 窗口内均匀抽帧。
     */
    List<Double> planFrameTimestamps(Path videoFile, PresenceIngestProperties.AiAnalysis analysis,
            List<Double> eventTimesSec) throws Exception
    {
        double duration = probeVideoDurationSec(videoFile);
        if (duration <= 0.2)
        {
            duration = 10.0;
        }
        int maxFrames = Math.max(1, analysis.getMaxFrameCount());
        double padding = Math.max(0.3, analysis.getEventPaddingSec());
        List<Double> events = normalizeEventTimes(eventTimesSec, duration);
        if (!events.isEmpty())
        {
            return planEventCenteredTimestamps(duration, events, maxFrames, padding);
        }
        double ratio = analysis.getMiddleWindowRatio();
        if (ratio <= 0.1 || ratio > 1.0)
        {
            ratio = 0.6;
        }
        return planMiddleWindowTimestamps(duration, maxFrames, ratio);
    }

    private List<Double> normalizeEventTimes(List<Double> eventTimesSec, double duration)
    {
        List<Double> events = new ArrayList<>();
        if (eventTimesSec == null)
        {
            return events;
        }
        for (Double t : eventTimesSec)
        {
            if (t == null || t.isNaN() || t.isInfinite())
            {
                continue;
            }
            double clamped = Math.max(0.0, Math.min(duration, t));
            events.add(clamped);
        }
        events.sort(Double::compareTo);
        return dedupeTimestamps(events, 0.25);
    }

    private List<Double> planEventCenteredTimestamps(double duration, List<Double> events, int maxFrames,
            double padding)
    {
        List<Double> candidates = new ArrayList<>();
        for (Double event : events)
        {
            candidates.add(clampTime(event - padding, duration));
            candidates.add(clampTime(event, duration));
            candidates.add(clampTime(event + padding, duration));
        }
        candidates = dedupeTimestamps(candidates, 0.35);
        if (candidates.size() >= maxFrames)
        {
            return downsampleEven(candidates, maxFrames);
        }
        // 在覆盖所有事件的区间内补帧
        double winStart = Math.max(0.0, events.get(0) - padding);
        double winEnd = Math.min(duration, events.get(events.size() - 1) + padding);
        if (winEnd - winStart < 1.0)
        {
            double mid = (winStart + winEnd) * 0.5;
            winStart = Math.max(0.0, mid - 0.8);
            winEnd = Math.min(duration, mid + 0.8);
        }
        List<Double> filled = new ArrayList<>(candidates);
        int need = maxFrames - filled.size();
        for (int i = 1; i <= need; i++)
        {
            double t = winStart + (winEnd - winStart) * i / (need + 1.0);
            filled.add(clampTime(t, duration));
        }
        filled = dedupeTimestamps(filled, 0.3);
        if (filled.size() > maxFrames)
        {
            return downsampleEven(filled, maxFrames);
        }
        return filled;
    }

    private List<Double> planMiddleWindowTimestamps(double duration, int maxFrames, double ratio)
    {
        double margin = (1.0 - ratio) * 0.5;
        double start = duration * margin;
        double end = duration * (1.0 - margin);
        if (end <= start + 0.2)
        {
            start = 0.0;
            end = duration;
        }
        List<Double> times = new ArrayList<>();
        if (maxFrames == 1)
        {
            times.add(clampTime((start + end) * 0.5, duration));
            return times;
        }
        for (int i = 0; i < maxFrames; i++)
        {
            double t = start + (end - start) * i / (maxFrames - 1.0);
            times.add(clampTime(t, duration));
        }
        return dedupeTimestamps(times, 0.2);
    }

    private List<Double> downsampleEven(List<Double> times, int maxFrames)
    {
        if (times.size() <= maxFrames)
        {
            return times;
        }
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < maxFrames; i++)
        {
            int idx = (int) Math.round(i * (times.size() - 1.0) / (maxFrames - 1.0));
            out.add(times.get(idx));
        }
        return dedupeTimestamps(out, 0.15);
    }

    private List<Double> dedupeTimestamps(List<Double> times, double minGap)
    {
        List<Double> sorted = new ArrayList<>(times);
        sorted.sort(Double::compareTo);
        List<Double> out = new ArrayList<>();
        for (Double t : sorted)
        {
            if (out.isEmpty() || t - out.get(out.size() - 1) >= minGap)
            {
                out.add(t);
            }
        }
        return out;
    }

    private double clampTime(double t, double duration)
    {
        if (duration <= 0)
        {
            return Math.max(0.0, t);
        }
        // 避开极端片头片尾，降低黑帧概率
        double lo = Math.min(0.05, duration * 0.01);
        double hi = Math.max(lo + 0.05, duration - 0.05);
        return Math.max(lo, Math.min(hi, t));
    }

    private double probeVideoDurationSec(Path videoFile) throws Exception
    {
        String ffprobe = resolveFfprobePath();
        List<String> cmd = List.of(
                ffprobe, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int code = process.waitFor();
        if (code != 0 || StringUtils.isEmpty(output))
        {
            log.warn("ffprobe duration failed, fallback duration=10s: {}", truncate(output, 200));
            return 10.0;
        }
        try
        {
            return Double.parseDouble(output.split("\\s+")[0]);
        }
        catch (NumberFormatException ex)
        {
            return 10.0;
        }
    }

    private String resolveFfmpegPath()
    {
        if (ingestProperties.getClip() != null && !StringUtils.isEmpty(ingestProperties.getClip().getFfmpegPath()))
        {
            return ingestProperties.getClip().getFfmpegPath();
        }
        return "ffmpeg";
    }

    private String resolveFfprobePath()
    {
        String ffmpeg = resolveFfmpegPath();
        String lower = ffmpeg.toLowerCase();
        if (lower.endsWith("ffmpeg.exe"))
        {
            return ffmpeg.substring(0, ffmpeg.length() - "ffmpeg.exe".length()) + "ffprobe.exe";
        }
        if (lower.endsWith("ffmpeg"))
        {
            return ffmpeg.substring(0, ffmpeg.length() - "ffmpeg".length()) + "ffprobe";
        }
        return "ffprobe";
    }

    private List<String> extractFrameDataUrlsAt(Path videoFile, PresenceIngestProperties.AiAnalysis analysis,
            List<Double> timestamps) throws Exception
    {
        if (timestamps == null || timestamps.isEmpty())
        {
            return List.of();
        }
        String ffmpeg = resolveFfmpegPath();
        int width = Math.max(320, analysis.getFrameWidth() <= 0 ? 960 : analysis.getFrameWidth());
        Path tmpDir = Files.createTempDirectory("nwueyes-ai-frames-");
        try
        {
            List<String> urls = new ArrayList<>();
            int index = 0;
            for (Double ts : timestamps)
            {
                Path out = tmpDir.resolve(String.format("frame_%03d.jpg", index++));
                List<String> cmd = List.of(
                        ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                        "-ss", String.format(java.util.Locale.US, "%.3f", ts),
                        "-i", videoFile.toAbsolutePath().toString(),
                        "-frames:v", "1",
                        "-vf", "scale=" + width + ":-2",
                        "-q:v", "5",
                        out.toAbsolutePath().toString());
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int code = process.waitFor();
                if (code != 0 || !Files.isRegularFile(out) || Files.size(out) <= 0)
                {
                    log.warn("ffmpeg extract at {}s failed: {}", ts, truncate(output, 200));
                    continue;
                }
                String b64 = Base64.getEncoder().encodeToString(Files.readAllBytes(out));
                urls.add("data:image/jpeg;base64," + b64);
            }
            return urls;
        }
        finally
        {
            try (Stream<Path> stream = Files.list(tmpDir))
            {
                stream.forEach(p -> {
                    try
                    {
                        Files.deleteIfExists(p);
                    }
                    catch (Exception ignored)
                    {
                    }
                });
            }
            catch (Exception ignored)
            {
            }
            try
            {
                Files.deleteIfExists(tmpDir);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private String guessVideoMime(Path videoFile)
    {
        String name = videoFile.getFileName().toString().toLowerCase();
        if (name.endsWith(".webm"))
        {
            return "video/webm";
        }
        if (name.endsWith(".mov"))
        {
            return "video/quicktime";
        }
        return "video/mp4";
    }

    private String buildPrompt(PresenceVideoClipVo clip, PresenceIngestProperties.AiAnalysis analysis)
    {
        String modelVideoUrl = StringUtils.nvl(clip.getPublicVideoUrl(), clip.getVideoUrl());
        return analysis.getPrompt()
                + "\nclipType=" + clip.getClipType()
                + "\nsceneGroupId=" + StringUtils.nvl(clip.getSceneGroupId(), "")
                + "\ntrackKey=" + StringUtils.nvl(clip.getTrackKey(), "")
                + "\nvideoUrl=" + modelVideoUrl;
    }

    private Path resolveLocalClipFile(PresenceVideoClipVo clip)
    {
        String videoUrl = clip.getVideoUrl();
        if (StringUtils.isEmpty(videoUrl))
        {
            return null;
        }
        String lower = videoUrl.toLowerCase();
        if (lower.startsWith("ezopen://") || (isHttpUrl(videoUrl) && !videoUrl.contains("/dashboard/storage/file/")))
        {
            return null;
        }
        return storagePaths.resolveClipUrlToFile(videoUrl).orElse(null);
    }

    private String resolveModelVideoUrl(PresenceVideoClipVo clip)
    {
        String publicVideoUrl = StringUtils.nvl(clip.getPublicVideoUrl(), "");
        if (isHttpUrl(publicVideoUrl))
        {
            return publicVideoUrl;
        }
        String videoUrl = clip.getVideoUrl();
        if (StringUtils.isEmpty(videoUrl))
        {
            throw new IllegalArgumentException("video URL is empty; wait for Ezviz download before analysis.");
        }
        String lower = videoUrl.toLowerCase();
        if (lower.startsWith("ezopen://"))
        {
            throw new IllegalArgumentException(
                    "Ezviz playback URLs cannot be downloaded by cloud vision models; download/upload the clip first.");
        }
        if (isHttpUrl(videoUrl) && !videoUrl.contains("/dashboard/storage/file/"))
        {
            return videoUrl;
        }
        Path localFile = storagePaths.resolveClipUrlToFile(videoUrl)
                .orElseThrow(() -> new IllegalArgumentException("local clip file not found for analysis: " + videoUrl));
        PresenceIngestProperties.Oss oss = ingestProperties.getAnalysis() == null ? null
                : ingestProperties.getAnalysis().getOss();
        if (oss != null && oss.isEnabled())
        {
            String uploaded = ossUploadService.uploadClip(localFile, buildOssObjectKey(clip, localFile));
            videoAnalysisMapper.updateClipPublicVideoUrl(clip.getId(), uploaded, "uploaded");
            clip.setPublicVideoUrl(uploaded);
            return uploaded;
        }
        throw new IllegalArgumentException("local clip requires OSS or frame-extract path; unexpected fallthrough");
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

    private String buildOssObjectKey(PresenceVideoClipVo clip, Path localFile)
    {
        String fileName = localFile.getFileName().toString();
        String datePart = "unknown-date";
        if (clip.getStartTime() != null)
        {
            datePart = DateTimeFormatter.ofPattern("yyyy/MM/dd")
                    .format(clip.getStartTime().toInstant().atZone(ZoneId.of("Asia/Shanghai")));
        }
        return datePart + "/clip_" + clip.getId() + "_" + fileName;
    }

    private Map<String, String> parseModelResponse(String body) throws Exception
    {
        Map<String, String> result = new HashMap<>();
        result.put("rawJson", body);
        JsonNode root = objectMapper.readTree(body);
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        JsonNode content = tryParseJson(stripCodeFence(text));
        if (content == null)
        {
            content = tryExtractEmbeddedJson(text);
        }
        if (content == null)
        {
            result.put("summary", text);
            result.put("appearance", "");
            result.put("behavior", "");
            result.put("personCount", "");
            return result;
        }
        result.put("summary", content.path("summary").asText(""));
        result.put("appearance", content.path("appearance").asText(""));
        result.put("behavior", content.path("behavior").asText(""));
        result.put("personCount", extractPersonCountText(content));
        return result;
    }

    private String extractPersonCountText(JsonNode content)
    {
        if (content == null)
        {
            return "";
        }
        if (content.has("personCount") && !content.get("personCount").isNull())
        {
            return content.get("personCount").asText("");
        }
        if (content.has("person_count") && !content.get("person_count").isNull())
        {
            return content.get("person_count").asText("");
        }
        if (content.has("peopleCount") && !content.get("peopleCount").isNull())
        {
            return content.get("peopleCount").asText("");
        }
        if (content.has("人数") && !content.get("人数").isNull())
        {
            return content.get("人数").asText("");
        }
        return "";
    }

    private Integer parsePersonCountValue(String raw)
    {
        if (StringUtils.isEmpty(raw))
        {
            return null;
        }
        String text = raw.trim();
        try
        {
            if (text.matches("^-?\\d+$"))
            {
                return Integer.valueOf(text);
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            if (matcher.find())
            {
                return Integer.valueOf(matcher.group(1));
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    private String stripCodeFence(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return text;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```"))
        {
            return trimmed;
        }
        int firstNl = trimmed.indexOf('\n');
        if (firstNl < 0)
        {
            return trimmed;
        }
        String body = trimmed.substring(firstNl + 1);
        int fence = body.lastIndexOf("```");
        if (fence >= 0)
        {
            body = body.substring(0, fence);
        }
        return body.trim();
    }

    private JsonNode tryExtractEmbeddedJson(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            return null;
        }
        return tryParseJson(text.substring(start, end + 1));
    }

    private JsonNode tryParseJson(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        try
        {
            return objectMapper.readTree(text);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private String truncate(String text, int max)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String formatAnalysisError(Throwable ex)
    {
        if (ex == null)
        {
            return "unknown error";
        }
        String msg = ex.getMessage();
        if (StringUtils.isEmpty(msg))
        {
            msg = ex.getClass().getSimpleName();
        }
        Throwable cause = ex.getCause();
        if (cause != null && !StringUtils.isEmpty(cause.getMessage())
                && (msg == null || !msg.contains(cause.getMessage())))
        {
            msg = msg + " | cause: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return truncate(msg, 800);
    }
}
