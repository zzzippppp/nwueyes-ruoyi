package com.ruoyi.system.service.impl;



import java.io.BufferedReader;

import java.io.File;

import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.BehaviorLogImportFromVideoBo;
import com.ruoyi.system.domain.bo.PresenceReplayStartBo;
import com.ruoyi.system.domain.vo.AiAnalysisResultVo;
import com.ruoyi.system.domain.vo.BehaviorLogImportResultVo;
import com.ruoyi.system.domain.vo.CameraConfigVo;
import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;
import com.ruoyi.system.service.IBehaviorLogService;
import com.ruoyi.system.service.ICameraService;
import com.ruoyi.system.service.IPresenceReplayService;
import com.ruoyi.system.service.IPresenceVideoClipService;
import com.ruoyi.system.service.IVideoAnalysisService;
import com.ruoyi.system.util.PythonProcessLauncher;

import jakarta.annotation.Resource;



@Service

public class PresenceReplayServiceImpl implements IPresenceReplayService

{
    private static final Logger log = LoggerFactory.getLogger(PresenceReplayServiceImpl.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");



    private final Map<String, ReplayTaskState> taskMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private ICameraService cameraService;

    @Autowired
    @Lazy
    private IBehaviorLogService behaviorLogService;

    @Autowired
    @Lazy
    private IPresenceVideoClipService presenceVideoClipService;

    @Autowired
    private IVideoAnalysisService videoAnalysisService;



    @Resource(name = "threadPoolTaskExecutor")

    private ThreadPoolTaskExecutor executor;

    /** 离线 YOLO 分析专用池，与公共异步池隔离 */
    @Resource(name = "presenceAnalyzeExecutor")
    private ThreadPoolTaskExecutor analyzeExecutor;



    @Override

    public PresenceReplayTaskVo startReplay(PresenceReplayStartBo bo)

    {

        return startTask(bo, "replay", true);

    }



    @Override

    public PresenceReplayTaskVo startAnalyze(PresenceReplayStartBo bo)

    {

        return startTask(bo, "analyze", false);

    }



    @Override

    public PresenceReplayTaskVo getTask(String taskId)

    {

        ReplayTaskState state = taskMap.get(taskId);

        if (state != null)

        {
            File resultFile = analyzeResultFile(taskId);
            if (resultFile.exists())
            {
                try
                {
                    state.resultJson = Files.readString(resultFile.toPath(), StandardCharsets.UTF_8);
                }
                catch (Exception ignored)
                {
                }
            }
            return state.toVo();

        }

        return loadTaskFromDisk(taskId);

    }



    @Override
    public Map<String, Object> submitAiAnalysisForTask(String taskId, List<String> modelKeys)
    {
        if (StringUtils.isEmpty(taskId))
        {
            throw new IllegalArgumentException("taskId cannot be empty");
        }
        File resultFile = analyzeResultFile(taskId);
        if (!resultFile.exists())
        {
            throw new IllegalArgumentException("分析结果不存在，请先完成 YOLO 检测: " + taskId);
        }
        try
        {
            ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
            String status = root.path("aiAnalysisStatus").asText("");
            if ("pending".equals(status) || "running".equals(status))
            {
                Map<String, Object> busy = new HashMap<>();
                busy.put("taskId", taskId);
                busy.put("status", "pending");
                busy.put("message", "AI 分析进行中");
                return busy;
            }
            String sourceVideo = root.path("sourceVideo").asText("");
            if (StringUtils.isEmpty(sourceVideo))
            {
                throw new IllegalArgumentException("分析结果缺少 sourceVideo");
            }
            Path videoPath = Path.of(sourceVideo);
            if (!Files.isRegularFile(videoPath))
            {
                throw new IllegalArgumentException("源视频不存在: " + sourceVideo);
            }
            root.put("aiAnalysisStatus", "pending");
            root.put("aiAnalysisError", "");
            root.putNull("aiAnalysis");
            String pendingJson = objectMapper.writeValueAsString(root);
            Files.writeString(resultFile.toPath(), pendingJson, StandardCharsets.UTF_8);
            ReplayTaskState state = taskMap.get(taskId);
            if (state != null)
            {
                state.resultJson = pendingJson;
            }

            final List<String> keys = modelKeys;
            final List<Double> eventTimes = extractEventTimesSec(root);
            executor.execute(() -> runAiAnalysisJob(taskId, videoPath, keys, eventTimes));

            Map<String, Object> accepted = new HashMap<>();
            accepted.put("taskId", taskId);
            accepted.put("status", "pending");
            accepted.put("message", "AI 分析已提交");
            accepted.put("eventCount", eventTimes.size());
            return accepted;
        }
        catch (IllegalArgumentException | IllegalStateException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("提交 AI 分析失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Map<String, Object> ensureAiAnalysisForTask(String taskId)
    {
        Map<String, Object> out = new HashMap<>();
        out.put("taskId", taskId);
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || !analysis.isEnabled())
        {
            out.put("status", "skipped");
            out.put("message", "AI 分析未启用");
            return out;
        }
        if (StringUtils.isEmpty(taskId))
        {
            throw new IllegalArgumentException("taskId cannot be empty");
        }
        File resultFile = analyzeResultFile(taskId);
        if (!resultFile.exists())
        {
            throw new IllegalArgumentException("分析结果不存在，请先完成 YOLO 检测: " + taskId);
        }
        try
        {
            ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
            String status = root.path("aiAnalysisStatus").asText("");
            if ("success".equals(status) && root.path("aiAnalysis").isArray() && !root.path("aiAnalysis").isEmpty())
            {
                out.put("status", "success");
                out.put("message", "AI 分析已存在");
                return out;
            }
            // 异步任务进行中：短暂轮询等待完成
            if ("pending".equals(status) || "running".equals(status))
            {
                for (int i = 0; i < 60; i++)
                {
                    Thread.sleep(1000L);
                    root = (ObjectNode) objectMapper.readTree(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
                    status = root.path("aiAnalysisStatus").asText("");
                    if ("success".equals(status) || "failed".equals(status))
                    {
                        break;
                    }
                }
                if ("success".equals(status) && root.path("aiAnalysis").isArray() && !root.path("aiAnalysis").isEmpty())
                {
                    out.put("status", "success");
                    out.put("message", "AI 分析等待完成");
                    return out;
                }
                if ("pending".equals(status) || "running".equals(status))
                {
                    out.put("status", status);
                    out.put("message", "AI 分析仍在进行，跳过等待");
                    return out;
                }
            }

            String sourceVideo = root.path("sourceVideo").asText("");
            if (StringUtils.isEmpty(sourceVideo))
            {
                throw new IllegalArgumentException("分析结果缺少 sourceVideo");
            }
            Path videoPath = Path.of(sourceVideo);
            if (!Files.isRegularFile(videoPath))
            {
                throw new IllegalArgumentException("源视频不存在: " + sourceVideo);
            }
            root.put("aiAnalysisStatus", "running");
            root.put("aiAnalysisError", "");
            Files.writeString(resultFile.toPath(), objectMapper.writeValueAsString(root), StandardCharsets.UTF_8);
            List<Double> eventTimes = extractEventTimesSec(root);
            runAiAnalysisJob(taskId, videoPath, null, eventTimes);

            root = (ObjectNode) objectMapper.readTree(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
            out.put("status", root.path("aiAnalysisStatus").asText("failed"));
            out.put("message", "AI 分析已同步完成");
            String err = root.path("aiAnalysisError").asText("");
            if (!StringUtils.isEmpty(err))
            {
                out.put("error", err);
            }
            return out;
        }
        catch (IllegalArgumentException | IllegalStateException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("同步 AI 分析失败: " + ex.getMessage(), ex);
        }
    }

    private void runAiAnalysisJob(String taskId, Path videoPath, List<String> modelKeys, List<Double> eventTimes)
    {
        File resultFile = analyzeResultFile(taskId);
        try
        {
            List<AiAnalysisResultVo> results = videoAnalysisService.analyzeLocalVideo(videoPath, modelKeys, eventTimes,
                    resolveTaskCameraId(taskId, resultFile));
            ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
            root.set("aiAnalysis", objectMapper.valueToTree(results == null ? Collections.emptyList() : results));
            root.put("aiAnalysisStatus", "success");
            root.put("aiAnalysisError", "");
            root.put("aiFrameStrategy", (eventTimes != null && !eventTimes.isEmpty()) ? "event" : "middle60");
            boolean anySuccess = results != null && results.stream().anyMatch(r -> "success".equals(r.getStatus()));
            boolean anyFailed = results != null && results.stream().anyMatch(r -> "failed".equals(r.getStatus()));
            if (anyFailed && !anySuccess)
            {
                root.put("aiAnalysisStatus", "failed");
                String err = results.stream()
                        .map(AiAnalysisResultVo::getErrorMessage)
                        .filter(s -> s != null && !s.isEmpty())
                        .findFirst()
                        .orElse("AI 分析失败");
                root.put("aiAnalysisError", err);
            }
            String updated = objectMapper.writeValueAsString(root);
            Files.writeString(resultFile.toPath(), updated, StandardCharsets.UTF_8);
            ReplayTaskState state = taskMap.get(taskId);
            if (state != null)
            {
                state.resultJson = updated;
            }
        }
        catch (Exception ex)
        {
            try
            {
                ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
                root.put("aiAnalysisStatus", "failed");
                root.put("aiAnalysisError", ex.getMessage() == null ? "AI 分析失败" : ex.getMessage());
                String updated = objectMapper.writeValueAsString(root);
                Files.writeString(resultFile.toPath(), updated, StandardCharsets.UTF_8);
                ReplayTaskState state = taskMap.get(taskId);
                if (state != null)
                {
                    state.resultJson = updated;
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private Long resolveTaskCameraId(String taskId, File resultFile)
    {
        ReplayTaskState state = taskMap.get(taskId);
        if (state != null && state.cameraId != null)
        {
            return state.cameraId;
        }
        try
        {
            if (resultFile != null && resultFile.exists())
            {
                ObjectNode root = (ObjectNode) objectMapper.readTree(
                        Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
                if (root.hasNonNull("cameraId") && root.path("cameraId").isNumber())
                {
                    long id = root.path("cameraId").asLong(0L);
                    return id > 0 ? id : null;
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    private List<Double> extractEventTimesSec(ObjectNode root)
    {
        List<Double> times = new ArrayList<>();
        if (root == null || !root.has("events") || !root.get("events").isArray())
        {
            return times;
        }
        for (com.fasterxml.jackson.databind.JsonNode node : root.get("events"))
        {
            if (node == null || node.isNull())
            {
                continue;
            }
            if (node.has("timeSec") && node.get("timeSec").isNumber())
            {
                times.add(node.get("timeSec").asDouble());
            }
            else if (node.has("time_sec") && node.get("time_sec").isNumber())
            {
                times.add(node.get("time_sec").asDouble());
            }
        }
        return times;
    }



    private PresenceReplayTaskVo loadTaskFromDisk(String taskId)

    {

        if (StringUtils.isEmpty(taskId))

        {

            return null;

        }

        File resultFile = analyzeResultFile(taskId);

        if (!resultFile.exists())

        {

            return null;

        }

        PresenceReplayTaskVo vo = new PresenceReplayTaskVo();

        vo.setTaskId(taskId);

        vo.setStatus("success");

        vo.setMessage("已从磁盘加载分析结果");

        try

        {

            vo.setResultJson(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));

        }

        catch (Exception ex)

        {

            vo.setStatus("failed");

            vo.setMessage("读取分析结果失败: " + ex.getMessage());

        }

        return vo;

    }



    private PresenceReplayTaskVo startTask(PresenceReplayStartBo bo, String prefix, boolean replayMode)

    {

        if (StringUtils.isEmpty(bo.getUploadedFileName()) && StringUtils.isEmpty(bo.getVideoPath()))

        {

            throw new IllegalArgumentException("uploadedFileName 与 videoPath 不能同时为空");

        }

        if (replayMode && StringUtils.isEmpty(bo.getUploadedFileName()))

        {

            throw new IllegalArgumentException("回放测试需要 uploadedFileName");

        }

        applyCameraConfig(bo);

        String taskId = prefix + "_" + IdUtils.fastSimpleUUID();

        ReplayTaskState state = new ReplayTaskState(taskId);

        state.cameraId = bo.getCameraId();

        taskMap.put(taskId, state);

        ThreadPoolTaskExecutor pool = replayMode ? executor : analyzeExecutor;
        try
        {
            pool.execute(() -> runTask(state, bo, replayMode));
        }
        catch (RejectedExecutionException ex)
        {
            state.status = "failed";
            state.message = replayMode ? "回放队列已满" : "分析队列已满，已跳过本片（避免无限堆积）";
            state.finishedAt = nowText();
            state.appendLog("[error] " + state.message + ": " + ex.getMessage());
            log.warn("presence task rejected mode={} taskId={} cameraId={} tip={}",
                    replayMode ? "replay" : "analyze", taskId, bo.getCameraId(), state.message);
        }

        return state.toVo();

    }



    private void runTask(ReplayTaskState state, PresenceReplayStartBo bo, boolean replayMode)

    {

        state.status = "running";

        state.startedAt = nowText();

        try

        {

            List<String> command = replayMode ? buildReplayCommand(bo) : buildAnalyzeCommand(state.taskId, bo);

            ProcessBuilder pb = new ProcessBuilder(command);

            pb.directory(new File(ingestProperties.getWorkspaceRoot()));

            pb.redirectErrorStream(true);

            Process process = PythonProcessLauncher.start(pb);

            Thread gobbler = new Thread(() ->
            {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        state.appendLog(line);
                    }
                }
                catch (Exception ex)
                {
                    state.appendLog("[error] log-gobbler: " + ex.getMessage());
                }
            }, "presence-" + (replayMode ? "replay" : "analyze") + "-log-" + state.taskId);
            gobbler.setDaemon(true);
            gobbler.start();

            int timeoutSec = resolveAnalyzeTimeoutSec(replayMode);
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                try
                {
                    process.waitFor(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                }
                try
                {
                    gobbler.join(5000L);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException(
                        (replayMode ? "回放" : "YOLO 分析") + "超时(" + timeoutSec + "s)，已强制结束进程");
            }
            try
            {
                gobbler.join(5000L);
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt();
            }
            int exitCode = process.exitValue();

            state.exitCode = exitCode;

            if (exitCode == 0)

            {

                state.status = "success";

                if (replayMode)

                {

                    state.message = "回放测试已完成";

                }

                else

                {

                    loadAnalyzeResult(state);

                    state.message = "YOLO 分析已完成";

                    // 先同步任务级 AI，再导入行为日志（导入侧会按同人合并规则过滤 pass）
                    maybeEnsureTaskAiAnalysis(state);

                    maybeAutoImportBehaviorLogs(state, bo);

                    // 分析后清理：无证据片段（门内仅出门、门外未命中在场者）删除视频+片段行+分析产物
                    boolean discarded = maybeCleanupDiscardedClip(state, bo);

                    if (!discarded)
                    {
                        maybeAutoAiAnalysis(bo);
                    }

                }

            }

            else

            {

                state.status = "failed";

                state.message = replayMode ? "回放测试失败，详见日志" : "YOLO 分析失败，详见日志";

            }

        }

        catch (Exception ex)

        {

            state.status = "failed";

            state.message = ex.getMessage();

            state.appendLog("[error] " + ex.getMessage());

        }

        finally

        {

            state.finishedAt = nowText();

        }

    }

    private int resolveAnalyzeTimeoutSec(boolean replayMode)
    {
        if (replayMode)
        {
            return 1800;
        }
        PresenceIngestProperties.ClipCapture clip = ingestProperties.getClip();
        int timeout = clip == null ? 900 : clip.getAnalyzeTimeoutSec();
        return Math.max(60, timeout);
    }



    private void applyCameraConfig(PresenceReplayStartBo bo)
    {
        if (bo == null || bo.getCameraId() == null)
        {
            return;
        }
        CameraConfigVo cfg = cameraService.getCameraConfig(bo.getCameraId());
        if (cfg == null)
        {
            throw new IllegalArgumentException("摄像头不存在: " + bo.getCameraId());
        }
        if (StringUtils.isEmpty(bo.getCameraRole()) && !StringUtils.isEmpty(cfg.getCameraRole()))
        {
            bo.setCameraRole(cfg.getCameraRole());
        }
        if (bo.getLineY() == null && cfg.getLineY() != null)
        {
            bo.setLineY(cfg.getLineY());
        }
        if (StringUtils.isEmpty(bo.getRoi()) && !StringUtils.isEmpty(cfg.getRoi()))
        {
            bo.setRoi(cfg.getRoi());
        }
        if (bo.getRefWidth() == null && cfg.getRefWidth() != null)
        {
            bo.setRefWidth(cfg.getRefWidth());
        }
        if (bo.getRefHeight() == null && cfg.getRefHeight() != null)
        {
            bo.setRefHeight(cfg.getRefHeight());
        }
    }

    private void loadAnalyzeResult(ReplayTaskState state) throws Exception

    {

        File resultFile = analyzeResultFile(state.taskId);

        if (!resultFile.exists())

        {

            state.message = "分析完成但未找到结果文件: " + resultFile.getAbsolutePath();

            return;

        }

        state.resultJson = Files.readString(resultFile.toPath(), StandardCharsets.UTF_8);

        if (state.cameraId != null)

        {

            ObjectNode root = (ObjectNode) objectMapper.readTree(state.resultJson);

            root.put("cameraId", state.cameraId);

            state.resultJson = objectMapper.writeValueAsString(root);

            Files.writeString(resultFile.toPath(), state.resultJson, StandardCharsets.UTF_8);

        }

    }

    private void maybeEnsureTaskAiAnalysis(ReplayTaskState state)
    {
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || !analysis.isEnabled() || !analysis.isAutoRun())
        {
            return;
        }
        try
        {
            Map<String, Object> ai = ensureAiAnalysisForTask(state.taskId);
            Object status = ai == null ? null : ai.get("status");
            state.appendLog("[auto-ai] task status=" + status);
            // 刷新内存中的 resultJson，供后续导入读取 AI 摘要
            File resultFile = analyzeResultFile(state.taskId);
            if (resultFile.exists())
            {
                state.resultJson = Files.readString(resultFile.toPath(), StandardCharsets.UTF_8);
            }
        }
        catch (Exception ex)
        {
            state.appendLog("[auto-ai] task failed: " + ex.getMessage());
        }
    }

    private void maybeAutoImportBehaviorLogs(ReplayTaskState state, PresenceReplayStartBo bo)
    {
        boolean autoImport = Boolean.TRUE.equals(bo.getAutoImportBehaviorLogs());
        if (!autoImport)
        {
            return;
        }
        if (StringUtils.isEmpty(state.resultJson))
        {
            state.appendLog("[auto-import] skipped: empty resultJson");
            return;
        }
        try
        {
            BehaviorLogImportFromVideoBo importBo = new BehaviorLogImportFromVideoBo();
            importBo.setTaskId(state.taskId);
            importBo.setCameraId(bo.getCameraId() != null ? bo.getCameraId() : state.cameraId);
            importBo.setClipId(bo.getClipId());
            importBo.setSceneGroupId(bo.getSceneGroupId());
            importBo.setVideoBaseTime(bo.getVideoBaseTime());
            importBo.setAllowEmptyEvents(true);
            BehaviorLogImportResultVo imported = behaviorLogService.importFromVideoAnalyze(importBo);
            String msg = imported == null ? "ok" : imported.getMessage();
            state.appendLog("[auto-import] " + msg);
            state.message = "YOLO 分析已完成，并已自动写入行为日志";
        }
        catch (Exception ex)
        {
            state.appendLog("[auto-import] failed: " + ex.getMessage());
            state.message = "YOLO 分析已完成，但自动导入行为日志失败: " + ex.getMessage();
        }
    }

    private void maybeAutoAiAnalysis(PresenceReplayStartBo bo)
    {
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        if (analysis == null || !analysis.isEnabled() || !analysis.isAutoRun())
        {
            return;
        }
        try
        {
            if (!StringUtils.isEmpty(bo.getSceneGroupId()))
            {
                videoAnalysisService.runAnalysis("scene_group", bo.getSceneGroupId(), null);
            }
            else if (bo.getClipId() != null)
            {
                videoAnalysisService.runAnalysis("clip", String.valueOf(bo.getClipId()), null);
            }
        }
        catch (Exception ex)
        {
            // 自动 AI 失败不影响主流程
        }
    }



    private boolean maybeCleanupDiscardedClip(ReplayTaskState state, PresenceReplayStartBo bo)
    {
        if (!Boolean.TRUE.equals(bo.getAutoImportBehaviorLogs()))
        {
            return false;
        }
        if (bo.getClipId() == null && StringUtils.isEmpty(bo.getSceneGroupId()))
        {
            return false;
        }
        try
        {
            boolean deleted = presenceVideoClipService.deleteClipIfNoEvidence(
                    bo.getClipId(), bo.getSceneGroupId(), state.taskId);
            if (deleted)
            {
                state.appendLog("[cleanup] 无证据片段已删除 clipId=" + bo.getClipId()
                        + " scene=" + bo.getSceneGroupId());
                state.message = "YOLO 分析完成：无证据事件，片段已删除";
            }
            return deleted;
        }
        catch (Exception ex)
        {
            state.appendLog("[cleanup] failed: " + ex.getMessage());
            return false;
        }
    }

    private File analyzeDir()

    {

        return new File(ingestProperties.getReplayProfileRoot(), "analyze");

    }



    private File analyzeResultFile(String taskId)

    {

        return new File(analyzeDir(), taskId + ".json");

    }



    private File analyzeDebugFile(String taskId)

    {

        return new File(analyzeDir(), taskId + ".mp4");

    }



    private List<String> buildAnalyzeCommand(String taskId, PresenceReplayStartBo bo)

    {

        analyzeDir().mkdirs();

        File resultFile = analyzeResultFile(taskId);

        File debugFile = analyzeDebugFile(taskId);

        String debugUrl = "/profile/analyze/" + taskId + ".mp4";



        List<String> cmd = new ArrayList<>();

        cmd.add(Objects.requireNonNullElse(ingestProperties.getReplayPythonCommand(), "python"));

        cmd.add(Objects.requireNonNullElse(ingestProperties.getAnalyzeScriptPath(), "scripts/video_analyze_yolo.py"));

        if (!StringUtils.isEmpty(bo.getVideoPath()))

        {

            cmd.add("--video-path");

            cmd.add(bo.getVideoPath());

            cmd.add("--uploaded-file-name");

            cmd.add("");

        }

        else

        {

            cmd.add("--uploaded-file-name");

            cmd.add(bo.getUploadedFileName());

        }

        cmd.add("--profile-root");

        cmd.add(ingestProperties.getReplayProfileRoot());

        cmd.add("--line-y");

        cmd.add(String.valueOf(bo.getLineY() == null ? ingestProperties.getReplayLineY() : bo.getLineY()));

        cmd.add("--roi");

        cmd.add(StringUtils.isEmpty(bo.getRoi()) ? ingestProperties.getReplayRoi() : bo.getRoi());

        cmd.add("--ref-width");

        cmd.add(String.valueOf(bo.getRefWidth() == null ? 1920 : bo.getRefWidth()));

        cmd.add("--ref-height");

        cmd.add(String.valueOf(bo.getRefHeight() == null ? 1080 : bo.getRefHeight()));

        cmd.add("--role");

        cmd.add(StringUtils.isEmpty(bo.getCameraRole()) ? "door" : bo.getCameraRole());

        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        if (live != null)
        {
            cmd.add("--conf");
            cmd.add(String.valueOf(live.getYoloConf()));
        }

        cmd.add("--debug-out");

        cmd.add(debugFile.getAbsolutePath());

        cmd.add("--result-json");

        cmd.add(resultFile.getAbsolutePath());

        cmd.add("--debug-video-url");

        cmd.add(debugUrl);

        cmd.add("--task-id");

        cmd.add(taskId);

        cmd.add("--storage-root");

        cmd.add(ingestProperties.resolveStorageRoot());

        cmd.add("--snapshot-window-sec");

        cmd.add(String.valueOf(ingestProperties.getSnapshotWindowSec() == null ? 5.0 : ingestProperties.getSnapshotWindowSec()));

        if (live != null)
        {
            cmd.add("--enter-face-hunt-max-sec");
            cmd.add(String.valueOf(live.getEnterFaceHuntMaxSec()));
            cmd.add("--enter-face-grace-sec");
            cmd.add(String.valueOf(live.getEnterFaceGraceSec()));
        }
        Double minFaceDet = ingestProperties.getFaceMinDetScore();
        cmd.add("--min-face-det-score");
        cmd.add(String.valueOf(minFaceDet == null ? 0.45 : minFaceDet));

        return cmd;

    }



    private List<String> buildReplayCommand(PresenceReplayStartBo bo)

    {

        List<String> cmd = new ArrayList<>();

        cmd.add(Objects.requireNonNullElse(ingestProperties.getReplayPythonCommand(), "python"));

        cmd.add(Objects.requireNonNullElse(ingestProperties.getReplayScriptPath(), "scripts/video_replay_worker_yolo.py"));

        cmd.add("--uploaded-file-name");

        cmd.add(bo.getUploadedFileName());

        cmd.add("--profile-root");

        cmd.add(ingestProperties.getReplayProfileRoot());

        cmd.add("--ingest-base-url");

        cmd.add(ingestProperties.getReplayIngestBaseUrl());

        cmd.add("--ingest-key");

        cmd.add(ingestProperties.getApiKey());

        cmd.add("--camera-id");

        cmd.add(String.valueOf(bo.getCameraId() == null ? ingestProperties.getDefaultCameraId() : bo.getCameraId()));

        cmd.add("--line-y");

        cmd.add(String.valueOf(bo.getLineY() == null ? ingestProperties.getReplayLineY() : bo.getLineY()));

        cmd.add("--roi");

        cmd.add(StringUtils.isEmpty(bo.getRoi()) ? ingestProperties.getReplayRoi() : bo.getRoi());

        cmd.add("--ref-width");

        cmd.add(String.valueOf(bo.getRefWidth() == null ? 1920 : bo.getRefWidth()));

        cmd.add("--ref-height");

        cmd.add(String.valueOf(bo.getRefHeight() == null ? 1080 : bo.getRefHeight()));

        if (!StringUtils.isEmpty(bo.getDebugOut()))

        {

            cmd.add("--debug-out");

            cmd.add(bo.getDebugOut());

        }

        return cmd;

    }



    private String nowText()

    {

        return LocalDateTime.now().format(TIME_FMT);

    }



    private static class ReplayTaskState

    {

        private static final int LOG_MAX_LEN = 8000;

        private final String taskId;

        private volatile String status = "pending";

        private volatile Integer exitCode;

        private volatile String startedAt;

        private volatile String finishedAt;

        private volatile String message = "任务已创建";

        private volatile String resultJson;

        private volatile Long cameraId;

        private final StringBuilder logs = new StringBuilder();



        private ReplayTaskState(String taskId)

        {

            this.taskId = taskId;

        }



        private synchronized void appendLog(String line)

        {

            logs.append(line).append('\n');

            if (logs.length() > LOG_MAX_LEN)

            {

                logs.delete(0, logs.length() - LOG_MAX_LEN);

            }

        }



        private synchronized PresenceReplayTaskVo toVo()

        {

            PresenceReplayTaskVo vo = new PresenceReplayTaskVo();

            vo.setTaskId(taskId);

            vo.setStatus(status);

            vo.setExitCode(exitCode);

            vo.setStartedAt(startedAt);

            vo.setFinishedAt(finishedAt);

            vo.setMessage(message);

            vo.setLogTail(logs.toString());

            vo.setResultJson(resultJson);

            vo.setCameraId(cameraId);

            return vo;

        }

    }

}


