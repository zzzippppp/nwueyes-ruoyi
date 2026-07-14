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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.system.domain.vo.CameraConfigVo;
import com.ruoyi.system.domain.vo.PresenceLiveProbeVo;
import com.ruoyi.system.domain.vo.PresenceLiveTaskVo;
import com.ruoyi.system.storage.PresenceStoragePaths;
import com.ruoyi.system.service.IEzvizScreenService;
import com.ruoyi.system.service.IPresenceLiveService;

/**
 * 区域闯入直播识别服务实现
 * <p>
 * 负责管理直播识别任务的全生命周期：启动、监控、停止。
 * 通过调用 Python 脚本（YOLO 识别进程）对摄像头实时视频流进行识别分析，
 * 检测人员闯入事件。支持局域网 RTSP 和公网云转发两种拉流模式。
 * </p>
 *
 * @author ruoyi
 */
@Service
public class PresenceLiveServiceImpl implements IPresenceLiveService
{
    /** 自定义错误码：局域网 RTSP 连接失败，需切换至局域网或公网云转发 */
    public static final int STREAM_RTSP_LAN_REQUIRED = 4601;

    /** 自定义错误码：视频编码非 H.264，无法解码识别流 */
    public static final int STREAM_CODEC_NOT_H264 = 4602;

    /** 统一的时间格式化器 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 任务映射表，key 为任务 ID，value 为任务状态（线程安全） */
    private final Map<String, LiveTaskState> taskMap = new ConcurrentHashMap<>();

    /** 当前活跃任务 ID，同一时间只允许一个直播识别任务运行（volatile 保证可见性） */
    private volatile String activeTaskId;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private IEzvizScreenService ezvizScreenService;

    @Autowired
    private com.ruoyi.system.service.ICameraService cameraService;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Override
    public synchronized PresenceLiveTaskVo startLive(PresenceLiveStartBo bo)
    {
        applyCameraFromBo(bo);
        // 1. 参数校验
        validateStartBo(bo);
        // 2. 保证同时只有一个直播任务：先停掉旧任务
        stopActiveIfRunning();

        int channelNo = bo.getChannelNo() == null || bo.getChannelNo() < 1 ? 1 : bo.getChannelNo();
        Long cameraId = bo.getCameraId();
        if (cameraId == null)
        {
            cameraId = cameraService.resolveOrCreateCamera(bo.getDeviceSerial(), channelNo,
                    "监控点位-" + bo.getDeviceSerial());
            bo.setCameraId(cameraId);
        }

        com.ruoyi.system.domain.vo.CameraConfigVo cameraConfig = cameraService.getCameraConfig(cameraId);
        mergeCameraConfigIntoBo(bo, cameraConfig);

        // 3. 归一化拉流模式，解析拉流地址和协议
        String streamMode = normalizeStreamMode(bo.getStreamMode());
        boolean lanRtsp = PresenceLiveStartBo.STREAM_LAN_RTSP.equals(streamMode);
        String localIp = cameraConfig != null ? cameraConfig.getIpAddr() : null;
        String streamUrl = ezvizScreenService.resolveAnalyzeStreamUrl(bo.getDeviceSerial(), bo.getChannelNo(), streamMode,
                bo.getValidCode(), localIp);
        String streamProtocol = resolveStreamProtocol(streamUrl, lanRtsp);

        // 4. 创建任务状态记录（清理历史已结束任务，避免内存堆积）
        purgeFinishedTasks();
        String taskId = "live_" + IdUtils.fastSimpleUUID();
        LiveTaskState state = new LiveTaskState(taskId, bo.getDeviceSerial(), streamMode, streamProtocol);
        taskMap.put(taskId, state);
        activeTaskId = taskId;

        try
        {
            // 5. 构建命令并启动 Python 子进程
            List<String> command = buildLiveCommand(taskId, bo, streamUrl, streamProtocol);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(ingestProperties.getWorkspaceRoot()));
            pb.environment().put("PYTHONUNBUFFERED", "1");   // 禁用 Python 输出缓冲，确保日志实时可读
            pb.redirectErrorStream(true);                     // 合并标准错误到标准输出
            Process process = pb.start();
            state.process = process;
            state.status = "starting";
            state.startedAt = nowText();
            state.message = "正在打开直播流…";

            // 6. 启动守护线程持续读取子进程输出日志
            Thread reader = new Thread(() -> pumpOutput(state, process), "live-log-" + taskId);
            reader.setDaemon(true);
            reader.start();

            // 7. 异步等待 stream ready，HTTP 立即返回，避免公网首帧慢导致前端 90s 超时
            Thread readyWaiter = new Thread(() -> waitForStreamReadyAsync(taskId, state, lanRtsp),
                    "live-ready-" + taskId);
            readyWaiter.setDaemon(true);
            readyWaiter.start();
            return state.toVo();
        }
        catch (ServiceException ex)
        {
            // 业务异常直接向上抛，清理任务
            cleanupTask(taskId, state);
            throw ex;
        }
        catch (Exception ex)
        {
            // 其他异常包装为 ServiceException
            cleanupTask(taskId, state);
            throw new ServiceException("启动直播识别失败: " + ex.getMessage());
        }
    }

    /**
     * 停止指定直播识别任务
     *
     * @param taskId 任务 ID
     * @return 更新后的任务视图对象
     * @throws IllegalArgumentException 当 taskId 为空或任务不存在时
     */
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
            return buildNotFoundTask(taskId);
        }
        // 强制终止子进程，更新状态
        destroyProcess(state);
        state.status = "stopped";
        state.message = "直播识别已停止";
        state.finishedAt = nowText();
        // 清除活跃任务标记
        if (taskId.equals(activeTaskId))
        {
            activeTaskId = null;
        }
        return state.toVo();
    }

    /**
     * 获取当前活跃的直播任务
     * <p>
     * 活跃任务指状态为 running 或 starting 的任务，已终止（stopped/failed/success）的任务不算。
     * </p>
     *
     * @return 活跃任务视图对象，无活跃任务时返回 null
     */
    @Override
    public PresenceLiveTaskVo getActiveTask()
    {
        if (StringUtils.isEmpty(activeTaskId))
        {
            return null;
        }
        PresenceLiveTaskVo task = getTask(activeTaskId);
        // 仅 running/starting 状态视为活跃
        String status = StringUtils.nvl(task.getStatus(), "");
        if ("stopped".equals(status) || "failed".equals(status) || "success".equals(status)
                || "not_found".equals(status))
        {
            return null;
        }
        return task;
    }

    @Override
    public PresenceLiveProbeVo captureProbeFrame(PresenceLiveStartBo bo)
    {
        applyCameraFromBo(bo);
        validateStartBo(bo);

        int channelNo = bo.getChannelNo() == null || bo.getChannelNo() < 1 ? 1 : bo.getChannelNo();
        Long cameraId = bo.getCameraId();
        if (cameraId == null)
        {
            cameraId = cameraService.resolveOrCreateCamera(bo.getDeviceSerial(), channelNo,
                    "监控点位-" + bo.getDeviceSerial());
            bo.setCameraId(cameraId);
        }

        CameraConfigVo cameraConfig = cameraService.getCameraConfig(cameraId);
        mergeCameraConfigIntoBo(bo, cameraConfig);

        String streamMode = normalizeStreamMode(bo.getStreamMode());
        boolean lanRtsp = PresenceLiveStartBo.STREAM_LAN_RTSP.equals(streamMode);
        String localIp = cameraConfig != null ? cameraConfig.getIpAddr() : null;
        String streamUrl = ezvizScreenService.resolveAnalyzeStreamUrl(bo.getDeviceSerial(), bo.getChannelNo(), streamMode,
                bo.getValidCode(), localIp);
        String streamProtocol = resolveStreamProtocol(streamUrl, lanRtsp);

        try
        {
            storagePaths.ensureBaseDirectories();
        }
        catch (Exception ex)
        {
            throw new ServiceException("创建标定目录失败: " + ex.getMessage());
        }

        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        List<String> command = new ArrayList<>();
        command.add(Objects.requireNonNullElse(ingestProperties.getReplayPythonCommand(), "python"));
        command.add("-u");
        command.add(Objects.requireNonNullElse(ingestProperties.getProbeScriptPath(), "scripts/capture_stream_probe.py"));
        command.add("--stream-url");
        command.add(streamUrl);
        command.add("--stream-protocol");
        command.add(streamProtocol);
        command.add("--storage-root");
        command.add(ingestProperties.resolveStorageRoot());
        command.add("--camera-id");
        command.add(String.valueOf(cameraId));
        command.add("--line-y");
        command.add(String.valueOf(bo.getLineY() == null ? ingestProperties.getReplayLineY() : bo.getLineY()));
        command.add("--roi");
        command.add(StringUtils.isEmpty(bo.getRoi()) ? ingestProperties.getReplayRoi() : bo.getRoi());
        command.add("--open-timeout-sec");
        if (lanRtsp)
        {
            command.add(String.valueOf(Math.max(live.getRtspOpenTimeoutSec(), live.getStreamOpenTimeoutSec())));
        }
        else
        {
            command.add(String.valueOf(Math.max(live.getCloudOpenTimeoutSec(), live.getCloudStreamOpenTimeoutSec())));
        }
        command.add("--rtsp-buffer-size");
        command.add(String.valueOf(live.getRtspBufferSize()));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(ingestProperties.getWorkspaceRoot()));
        pb.environment().put("PYTHONUNBUFFERED", "1");
        pb.redirectErrorStream(true);

        try
        {
            Process process = pb.start();
            long waitSec = lanRtsp
                    ? (long) Math.ceil(Math.max(live.getRtspOpenTimeoutSec(), live.getStreamOpenTimeoutSec()) + 30)
                    : (long) Math.ceil(Math.max(live.getCloudOpenTimeoutSec(), live.getCloudStreamOpenTimeoutSec()) + 30);
            String output = readProcessOutput(process, waitSec);
            int exitCode = process.waitFor();
            JSONObject json = parseProbeJson(output);
            if (exitCode != 0 || json == null || !Boolean.TRUE.equals(json.getBoolean("ok")))
            {
                String msg = json != null ? json.getString("message") : null;
                if (StringUtils.isEmpty(msg))
                {
                    msg = resolveProbeFailureMessage(exitCode, output, lanRtsp);
                }
                throw new ServiceException(msg);
            }

            PresenceLiveProbeVo vo = new PresenceLiveProbeVo();
            vo.setCameraId(cameraId);
            String rawFileName = json.getString("rawFileName");
            String overlayFileName = json.getString("overlayFileName");
            if (!StringUtils.isEmpty(rawFileName))
            {
                vo.setRawImageUrl(storagePaths.buildProbeFileUrl(rawFileName));
            }
            if (!StringUtils.isEmpty(overlayFileName))
            {
                vo.setOverlayImageUrl(storagePaths.buildProbeFileUrl(overlayFileName));
            }
            vo.setWidth(json.getInteger("width"));
            vo.setHeight(json.getInteger("height"));
            vo.setLineY(json.getInteger("lineY"));
            vo.setRoi(json.getString("roi"));
            vo.setMessage("抽帧成功，请使用原图标注门线（坐标系 1920×1080）");
            return vo;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("抽帧失败: " + ex.getMessage());
        }
    }

    /**
     * 根据任务 ID 获取任务详情
     * <p>
     * 该方法会检查子进程是否已退出，若退出则同步更新状态。
     * </p>
     *
     * @param taskId 任务 ID
     * @return 任务视图对象，不存在时返回 null
     */
    @Override
    public PresenceLiveTaskVo getTask(String taskId)
    {
        LiveTaskState state = taskMap.get(taskId);
        if (state == null)
        {
            return buildNotFoundTask(taskId);
        }
        // 检测子进程是否已退出，尝试获取退出码
        if (state.process != null && !state.process.isAlive() && state.exitCode == null)
        {
            try
            {
                state.exitCode = state.process.exitValue();
            }
            catch (IllegalThreadStateException ignored)
            {
                // 进程尚未结束，忽略
            }
        }
        // 进程已退出但状态仍为 running，根据退出码判定最终状态
        if (state.exitCode != null && "running".equals(state.status))
        {
            state.status = state.exitCode == 0 ? "success" : "failed";
            state.finishedAt = nowText();
            if (state.exitCode != 0)
            {
                state.message = resolveLiveFailureMessage(state.exitCode, state.logSnapshot(), state.isLanRtsp());
            }
            // 清理活跃任务标记
            if (taskId.equals(activeTaskId))
            {
                activeTaskId = null;
            }
        }
        return state.toVo();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验启动请求参数
     *
     * @param bo 启动参数对象
     * @throws IllegalArgumentException 当必填字段为空时
     */
    private void validateStartBo(PresenceLiveStartBo bo)
    {
        if (bo == null)
        {
            throw new IllegalArgumentException("启动参数不能为空");
        }
        if (bo.getCameraId() == null && StringUtils.isEmpty(bo.getDeviceSerial()))
        {
            throw new IllegalArgumentException("请选择识别摄像头或填写设备序列号");
        }
        if (StringUtils.isEmpty(bo.getStreamMode()))
        {
            throw new IllegalArgumentException("streamMode 不能为空");
        }
    }

    private void applyCameraFromBo(PresenceLiveStartBo bo)
    {
        if (bo == null || bo.getCameraId() == null)
        {
            return;
        }
        CameraConfigVo cfg = cameraService.getCameraConfig(bo.getCameraId());
        if (cfg == null)
        {
            throw new ServiceException("摄像头不存在: " + bo.getCameraId());
        }
        if (!StringUtils.isEmpty(cfg.getSerialNo()))
        {
            bo.setDeviceSerial(cfg.getSerialNo());
        }
        if (bo.getChannelNo() == null || bo.getChannelNo() < 1)
        {
            bo.setChannelNo(cfg.getChannelNo() == null ? 1 : cfg.getChannelNo());
        }
        mergeCameraConfigIntoBo(bo, cfg);
    }

    private void mergeCameraConfigIntoBo(PresenceLiveStartBo bo, CameraConfigVo cameraConfig)
    {
        if (bo == null || cameraConfig == null)
        {
            return;
        }
        if (bo.getLineY() == null && cameraConfig.getLineY() != null)
        {
            bo.setLineY(cameraConfig.getLineY());
        }
        if (StringUtils.isEmpty(bo.getRoi()) && !StringUtils.isEmpty(cameraConfig.getRoi()))
        {
            bo.setRoi(cameraConfig.getRoi());
        }
        if (StringUtils.isEmpty(bo.getValidCode()) && !StringUtils.isEmpty(cameraConfig.getVerifyCode()))
        {
            bo.setValidCode(cameraConfig.getVerifyCode());
        }
    }

    private String readProcessOutput(Process process, long timeoutSec) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
            while (System.currentTimeMillis() < deadline)
            {
                while (reader.ready())
                {
                    String line = reader.readLine();
                    if (line != null)
                    {
                        sb.append(line).append('\n');
                    }
                }
                if (!process.isAlive())
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        sb.append(line).append('\n');
                    }
                    break;
                }
                Thread.sleep(200L);
            }
        }
        return sb.toString();
    }

    private JSONObject parseProbeJson(String output)
    {
        if (StringUtils.isEmpty(output))
        {
            return null;
        }
        String[] lines = output.split("\n");
        for (int i = lines.length - 1; i >= 0; i--)
        {
            String line = lines[i].trim();
            if (line.startsWith("{") && line.endsWith("}"))
            {
                try
                {
                    return JSON.parseObject(line);
                }
                catch (Exception ignored)
                {
                    // try previous line
                }
            }
        }
        return null;
    }

    private String resolveProbeFailureMessage(int exitCode, String output, boolean lanRtsp)
    {
        if (exitCode == 3)
        {
            return lanRtsp ? "局域网 RTSP 抽帧失败：请确认同网、验证码与 RTSP 已开启"
                    : "公网直播抽帧超时：请确认设备在线、验证码正确，或改用局域网 RTSP";
        }
        if (exitCode == 4)
        {
            throwCodecNotH264();
        }
        String tail = output == null ? "" : output.trim();
        if (tail.length() > 200)
        {
            tail = tail.substring(tail.length() - 200);
        }
        return "抽帧失败（exitCode=" + exitCode + "）" + (StringUtils.isEmpty(tail) ? "" : ": " + tail);
    }

    /**
     * 归一化拉流模式字符串
     * <p>
     * 将用户输入的拉流模式映射为标准化常量值（忽略大小写）：
     * LAN_RTSP → 局域网 RTSP，CLOUD_HLS → 公网云转发。
     * </p>
     *
     * @param streamMode 原始拉流模式字符串
     * @return 标准化后的拉流模式常量
     * @throws IllegalArgumentException 当模式不在支持范围内时
     */
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

    /**
     * 停止当前活跃任务（如果存在）
     * <p>
     * 用于新任务启动前的清理，确保同一时间只有一个直播任务。
     * 停止过程中的异常会被静默吞掉，避免阻塞新任务的启动。
     * </p>
     */
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
                // 强制清理旧任务，忽略可能的异常
            }
        }
    }

    /**
     * 后台等待拉流就绪；失败时更新任务状态并清理子进程。
     */
    private void waitForStreamReadyAsync(String taskId, LiveTaskState state, boolean lanRtsp)
    {
        try
        {
            awaitStreamReady(state, lanRtsp);
        }
        catch (ServiceException ex)
        {
            markTaskFailed(taskId, state, ex.getMessage());
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            markTaskFailed(taskId, state, "启动直播识别被中断");
        }
        catch (Exception ex)
        {
            markTaskFailed(taskId, state, "启动直播识别失败: " + ex.getMessage());
        }
    }

    /**
     * 等待子进程输出流就绪信号（超时轮询）
     * <p>
     * 每隔 200ms 读取子进程日志输出，检查是否出现 "stream ready" 信号或各类错误标识。
     * 局域网 RTSP 和公网模式的超时时间不同，分别由配置控制。
     * </p>
     *
     * @param state   任务状态
     * @param lanRtsp 是否为局域网 RTSP 模式
     * @throws InterruptedException 线程被中断
     * @throws ServiceException     流打开失败或超时
     */
    private void awaitStreamReady(LiveTaskState state, boolean lanRtsp) throws InterruptedException
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        long timeoutMs = lanRtsp
                ? (long) Math.max(live.getStreamOpenTimeoutSec(), Math.ceil(live.getRtspOpenTimeoutSec()) + 20L) * 1000L
                : (long) Math.max(live.getCloudStreamOpenTimeoutSec(),
                        Math.ceil(live.getCloudOpenTimeoutSec()) + 30L) * 1000L;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            String log = state.logSnapshot();
            // 收到 stream ready → 流打开成功，任务进入运行态
            if (log.contains("[live] stream ready"))
            {
                state.status = "running";
                state.message = "直播识别运行中";
                return;
            }
            // RTSP 连接失败 → 可能是网络不通或未开启 RTSP
            if (log.contains("RTSP_OPEN_FAILED"))
            {
                throwRtspLanRequired();
            }
            // 编码格式不是 H.264 → 提示用户切换编码
            if (log.contains("STREAM_CODEC_NOT_H264"))
            {
                throwCodecNotH264();
            }
            // 公网流打开失败 → 提示检查设备状态或切换模式
            if (log.contains("STREAM_OPEN_FAILED"))
            {
                throw new ServiceException(
                        "公网直播流打开失败：请确认设备已开启直播；若设备加密请填写验证码，或改用局域网 RTSP");
            }
            // 子进程已退出，根据退出码判断失败原因
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
                throw new ServiceException(resolveLiveFailureMessage(code, log, lanRtsp));
            }
            // 进程意外退出
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
        // 超时处理：正在打开但没等到 ready 信号
        String tail = state.logSnapshot();
        if (tail.contains("[live] opening stream") && !tail.contains("STREAM_OPEN_FAILED"))
        {
            throw new ServiceException(lanRtsp ? "直播流打开超时，请检查网络或拉流模式"
                    : "公网直播流仍在连接中但超过等待上限（首帧可能需 60~90 秒）。请稍后重试；若设备加密请填写验证码，或改用局域网 RTSP");
        }
        throw new ServiceException(lanRtsp ? "局域网 RTSP 打开超时：请填写设备验证码、确认同网且已开启 RTSP，或改用公网云转发"
                : "公网直播流打开超时，请确认设备在线、已开启直播，加密设备需填写验证码");
    }

    /**
     * 根据拉流地址解析传输协议
     * <p>
     * 判断逻辑：
     * <ul>
     *   <li>局域网 RTSP 模式或 URL 以 rtsp 开头 → rtsp</li>
     *   <li>URL 包含 flv 特征 → flv</li>
     *   <li>其他情况默认 → hls</li>
     * </ul>
     * </p>
     *
     * @param streamUrl 拉流地址
     * @param lanRtsp   是否为局域网 RTSP 模式
     * @return 协议名称（rtsp/flv/hls）
     */
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

    /**
     * 根据进程退出码与日志尾部，生成面向用户的失败说明。
     */
    private String resolveLiveFailureMessage(Integer exitCode, String logTail, boolean lanRtsp)
    {
        String log = StringUtils.nvl(logTail, "");
        String fatalDetail = extractFatalDetail(log);
        if (exitCode == null)
        {
            return "直播识别异常结束";
        }
        if (exitCode == 0)
        {
            return "直播识别已正常结束";
        }
        if (exitCode == 2)
        {
            if (log.contains("401 Unauthorized") || log.contains("401"))
            {
                return "RTSP 鉴权失败：请填写正确的设备验证码，或确认 RTSP 用户名/密码";
            }
            if (log.contains("缺少 ultralytics"))
            {
                return "缺少 Python 依赖 ultralytics，请在识别环境中执行 pip install ultralytics";
            }
            return lanRtsp
                    ? "局域网 RTSP 连接失败：请确认服务器与摄像头同网、已在萤石 App 开启 RTSP，或切换公网云转发"
                    : "RTSP 连接失败：请检查拉流地址与网络";
        }
        if (exitCode == 3)
        {
            return "公网直播流打开失败：请确认设备在线、已开启直播；加密设备需填写验证码，或改用局域网 RTSP";
        }
        if (exitCode == 4)
        {
            return "视频编码不是 H264：请在萤石 App 将编码改为 H264，或改用局域网 RTSP";
        }
        if (exitCode == 1)
        {
            if (log.contains("MemoryError") || log.toLowerCase().contains("out of memory"))
            {
                return "识别进程内存不足：主码流分辨率较高，请保持 clip 关闭、降低码率/分辨率，或重启后再试";
            }
            if (log.contains("frame reader crashed") || log.contains("Stream timeout"))
            {
                return "直播流中途断开：请检查网络与摄像头是否在线，然后重新点击「开始识别」";
            }
            if (fatalDetail != null)
            {
                return "识别运行中崩溃：" + fatalDetail;
            }
            if (log.contains("[live] stream ready"))
            {
                return "识别运行中异常退出：拉流已成功，可能是网络中断、内存不足或推理报错";
            }
            return "识别进程启动后异常退出，请稍后重试";
        }
        if (exitCode == -1)
        {
            return "识别任务被中断";
        }
        String suffix = fatalDetail != null ? "：" + fatalDetail : "";
        return "直播识别异常退出（exitCode=" + exitCode + "）" + suffix;
    }

    private String extractFatalDetail(String log)
    {
        if (StringUtils.isEmpty(log))
        {
            return null;
        }
        int idx = log.lastIndexOf("[fatal]");
        if (idx < 0)
        {
            return null;
        }
        String line = log.substring(idx);
        int newline = line.indexOf('\n');
        if (newline > 0)
        {
            line = line.substring(0, newline);
        }
        line = line.replaceFirst("\\[fatal\\]\\s*", "").trim();
        if (line.startsWith("live loop crashed:"))
        {
            line = line.substring("live loop crashed:".length()).trim();
        }
        else if (line.startsWith("frame reader crashed:"))
        {
            line = line.substring("frame reader crashed:".length()).trim();
        }
        if (line.length() > 160)
        {
            line = line.substring(0, 157) + "...";
        }
        return StringUtils.isEmpty(line) ? null : line;
    }

    /**
     * 抛出视频编码非 H.264 的业务异常
     */
    private void throwCodecNotH264()
    {
        throw new ServiceException(
                "摄像头视频编码不是 H264：萤石云识别流无法解码。请在萤石 App 将编码改为 H264，或改用局域网 RTSP",
                STREAM_CODEC_NOT_H264);
    }

    /**
     * 抛出局域网 RTSP 连接失败的业务异常
     */
    private void throwRtspLanRequired()
    {
        throw new ServiceException(
                "局域网 RTSP 连接失败：请填写设备验证码、确认识别服务器与摄像头同网，并在萤石 App 开启 RTSP；或切换公网云转发",
                STREAM_RTSP_LAN_REQUIRED);
    }

    /**
     * 子进程输出泵——持续读取进程 stdout/stderr 并追加到日志缓冲区
     * <p>
     * 该方法在独立守护线程中运行，阻塞读取直到进程结束。
     * 进程结束后同步更新任务退出码和最终状态。
     * </p>
     *
     * @param state   任务状态
     * @param process 子进程对象
     */
    private void pumpOutput(LiveTaskState state, Process process)
    {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            // 逐行读取子进程输出，追加到任务日志
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
            // 等待进程结束，获取退出码
            try
            {
                state.exitCode = process.waitFor();
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                state.exitCode = -1;
            }
            // 根据退出码更新任务最终状态
            if ("running".equals(state.status) || "starting".equals(state.status))
            {
                state.status = state.exitCode != null && state.exitCode == 0 ? "success" : "failed";
                state.finishedAt = nowText();
                if (!"success".equals(state.status) && state.exitCode != null)
                {
                    state.message = resolveLiveFailureMessage(state.exitCode, state.logSnapshot(), state.isLanRtsp());
                }
            }
            if (state.taskId.equals(activeTaskId))
            {
                activeTaskId = null;
            }
        }
    }

    /**
     * 构建启动 Python 识别脚本的命令行参数
     * <p>
     * 根据配置和请求参数组装完整的命令行，包含拉流地址、存储路径、
     * 检测参数（YOLO 置信度/图像尺寸、绊线位置、ROI 区域）、
     * 截图/录像切片的各类时延参数等。
     * </p>
     *
     * @param taskId    任务 ID
     * @param bo        启动参数
     * @param streamUrl 拉流地址
     * @param protocol  传输协议
     * @return 命令行参数列表
     */
    private List<String> buildLiveCommand(String taskId, PresenceLiveStartBo bo, String streamUrl, String protocol)
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        boolean lanRtsp = isLanRtsp(bo.getStreamMode());
        List<String> cmd = new ArrayList<>();
        // Python 解释器路径
        cmd.add(Objects.requireNonNullElse(ingestProperties.getReplayPythonCommand(), "python"));
        cmd.add("-u");  // 无缓冲输出
        // 识别脚本路径
        cmd.add(Objects.requireNonNullElse(ingestProperties.getLiveScriptPath(), "scripts/live_stream_worker_yolo.py"));

        // ---- 拉流参数 ----
        cmd.add("--stream-url");
        cmd.add(streamUrl);
        cmd.add("--stream-protocol");
        cmd.add(protocol);
        cmd.add("--task-id");
        cmd.add(taskId);

        // ---- 存储与回调地址 ----
        cmd.add("--storage-root");
        cmd.add(ingestProperties.resolveStorageRoot());
        cmd.add("--ingest-base-url");
        cmd.add(ingestProperties.getReplayIngestBaseUrl());
        cmd.add("--ingest-key");
        cmd.add(StringUtils.nvl(ingestProperties.getApiKey(), ""));

        // ---- 检测区域参数（优先使用请求参数，否则使用配置默认值） ----
        cmd.add("--camera-id");
        cmd.add(String.valueOf(bo.getCameraId() == null ? ingestProperties.getDefaultCameraId() : bo.getCameraId()));
        cmd.add("--line-y");
        cmd.add(String.valueOf(bo.getLineY() == null ? ingestProperties.getReplayLineY() : bo.getLineY()));
        cmd.add("--roi");
        cmd.add(StringUtils.isEmpty(bo.getRoi()) ? ingestProperties.getReplayRoi() : bo.getRoi());

        // ---- YOLO 检测参数 ----
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

        // ---- 截图窗口 ----
        cmd.add("--snapshot-window-sec");
        cmd.add(String.valueOf(ingestProperties.getSnapshotWindowSec()));

        // ---- 视频切片参数 ----
        cmd.add("--clip-enabled");
        cmd.add(String.valueOf(ingestProperties.getClip().isEnabled()).toLowerCase());
        cmd.add("--clip-pre-roll-sec");
        cmd.add(String.valueOf(ingestProperties.getClip().getPreRollSec()));
        cmd.add("--clip-post-roll-sec");
        cmd.add(String.valueOf(ingestProperties.getClip().getPostRollSec()));
        cmd.add("--clip-track-lost-sec");
        cmd.add(String.valueOf(ingestProperties.getClip().getTrackLostSec()));
        cmd.add("--clip-scene-merge-gap-sec");
        cmd.add(String.valueOf(ingestProperties.getClip().getSceneMergeGapSec()));
        cmd.add("--clip-local-recording-enabled");
        cmd.add(String.valueOf(ingestProperties.getClip().isLocalRecordingEnabled()).toLowerCase());
        cmd.add("--device-serial");
        cmd.add(StringUtils.nvl(bo.getDeviceSerial(), ""));
        cmd.add("--channel-no");
        cmd.add(String.valueOf(bo.getChannelNo() == null ? 1 : bo.getChannelNo()));
        cmd.add("--valid-code");
        cmd.add(StringUtils.nvl(bo.getValidCode(), ""));

        // ---- 人脸识别相关参数 ----
        cmd.add("--enter-face-hunt-max-sec");
        cmd.add(String.valueOf(live.getEnterFaceHuntMaxSec()));
        cmd.add("--enter-face-grace-sec");
        cmd.add(String.valueOf(live.getEnterFaceGraceSec()));
        cmd.add("--face-min-det-score");
        cmd.add(String.valueOf(ingestProperties.getFaceMinDetScore()));

        // ---- 交叉确认与超时 ----
        cmd.add("--cross-confirm-frames");
        cmd.add("1");
        cmd.add("--open-timeout-sec");
        if (lanRtsp)
        {
            cmd.add(String.valueOf(Math.max(live.getRtspOpenTimeoutSec(), live.getStreamOpenTimeoutSec())));
        }
        else
        {
            cmd.add(String.valueOf(Math.max(live.getCloudOpenTimeoutSec(), live.getCloudStreamOpenTimeoutSec())));
        }
        return cmd;
    }

    /**
     * 判断拉流模式是否为局域网 RTSP
     *
     * @param streamMode 拉流模式字符串
     * @return true 表示局域网 RTSP 模式
     */
    private boolean isLanRtsp(String streamMode)
    {
        return PresenceLiveStartBo.STREAM_LAN_RTSP.equalsIgnoreCase(streamMode);
    }

    /**
     * 强制终止子进程
     *
     * @param state 任务状态
     */
    private void destroyProcess(LiveTaskState state)
    {
        Process process = state.process;
        if (process != null && process.isAlive())
        {
            process.destroyForcibly();
        }
    }

    /**
     * 标记任务失败并终止子进程，但保留任务记录供前端轮询读取失败原因与日志。
     */
    private void markTaskFailed(String taskId, LiveTaskState state, String message)
    {
        destroyProcess(state);
        state.status = "failed";
        state.message = message;
        state.finishedAt = nowText();
        if (taskId.equals(activeTaskId))
        {
            activeTaskId = null;
        }
    }

    /**
     * 清理任务资源：终止子进程并从任务表移除（仅用于启动请求同步失败、客户端尚未轮询的场景）。
     */
    private void cleanupTask(String taskId, LiveTaskState state)
    {
        destroyProcess(state);
        taskMap.remove(taskId);
        if (taskId.equals(activeTaskId))
        {
            activeTaskId = null;
        }
    }

    /** 移除已结束的历史任务，保留当前活跃任务。 */
    private void purgeFinishedTasks()
    {
        taskMap.entrySet().removeIf(entry ->
        {
            if (entry.getKey().equals(activeTaskId))
            {
                return false;
            }
            String status = entry.getValue().status;
            return "failed".equals(status) || "stopped".equals(status) || "success".equals(status);
        });
    }

    private PresenceLiveTaskVo buildNotFoundTask(String taskId)
    {
        PresenceLiveTaskVo vo = new PresenceLiveTaskVo();
        vo.setTaskId(taskId);
        vo.setStatus("not_found");
        vo.setMessage("任务不存在或已过期（可能服务已重启），请重新点击「开始识别」");
        return vo;
    }

    /**
     * 获取当前时间的格式化字符串
     *
     * @return 格式为 yyyy-MM-dd HH:mm:ss 的时间字符串
     */
    private String nowText()
    {
        return LocalDateTime.now().format(TIME_FMT);
    }

    // ==================== 内部类 ====================

    /**
     * 直播任务运行时状态
     * <p>
     * 封装单个直播识别任务的完整状态信息，包括子进程引用、运行状态、
     * 时间戳、退出码和日志缓冲区等。日志缓冲区有最大长度限制（{@value #LOG_MAX_LEN} 字符），
     * 超出后自动截断前半部分。
     * </p>
     */
    private static class LiveTaskState
    {
        /** 日志缓冲区最大长度（字符数），超出后保留尾部 */
        private static final int LOG_MAX_LEN = 12000;

        /** 任务唯一标识 */
        private final String taskId;

        /** 设备序列号 */
        private final String deviceSerial;

        /** 拉流模式（LAN_RTSP / CLOUD_HLS） */
        private final String streamMode;

        /** 传输协议（rtsp / flv / hls） */
        private final String streamProtocol;

        /** Python 识别子进程引用（volatile 保证跨线程可见） */
        private volatile Process process;

        /** 任务状态：pending → starting → running → (stopped | success | failed) */
        private volatile String status = "pending";

        /** 进程退出码，null 表示进程尚未结束 */
        private volatile Integer exitCode;

        /** 任务启动时间 */
        private volatile String startedAt;

        /** 任务结束时间 */
        private volatile String finishedAt;

        /** 当前状态描述信息 */
        private volatile String message = "任务已创建";

        /** 日志缓冲区（线程安全，通过 synchronized 方法访问） */
        private final StringBuilder logs = new StringBuilder();

        private LiveTaskState(String taskId, String deviceSerial, String streamMode, String streamProtocol)
        {
            this.taskId = taskId;
            this.deviceSerial = deviceSerial;
            this.streamMode = streamMode;
            this.streamProtocol = streamProtocol;
        }

        private boolean isLanRtsp()
        {
            return PresenceLiveStartBo.STREAM_LAN_RTSP.equalsIgnoreCase(streamMode);
        }

        /**
         * 追加一行日志到缓冲区，超出最大长度时截断头部
         *
         * @param line 日志行
         */
        private synchronized void appendLog(String line)
        {
            logs.append(line).append('\n');
            if (logs.length() > LOG_MAX_LEN)
            {
                logs.delete(0, logs.length() - LOG_MAX_LEN);
            }
        }

        /**
         * 获取当前日志快照
         *
         * @return 日志字符串
         */
        private synchronized String logSnapshot()
        {
            return logs.toString();
        }

        /**
         * 将状态转换为视图对象返回给前端
         *
         * @return 任务视图对象
         */
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
