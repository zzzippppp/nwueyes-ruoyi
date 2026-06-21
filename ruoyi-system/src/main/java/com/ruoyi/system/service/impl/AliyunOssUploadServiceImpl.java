package com.ruoyi.system.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.service.IOssUploadService;

@Service
public class AliyunOssUploadServiceImpl implements IOssUploadService
{
    private static final String CONTENT_TYPE_MP4 = "video/mp4";

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Override
    public String uploadClip(Path localFile, String objectKey)
    {
        PresenceIngestProperties.Oss oss = resolveOssConfig();
        if (localFile == null || !Files.exists(localFile))
        {
            throw new IllegalArgumentException("local clip file does not exist");
        }
        String normalizedKey = normalizeObjectKey(objectKey, localFile);
        try
        {
            String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(java.time.ZoneOffset.UTC));
            String resource = "/" + oss.getBucket().trim() + "/" + normalizedKey;
            String signature = sign("PUT\n\n" + CONTENT_TYPE_MP4 + "\n" + date + "\n" + resource,
                    oss.getAccessKeySecret().trim());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildPutUrl(oss, normalizedKey)))
                    .timeout(Duration.ofSeconds(120))
                    .header("Date", date)
                    .header("Content-Type", CONTENT_TYPE_MP4)
                    .header("Authorization", "OSS " + oss.getAccessKeyId().trim() + ":" + signature)
                    .PUT(HttpRequest.BodyPublishers.ofFile(localFile))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                throw new IllegalStateException("OSS upload HTTP " + response.statusCode() + ": " + response.body());
            }
            return buildPublicUrl(oss, normalizedKey);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("OSS upload failed: " + ex.getMessage(), ex);
        }
    }

    private PresenceIngestProperties.Oss resolveOssConfig()
    {
        PresenceIngestProperties.AiAnalysis analysis = ingestProperties.getAnalysis();
        PresenceIngestProperties.Oss oss = analysis == null ? null : analysis.getOss();
        if (oss == null || !oss.isEnabled())
        {
            throw new IllegalStateException("OSS is not enabled");
        }
        if (StringUtils.isEmpty(oss.getEndpoint()) || StringUtils.isEmpty(oss.getBucket())
                || StringUtils.isEmpty(oss.getAccessKeyId()) || StringUtils.isEmpty(oss.getAccessKeySecret())
                || StringUtils.isEmpty(oss.getPublicBaseUrl()))
        {
            throw new IllegalStateException("OSS endpoint/bucket/accessKey/publicBaseUrl is incomplete");
        }
        return oss;
    }

    private String normalizeObjectKey(String objectKey, Path localFile)
    {
        String prefix = "";
        PresenceIngestProperties.Oss oss = ingestProperties.getAnalysis().getOss();
        if (oss != null && StringUtils.isNotEmpty(oss.getObjectPrefix()))
        {
            prefix = oss.getObjectPrefix().replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
        }
        String key = StringUtils.nvl(objectKey, localFile.getFileName().toString())
                .replace("\\", "/")
                .replaceAll("^/+", "");
        if (StringUtils.isNotEmpty(prefix) && !key.startsWith(prefix + "/"))
        {
            key = prefix + "/" + key;
        }
        return key;
    }

    private String buildPutUrl(PresenceIngestProperties.Oss oss, String objectKey)
    {
        String endpoint = oss.getEndpoint().trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://"))
        {
            endpoint = "https://" + endpoint;
        }
        endpoint = endpoint.replaceAll("/+$", "");
        URI endpointUri = URI.create(endpoint);
        String host = endpointUri.getHost();
        String scheme = endpointUri.getScheme();
        int port = endpointUri.getPort();
        String authority = oss.getBucket().trim() + "." + host + (port > 0 ? ":" + port : "");
        return scheme + "://" + authority + "/" + objectKey;
    }

    private String buildPublicUrl(PresenceIngestProperties.Oss oss, String objectKey)
    {
        return oss.getPublicBaseUrl().replaceAll("/+$", "") + "/" + objectKey;
    }

    private String sign(String text, String secret) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
