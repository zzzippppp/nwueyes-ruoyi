package com.ruoyi.system.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
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
import com.ruoyi.system.service.ILanPreviewService;
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
    private static final Logger log = LoggerFactory.getLogger(PresenceLiveServiceImpl.class);

    /** 自定义错误码：局域网 RTSP 连接失败，需切换至局域网或公网云转发 */
    public static final int STREAM_RTSP_LAN_REQUIRED = 4601;

    /** 自定义错误码：视频编码非 H.264，无法解码识别流 */
    public static final int STREAM_CODEC_NOT_H264 = 4602;

    /** H264 编码错误退出码：配置问题，不自动无限重启 */
    private static final int EXIT_CODEC_NOT_H264 = 4;

    /** 统一的时间格式化器 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String DESIRED_STATE_RELATIVE = "capture_manifest/live_desired_state.json";

    /** 任务映射表，key 为任务 ID，value 为任务状态（线程安全） */
    private final Map<String, LiveTaskState> taskMap = new ConcurrentHashMap<>();

    /**
     * 每台摄像头一个控制块，支持多摄像头并行识别。
     * key = cameraId，value = 该摄像头的活跃任务/期望状态/重启/心跳等。
     */
    private final Map<Long, CamCtrl> cameras = new ConcurrentHashMap<>();

    /** 期望状态文件读写锁（多摄像头共用一个 JSON 文件） */
    private final Object desiredFileLock = new Object();

    private final ScheduledExecutorService supervisor =
            Executors.newSingleThreadScheduledExecutor(r ->
            {
                Thread t = new Thread(r, "live-supervisor");
                t.setDaemon(true);
                return t;
            });

    private volatile ScheduledFuture<?> watchdogFuture;

    /**
     * 单台摄像头的直播识别控制块：活跃任务、期望运行、自动重启、心跳等均按摄像头隔离。
     */
    private static class CamCtrl
    {
        private final Long cameraId;
        /** 当前活跃任务 ID（该摄像头同一时刻只跑一个 worker） */
        private volatile String activeTaskId;
        /** 是否期望识别持续运行（人工停止后为 false） */
        private final AtomicBoolean desiredRunning = new AtomicBoolean(false);
        /** 用户/替换任务请求停止，抑制本次退出触发的自动重启 */
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);
        /** 最近一次期望启动参数（用于自动重启与开机续跑） */
        private final AtomicReference<PresenceLiveStartBo> desiredStartBo = new AtomicReference<>();
        private final AtomicInteger restartAttempt = new AtomicInteger(0);
        private final AtomicLong lastHeartbeatMs = new AtomicLong(0L);
        private volatile ScheduledFuture<?> pendingRestart;

        private CamCtrl(Long cameraId)
        {
            this.cameraId = cameraId;
        }
    }

    /** 取（或创建）指定摄像头的控制块。 */
    private CamCtrl ctrl(Long cameraId)
    {
        return cameras.computeIfAbsent(cameraId, CamCtrl::new);
    }

    /** 按任务 ID 找到其所属摄像头的控制块；找不到返回 null。 */
    private CamCtrl ctrlOfTask(String taskId)
    {
        LiveTaskState state = taskMap.get(taskId);
        if (state == null || state.cameraId == null)
        {
            return null;
        }
        return cameras.get(state.cameraId);
    }

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private ILanPreviewService lanPreviewService;

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

        // 2. 先解析出 cameraId，得到本摄像头的控制块（多摄像头并行，互不影响）
        int channelNo = bo.getChannelNo() == null || bo.getChannelNo() < 1 ? 1 : bo.getChannelNo();
        Long cameraId = bo.getCameraId();
        if (cameraId == null)
        {
            cameraId = cameraService.resolveOrCreateCamera(bo.getDeviceSerial(), channelNo,
                    "监控点位-" + bo.getDeviceSerial());
            bo.setCameraId(cameraId);
        }
        CamCtrl cc = ctrl(cameraId);

        // 3. 只停掉「本摄像头」的旧任务（不清除期望状态，由本次 start 覆盖）
        cc.stopRequested.set(true);
        cancelPendingRestart(cc);
        stopActiveProcessOnly(cc);
        // 清掉本摄像头残留的孤儿 Python（按 cameraId 精确匹配，绝不误杀其它摄像头）
        killOrphanLiveWorkers(cameraId, null, "before-start");

        com.ruoyi.system.domain.vo.CameraConfigVo cameraConfig = cameraService.getCameraConfig(cameraId);
        mergeCameraConfigIntoBo(bo, cameraConfig);

        // 4. 归一化拉流模式，解析拉流地址和协议
        String streamMode = normalizeStreamMode(bo.getStreamMode());
        bo.setStreamMode(streamMode);
        boolean lanRtsp = PresenceLiveStartBo.STREAM_LAN_RTSP.equals(streamMode);
        // 先把摄像头 RTSP 注册到 go2rtc，再让 Python 从本地 RTSP 出口消费。
        // 预览、识别与抽帧因此共用同一条摄像头连接。
        String streamUrl = lanPreviewService.ensureLocalRtsp(bo);
        String streamProtocol = resolveStreamProtocol(streamUrl, lanRtsp);

        // 5. 持久化期望运行状态，供崩溃自动重启 / 开机续跑
        saveDesiredState(bo);
        cc.desiredStartBo.set(copyStartBo(bo));
        cc.desiredRunning.set(true);
        cc.stopRequested.set(false);
        ensureWatchdogStarted();

        // 6. 创建任务状态记录（清理历史已结束任务，避免内存堆积）
        purgeFinishedTasks();
        String taskId = "live_" + IdUtils.fastSimpleUUID();
        LiveTaskState state = new LiveTaskState(taskId, cameraId, bo.getDeviceSerial(), streamMode, streamProtocol);
        taskMap.put(taskId, state);
        cc.activeTaskId = taskId;
        cc.lastHeartbeatMs.set(System.currentTimeMillis());

        try
        {
            // 6. 构建命令并启动 Python 子进程
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

            // 7. 启动守护线程持续读取子进程输出日志
            Thread reader = new Thread(() -> pumpOutput(state, process), "live-log-" + taskId);
            reader.setDaemon(true);
            reader.start();

            // 8. 异步等待 stream ready，HTTP 立即返回，避免公网首帧慢导致前端 90s 超时
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
        // 开机续跑占位任务：pending_resume 或 pending_resume_<cameraId>
        if (taskId.startsWith("pending_resume"))
        {
            Long camId = parsePendingResumeCameraId(taskId);
            if (camId != null)
            {
                stopCamera(ctrl(camId));
            }
            else
            {
                // 未带 cameraId：停掉所有摄像头的期望运行
                for (CamCtrl cc : cameras.values())
                {
                    stopCamera(cc);
                }
            }
            PresenceLiveTaskVo vo = new PresenceLiveTaskVo();
            vo.setTaskId(taskId);
            vo.setStatus("stopped");
            vo.setMessage("直播识别已停止");
            if (camId != null)
            {
                vo.setCameraId(camId);
            }
            return vo;
        }
        LiveTaskState state = taskMap.get(taskId);
        if (state == null)
        {
            return buildNotFoundTask(taskId);
        }
        CamCtrl cc = ctrl(state.cameraId);
        // 人工停止：清除期望运行，禁止自动重启（仅本摄像头）
        cc.stopRequested.set(true);
        cc.desiredRunning.set(false);
        clearDesiredState(state.cameraId);
        cancelPendingRestart(cc);
        cc.restartAttempt.set(0);
        // 强制终止子进程，更新状态
        destroyProcess(state);
        state.status = "stopped";
        state.message = "直播识别已停止";
        state.finishedAt = nowText();
        // 清除活跃任务标记
        if (taskId.equals(cc.activeTaskId))
        {
            cc.activeTaskId = null;
        }
        return state.toVo();
    }

    /** 停止某摄像头的期望运行并清理其活跃进程（用于人工停止/占位任务停止）。 */
    private void stopCamera(CamCtrl cc)
    {
        if (cc == null)
        {
            return;
        }
        cc.stopRequested.set(true);
        cc.desiredRunning.set(false);
        clearDesiredState(cc.cameraId);
        cancelPendingRestart(cc);
        cc.restartAttempt.set(0);
        stopActiveProcessOnly(cc);
    }

    /** 从 pending_resume[_<cameraId>] 解析 cameraId，无后缀返回 null。 */
    private Long parsePendingResumeCameraId(String taskId)
    {
        String prefix = "pending_resume_";
        if (taskId != null && taskId.startsWith(prefix) && taskId.length() > prefix.length())
        {
            try
            {
                return Long.valueOf(taskId.substring(prefix.length()));
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取当前活跃的直播任务
     * <p>
     * 活跃任务指状态为 running / starting / reconnecting 的任务，已终止（stopped/failed/success）的任务不算。
     * </p>
     *
     * @return 活跃任务视图对象，无活跃任务时返回 null
     */
    @Override
    public List<PresenceLiveTaskVo> getActiveTasks()
    {
        List<PresenceLiveTaskVo> result = new ArrayList<>();
        for (CamCtrl cc : cameras.values())
        {
            PresenceLiveTaskVo vo = activeTaskForCtrl(cc);
            if (vo != null)
            {
                result.add(vo);
            }
        }
        return result;
    }

    /** 计算单个摄像头当前对外可展示的活跃任务（running/starting/reconnecting 或续跑占位）。 */
    private PresenceLiveTaskVo activeTaskForCtrl(CamCtrl cc)
    {
        String activeTaskId = cc.activeTaskId;
        if (StringUtils.isEmpty(activeTaskId))
        {
            // 期望仍在跑但进程空窗（等待自动重启 / 开机续跑）时，返回可展示状态
            if (cc.desiredRunning.get())
            {
                for (LiveTaskState state : taskMap.values())
                {
                    if (!cc.cameraId.equals(state.cameraId))
                    {
                        continue;
                    }
                    if ("reconnecting".equals(state.status) || "starting".equals(state.status))
                    {
                        return state.toVo();
                    }
                }
                PresenceLiveStartBo bo = cc.desiredStartBo.get();
                if (bo != null)
                {
                    return buildDesiredPendingTask(bo);
                }
            }
            return null;
        }
        PresenceLiveTaskVo task = getTask(activeTaskId);
        // 仅 running/starting/reconnecting 状态视为活跃
        String status = StringUtils.nvl(task.getStatus(), "");
        if ("stopped".equals(status) || "failed".equals(status) || "success".equals(status)
                || "not_found".equals(status))
        {
            return null;
        }
        return task;
    }

    private PresenceLiveTaskVo buildDesiredPendingTask(PresenceLiveStartBo bo)
    {
        PresenceLiveTaskVo vo = new PresenceLiveTaskVo();
        vo.setTaskId(bo.getCameraId() != null ? "pending_resume_" + bo.getCameraId() : "pending_resume");
        vo.setStatus("reconnecting");
        vo.setMessage("服务重启后正在自动恢复识别…");
        vo.setCameraId(bo.getCameraId());
        vo.setDeviceSerial(bo.getDeviceSerial());
        vo.setStreamMode(StringUtils.nvl(bo.getStreamMode(), PresenceLiveStartBo.STREAM_LAN_RTSP));
        return vo;
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
        String streamUrl = lanPreviewService.ensureLocalRtsp(bo);
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
        command.add("--ref-width");
        command.add(String.valueOf(bo.getRefWidth() == null ? 1920 : bo.getRefWidth()));
        command.add("--ref-height");
        command.add(String.valueOf(bo.getRefHeight() == null ? 1080 : bo.getRefHeight()));
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
        if (taskId != null && taskId.startsWith("pending_resume"))
        {
            Long camId = parsePendingResumeCameraId(taskId);
            if (camId != null)
            {
                CamCtrl cc = cameras.get(camId);
                if (cc != null && cc.desiredRunning.get())
                {
                    PresenceLiveStartBo bo = cc.desiredStartBo.get();
                    if (bo != null)
                    {
                        return buildDesiredPendingTask(bo);
                    }
                }
            }
            return buildNotFoundTask(taskId);
        }
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
        CamCtrl cc = ctrl(state.cameraId);
        // 进程已退出但状态仍为 running，根据退出码判定最终状态
        if (state.exitCode != null && ("running".equals(state.status) || "starting".equals(state.status)))
        {
            // 期望续跑时由 pumpOutput / supervisor 切到 reconnecting，这里不要抢先标 failed
            if (cc.desiredRunning.get() && !cc.stopRequested.get() && shouldAutoRestart(state.exitCode))
            {
                state.status = "reconnecting";
                state.message = "识别进程已退出，正在自动重启…";
            }
            else
            {
                state.status = state.exitCode == 0 ? "success" : "failed";
                state.finishedAt = nowText();
                if (state.exitCode != 0)
                {
                    state.message = resolveLiveFailureMessage(state.exitCode, state.logSnapshot(), state.isLanRtsp());
                }
                // 清理活跃任务标记
                if (taskId.equals(cc.activeTaskId))
                {
                    cc.activeTaskId = null;
                }
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
        // 空值交给 normalizeStreamMode 默认 lan_rtsp；公网模式在 normalize 阶段拒绝
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
        if (bo.getRefWidth() == null && cameraConfig.getRefWidth() != null)
        {
            bo.setRefWidth(cameraConfig.getRefWidth());
        }
        if (bo.getRefHeight() == null && cameraConfig.getRefHeight() != null)
        {
            bo.setRefHeight(cameraConfig.getRefHeight());
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
        if (StringUtils.isEmpty(streamMode) || PresenceLiveStartBo.STREAM_LAN_RTSP.equalsIgnoreCase(streamMode))
        {
            return PresenceLiveStartBo.STREAM_LAN_RTSP;
        }
        if (PresenceLiveStartBo.STREAM_CLOUD_HLS.equalsIgnoreCase(streamMode))
        {
            throw new ServiceException("已禁用公网云转发，请使用局域网 RTSP（streamMode=lan_rtsp）");
        }
        throw new IllegalArgumentException("不支持的 streamMode: " + streamMode);
    }

    /**
     * 停止某摄像头当前活跃任务进程（不清除期望运行状态）。
     * 用于新任务启动前的清理，以及自动重启前的换进程。
     */
    private void stopActiveProcessOnly(CamCtrl cc)
    {
        if (cc == null || StringUtils.isEmpty(cc.activeTaskId))
        {
            return;
        }
        LiveTaskState state = taskMap.get(cc.activeTaskId);
        if (state == null)
        {
            cc.activeTaskId = null;
            return;
        }
        destroyProcess(state);
        if (!"reconnecting".equals(state.status))
        {
            state.status = "stopped";
            state.message = "任务已被替换";
            state.finishedAt = nowText();
        }
        cc.activeTaskId = null;
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
        CamCtrl cc = ctrl(state.cameraId);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            // 逐行读取子进程输出，追加到任务日志
            while ((line = reader.readLine()) != null)
            {
                state.appendLog(line);
                if (isHeartbeatLine(line))
                {
                    cc.lastHeartbeatMs.set(System.currentTimeMillis());
                    if ("running".equals(state.status) || "starting".equals(state.status))
                    {
                        // stream ready 时由 awaitStreamReady 切 running；重连日志也刷新心跳
                    }
                }
                if (line.contains("[live] stream ready") || line.contains("[live] reconnect ok"))
                {
                    cc.lastHeartbeatMs.set(System.currentTimeMillis());
                    cc.restartAttempt.set(0);
                }
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
            handleWorkerExit(state);
        }
    }

    private void handleWorkerExit(LiveTaskState state)
    {
        CamCtrl cc = ctrl(state.cameraId);
        Integer exitCode = state.exitCode;
        boolean expectResume = cc.desiredRunning.get() && !cc.stopRequested.get() && shouldAutoRestart(exitCode);
        if (expectResume && ingestProperties.getLive().isAutoRestartEnabled())
        {
            state.status = "reconnecting";
            state.message = "识别进程已退出，正在自动重启…";
            state.finishedAt = nowText();
            state.appendLog("[supervisor] worker exited exitCode=" + exitCode + ", scheduling restart");
            if (state.taskId.equals(cc.activeTaskId))
            {
                cc.activeTaskId = null;
            }
            scheduleRestart(cc, "exit-" + exitCode);
            return;
        }
        // 根据退出码更新任务最终状态
        if ("running".equals(state.status) || "starting".equals(state.status) || "reconnecting".equals(state.status))
        {
            state.status = exitCode != null && exitCode == 0 ? "success" : "failed";
            state.finishedAt = nowText();
            if (!"success".equals(state.status) && exitCode != null)
            {
                state.message = resolveLiveFailureMessage(exitCode, state.logSnapshot(), state.isLanRtsp());
            }
        }
        if (state.taskId.equals(cc.activeTaskId))
        {
            cc.activeTaskId = null;
        }
        // 编码错误等不可恢复问题：停止自动续跑，避免空转
        if (exitCode != null && exitCode == EXIT_CODEC_NOT_H264)
        {
            cc.desiredRunning.set(false);
            clearDesiredState(cc.cameraId);
        }
    }

    private boolean isHeartbeatLine(String line)
    {
        if (line == null)
        {
            return false;
        }
        return line.contains("[live] heartbeat")
                || line.contains("[live] reconnecting")
                || line.contains("[live] reconnect ")
                || line.contains("[live] connecting...")
                || line.contains("[live] stream ready")
                || line.contains("[detect] pass=");
    }

    private boolean shouldAutoRestart(Integer exitCode)
    {
        if (exitCode != null && exitCode == EXIT_CODEC_NOT_H264)
        {
            return false;
        }
        return true;
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
        cmd.add("--ref-width");
        cmd.add(String.valueOf(bo.getRefWidth() == null ? 1920 : bo.getRefWidth()));
        cmd.add("--ref-height");
        cmd.add(String.valueOf(bo.getRefHeight() == null ? 1080 : bo.getRefHeight()));

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
        cmd.add("--clip-ffmpeg-path");
        cmd.add(StringUtils.nvl(ingestProperties.getClip().getFfmpegPath(), "ffmpeg"));
        cmd.add("--clip-ring-segment-sec");
        cmd.add(String.valueOf(ingestProperties.getClip().getRingSegmentSec()));
        cmd.add("--clip-max-duration-sec");
        cmd.add(String.valueOf(ingestProperties.getClip().getMaxDurationSec()));
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
        cmd.add("--event-ingest-enabled");
        cmd.add(String.valueOf(live.isEventIngestEnabled()).toLowerCase());

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
        cmd.add("--frame-reconnect-idle-sec");
        cmd.add(String.valueOf(live.getFrameReconnectIdleSec()));
        cmd.add("--reconnect-backoff-max-sec");
        cmd.add(String.valueOf(live.getRestartBackoffMaxSec()));
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
     * 强制终止子进程，并等待其退出，避免 JVM 重启后留下孤儿 Python 继续写片。
     */
    private void destroyProcess(LiveTaskState state)
    {
        Process process = state.process;
        if (process != null && process.isAlive())
        {
            process.destroyForcibly();
            try
            {
                process.waitFor(8, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 清理指定摄像头残留的 live_stream_worker 进程（按 --camera-id 精确匹配，绝不误杀其它摄像头）。
     *
     * @param cameraId      仅清理该摄像头的 worker
     * @param exceptTaskId  跳过该 task 对应的进程（避免杀掉刚启动的当前任务），可为 null
     */
    private void killOrphanLiveWorkers(Long cameraId, String exceptTaskId, String reason)
    {
        if (cameraId == null)
        {
            return;
        }
        String marker = "live_stream_worker_yolo.py";
        String cameraToken = "--camera-id " + cameraId + " ";
        String exceptToken = StringUtils.isEmpty(exceptTaskId) ? null : "--task-id " + exceptTaskId + " ";
        int killed = 0;
        try
        {
            for (ProcessHandle handle : ProcessHandle.allProcesses().toList())
            {
                String cmd = handle.info().commandLine().orElse("");
                if (StringUtils.isEmpty(cmd) || !cmd.contains(marker))
                {
                    continue;
                }
                // 只杀本摄像头的 worker：命令行必须包含 --camera-id <id>
                if (!cmd.contains(cameraToken))
                {
                    continue;
                }
                // 跳过当前任务对应的进程
                if (exceptToken != null && cmd.contains(exceptToken))
                {
                    continue;
                }
                killed += destroyHandle(handle);
            }
        }
        catch (Exception ex)
        {
            log.debug("扫描孤儿 live worker 失败: {}", ex.getMessage());
        }
        afterOrphanKill(killed, reason);
    }

    /**
     * 关机时清理本机全部 live_stream_worker 进程。
     */
    private void killAllOrphanLiveWorkers(String reason)
    {
        String marker = "live_stream_worker_yolo.py";
        int killed = 0;
        try
        {
            for (ProcessHandle handle : ProcessHandle.allProcesses().toList())
            {
                String cmd = handle.info().commandLine().orElse("");
                if (StringUtils.isEmpty(cmd) || !cmd.contains(marker))
                {
                    continue;
                }
                killed += destroyHandle(handle);
            }
        }
        catch (Exception ex)
        {
            log.debug("扫描孤儿 live worker 失败: {}", ex.getMessage());
        }
        afterOrphanKill(killed, reason);
    }

    private int destroyHandle(ProcessHandle handle)
    {
        try
        {
            boolean ok = handle.destroyForcibly();
            if (ok)
            {
                handle.onExit().orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
                return 1;
            }
        }
        catch (Exception ignored)
        {
        }
        return 0;
    }

    private void afterOrphanKill(int killed, String reason)
    {
        if (killed > 0)
        {
            log.warn("已清理 {} 个残留 live_stream_worker 进程 reason={}", killed, reason);
            try
            {
                Thread.sleep(500L);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    @PreDestroy
    public void shutdownSupervisor()
    {
        // 关机时只杀进程，保留 desired state，便于下次开机续跑
        for (CamCtrl cc : cameras.values())
        {
            cc.stopRequested.set(true);
            cancelPendingRestart(cc);
            stopActiveProcessOnly(cc);
        }
        killAllOrphanLiveWorkers("jvm-shutdown");
        ScheduledFuture<?> wd = watchdogFuture;
        if (wd != null)
        {
            wd.cancel(false);
        }
        supervisor.shutdownNow();
    }

    /**
     * 标记任务失败并终止子进程；若仍期望运行则改为自动重启，否则保留失败记录供前端读取。
     */
    private void markTaskFailed(String taskId, LiveTaskState state, String message)
    {
        CamCtrl cc = ctrl(state.cameraId);
        destroyProcess(state);
        if (cc.desiredRunning.get() && !cc.stopRequested.get() && ingestProperties.getLive().isAutoRestartEnabled())
        {
            state.status = "reconnecting";
            state.message = message + "（将自动重启）";
            state.finishedAt = nowText();
            state.appendLog("[supervisor] start failed: " + message);
            if (taskId.equals(cc.activeTaskId))
            {
                cc.activeTaskId = null;
            }
            scheduleRestart(cc, "start-failed");
            return;
        }
        state.status = "failed";
        state.message = message;
        state.finishedAt = nowText();
        if (taskId.equals(cc.activeTaskId))
        {
            cc.activeTaskId = null;
        }
    }

    /**
     * 清理任务资源：终止子进程并从任务表移除（仅用于启动请求同步失败、客户端尚未轮询的场景）。
     */
    private void cleanupTask(String taskId, LiveTaskState state)
    {
        CamCtrl cc = ctrl(state.cameraId);
        destroyProcess(state);
        taskMap.remove(taskId);
        if (taskId.equals(cc.activeTaskId))
        {
            cc.activeTaskId = null;
        }
        // 同步启动失败：若已写入期望状态，仍安排重启
        if (cc.desiredRunning.get() && !cc.stopRequested.get() && ingestProperties.getLive().isAutoRestartEnabled())
        {
            scheduleRestart(cc, "cleanup-start-failed");
        }
    }

    /** 移除已结束的历史任务，保留各摄像头当前活跃任务与最近 reconnecting。 */
    private void purgeFinishedTasks()
    {
        taskMap.entrySet().removeIf(entry ->
        {
            LiveTaskState st = entry.getValue();
            CamCtrl cc = st.cameraId == null ? null : cameras.get(st.cameraId);
            if (cc != null && entry.getKey().equals(cc.activeTaskId))
            {
                return false;
            }
            String status = st.status;
            if ("reconnecting".equals(status) && cc != null && cc.desiredRunning.get())
            {
                return false;
            }
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

    // ==================== 保活：期望状态 / 自动重启 / 心跳 ====================

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void resumeDesiredLiveOnBoot()
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        if (!live.isResumeOnBoot())
        {
            return;
        }
        List<PresenceLiveStartBo> desired = loadAllDesiredStates();
        if (desired.isEmpty())
        {
            log.info("开机续跑：未找到期望运行的直播识别状态，跳过");
            return;
        }
        ensureWatchdogStarted();
        for (PresenceLiveStartBo bo : desired)
        {
            if (bo.getCameraId() == null)
            {
                continue;
            }
            CamCtrl cc = ctrl(bo.getCameraId());
            cc.desiredStartBo.set(bo);
            cc.desiredRunning.set(true);
            cc.stopRequested.set(false);
            log.info("检测到期望运行的直播识别任务，将自动恢复 cameraId={} serial={}",
                    bo.getCameraId(), bo.getDeviceSerial());
            // 略等 go2rtc 就绪；前端可通过 /live/active 立刻看到 reconnecting
            supervisor.schedule(() ->
            {
                try
                {
                    if (!cc.desiredRunning.get() || cc.stopRequested.get())
                    {
                        return;
                    }
                    startLive(copyStartBo(bo));
                    log.info("开机自动恢复直播识别成功 cameraId={}", bo.getCameraId());
                }
                catch (Exception ex)
                {
                    log.warn("开机自动恢复直播识别失败: {}", ex.getMessage());
                    scheduleRestart(cc, "boot-resume-failed");
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    private void ensureWatchdogStarted()
    {
        if (watchdogFuture != null && !watchdogFuture.isCancelled())
        {
            return;
        }
        watchdogFuture = supervisor.scheduleWithFixedDelay(this::watchdogTick, 20, 20, TimeUnit.SECONDS);
    }

    /** 看门狗：遍历所有摄像头，各自独立做心跳超时/掉线检测与自动重启。 */
    private void watchdogTick()
    {
        for (CamCtrl cc : cameras.values())
        {
            try
            {
                watchdogTickForCamera(cc);
            }
            catch (Exception ex)
            {
                log.debug("live watchdog tick error cameraId={}: {}", cc.cameraId, ex.getMessage());
            }
        }
    }

    private void watchdogTickForCamera(CamCtrl cc)
    {
        if (!cc.desiredRunning.get() || cc.stopRequested.get())
        {
            return;
        }
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        if (!live.isAutoRestartEnabled())
        {
            return;
        }
        String taskId = cc.activeTaskId;
        if (StringUtils.isEmpty(taskId))
        {
            if (cc.pendingRestart == null || cc.pendingRestart.isDone())
            {
                scheduleRestart(cc, "watchdog-no-active");
            }
            return;
        }
        LiveTaskState state = taskMap.get(taskId);
        if (state == null)
        {
            scheduleRestart(cc, "watchdog-missing-state");
            return;
        }
        if ("starting".equals(state.status) || "reconnecting".equals(state.status))
        {
            return;
        }
        if (!"running".equals(state.status))
        {
            return;
        }
        long timeoutMs = (long) Math.max(60.0, live.getHeartbeatTimeoutSec()) * 1000L;
        long last = cc.lastHeartbeatMs.get();
        if (last <= 0L)
        {
            return;
        }
        long idle = System.currentTimeMillis() - last;
        if (idle <= timeoutMs)
        {
            return;
        }
        state.appendLog("[supervisor] heartbeat timeout idleSec=" + (idle / 1000)
                + ", killing worker for restart");
        log.warn("直播识别心跳超时 {}s，强制重启 worker cameraId={} taskId={}", idle / 1000, cc.cameraId, taskId);
        destroyProcess(state);
    }

    private synchronized void scheduleRestart(CamCtrl cc, String reason)
    {
        if (cc == null || !cc.desiredRunning.get() || cc.stopRequested.get())
        {
            return;
        }
        if (!ingestProperties.getLive().isAutoRestartEnabled())
        {
            return;
        }
        PresenceLiveStartBo bo = cc.desiredStartBo.get();
        if (bo == null)
        {
            log.warn("无法自动重启直播识别：缺少期望启动参数 cameraId={} reason={}", cc.cameraId, reason);
            return;
        }
        if (cc.pendingRestart != null && !cc.pendingRestart.isDone())
        {
            return;
        }
        int attempt = cc.restartAttempt.incrementAndGet();
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        double base = Math.max(1.0, live.getRestartBackoffSec());
        double max = Math.max(base, live.getRestartBackoffMaxSec());
        long delaySec = (long) Math.min(max, base * Math.pow(2, Math.min(attempt - 1, 6)));
        PresenceLiveStartBo restartBo = copyStartBo(bo);
        log.info("调度直播识别自动重启 cameraId={} attempt={} delaySec={} reason={}",
                cc.cameraId, attempt, delaySec, reason);
        cc.pendingRestart = supervisor.schedule(() ->
        {
            try
            {
                if (!cc.desiredRunning.get() || cc.stopRequested.get())
                {
                    return;
                }
                startLive(restartBo);
            }
            catch (Exception ex)
            {
                log.warn("自动重启直播识别失败: {}", ex.getMessage());
                scheduleRestart(cc, "restart-failed");
            }
        }, delaySec, TimeUnit.SECONDS);
    }

    private void cancelPendingRestart(CamCtrl cc)
    {
        if (cc == null)
        {
            return;
        }
        ScheduledFuture<?> future = cc.pendingRestart;
        if (future != null)
        {
            future.cancel(false);
            cc.pendingRestart = null;
        }
    }

    private Path desiredStatePath()
    {
        String root = ingestProperties.resolveStorageRoot();
        return Paths.get(root, DESIRED_STATE_RELATIVE);
    }

    private JSONObject boToJson(PresenceLiveStartBo bo)
    {
        JSONObject json = new JSONObject();
        json.put("cameraId", bo.getCameraId());
        json.put("deviceSerial", bo.getDeviceSerial());
        json.put("channelNo", bo.getChannelNo());
        json.put("streamMode", bo.getStreamMode());
        json.put("lineY", bo.getLineY());
        json.put("roi", bo.getRoi());
        json.put("refWidth", bo.getRefWidth());
        json.put("refHeight", bo.getRefHeight());
        json.put("validCode", bo.getValidCode());
        json.put("savedAt", nowText());
        return json;
    }

    private PresenceLiveStartBo jsonToBo(JSONObject json)
    {
        if (json == null)
        {
            return null;
        }
        PresenceLiveStartBo bo = new PresenceLiveStartBo();
        bo.setCameraId(json.getLong("cameraId"));
        bo.setDeviceSerial(json.getString("deviceSerial"));
        bo.setChannelNo(json.getInteger("channelNo"));
        bo.setStreamMode(json.getString("streamMode"));
        bo.setLineY(json.getInteger("lineY"));
        bo.setRoi(json.getString("roi"));
        bo.setRefWidth(json.getInteger("refWidth"));
        bo.setRefHeight(json.getInteger("refHeight"));
        bo.setValidCode(json.getString("validCode"));
        if (bo.getCameraId() == null && StringUtils.isEmpty(bo.getDeviceSerial()))
        {
            return null;
        }
        return bo;
    }

    /** 读取期望状态文件（多摄像头 map，key=cameraId 字符串）。兼容旧的单对象格式。 */
    private JSONObject readDesiredMap()
    {
        try
        {
            Path path = desiredStatePath();
            if (!Files.isRegularFile(path))
            {
                return new JSONObject();
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(text);
            if (json == null)
            {
                return new JSONObject();
            }
            // 兼容旧格式：单个对象（含 cameraId 字段）→ 转成 map
            if (json.containsKey("cameraId") || json.containsKey("deviceSerial"))
            {
                JSONObject map = new JSONObject();
                Long camId = json.getLong("cameraId");
                if (camId != null)
                {
                    map.put(String.valueOf(camId), json);
                }
                return map;
            }
            return json;
        }
        catch (Exception ex)
        {
            log.warn("读取直播期望状态失败: {}", ex.getMessage());
            return new JSONObject();
        }
    }

    private void saveDesiredState(PresenceLiveStartBo bo)
    {
        if (bo == null || bo.getCameraId() == null)
        {
            return;
        }
        synchronized (desiredFileLock)
        {
            try
            {
                Path path = desiredStatePath();
                Files.createDirectories(path.getParent());
                JSONObject map = readDesiredMap();
                map.put(String.valueOf(bo.getCameraId()), boToJson(bo));
                Files.writeString(path, map.toJSONString(), StandardCharsets.UTF_8);
            }
            catch (Exception ex)
            {
                log.warn("保存直播期望状态失败: {}", ex.getMessage());
            }
        }
    }

    private void clearDesiredState(Long cameraId)
    {
        if (cameraId != null)
        {
            CamCtrl cc = cameras.get(cameraId);
            if (cc != null)
            {
                cc.desiredStartBo.set(null);
            }
        }
        synchronized (desiredFileLock)
        {
            try
            {
                Path path = desiredStatePath();
                if (!Files.isRegularFile(path))
                {
                    return;
                }
                JSONObject map = readDesiredMap();
                if (cameraId != null)
                {
                    map.remove(String.valueOf(cameraId));
                }
                if (map.isEmpty())
                {
                    Files.deleteIfExists(path);
                }
                else
                {
                    Files.writeString(path, map.toJSONString(), StandardCharsets.UTF_8);
                }
            }
            catch (Exception ex)
            {
                log.debug("清除直播期望状态失败: {}", ex.getMessage());
            }
        }
    }

    /** 读取所有摄像头的期望启动参数（开机续跑用）。 */
    private List<PresenceLiveStartBo> loadAllDesiredStates()
    {
        List<PresenceLiveStartBo> result = new ArrayList<>();
        JSONObject map = readDesiredMap();
        for (String key : map.keySet())
        {
            JSONObject item = map.getJSONObject(key);
            PresenceLiveStartBo bo = jsonToBo(item);
            if (bo != null)
            {
                result.add(bo);
            }
        }
        return result;
    }

    private PresenceLiveStartBo copyStartBo(PresenceLiveStartBo src)
    {
        PresenceLiveStartBo bo = new PresenceLiveStartBo();
        if (src == null)
        {
            return bo;
        }
        bo.setCameraId(src.getCameraId());
        bo.setDeviceSerial(src.getDeviceSerial());
        bo.setChannelNo(src.getChannelNo());
        bo.setStreamMode(src.getStreamMode());
        bo.setLineY(src.getLineY());
        bo.setRoi(src.getRoi());
        bo.setRefWidth(src.getRefWidth());
        bo.setRefHeight(src.getRefHeight());
        bo.setValidCode(src.getValidCode());
        return bo;
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

        /** 摄像头 ID（列表页匹配识别状态用） */
        private final Long cameraId;

        /** 设备序列号 */
        private final String deviceSerial;

        /** 拉流模式（LAN_RTSP / CLOUD_HLS） */
        private final String streamMode;

        /** 传输协议（rtsp / flv / hls） */
        private final String streamProtocol;

        /** Python 识别子进程引用（volatile 保证跨线程可见） */
        private volatile Process process;

        /** 任务状态：pending → starting → running → (stopped | success | failed | reconnecting) */
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

        private LiveTaskState(String taskId, Long cameraId, String deviceSerial, String streamMode, String streamProtocol)
        {
            this.taskId = taskId;
            this.cameraId = cameraId;
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
            vo.setCameraId(cameraId);
            vo.setDeviceSerial(deviceSerial);
            vo.setStreamMode(streamMode);
            vo.setStreamProtocol(streamProtocol);
            return vo;
        }
    }
}
