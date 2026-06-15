package com.ruoyi.system.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private void analyzeClip(PresenceVideoClipVo clip, List<String> modelKeys)
    {
        String targetType = "scene_group".equals(clip.getClipType()) ? "scene_group" : "clip";
        String targetId = "scene_group".equals(targetType) ? clip.getSceneGroupId() : String.valueOf(clip.getId());
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || !analysis.isEnabled() || analysis.getModels() == null || analysis.getModels().isEmpty())
        {
            videoAnalysisMapper.insertAnalysisPending(targetType, targetId, "not_configured", "Not configured");
            videoAnalysisMapper.updateAnalysisResult(targetType, targetId, "not_configured", "skipped",
                    "AI analysis is not enabled.", "", "", "unknown", "{}", "No enabled AI model configured.");
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
                        parsed.get("riskLevel"), parsed.get("rawJson"), null);
            }
            catch (Exception ex)
            {
                log.warn("AI analysis failed clip={} model={}: {}", clip.getId(), modelKey, ex.getMessage());
                videoAnalysisMapper.updateAnalysisResult(targetType, targetId, modelKey, "failed",
                        "", "", "", "unknown", "{}", ex.getMessage());
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
        if (StringUtils.isEmpty(model.getBaseUrl()))
        {
            throw new IllegalArgumentException("baseUrl is empty");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model.getModelName());
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", buildPrompt(clip, analysis)),
                        Map.of("type", "video_url", "video_url", Map.of("url", resolveModelVideoUrl(clip)))))));
        String json = objectMapper.writeValueAsString(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(model.getBaseUrl()))
                .timeout(Duration.ofSeconds(Math.max(5, analysis.getTimeoutSec())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (!StringUtils.isEmpty(model.getApiKey()))
        {
            builder.header("Authorization", "Bearer " + model.getApiKey());
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, analysis.getTimeoutSec())))
                .build();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IllegalStateException("model HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseModelResponse(response.body());
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
                .orElseThrow(() -> new IllegalArgumentException("local clip file not found for OSS upload: " + videoUrl));
        String uploaded = ossUploadService.uploadClip(localFile, buildOssObjectKey(clip, localFile));
        videoAnalysisMapper.updateClipPublicVideoUrl(clip.getId(), uploaded, "uploaded");
        clip.setPublicVideoUrl(uploaded);
        return uploaded;
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
        JsonNode content = tryParseJson(text);
        if (content == null)
        {
            result.put("summary", text);
            result.put("appearance", "");
            result.put("behavior", "");
            result.put("riskLevel", "unknown");
            return result;
        }
        result.put("summary", content.path("summary").asText(""));
        result.put("appearance", content.path("appearance").asText(""));
        result.put("behavior", content.path("behavior").asText(""));
        result.put("riskLevel", content.path("riskLevel").asText(content.path("risk_level").asText("unknown")));
        return result;
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
}
