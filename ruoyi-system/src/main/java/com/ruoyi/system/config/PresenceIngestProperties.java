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
     * 进门人脸 cosine 相似度阈值（0~1，越高越严）
     */
    private Double faceMatchThreshold = 0.45;

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
    }

    public static class ClipCapture
    {
        private boolean enabled = true;

        private double preRollSec = 3.0;

        private double postRollSec = 3.0;

        private double trackLostSec = 2.0;

        private double sceneMergeGapSec = 5.0;

        /** 本地 OpenCV MP4 仅作为兜底录像，默认由萤石回放提供最终片段。 */
        private boolean localRecordingEnabled = false;

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

        private int timeoutSec = 60;

        private boolean autoRun = false;

        private String prompt = "请分析这段监控视频中的人物外观、行为、多人互动和潜在风险，返回JSON字段summary、appearance、behavior、riskLevel。";

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
