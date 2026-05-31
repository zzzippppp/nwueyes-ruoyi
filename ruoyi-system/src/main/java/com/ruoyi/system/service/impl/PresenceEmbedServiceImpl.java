package com.ruoyi.system.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.vo.AnalyzeEmbedResultVo;
import com.ruoyi.system.domain.vo.CaptureTrackEmbedVo;
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;
import com.ruoyi.system.service.IPresenceEmbedService;
import com.ruoyi.system.service.IPresenceReplayService;
import com.ruoyi.system.storage.PresenceStoragePaths;

@Service
public class PresenceEmbedServiceImpl implements IPresenceEmbedService
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IPresenceReplayService presenceReplayService;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Override
    public AnalyzeEmbedResultVo embedAnalyzeCaptures(String taskId)
    {
        if (StringUtils.isEmpty(taskId))
        {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        PresenceReplayTaskVo task = presenceReplayService.getTask(taskId);
        if (task == null)
        {
            throw new IllegalArgumentException("分析任务不存在: " + taskId);
        }
        if (!"success".equals(task.getStatus()))
        {
            throw new IllegalStateException("分析任务未成功完成，无法抽取向量");
        }
        if (StringUtils.isEmpty(task.getResultJson()))
        {
            throw new IllegalStateException("分析结果为空");
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

        JsonNode tracksNode = root.path("captureTracks");
        if (!tracksNode.isArray() || tracksNode.isEmpty())
        {
            throw new IllegalStateException("分析结果中没有抓拍数据 captureTracks");
        }

        AnalyzeEmbedResultVo result = new AnalyzeEmbedResultVo();
        result.setTaskId(taskId);
        List<CaptureTrackEmbedVo> rows = new ArrayList<>();
        int faceOk = 0;
        int bodyOk = 0;

        for (Iterator<JsonNode> it = tracksNode.elements(); it.hasNext();)
        {
            JsonNode track = it.next();
            CaptureTrackEmbedVo row = new CaptureTrackEmbedVo();
            row.setTrackId(track.path("trackId").asInt(0));
            row.setTrackKey(track.path("trackKey").asText(""));
            row.setFaceImageUrl(track.path("faceImageUrl").asText(""));
            row.setBodyImageUrl(track.path("bodyImageUrl").asText(""));
            if (track.has("faceScore") && !track.path("faceScore").isNull())
            {
                row.setFaceScore(track.path("faceScore").asDouble());
            }
            if (track.has("bodyScore") && !track.path("bodyScore").isNull())
            {
                row.setBodyScore(track.path("bodyScore").asDouble());
            }
            row.setSampledFrames(track.path("sampledFrames").asInt(0));

            EmbeddingVectorVo faceEmbed = embedKind("face", row.getFaceImageUrl());
            EmbeddingVectorVo bodyEmbed = embedKind("body", row.getBodyImageUrl());
            row.setFaceEmbedding(faceEmbed);
            row.setBodyEmbedding(bodyEmbed);
            if (Boolean.TRUE.equals(faceEmbed.getOk()))
            {
                faceOk++;
            }
            if (Boolean.TRUE.equals(bodyEmbed.getOk()))
            {
                bodyOk++;
            }
            rows.add(row);
        }

        result.setTracks(rows);
        result.setTrackCount(rows.size());
        result.setFaceOkCount(faceOk);
        result.setBodyOkCount(bodyOk);
        return result;
    }

    @Override
    public EmbeddingVectorVo embedImage(String kind, String imageUrl)
    {
        return embedKind(kind, imageUrl);
    }

    private EmbeddingVectorVo embedKind(String kind, String imageUrl)
    {
        EmbeddingVectorVo vo = new EmbeddingVectorVo();
        if (StringUtils.isEmpty(imageUrl))
        {
            vo.setOk(false);
            vo.setDim(512);
            vo.setError("图片 URL 为空");
            return vo;
        }
        Optional<Path> fileOpt = storagePaths.resolveImageUrlToFile(imageUrl);
        if (fileOpt.isEmpty())
        {
            vo.setOk(false);
            vo.setDim(512);
            vo.setError("图片文件不存在: " + imageUrl);
            return vo;
        }
        Path file = fileOpt.get();
        try
        {
            JsonNode payload = runEmbedScript(kind, file);
            vo.setOk(payload.path("ok").asBoolean(false));
            vo.setDim(payload.path("dim").asInt(512));
            vo.setModel(payload.path("model").asText(""));
            vo.setError(payload.path("error").isNull() ? null : payload.path("error").asText(""));
            vo.setImagePath(file.toAbsolutePath().toString().replace("\\", "/"));
            if (payload.path("embedding").isArray())
            {
                List<Double> embedding = new ArrayList<>();
                for (JsonNode item : payload.path("embedding"))
                {
                    embedding.add(item.asDouble());
                }
                vo.setEmbedding(embedding);
            }
            if (payload.path("quality").isObject())
            {
                vo.setQuality(objectMapper.convertValue(payload.path("quality"), java.util.Map.class));
            }
            return vo;
        }
        catch (Exception ex)
        {
            vo.setOk(false);
            vo.setDim(512);
            vo.setError(ex.getMessage());
            vo.setImagePath(file.toAbsolutePath().toString().replace("\\", "/"));
            return vo;
        }
    }

    private JsonNode runEmbedScript(String kind, Path imageFile) throws Exception
    {
        List<String> cmd = new ArrayList<>();
        cmd.add(Objects.requireNonNullElse(ingestProperties.getReplayPythonCommand(), "python"));
        cmd.add(Objects.requireNonNullElse(ingestProperties.getEmbedScriptPath(), "scripts/embed_features.py"));
        cmd.add("--kind");
        cmd.add(kind);
        cmd.add("--image");
        cmd.add(imageFile.toAbsolutePath().toString());
        if ("face".equals(kind))
        {
            cmd.add("--face-mode");
            cmd.add("crop");
            cmd.add("--face-model");
            cmd.add(Objects.requireNonNullElse(ingestProperties.getFaceEmbedModel(), "buffalo_l"));
            Double minScore = ingestProperties.getFaceMinDetScore();
            cmd.add("--min-det-score");
            cmd.add(String.valueOf(minScore == null ? 0.45 : minScore));
        }
        else
        {
            cmd.add("--body-model");
            cmd.add(Objects.requireNonNullElse(ingestProperties.getBodyEmbedModel(), "osnet_x0_25"));
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(ingestProperties.getWorkspaceRoot()));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                output.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        String text = output.toString().trim();
        JsonNode json = extractJson(text);
        if (json != null)
        {
            return json;
        }
        throw new IllegalStateException("embedding 脚本失败 exitCode=" + exitCode + " log=" + abbreviate(text, 800));
    }

    private JsonNode extractJson(String text)
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
        try
        {
            return objectMapper.readTree(text.substring(start, end + 1));
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private String abbreviate(String text, int max)
    {
        if (text == null || text.length() <= max)
        {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
