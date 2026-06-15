package com.ruoyi.system.service.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.EzvizProperties;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.vo.EzvizPlaybackClipVo;
import com.ruoyi.system.service.IEzvizPlaybackService;
import com.ruoyi.system.service.IEzvizScreenService;
import com.ruoyi.system.storage.PresenceStoragePaths;

@Service
public class EzvizPlaybackServiceImpl implements IEzvizPlaybackService
{
    private static final Logger log = LoggerFactory.getLogger(EzvizPlaybackServiceImpl.class);

    private static final ZoneId DEVICE_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter EZOPEN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String ADDRESS_API = "/api/service/open/vod/file/address/hls";

    private static final String TRANSCODE_API = "/api/service/open/vod/media/trans/code";

    private static final String TASK_API = "/api/service/open/vod/task";

    @Autowired
    private EzvizProperties ezvizProperties;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private IEzvizScreenService ezvizScreenService;

    @Autowired
    private PresenceStoragePaths storagePaths;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public EzvizPlaybackClipVo resolvePlaybackClip(String deviceSerial, Integer channelNo, String validCode,
            Date startTime, Date endTime, boolean preferLocal)
    {
        EzvizPlaybackClipVo result = new EzvizPlaybackClipVo();
        if (StringUtils.isEmpty(deviceSerial) || startTime == null || endTime == null)
        {
            result.setProviderStatus("failed");
            result.setErrorMessage("deviceSerial/startTime/endTime cannot be empty");
            return result;
        }

        int channel = channelNo == null || channelNo.intValue() < 1 ? 1 : channelNo.intValue();
        PresenceIngestProperties.ClipCapture clipConfig = ingestProperties.getClip();
        try
        {
            log.info("ezviz-address-request device={} channel={} start={} end={}", deviceSerial, channel,
                    formatRequestTime(startTime), formatRequestTime(endTime));
            String sourceUrl = resolvePlaybackAddressByTime(deviceSerial, channel, startTime, endTime, clipConfig);
            if (StringUtils.isNotEmpty(sourceUrl))
            {
                result.setPlaybackUrl(sourceUrl);
                result.setProviderSourceUrl(sourceUrl);
                if (preferLocal)
                {
                    String localUrl = downloadToLocal(sourceUrl, deviceSerial, channel, startTime);
                    result.setDownloadedLocalUrl(localUrl);
                    result.setProviderStatus("local_downloaded");
                    log.info("local-download-success device={} channel={} url={}", deviceSerial, channel, localUrl);
                    return result;
                }
                result.setProviderStatus("ezviz_playback_only");
                return result;
            }
        }
        catch (Exception ex)
        {
            result.setErrorMessage("3973 failed: " + ex.getMessage());
            log.warn("ezviz-address-request failed device={} channel={}: {}", deviceSerial, channel, ex.getMessage());
        }

        try
        {
            String taskId = createTranscodeTask(deviceSerial, channel, validCode, startTime, endTime, clipConfig);
            result.setProviderTaskId(taskId);
            result.setProviderStatus("ezviz_task_processing");
            if (waitTranscodeTask(taskId, clipConfig))
            {
                log.info("ezviz-task-finished taskId={} device={} channel={}", taskId, deviceSerial, channel);
                String sourceUrl = resolvePlaybackAddressByTime(deviceSerial, channel, startTime, endTime, clipConfig);
                result.setPlaybackUrl(sourceUrl);
                result.setProviderSourceUrl(sourceUrl);
                if (preferLocal && StringUtils.isNotEmpty(sourceUrl))
                {
                    String localUrl = downloadToLocal(sourceUrl, deviceSerial, channel, startTime);
                    result.setDownloadedLocalUrl(localUrl);
                    result.setProviderStatus("local_downloaded");
                    log.info("local-download-success taskId={} url={}", taskId, localUrl);
                    return result;
                }
                result.setProviderStatus("ezviz_playback_only");
                return result;
            }
            result.setProviderStatus("ezviz_task_failed");
            result.setErrorMessage(appendError(result.getErrorMessage(), "transcode task timeout or failed"));
            return result;
        }
        catch (Exception ex)
        {
            result.setProviderStatus("ezviz_task_failed");
            result.setErrorMessage(appendError(result.getErrorMessage(), "3971/3972 failed: " + ex.getMessage()));
            log.warn("ezviz-task-create/poll failed device={} channel={}: {}", deviceSerial, channel, ex.getMessage());
        }

        String ezopenUrl = buildEzopenPlaybackUrl(deviceSerial, channel, validCode, startTime, endTime);
        result.setPlaybackUrl(ezopenUrl);
        result.setProviderSourceUrl(ezopenUrl);
        result.setProviderStatus("ezviz_playback_only");
        result.setErrorMessage(appendError(result.getErrorMessage(), "fallback to ezopen playback URL; local download unavailable"));
        return result;
    }

    private String resolvePlaybackAddressByTime(String deviceSerial, int channelNo, Date startTime, Date endTime,
            PresenceIngestProperties.ClipCapture clipConfig) throws Exception
    {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("startTime", formatRequestTime(startTime));
        body.put("endTime", formatRequestTime(endTime));
        body.put("format", StringUtils.nvl(clipConfig.getEzvizRecordingFormat(), "MP4"));
        body.put("expireSeconds", String.valueOf(Math.max(60, clipConfig.getEzvizAddressExpireSeconds())));
        putIfNotEmpty(body, "spaceId", clipConfig.getEzvizSpaceId());
        JSONObject response = sendEzvizRequest("POST", ADDRESS_API, deviceSerial, channelNo, body);
        JSONArray data = resolveDataArray(response);
        if (data == null || data.isEmpty())
        {
            return "";
        }
        JSONObject first = data.getJSONObject(0);
        return firstNotBlank(first.getString("hlsAddress"), first.getString("url"), first.getString("playbackUrl"),
                first.getString("fileAddress"), first.getString("address"));
    }

    private String createTranscodeTask(String deviceSerial, int channelNo, String validCode, Date startTime, Date endTime,
            PresenceIngestProperties.ClipCapture clipConfig) throws Exception
    {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("busType", String.valueOf(clipConfig.getEzvizTranscodeBusType()));
        body.put("format", StringUtils.nvl(clipConfig.getEzvizRecordingFormat(), "MP4"));
        body.put("startTime", formatRequestTime(startTime));
        body.put("endTime", formatRequestTime(endTime));
        putIfNotEmpty(body, "validateCode", validCode);
        putIfNotEmpty(body, "spaceId", clipConfig.getEzvizSpaceId());
        putIfNotEmpty(body, "resultSpaceId", clipConfig.getEzvizResultSpaceId());
        log.info("ezviz-task-create device={} channel={} start={} end={}", deviceSerial, channelNo,
                body.get("startTime"), body.get("endTime"));
        JSONObject response = sendEzvizRequest("POST", TRANSCODE_API, deviceSerial, channelNo, body);
        JSONObject data = response.getJSONObject("data");
        String taskId = data == null ? "" : data.getString("taskId");
        if (StringUtils.isEmpty(taskId))
        {
            throw new IllegalStateException("transcode response missing taskId");
        }
        return taskId;
    }

    private boolean waitTranscodeTask(String taskId, PresenceIngestProperties.ClipCapture clipConfig) throws Exception
    {
        int attempts = Math.max(1, clipConfig.getEzvizTranscodePollMaxAttempts());
        long intervalMs = Math.max(500, clipConfig.getEzvizTranscodePollIntervalMs());
        for (int i = 0; i < attempts; i++)
        {
            JSONObject response = sendEzvizRequest("GET", TASK_API + "?taskId=" + encode(taskId), "", 1, null);
            JSONObject data = response.getJSONObject("data");
            Integer rawStatus = data == null ? null : data.getInteger("taskStatus");
            int status = rawStatus == null ? -1 : rawStatus.intValue();
            String progress = data == null ? "" : firstNotBlank(data.getString("progressRate"), data.getString("completeRate"));
            log.info("ezviz-task-poll taskId={} attempt={}/{} status={} progress={}", taskId, i + 1, attempts,
                    status, progress);
            if (status == 0 || status == 3)
            {
                return true;
            }
            if (status == 4 || status == 5 || status == 6)
            {
                String error = data == null ? "" : firstNotBlank(data.getString("errorMsg"), data.getString("errorCode"));
                throw new IllegalStateException("transcode task failed status=" + status + " " + error);
            }
            Thread.sleep(intervalMs);
        }
        return false;
    }

    private String downloadToLocal(String sourceUrl, String deviceSerial, int channelNo, Date startTime) throws Exception
    {
        if (StringUtils.isEmpty(sourceUrl) || (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")))
        {
            throw new IllegalArgumentException("source url is not downloadable http(s)");
        }
        LocalDate date = startTime.toInstant().atZone(DEVICE_ZONE).toLocalDate();
        String fileName = "ezviz_" + sanitize(deviceSerial) + "_ch" + channelNo + "_"
                + EZOPEN_TIME.format(startTime.toInstant().atZone(DEVICE_ZONE)) + ".mp4";
        Path dir = storagePaths.clipDir(date);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName).normalize();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(180))
                .GET()
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            Files.deleteIfExists(target);
            throw new IllegalStateException("download HTTP " + response.statusCode());
        }
        if (!Files.exists(target) || Files.size(target) <= 0)
        {
            Files.deleteIfExists(target);
            throw new IllegalStateException("downloaded file is empty");
        }
        return storagePaths.buildClipUrl(date, fileName);
    }

    private JSONObject sendEzvizRequest(String method, String path, String deviceSerial, int channelNo,
            Map<String, String> body) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(buildRequestUrl(path)))
                .timeout(Duration.ofSeconds(30))
                .header("accessToken", ezvizScreenService.getOpenApiAccessToken());
        if (StringUtils.isNotEmpty(deviceSerial))
        {
            builder.header("deviceSerial", deviceSerial.trim());
            builder.header("localIndex", String.valueOf(channelNo));
        }
        if ("POST".equalsIgnoreCase(method))
        {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormBody(body), StandardCharsets.UTF_8));
        }
        else
        {
            builder.GET();
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IllegalStateException("Ezviz HTTP " + response.statusCode() + ": " + response.body());
        }
        JSONObject json = JSON.parseObject(response.body());
        if (!isSuccess(json))
        {
            throw new IllegalStateException("Ezviz response failed: " + response.body());
        }
        return json;
    }

    private boolean isSuccess(JSONObject json)
    {
        if (json == null)
        {
            return false;
        }
        String code = json.getString("code");
        if ("200".equals(code))
        {
            return true;
        }
        JSONObject meta = json.getJSONObject("meta");
        if (meta == null)
        {
            return false;
        }
        return "200".equals(meta.getString("code")) || "200".equals(meta.getString("status"));
    }

    private JSONArray resolveDataArray(JSONObject response)
    {
        JSONArray data = response.getJSONArray("data");
        if (data != null)
        {
            return data;
        }
        JSONObject dataObject = response.getJSONObject("data");
        if (dataObject == null)
        {
            return null;
        }
        JSONArray list = dataObject.getJSONArray("list");
        if (list != null)
        {
            return list;
        }
        JSONArray files = dataObject.getJSONArray("files");
        if (files != null)
        {
            return files;
        }
        JSONArray wrapped = new JSONArray();
        wrapped.add(dataObject);
        return wrapped;
    }

    private String buildRequestUrl(String path)
    {
        String baseUrl = ezvizProperties.getBaseUrl();
        if (StringUtils.endsWith(baseUrl, "/"))
        {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    private String buildFormBody(Map<String, String> params)
    {
        if (params == null || params.isEmpty())
        {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet())
        {
            if (builder.length() > 0)
            {
                builder.append("&");
            }
            builder.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private String buildEzopenPlaybackUrl(String deviceSerial, int channelNo, String validCode, Date startTime, Date endTime)
    {
        String begin = EZOPEN_TIME.format(startTime.toInstant().atZone(DEVICE_ZONE));
        String end = EZOPEN_TIME.format(endTime.toInstant().atZone(DEVICE_ZONE));
        String authPrefix = StringUtils.isEmpty(validCode) ? "" : encode(validCode.trim()) + "@";
        return "ezopen://" + authPrefix + "open.ys7.com/" + deviceSerial.trim().toUpperCase() + "/"
                + channelNo + ".rec?begin=" + begin + "&end=" + end;
    }

    private String formatRequestTime(Date time)
    {
        return REQUEST_TIME.format(time.toInstant().atZone(DEVICE_ZONE));
    }

    private void putIfNotEmpty(Map<String, String> body, String key, String value)
    {
        if (StringUtils.isNotEmpty(value))
        {
            body.put(key, value.trim());
        }
    }

    private String firstNotBlank(String... values)
    {
        for (String value : values)
        {
            if (StringUtils.isNotEmpty(value))
            {
                return value;
            }
        }
        return StringUtils.EMPTY;
    }

    private String appendError(String left, String right)
    {
        if (StringUtils.isEmpty(left))
        {
            return right;
        }
        return left + "; " + right;
    }

    private String encode(String value)
    {
        return URLEncoder.encode(StringUtils.nvl(value, StringUtils.EMPTY), StandardCharsets.UTF_8);
    }

    private String sanitize(String value)
    {
        return StringUtils.nvl(value, "device").replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
