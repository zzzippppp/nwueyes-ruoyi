package com.ruoyi.system.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;

/**
 * 识别事件接入配置
 */
@Component
@ConfigurationProperties(prefix = "presence.ingest")
public class PresenceIngestProperties
{
    /**
     * 是否启用事件接入接口
     */
    private boolean enabled = false;

    /**
     * 事件上报 API Key，空则不校验
     */
    private String apiKey;

    /**
     * 默认点位（请求未传 cameraId 时使用）
     */
    private Long defaultCameraId;

    /**
     * 回放脚本执行目录（项目根）
     */
    private String workspaceRoot = ".";

    /**
     * 回放脚本 python 命令
     */
    private String replayPythonCommand = "python";

    /**
     * 回放脚本路径（相对 workspaceRoot）
     */
    private String replayScriptPath = "scripts/video_replay_worker_yolo.py";

    /**
     * YOLO 分析脚本（仅输出结果，不入库）
     */
    private String analyzeScriptPath = "scripts/video_analyze_yolo.py";

    /**
     * 回放读取上传目录
     */
    private String replayProfileRoot = "D:/ruoyi/uploadPath";

    /**
     * 回放上报 ingest 地址
     */
    private String replayIngestBaseUrl = "http://localhost:8080";

    /**
     * 默认过线 y
     */
    private Integer replayLineY = 520;

    /**
     * 默认 ROI
     */
    private String replayRoi = "620,170,1290,760";

    /**
     * 最佳抓拍脚本（相对 workspaceRoot）
     */
    private String captureScriptPath = "scripts/capture_best_snapshots.py";

    /**
     * face_library / body_library 所在项目根（通常为 workspaceRoot 上一级）
     */
    private String storageRoot = ".";

    /**
     * 轨迹首次出现后采样窗口（秒）
     */
    private Double snapshotWindowSec = 5.0;

    /**
     * 512 维 embedding 脚本（相对 workspaceRoot）
     */
    private String embedScriptPath = "scripts/embed_features.py";

    /**
     * InsightFace 模型包名
     */
    private String faceEmbedModel = "buffalo_l";

    /**
     * 体态 ReID 模型名
     */
    private String bodyEmbedModel = "osnet_x0_25";

    /**
     * 人脸检测最低置信度
     */
    private Double faceMinDetScore = 0.45;

    /**
     * 人脸向量模式：detect=检脸+对齐（推荐）；crop=整图拉伸112
     */
    private String faceEmbedMode = "detect";

    /**
     * 进门人脸 cosine 相似度阈值（0~1，越高越严）
     */
    private Double faceMatchThreshold = 0.35;

    /**
     * 出门体态 vs open session enter_body_embedding 相似度阈值
     */
    private Double bodyMatchThreshold = 0.50;

    /**
     * 直播识别脚本（相对 workspaceRoot）
     */
    private String liveScriptPath = "scripts/live_stream_worker_yolo.py";

    /**
     * 直播抽帧标定脚本（相对 workspaceRoot）
     */
    private String probeScriptPath = "scripts/capture_stream_probe.py";

    /**
     * 直播识别相关参数
     */
    private LiveIngest live = new LiveIngest();

    /**
     * 视频片段录制配置。
     */
    private ClipCapture clip = new ClipCapture();

    /**
     * 大模型分析配置。
     */
    private AiAnalysis analysis = new AiAnalysis();

    public static class LiveIngest
    {
        private double targetDetectFps = 3.0;

        private double yoloConf = 0.35;

        /** YOLO 推理尺寸，越小越快；960 在 CPU 上约可达 15fps */
        private int yoloImgsz = 1280;

        private int enterInferMargin = 80;

        private int exitInferMargin = 80;

        private int grabFlushFrames = 5;

        private int rtspBufferSize = 1;

        private double eventCooldownSec = 2.0;

        /** 进门穿线后持续追脸最长时间（秒） */
        private double enterFaceHuntMaxSec = 10.0;

        /** 进门首次检出脸后继续择优的宽限时间（秒） */
        private double enterFaceGraceSec = 1.5;

        /**
         * 是否实时上报过线事件并追脸入库。
         * false=只检人+场景录像，身份/行为交给离线视频分析（AB 方案）。
         */
        private boolean eventIngestEnabled = false;

        private int streamOpenTimeoutSec = 12;

        private int ingestCorePoolSize = 2;

        private int ingestMaxPoolSize = 4;

        private int ingestQueueCapacity = 30;

        private long ingestDedupeMs = 1500L;

        /** 已废弃：萤石取流 API 不支持 protocol=5，局域网 RTSP 改走设备 localAddress 直连 */
        private int ezvizRtspProtocol = 5;

        /** 公网云转发协议：4=FLV（OpenCV 更稳），2=HLS */
        private int ezvizCloudProtocol = 4;

        /** 公网清晰度：固定 1=主码流（2880×1620 等）；不再使用子码流 */
        private int ezvizCloudQuality = 1;

        /** 可选：完整 RTSP 地址，配置后优先于自动拼接 */
        private String lanRtspUrl = "";

        private String lanRtspUsername = "admin";

        private int lanRtspPort = 554;

        /** 主码流：萤石 IPC 常用 /h264/ch1/main/av_stream；海康系可用 /Streaming/Channels/101 */
        private String lanRtspStreamPath = "/h264/ch1/main/av_stream";

        private int ezvizHlsProtocol = 2;

        private int ezvizStreamExpireSec = 3600;

        private int cloudStreamOpenTimeoutSec = 90;

        private double rtspOpenTimeoutSec = 10;

        private double cloudOpenTimeoutSec = 75;

        /** go2rtc 服务地址（后端注册流用，如 http://127.0.0.1:1984） */
        private String go2rtcBaseUrl = "http://127.0.0.1:1984";

        /** 浏览器访问 go2rtc 的地址；为空则复用 go2rtcBaseUrl */
        private String go2rtcPublicBaseUrl = "";

        /** Python 识别/抽帧连接 go2rtc 的本地 RTSP 地址 */
        private String go2rtcRtspBaseUrl = "rtsp://127.0.0.1:8554";

        /** worker 异常退出后是否自动重启（人工停止除外） */
        private boolean autoRestartEnabled = true;

        /** 自动重启退避起始秒数 */
        private double restartBackoffSec = 3.0;

        /** 自动重启退避封顶秒数 */
        private double restartBackoffMaxSec = 60.0;

        /** Java 重启后是否自动恢复上次期望运行的识别任务 */
        private boolean resumeOnBoot = true;

        /** 持续无有效帧超过该秒数则触发 Python 侧重连 */
        private double frameReconnectIdleSec = 15.0;

        /** 运行中超过该秒数无 heartbeat 日志则强杀 worker 并自动拉起 */
        private double heartbeatTimeoutSec = 180.0;

        public double getTargetDetectFps()
        {
            return targetDetectFps;
        }

        public void setTargetDetectFps(double targetDetectFps)
        {
            this.targetDetectFps = targetDetectFps;
        }

        public double getYoloConf()
        {
            return yoloConf;
        }

        public void setYoloConf(double yoloConf)
        {
            this.yoloConf = yoloConf;
        }

        public int getYoloImgsz()
        {
            return yoloImgsz;
        }

        public void setYoloImgsz(int yoloImgsz)
        {
            this.yoloImgsz = yoloImgsz;
        }

        public int getEnterInferMargin()
        {
            return enterInferMargin;
        }

        public void setEnterInferMargin(int enterInferMargin)
        {
            this.enterInferMargin = enterInferMargin;
        }

        public int getExitInferMargin()
        {
            return exitInferMargin;
        }

        public void setExitInferMargin(int exitInferMargin)
        {
            this.exitInferMargin = exitInferMargin;
        }

        public int getGrabFlushFrames()
        {
            return grabFlushFrames;
        }

        public void setGrabFlushFrames(int grabFlushFrames)
        {
            this.grabFlushFrames = grabFlushFrames;
        }

        public int getRtspBufferSize()
        {
            return rtspBufferSize;
        }

        public void setRtspBufferSize(int rtspBufferSize)
        {
            this.rtspBufferSize = rtspBufferSize;
        }

        public double getEventCooldownSec()
        {
            return eventCooldownSec;
        }

        public void setEventCooldownSec(double eventCooldownSec)
        {
            this.eventCooldownSec = eventCooldownSec;
        }

        public double getEnterFaceHuntMaxSec()
        {
            return enterFaceHuntMaxSec;
        }

        public void setEnterFaceHuntMaxSec(double enterFaceHuntMaxSec)
        {
            this.enterFaceHuntMaxSec = enterFaceHuntMaxSec;
        }

        public double getEnterFaceGraceSec()
        {
            return enterFaceGraceSec;
        }

        public void setEnterFaceGraceSec(double enterFaceGraceSec)
        {
            this.enterFaceGraceSec = enterFaceGraceSec;
        }

        public boolean isEventIngestEnabled()
        {
            return eventIngestEnabled;
        }

        public void setEventIngestEnabled(boolean eventIngestEnabled)
        {
            this.eventIngestEnabled = eventIngestEnabled;
        }

        public int getStreamOpenTimeoutSec()
        {
            return streamOpenTimeoutSec;
        }

        public void setStreamOpenTimeoutSec(int streamOpenTimeoutSec)
        {
            this.streamOpenTimeoutSec = streamOpenTimeoutSec;
        }

        public int getIngestCorePoolSize()
        {
            return ingestCorePoolSize;
        }

        public void setIngestCorePoolSize(int ingestCorePoolSize)
        {
            this.ingestCorePoolSize = ingestCorePoolSize;
        }

        public int getIngestMaxPoolSize()
        {
            return ingestMaxPoolSize;
        }

        public void setIngestMaxPoolSize(int ingestMaxPoolSize)
        {
            this.ingestMaxPoolSize = ingestMaxPoolSize;
        }

        public int getIngestQueueCapacity()
        {
            return ingestQueueCapacity;
        }

        public void setIngestQueueCapacity(int ingestQueueCapacity)
        {
            this.ingestQueueCapacity = ingestQueueCapacity;
        }

        public long getIngestDedupeMs()
        {
            return ingestDedupeMs;
        }

        public void setIngestDedupeMs(long ingestDedupeMs)
        {
            this.ingestDedupeMs = ingestDedupeMs;
        }

        public int getEzvizRtspProtocol()
        {
            return ezvizRtspProtocol;
        }

        public void setEzvizRtspProtocol(int ezvizRtspProtocol)
        {
            this.ezvizRtspProtocol = ezvizRtspProtocol;
        }

        public int getEzvizHlsProtocol()
        {
            return ezvizHlsProtocol;
        }

        public void setEzvizHlsProtocol(int ezvizHlsProtocol)
        {
            this.ezvizHlsProtocol = ezvizHlsProtocol;
        }

        public int getEzvizCloudProtocol()
        {
            return ezvizCloudProtocol;
        }

        public void setEzvizCloudProtocol(int ezvizCloudProtocol)
        {
            this.ezvizCloudProtocol = ezvizCloudProtocol;
        }

        public int getEzvizCloudQuality()
        {
            return ezvizCloudQuality;
        }

        public void setEzvizCloudQuality(int ezvizCloudQuality)
        {
            this.ezvizCloudQuality = ezvizCloudQuality;
        }

        public int getCloudStreamOpenTimeoutSec()
        {
            return cloudStreamOpenTimeoutSec;
        }

        public void setCloudStreamOpenTimeoutSec(int cloudStreamOpenTimeoutSec)
        {
            this.cloudStreamOpenTimeoutSec = cloudStreamOpenTimeoutSec;
        }

        public double getRtspOpenTimeoutSec()
        {
            return rtspOpenTimeoutSec;
        }

        public void setRtspOpenTimeoutSec(double rtspOpenTimeoutSec)
        {
            this.rtspOpenTimeoutSec = rtspOpenTimeoutSec;
        }

        public double getCloudOpenTimeoutSec()
        {
            return cloudOpenTimeoutSec;
        }

        public void setCloudOpenTimeoutSec(double cloudOpenTimeoutSec)
        {
            this.cloudOpenTimeoutSec = cloudOpenTimeoutSec;
        }

        public int getEzvizStreamExpireSec()
        {
            return ezvizStreamExpireSec;
        }

        public void setEzvizStreamExpireSec(int ezvizStreamExpireSec)
        {
            this.ezvizStreamExpireSec = ezvizStreamExpireSec;
        }

        public String getLanRtspUrl()
        {
            return lanRtspUrl;
        }

        public void setLanRtspUrl(String lanRtspUrl)
        {
            this.lanRtspUrl = lanRtspUrl;
        }

        public String getLanRtspUsername()
        {
            return lanRtspUsername;
        }

        public void setLanRtspUsername(String lanRtspUsername)
        {
            this.lanRtspUsername = lanRtspUsername;
        }

        public int getLanRtspPort()
        {
            return lanRtspPort;
        }

        public void setLanRtspPort(int lanRtspPort)
        {
            this.lanRtspPort = lanRtspPort;
        }

        public String getLanRtspStreamPath()
        {
            return lanRtspStreamPath;
        }

        public void setLanRtspStreamPath(String lanRtspStreamPath)
        {
            this.lanRtspStreamPath = lanRtspStreamPath;
        }

        public String getGo2rtcBaseUrl()
        {
            return go2rtcBaseUrl;
        }

        public void setGo2rtcBaseUrl(String go2rtcBaseUrl)
        {
            this.go2rtcBaseUrl = go2rtcBaseUrl;
        }

        public String getGo2rtcPublicBaseUrl()
        {
            return go2rtcPublicBaseUrl;
        }

        public void setGo2rtcPublicBaseUrl(String go2rtcPublicBaseUrl)
        {
            this.go2rtcPublicBaseUrl = go2rtcPublicBaseUrl;
        }

        public String getGo2rtcRtspBaseUrl()
        {
            return go2rtcRtspBaseUrl;
        }

        public void setGo2rtcRtspBaseUrl(String go2rtcRtspBaseUrl)
        {
            this.go2rtcRtspBaseUrl = go2rtcRtspBaseUrl;
        }

        public boolean isAutoRestartEnabled()
        {
            return autoRestartEnabled;
        }

        public void setAutoRestartEnabled(boolean autoRestartEnabled)
        {
            this.autoRestartEnabled = autoRestartEnabled;
        }

        public double getRestartBackoffSec()
        {
            return restartBackoffSec;
        }

        public void setRestartBackoffSec(double restartBackoffSec)
        {
            this.restartBackoffSec = restartBackoffSec;
        }

        public double getRestartBackoffMaxSec()
        {
            return restartBackoffMaxSec;
        }

        public void setRestartBackoffMaxSec(double restartBackoffMaxSec)
        {
            this.restartBackoffMaxSec = restartBackoffMaxSec;
        }

        public boolean isResumeOnBoot()
        {
            return resumeOnBoot;
        }

        public void setResumeOnBoot(boolean resumeOnBoot)
        {
            this.resumeOnBoot = resumeOnBoot;
        }

        public double getFrameReconnectIdleSec()
        {
            return frameReconnectIdleSec;
        }

        public void setFrameReconnectIdleSec(double frameReconnectIdleSec)
        {
            this.frameReconnectIdleSec = frameReconnectIdleSec;
        }

        public double getHeartbeatTimeoutSec()
        {
            return heartbeatTimeoutSec;
        }

        public void setHeartbeatTimeoutSec(double heartbeatTimeoutSec)
        {
            this.heartbeatTimeoutSec = heartbeatTimeoutSec;
        }
    }

    public static class ClipCapture
    {
        private boolean enabled = true;

        private double preRollSec = 3.0;

        private double postRollSec = 3.0;

        private double trackLostSec = 2.0;

        private double sceneMergeGapSec = 10.0;

        /** 本地录像：ffmpeg -c copy 分段环形缓冲，事件时按预录/后录切片（保持源码流画质）。 */
        private boolean localRecordingEnabled = false;

        /** ffmpeg 可执行文件；为空则使用 PATH 中的 ffmpeg */
        private String ffmpegPath = "ffmpeg";

        /** 环形缓冲分段时长（秒），越小时间边界越准 */
        private double ringSegmentSec = 2.0;

        /** 单段本地录像最长秒数，防止异常一直录 */
        private double maxDurationSec = 300.0;

        /** 场景片落盘后自动跑 YOLO 离线分析 */
        private boolean autoAnalyzeYolo = true;

        /** YOLO 分析成功后自动导入行为日志 */
        private boolean autoImportBehaviorLogs = true;

        /** Ezviz cloud recording address format. MP4 is preferred for download and analysis. */
        private String ezvizRecordingFormat = "MP4";

        private int ezvizAddressExpireSeconds = 1800;

        private int ezvizTranscodeBusType = 7;

        private int ezvizTranscodePollIntervalMs = 3000;

        private int ezvizTranscodePollMaxAttempts = 20;

        private String ezvizSpaceId = "";

        private String ezvizResultSpaceId = "";

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public double getPreRollSec()
        {
            return preRollSec;
        }

        public void setPreRollSec(double preRollSec)
        {
            this.preRollSec = preRollSec;
        }

        public double getPostRollSec()
        {
            return postRollSec;
        }

        public void setPostRollSec(double postRollSec)
        {
            this.postRollSec = postRollSec;
        }

        public double getTrackLostSec()
        {
            return trackLostSec;
        }

        public void setTrackLostSec(double trackLostSec)
        {
            this.trackLostSec = trackLostSec;
        }

        public double getSceneMergeGapSec()
        {
            return sceneMergeGapSec;
        }

        public void setSceneMergeGapSec(double sceneMergeGapSec)
        {
            this.sceneMergeGapSec = sceneMergeGapSec;
        }

        public boolean isLocalRecordingEnabled()
        {
            return localRecordingEnabled;
        }

        public void setLocalRecordingEnabled(boolean localRecordingEnabled)
        {
            this.localRecordingEnabled = localRecordingEnabled;
        }

        public String getFfmpegPath()
        {
            return ffmpegPath;
        }

        public void setFfmpegPath(String ffmpegPath)
        {
            this.ffmpegPath = ffmpegPath;
        }

        public double getRingSegmentSec()
        {
            return ringSegmentSec;
        }

        public void setRingSegmentSec(double ringSegmentSec)
        {
            this.ringSegmentSec = ringSegmentSec;
        }

        public double getMaxDurationSec()
        {
            return maxDurationSec;
        }

        public void setMaxDurationSec(double maxDurationSec)
        {
            this.maxDurationSec = maxDurationSec;
        }

        public boolean isAutoAnalyzeYolo()
        {
            return autoAnalyzeYolo;
        }

        public void setAutoAnalyzeYolo(boolean autoAnalyzeYolo)
        {
            this.autoAnalyzeYolo = autoAnalyzeYolo;
        }

        public boolean isAutoImportBehaviorLogs()
        {
            return autoImportBehaviorLogs;
        }

        public void setAutoImportBehaviorLogs(boolean autoImportBehaviorLogs)
        {
            this.autoImportBehaviorLogs = autoImportBehaviorLogs;
        }

        public String getEzvizRecordingFormat()
        {
            return ezvizRecordingFormat;
        }

        public void setEzvizRecordingFormat(String ezvizRecordingFormat)
        {
            this.ezvizRecordingFormat = ezvizRecordingFormat;
        }

        public int getEzvizAddressExpireSeconds()
        {
            return ezvizAddressExpireSeconds;
        }

        public void setEzvizAddressExpireSeconds(int ezvizAddressExpireSeconds)
        {
            this.ezvizAddressExpireSeconds = ezvizAddressExpireSeconds;
        }

        public int getEzvizTranscodeBusType()
        {
            return ezvizTranscodeBusType;
        }

        public void setEzvizTranscodeBusType(int ezvizTranscodeBusType)
        {
            this.ezvizTranscodeBusType = ezvizTranscodeBusType;
        }

        public int getEzvizTranscodePollIntervalMs()
        {
            return ezvizTranscodePollIntervalMs;
        }

        public void setEzvizTranscodePollIntervalMs(int ezvizTranscodePollIntervalMs)
        {
            this.ezvizTranscodePollIntervalMs = ezvizTranscodePollIntervalMs;
        }

        public int getEzvizTranscodePollMaxAttempts()
        {
            return ezvizTranscodePollMaxAttempts;
        }

        public void setEzvizTranscodePollMaxAttempts(int ezvizTranscodePollMaxAttempts)
        {
            this.ezvizTranscodePollMaxAttempts = ezvizTranscodePollMaxAttempts;
        }

        public String getEzvizSpaceId()
        {
            return ezvizSpaceId;
        }

        public void setEzvizSpaceId(String ezvizSpaceId)
        {
            this.ezvizSpaceId = ezvizSpaceId;
        }

        public String getEzvizResultSpaceId()
        {
            return ezvizResultSpaceId;
        }

        public void setEzvizResultSpaceId(String ezvizResultSpaceId)
        {
            this.ezvizResultSpaceId = ezvizResultSpaceId;
        }
    }

    public static class AiAnalysis
    {
        private boolean enabled = false;

        private int timeoutSec = 180;

        private boolean autoRun = false;

        private String prompt = "当前监控视角在实验室内部、朝门外拍摄。人物向外走（离开实验室）=出门，向里走（进入实验室）=进门。请分析这段监控视频，只返回JSON对象，字段：summary（用一两句话概括画面中有谁、是进门还是出门、在做什么）、personCount（画面中人数，整数）。不要返回其他字段。";

        /** 直接传视频文件时的抽帧频率（传给模型的 fps 参数） */
        private double videoFps = 2.0;

        /** 兼容旧配置；智能抽帧下仅作模型侧参考 fps */
        private double frameExtractFps = 1.0;

        /** 本地抽帧最多张数 */
        private int maxFrameCount = 10;

        /** 无 YOLO 事件时，在视频中段该比例窗口内均匀抽帧（默认 0.6 = 中间 60%） */
        private double middleWindowRatio = 0.6;

        /** 有 YOLO 事件时，每个事件前后各扩多少秒抽帧 */
        private double eventPaddingSec = 1.5;

        /** 抽帧缩放宽度（保持比例） */
        private int frameWidth = 960;

        /** 小于该字节数时优先 base64 直传整段视频，否则抽帧；默认 512KB，避免大文件上传超时 */
        private long maxBase64Bytes = 512L * 1024;

        private java.util.List<Model> models = new java.util.ArrayList<>();

        private Oss oss = new Oss();

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public int getTimeoutSec()
        {
            return timeoutSec;
        }

        public void setTimeoutSec(int timeoutSec)
        {
            this.timeoutSec = timeoutSec;
        }

        public boolean isAutoRun()
        {
            return autoRun;
        }

        public void setAutoRun(boolean autoRun)
        {
            this.autoRun = autoRun;
        }

        public String getPrompt()
        {
            return prompt;
        }

        public void setPrompt(String prompt)
        {
            this.prompt = prompt;
        }

        public double getVideoFps()
        {
            return videoFps;
        }

        public void setVideoFps(double videoFps)
        {
            this.videoFps = videoFps;
        }

        public double getFrameExtractFps()
        {
            return frameExtractFps;
        }

        public void setFrameExtractFps(double frameExtractFps)
        {
            this.frameExtractFps = frameExtractFps;
        }

        public int getMaxFrameCount()
        {
            return maxFrameCount;
        }

        public void setMaxFrameCount(int maxFrameCount)
        {
            this.maxFrameCount = maxFrameCount;
        }

        public double getMiddleWindowRatio()
        {
            return middleWindowRatio;
        }

        public void setMiddleWindowRatio(double middleWindowRatio)
        {
            this.middleWindowRatio = middleWindowRatio;
        }

        public double getEventPaddingSec()
        {
            return eventPaddingSec;
        }

        public void setEventPaddingSec(double eventPaddingSec)
        {
            this.eventPaddingSec = eventPaddingSec;
        }

        public int getFrameWidth()
        {
            return frameWidth;
        }

        public void setFrameWidth(int frameWidth)
        {
            this.frameWidth = frameWidth;
        }

        public long getMaxBase64Bytes()
        {
            return maxBase64Bytes;
        }

        public void setMaxBase64Bytes(long maxBase64Bytes)
        {
            this.maxBase64Bytes = maxBase64Bytes;
        }

        public java.util.List<Model> getModels()
        {
            return models;
        }

        public void setModels(java.util.List<Model> models)
        {
            this.models = models;
        }

        public Oss getOss()
        {
            return oss;
        }

        public void setOss(Oss oss)
        {
            this.oss = oss;
        }
    }

    public static class Oss
    {
        private boolean enabled = false;

        private String endpoint = "";

        private String bucket = "";

        private String accessKeyId = "";

        private String accessKeySecret = "";

        private String publicBaseUrl = "";

        private String objectPrefix = "safetyguard/clips";

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getEndpoint()
        {
            return endpoint;
        }

        public void setEndpoint(String endpoint)
        {
            this.endpoint = endpoint;
        }

        public String getBucket()
        {
            return bucket;
        }

        public void setBucket(String bucket)
        {
            this.bucket = bucket;
        }

        public String getAccessKeyId()
        {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId)
        {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret()
        {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret)
        {
            this.accessKeySecret = accessKeySecret;
        }

        public String getPublicBaseUrl()
        {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl)
        {
            this.publicBaseUrl = publicBaseUrl;
        }

        public String getObjectPrefix()
        {
            return objectPrefix;
        }

        public void setObjectPrefix(String objectPrefix)
        {
            this.objectPrefix = objectPrefix;
        }
    }

    public static class Model
    {
        private boolean enabled = false;

        private String modelKey;

        private String modelName;

        private String baseUrl;

        private String apiKey;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getModelKey()
        {
            return modelKey;
        }

        public void setModelKey(String modelKey)
        {
            this.modelKey = modelKey;
        }

        public String getModelName()
        {
            return modelName;
        }

        public void setModelName(String modelName)
        {
            this.modelName = modelName;
        }

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public Long getDefaultCameraId()
    {
        return defaultCameraId;
    }

    public void setDefaultCameraId(Long defaultCameraId)
    {
        this.defaultCameraId = defaultCameraId;
    }

    public String getWorkspaceRoot()
    {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot)
    {
        this.workspaceRoot = workspaceRoot;
    }

    public String getReplayPythonCommand()
    {
        return replayPythonCommand;
    }

    public void setReplayPythonCommand(String replayPythonCommand)
    {
        this.replayPythonCommand = replayPythonCommand;
    }

    public String getReplayScriptPath()
    {
        return replayScriptPath;
    }

    public void setReplayScriptPath(String replayScriptPath)
    {
        this.replayScriptPath = replayScriptPath;
    }

    public String getAnalyzeScriptPath()
    {
        return analyzeScriptPath;
    }

    public void setAnalyzeScriptPath(String analyzeScriptPath)
    {
        this.analyzeScriptPath = analyzeScriptPath;
    }

    public String getReplayProfileRoot()
    {
        return replayProfileRoot;
    }

    public void setReplayProfileRoot(String replayProfileRoot)
    {
        this.replayProfileRoot = replayProfileRoot;
    }

    public String getReplayIngestBaseUrl()
    {
        return replayIngestBaseUrl;
    }

    public void setReplayIngestBaseUrl(String replayIngestBaseUrl)
    {
        this.replayIngestBaseUrl = replayIngestBaseUrl;
    }

    public Integer getReplayLineY()
    {
        return replayLineY;
    }

    public void setReplayLineY(Integer replayLineY)
    {
        this.replayLineY = replayLineY;
    }

    public String getReplayRoi()
    {
        return replayRoi;
    }

    public void setReplayRoi(String replayRoi)
    {
        this.replayRoi = replayRoi;
    }

    public String getCaptureScriptPath()
    {
        return captureScriptPath;
    }

    public void setCaptureScriptPath(String captureScriptPath)
    {
        this.captureScriptPath = captureScriptPath;
    }

    public String getStorageRoot()
    {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot)
    {
        this.storageRoot = storageRoot;
    }

    public Double getSnapshotWindowSec()
    {
        return snapshotWindowSec;
    }

    public void setSnapshotWindowSec(Double snapshotWindowSec)
    {
        this.snapshotWindowSec = snapshotWindowSec;
    }

    public String getEmbedScriptPath()
    {
        return embedScriptPath;
    }

    public void setEmbedScriptPath(String embedScriptPath)
    {
        this.embedScriptPath = embedScriptPath;
    }

    public String getFaceEmbedModel()
    {
        return faceEmbedModel;
    }

    public void setFaceEmbedModel(String faceEmbedModel)
    {
        this.faceEmbedModel = faceEmbedModel;
    }

    public String getBodyEmbedModel()
    {
        return bodyEmbedModel;
    }

    public void setBodyEmbedModel(String bodyEmbedModel)
    {
        this.bodyEmbedModel = bodyEmbedModel;
    }

    public Double getFaceMinDetScore()
    {
        return faceMinDetScore;
    }

    public void setFaceMinDetScore(Double faceMinDetScore)
    {
        this.faceMinDetScore = faceMinDetScore;
    }

    public String getFaceEmbedMode()
    {
        return faceEmbedMode;
    }

    public void setFaceEmbedMode(String faceEmbedMode)
    {
        this.faceEmbedMode = faceEmbedMode;
    }

    public Double getFaceMatchThreshold()
    {
        return faceMatchThreshold;
    }

    public void setFaceMatchThreshold(Double faceMatchThreshold)
    {
        this.faceMatchThreshold = faceMatchThreshold;
    }

    public Double getBodyMatchThreshold()
    {
        return bodyMatchThreshold;
    }

    public void setBodyMatchThreshold(Double bodyMatchThreshold)
    {
        this.bodyMatchThreshold = bodyMatchThreshold;
    }

    public String getLiveScriptPath()
    {
        return liveScriptPath;
    }

    public void setLiveScriptPath(String liveScriptPath)
    {
        this.liveScriptPath = liveScriptPath;
    }

    public String getProbeScriptPath()
    {
        return probeScriptPath;
    }

    public void setProbeScriptPath(String probeScriptPath)
    {
        this.probeScriptPath = probeScriptPath;
    }

    public LiveIngest getLive()
    {
        return live;
    }

    public void setLive(LiveIngest live)
    {
        this.live = live;
    }

    public ClipCapture getClip()
    {
        return clip;
    }

    public void setClip(ClipCapture clip)
    {
        this.clip = clip;
    }

    public AiAnalysis getAnalysis()
    {
        return analysis;
    }

    public void setAnalysis(AiAnalysis analysis)
    {
        this.analysis = analysis;
    }

    /**
     * face_library / body_library / capture_manifest 所在根目录。
     * 优先使用 storageRoot 配置，未配置时取 workspaceRoot 上一级。
     */
    public String resolveStorageRoot()
    {
        if (!StringUtils.isEmpty(storageRoot) && !".".equals(storageRoot))
        {
            return storageRoot;
        }
        File workspace = new File(workspaceRoot);
        File parent = workspace.getParentFile();
        return parent != null ? parent.getAbsolutePath() : workspace.getAbsolutePath();
    }

    public Path resolveStorageRootPath()
    {
        return Paths.get(resolveStorageRoot()).normalize();
    }
}
