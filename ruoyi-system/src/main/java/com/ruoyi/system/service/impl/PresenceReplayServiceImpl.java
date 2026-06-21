package com.ruoyi.system.service.impl;



import java.io.BufferedReader;

import java.io.File;

import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.ruoyi.common.utils.StringUtils;

import com.ruoyi.common.utils.uuid.IdUtils;

import com.ruoyi.system.config.PresenceIngestProperties;

import com.ruoyi.system.domain.bo.PresenceReplayStartBo;

import com.ruoyi.system.domain.vo.PresenceReplayTaskVo;

import com.ruoyi.system.service.IPresenceReplayService;

import jakarta.annotation.Resource;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;



@Service

public class PresenceReplayServiceImpl implements IPresenceReplayService

{

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");



    private final Map<String, ReplayTaskState> taskMap = new ConcurrentHashMap<>();



    @Autowired

    private PresenceIngestProperties ingestProperties;



    @Resource(name = "threadPoolTaskExecutor")

    private ThreadPoolTaskExecutor executor;



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

            return state.toVo();

        }

        return loadTaskFromDisk(taskId);

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

        if (StringUtils.isEmpty(bo.getUploadedFileName()))

        {

            throw new IllegalArgumentException("uploadedFileName 不能为空");

        }

        String taskId = prefix + "_" + IdUtils.fastSimpleUUID();

        ReplayTaskState state = new ReplayTaskState(taskId);

        taskMap.put(taskId, state);

        executor.execute(() -> runTask(state, bo, replayMode));

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

            Process process = pb.start();



            try (BufferedReader reader = new BufferedReader(

                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))

            {

                String line;

                while ((line = reader.readLine()) != null)

                {

                    state.appendLog(line);

                }

            }

            int exitCode = process.waitFor();

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



    private void loadAnalyzeResult(ReplayTaskState state) throws Exception

    {

        File resultFile = analyzeResultFile(state.taskId);

        if (!resultFile.exists())

        {

            state.message = "分析完成但未找到结果文件: " + resultFile.getAbsolutePath();

            return;

        }

        state.resultJson = Files.readString(resultFile.toPath(), StandardCharsets.UTF_8);

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

        cmd.add("--uploaded-file-name");

        cmd.add(bo.getUploadedFileName());

        cmd.add("--profile-root");

        cmd.add(ingestProperties.getReplayProfileRoot());

        cmd.add("--line-y");

        cmd.add(String.valueOf(bo.getLineY() == null ? ingestProperties.getReplayLineY() : bo.getLineY()));

        cmd.add("--roi");

        cmd.add(StringUtils.isEmpty(bo.getRoi()) ? ingestProperties.getReplayRoi() : bo.getRoi());

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

            return vo;

        }

    }

}


