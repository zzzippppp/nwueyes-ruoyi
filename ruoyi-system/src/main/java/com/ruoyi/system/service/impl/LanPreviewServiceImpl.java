package com.ruoyi.system.service.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceLiveStartBo;
import com.ruoyi.system.domain.vo.CameraConfigVo;
import com.ruoyi.system.domain.vo.LanPreviewVo;
import com.ruoyi.system.service.ICameraService;
import com.ruoyi.system.service.IEzvizScreenService;
import com.ruoyi.system.service.ILanPreviewService;

@Service
public class LanPreviewServiceImpl implements ILanPreviewService
{
    private static final String STREAM_MODE = PresenceLiveStartBo.STREAM_LAN_RTSP;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Autowired
    private IEzvizScreenService ezvizScreenService;

    @Autowired
    private ICameraService cameraService;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Override
    public String ensureLocalRtsp(PresenceLiveStartBo bo)
    {
        validateBo(bo);
        applyCameraFromBo(bo);

        int channelNo = bo.getChannelNo() == null || bo.getChannelNo() < 1 ? 1 : bo.getChannelNo();
        CameraConfigVo cameraConfig = cameraService.getCameraConfig(bo.getCameraId());
        String localIp = cameraConfig != null ? cameraConfig.getIpAddr() : null;
        String rtspUrl = ezvizScreenService.resolveAnalyzeStreamUrl(
                bo.getDeviceSerial(), channelNo, STREAM_MODE, bo.getValidCode(), localIp);

        String streamName = buildStreamName(bo.getCameraId());
        registerGo2RtcStream(streamName, rtspUrl);
        return resolveLocalRtspBaseUrl() + "/" + streamName;
    }

    @Override
    public LanPreviewVo startPreview(PresenceLiveStartBo bo)
    {
        ensureLocalRtsp(bo);
        String streamName = buildStreamName(bo.getCameraId());

        String publicBase = resolvePublicBaseUrl();
        String previewUrl = publicBase + "/stream.html?src=" + urlEncode(streamName) + "&mode=webrtc,mse";

        LanPreviewVo vo = new LanPreviewVo();
        vo.setCameraId(bo.getCameraId());
        vo.setStreamName(streamName);
        vo.setPreviewUrl(previewUrl);
        vo.setPreviewMode("webrtc");
        return vo;
    }

    @Override
    public void stopPreview(Long cameraId)
    {
        // go2rtc 按需拉流：网页消费者退出后会自动断开上游。
        // 保留流注册，避免停止网页预览时误删 Python 识别/抽帧正在共用的流。
    }

    private void validateBo(PresenceLiveStartBo bo)
    {
        if (bo == null)
        {
            throw new ServiceException("预览参数不能为空");
        }
        if (bo.getCameraId() == null)
        {
            throw new ServiceException("请选择摄像头");
        }
    }

    private void applyCameraFromBo(PresenceLiveStartBo bo)
    {
        if (bo.getCameraId() == null)
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
        if (StringUtils.isEmpty(bo.getValidCode()) && !StringUtils.isEmpty(cfg.getVerifyCode()))
        {
            bo.setValidCode(cfg.getVerifyCode());
        }
    }

    private String buildStreamName(Long cameraId)
    {
        return "cam_" + cameraId;
    }

    private String resolveGo2RtcBaseUrl()
    {
        String base = ingestProperties.getLive().getGo2rtcBaseUrl();
        if (StringUtils.isEmpty(base))
        {
            throw new ServiceException("未配置 go2rtc 地址（presence.ingest.live.go2rtcBaseUrl），请先启动 go2rtc");
        }
        return trimTrailingSlash(base.trim());
    }

    private String resolvePublicBaseUrl()
    {
        String publicBase = ingestProperties.getLive().getGo2rtcPublicBaseUrl();
        if (StringUtils.isEmpty(publicBase))
        {
            publicBase = resolveGo2RtcBaseUrl();
        }
        return trimTrailingSlash(publicBase.trim());
    }

    private String resolveLocalRtspBaseUrl()
    {
        String base = ingestProperties.getLive().getGo2rtcRtspBaseUrl();
        if (StringUtils.isEmpty(base))
        {
            base = "rtsp://127.0.0.1:8554";
        }
        return trimTrailingSlash(base.trim());
    }

    private void registerGo2RtcStream(String streamName, String rtspUrl)
    {
        String base = resolveGo2RtcBaseUrl();
        String url = base + "/api/streams?name=" + urlEncode(streamName) + "&src=" + urlEncode(rtspUrl);
        int status = sendRequest("PUT", url);
        if (status >= 200 && status < 300)
        {
            return;
        }
        if (status == 400)
        {
            patchGo2RtcStream(streamName, rtspUrl, base);
            return;
        }
        throw new ServiceException("go2rtc 注册预览流失败（HTTP " + status + "），请确认 go2rtc 已启动且可访问");
    }

    private void patchGo2RtcStream(String streamName, String rtspUrl, String base)
    {
        String url = base + "/api/streams?name=" + urlEncode(streamName) + "&src=" + urlEncode(rtspUrl);
        int status = sendRequest("PATCH", url);
        if (status < 200 || status >= 300)
        {
            throw new ServiceException("go2rtc 更新预览流失败（HTTP " + status + "）");
        }
    }

    private int sendRequest(String method, String url)
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("无法连接 go2rtc（" + ex.getMessage() + "），请先启动 go2rtc 服务");
        }
    }

    private String urlEncode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value)
    {
        while (value.endsWith("/"))
        {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
