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

    /**
     * 启动直播识别任务
     * <p>
     * 核心流程：
     * <ol>
     *   <li>校验请求参数</li>
     *   <li>若已有活跃任务则先停止</li>
     *   <li>解析拉流地址与协议</li>
     *   <li>构建并启动 Python 识别子进程</li>
     *   <li>等待子进程就绪信号（stream ready），超时则抛异常</li>
     * </ol>
     * </p>
     *
     * @param bo 启动参数（设备序列号、通道号、拉流模式、验证码等）
     * @return 直播任务视图对象
     */
    @Override
    public synchronized PresenceLiveTaskVo startLive(PresenceLiveStartBo bo)
    {
        // 1. 参数校验
        validateStartBo(bo);
        // 2. 保证同时只有一个直播任务：先停掉旧任务
        stopActiveIfRunning();

        // 3. 归一化拉流模式，解析拉流地址和协议
        String streamMode = normalizeStreamMode(bo.getStreamMode());
        boolean lanRtsp = PresenceLiveStartBo.STREAM_LAN_RTSP.equals(streamMode);
        String streamUrl = ezvizScreenService.resolveAnalyzeStreamUrl(bo.getDeviceSerial(), bo.getChannelNo(), streamMode,
                bo.getValidCode());
        String streamProtocol = resolveStreamProtocol(streamUrl, lanRtsp);

        // 4. 创建任务状态记录
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

            // 7. 等待子进程输出 "stream ready" 信号，确认拉流成功
            awaitStreamReady(state, lanRtsp);
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
            throw new IllegalArgumentException("任务不存在: " + taskId);
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
        if (task == null)
        {
            return null;
        }
        // 仅 running/starting 状态视为活跃
        String status = StringUtils.nvl(task.getStatus(), "");
        if ("stopped".equals(status) || "failed".equals(status) || "success".equals(status))
        {
            return null;
        }
        return task;
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
            return null;
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
            if (state.exitCode == 2)
            {
                state.message = "RTSP 连接失败：请确认服务器与摄像头在同一局域网，或切换为公网云转发";
            }
            else if (state.exitCode != 0)
            {
                state.message = "直播识别进程异常退出 exitCode=" + state.exitCode;
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
        if (bo == null || StringUtils.isEmpty(bo.getDeviceSerial()))
        {
            throw new IllegalArgumentException("deviceSerial 不能为空");
        }
        if (StringUtils.isEmpty(bo.getStreamMode()))
        {
            throw new IllegalArgumentException("streamMode 不能为空");
        }
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
        long timeoutMs = lanRtsp ? live.getStreamOpenTimeoutSec() * 1000L
                : live.getCloudStreamOpenTimeoutSec() * 1000L;
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
                throw new ServiceException("直播识别启动失败，exitCode=" + code);
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
        cmd.add("--location-id");
        cmd.add(String.valueOf(bo.getLocationId() == null ? ingestProperties.getDefaultLocationId() : bo.getLocationId()));
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
        cmd.add(String.valueOf(lanRtsp ? live.getRtspOpenTimeoutSec() : live.getCloudOpenTimeoutSec()));
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
     * 清理任务资源
     * <p>
     * 终止子进程并从任务表中移除记录，同时清除活跃任务标记。
     * </p>
     *
     * @param taskId 任务 ID
     * @param state  任务状态
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
