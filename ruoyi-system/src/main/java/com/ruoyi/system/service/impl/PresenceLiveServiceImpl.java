package com.ruoyi.system.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceLiveStartBo;
import com.ruoyi.system.domain.vo.PresenceLiveTaskVo;
import com.ruoyi.system.service.IEzvizScreenService;
import com.ruoyi.system.service.IPresenceLiveService;

@Service
public class PresenceLiveServiceImpl implements IPresenceLiveService
{
    public static final int STREAM_RTSP_LAN_REQUIRED = 4601;

    public static final int STREAM_CODEC_NOT_H264 = 4602;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, LiveTaskState> taskMap = new ConcurrentHashMap<>();

    private volatile String activeTaskId;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private IEzvizScreenService ezvizScreenService;

    @Override
    public synchronized PresenceLiveTaskVo startLive(PresenceLiveStartBo bo)
    {
        validateStartBo(bo);
        stopActiveIfRunning();

        String streamMode = normalizeStreamMode(bo.getStreamMode());
        boolean lanRtsp = PresenceLiveStartBo.STREAM_LAN_RTSP.equals(streamMode);
        String streamUrl = ezvizScreenService.resolveAnalyzeStreamUrl(bo.getDeviceSerial(), bo.getChannelNo(), streamMode,
                bo.getValidCode());
        String streamProtocol = resolveStreamProtocol(streamUrl, lanRtsp);

        String taskId = "live_" + IdUtils.fastSimpleUUID();
        LiveTaskState state = new LiveTaskState(taskId, bo.getDeviceSerial(), streamMode, streamProtocol);
        taskMap.put(taskId, state);
        activeTaskId = taskId;

        try
        {
            List<String> command = buildLiveCommand(taskId, bo, streamUrl, streamProtocol);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(ingestProperties.getWorkspaceRoot()));
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            state.process = process;
            state.status = "starting";
            state.startedAt = nowText();
            state.message = "正在打开直播流…";

            Thread reader = new Thread(() -> pumpOutput(state, process), "live-log-" + taskId);
            reader.setDaemon(true);
            reader.start();

            awaitStreamReady(state, lanRtsp);
            return state.toVo();
        }
        catch (ServiceException ex)
        {
            cleanupTask(taskId, state);
            throw ex;
        }
        catch (Exception ex)
        {
            cleanupTask(taskId, state);
            throw new ServiceException("启动直播识别失败: " + ex.getMessage());
        }
    }

    @Override
    public synchronized PresenceLiveTaskVo stopLive(String taskId)
    {
        if (StringUtils.isEmpty(taskId))
        {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        LiveTaskState state = taskMap.get(taskId);
        if (state == null)
        {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        destroyProcess(state);
        state.status = "stopped";
        state.message = "直播识别已停止";
        state.finishedAt = nowText();
        if (taskId.equals(activeTaskId))
        {
            activeTaskId = null;
        }
        return state.toVo();
    }

    @Override
    public PresenceLiveTaskVo getActiveTask()
    {
        if (StringUtils.isEmpty(activeTaskId))
        {
            return null;
        }
        PresenceLiveTaskVo task = getTask(activeTaskId);
        if (task == null)
        {
            return null;
        }
        String status = StringUtils.nvl(task.getStatus(), "");
        if ("stopped".equals(status) || "failed".equals(status) || "success".equals(status))
        {
            return null;
        }
        return task;
    }

    @Override
    public PresenceLiveTaskVo getTask(String taskId)
    {
        LiveTaskState state = taskMap.get(taskId);
        if (state == null)
        {
            return null;
        }
        if (state.process != null && !state.process.isAlive() && state.exitCode == null)
        {
            try
            {
                state.exitCode = state.process.exitValue();
            }
            catch (IllegalThreadStateException ignored)
            {
            }
        }
        if (state.exitCode != null && "running".equals(state.status))
        {
            state.status = state.exitCode == 0 ? "success" : "failed";
            state.finishedAt = nowText();
            if (state.exitCode == 2)
            {
                state.message = "RTSP 连接失败：请确认服务器与摄像头在同一局域网，或切换为公网云转发";
            }
            else if (state.exitCode != 0)
            {
                state.message = "直播识别进程异常退出 exitCode=" + state.exitCode;
            }
            if (taskId.equals(activeTaskId))
            {
                activeTaskId = null;
            }
        }
        return state.toVo();
    }

    private void validateStartBo(PresenceLiveStartBo bo)
    {
        if (bo == null || StringUtils.isEmpty(bo.getDeviceSerial()))
        {
            throw new IllegalArgumentException("deviceSerial 不能为空");
        }
        if (StringUtils.isEmpty(bo.getStreamMode()))
        {
            throw new IllegalArgumentException("streamMode 不能为空");
        }
    }

    private String normalizeStreamMode(String streamMode)
    {
        if (PresenceLiveStartBo.STREAM_LAN_RTSP.equalsIgnoreCase(streamMode))
        {
            return PresenceLiveStartBo.STREAM_LAN_RTSP;
        }
        if (PresenceLiveStartBo.STREAM_CLOUD_HLS.equalsIgnoreCase(streamMode))
        {
            return PresenceLiveStartBo.STREAM_CLOUD_HLS;
        }
        throw new IllegalArgumentException("不支持的 streamMode: " + streamMode);
    }

    private void stopActiveIfRunning()
    {
        if (!StringUtils.isEmpty(activeTaskId))
        {
            try
            {
                stopLive(activeTaskId);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private void awaitStreamReady(LiveTaskState state, boolean lanRtsp) throws InterruptedException
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        long timeoutMs = lanRtsp ? live.getStreamOpenTimeoutSec() * 1000L
                : live.getCloudStreamOpenTimeoutSec() * 1000L;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            String log = state.logSnapshot();
            if (log.contains("[live] stream ready"))
            {
                state.status = "running";
                state.message = "直播识别运行中";
                return;
            }
            if (log.contains("RTSP_OPEN_FAILED"))
            {
                throwRtspLanRequired();
            }
            if (log.contains("STREAM_CODEC_NOT_H264"))
            {
                throwCodecNotH264();
            }
            if (log.contains("STREAM_OPEN_FAILED"))
            {
                throw new ServiceException(
                        "公网直播流打开失败：请确认设备已开启直播；若设备加密请填写验证码，或改用局域网 RTSP");
            }
            Integer code = state.exitCode;
            if (code != null)
            {
                if (code == 2 && lanRtsp)
                {
                    throwRtspLanRequired();
                }
                if (code == 4)
                {
                    throwCodecNotH264();
                }
                throw new ServiceException("直播识别启动失败，exitCode=" + code);
            }
            if (state.process != null && !state.process.isAlive())
            {
                try
                {
                    state.exitCode = state.process.exitValue();
                }
                catch (IllegalThreadStateException ignored)
                {
                }
                continue;
            }
            Thread.sleep(200L);
        }
        String tail = state.logSnapshot();
        if (tail.contains("[live] opening stream") && !tail.contains("STREAM_OPEN_FAILED"))
        {
            throw new ServiceException(lanRtsp ? "直播流打开超时，请检查网络或拉流模式"
                    : "公网直播流仍在连接中但超过等待上限（首帧可能需 60~90 秒）。请稍后重试；若设备加密请填写验证码，或改用局域网 RTSP");
        }
        throw new ServiceException(lanRtsp ? "局域网 RTSP 打开超时：请填写设备验证码、确认同网且已开启 RTSP，或改用公网云转发"
                : "公网直播流打开超时，请确认设备在线、已开启直播，加密设备需填写验证码");
    }

    private String resolveStreamProtocol(String streamUrl, boolean lanRtsp)
    {
        if (lanRtsp || StringUtils.nvl(streamUrl, "").toLowerCase().startsWith("rtsp"))
        {
            return "rtsp";
        }
        String lower = streamUrl.toLowerCase();
        if (lower.contains(".flv") || lower.contains("/flv/") || lower.contains("flv?"))
        {
            return "flv";
        }
        return "hls";
    }

    private void throwCodecNotH264()
    {
        throw new ServiceException(
                "摄像头视频编码不是 H264：萤石云识别流无法解码。请在萤石 App 将编码改为 H264，或改用局域网 RTSP",
                STREAM_CODEC_NOT_H264);
    }

    private void throwRtspLanRequired()
    {
        throw new ServiceException(
                "局域网 RTSP 连接失败：请填写设备验证码、确认识别服务器与摄像头同网，并在萤石 App 开启 RTSP；或切换公网云转发",
                STREAM_RTSP_LAN_REQUIRED);
    }

    private void pumpOutput(LiveTaskState state, Process process)
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
            state.appendLog("[error] " + ex.getMessage());
        }
        finally
        {
            try
            {
                state.exitCode = process.waitFor();
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                state.exitCode = -1;
            }
            if ("running".equals(state.status) || "starting".equals(state.status))
            {
                state.status = state.exitCode != null && state.exitCode == 0 ? "success" : "failed";
                state.finishedAt = nowText();
                if (state.exitCode != null && state.exitCode == 2)
                {
                    state.message = "RTSP 连接失败：请确认服务器与摄像头在同一局域网，或切换为公网云转发";
                }
                else if (!"success".equals(state.status))
                {
                    state.message = "直播识别进程已结束 exitCode=" + state.exitCode;
                }
            }
            if (state.taskId.equals(activeTaskId))
            {
                activeTaskId = null;
            }
        }
    }

    private List<String> buildLiveCommand(String taskId, PresenceLiveStartBo bo, String streamUrl, String protocol)
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        boolean lanRtsp = isLanRtsp(bo.getStreamMode());
        List<String> cmd = new ArrayList<>();
        cmd.add(Objects.requireNonNullElse(ingestProperties.getReplayPythonCommand(), "python"));
        cmd.add("-u");
        cmd.add(Objects.requireNonNullElse(ingestProperties.getLiveScriptPath(), "scripts/live_stream_worker_yolo.py"));
        cmd.add("--stream-url");
        cmd.add(streamUrl);
        cmd.add("--stream-protocol");
        cmd.add(protocol);
        cmd.add("--task-id");
        cmd.add(taskId);
        cmd.add("--storage-root");
        cmd.add(ingestProperties.resolveStorageRoot());
        cmd.add("--ingest-base-url");
        cmd.add(ingestProperties.getReplayIngestBaseUrl());
        cmd.add("--ingest-key");
        cmd.add(StringUtils.nvl(ingestProperties.getApiKey(), ""));
        cmd.add("--location-id");
        cmd.add(String.valueOf(bo.getLocationId() == null ? ingestProperties.getDefaultLocationId() : bo.getLocationId()));
        cmd.add("--line-y");
        cmd.add(String.valueOf(bo.getLineY() == null ? ingestProperties.getReplayLineY() : bo.getLineY()));
        cmd.add("--roi");
        cmd.add(StringUtils.isEmpty(bo.getRoi()) ? ingestProperties.getReplayRoi() : bo.getRoi());
        cmd.add("--target-detect-fps");
        cmd.add(String.valueOf(live.getTargetDetectFps()));
        cmd.add("--grab-flush-frames");
        cmd.add(String.valueOf(live.getGrabFlushFrames()));
        cmd.add("--rtsp-buffer-size");
        cmd.add(String.valueOf(live.getRtspBufferSize()));
        cmd.add("--event-cooldown-sec");
        cmd.add(String.valueOf(live.getEventCooldownSec()));
        cmd.add("--conf");
        cmd.add(String.valueOf(live.getYoloConf()));
        cmd.add("--imgsz");
        cmd.add(String.valueOf(live.getYoloImgsz()));
        cmd.add("--enter-infer-margin");
        cmd.add(String.valueOf(live.getEnterInferMargin()));
        cmd.add("--exit-infer-margin");
        cmd.add(String.valueOf(live.getExitInferMargin()));
        cmd.add("--snapshot-window-sec");
        cmd.add(String.valueOf(ingestProperties.getSnapshotWindowSec()));
        cmd.add("--enter-face-hunt-max-sec");
        cmd.add(String.valueOf(live.getEnterFaceHuntMaxSec()));
        cmd.add("--enter-face-grace-sec");
        cmd.add(String.valueOf(live.getEnterFaceGraceSec()));
        cmd.add("--face-min-det-score");
        cmd.add(String.valueOf(ingestProperties.getFaceMinDetScore()));
        cmd.add("--cross-confirm-frames");
        cmd.add("1");
        cmd.add("--open-timeout-sec");
        cmd.add(String.valueOf(lanRtsp ? live.getRtspOpenTimeoutSec() : live.getCloudOpenTimeoutSec()));
        return cmd;
    }

    private boolean isLanRtsp(String streamMode)
    {
        return PresenceLiveStartBo.STREAM_LAN_RTSP.equalsIgnoreCase(streamMode);
    }

    private void destroyProcess(LiveTaskState state)
    {
        Process process = state.process;
        if (process != null && process.isAlive())
        {
            process.destroyForcibly();
        }
    }

    private void cleanupTask(String taskId, LiveTaskState state)
    {
        destroyProcess(state);
        taskMap.remove(taskId);
        if (taskId.equals(activeTaskId))
        {
            activeTaskId = null;
        }
    }

    private String nowText()
    {
        return LocalDateTime.now().format(TIME_FMT);
    }

    private static class LiveTaskState
    {
        private static final int LOG_MAX_LEN = 12000;

        private final String taskId;

        private final String deviceSerial;

        private final String streamMode;

        private final String streamProtocol;

        private volatile Process process;

        private volatile String status = "pending";

        private volatile Integer exitCode;

        private volatile String startedAt;

        private volatile String finishedAt;

        private volatile String message = "任务已创建";

        private final StringBuilder logs = new StringBuilder();

        private LiveTaskState(String taskId, String deviceSerial, String streamMode, String streamProtocol)
        {
            this.taskId = taskId;
            this.deviceSerial = deviceSerial;
            this.streamMode = streamMode;
            this.streamProtocol = streamProtocol;
        }

        private synchronized void appendLog(String line)
        {
            logs.append(line).append('\n');
            if (logs.length() > LOG_MAX_LEN)
            {
                logs.delete(0, logs.length() - LOG_MAX_LEN);
            }
        }

        private synchronized String logSnapshot()
        {
            return logs.toString();
        }

        private synchronized PresenceLiveTaskVo toVo()
        {
            PresenceLiveTaskVo vo = new PresenceLiveTaskVo();
            vo.setTaskId(taskId);
            vo.setStatus(status);
            vo.setExitCode(exitCode);
            vo.setStartedAt(startedAt);
            vo.setFinishedAt(finishedAt);
            vo.setMessage(message);
            vo.setLogTail(logs.toString());
            vo.setDeviceSerial(deviceSerial);
            vo.setStreamMode(streamMode);
            vo.setStreamProtocol(streamProtocol);
            return vo;
        }
    }
}
