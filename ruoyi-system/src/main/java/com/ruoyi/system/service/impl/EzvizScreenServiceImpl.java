package com.ruoyi.system.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.HttpUtils;
import com.ruoyi.system.config.EzvizProperties;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.bo.PresenceLiveStartBo;
import com.ruoyi.system.domain.vo.EzvizDeviceVo;
import com.ruoyi.system.domain.vo.EzvizScreenConfigVo;
import com.ruoyi.system.service.IEzvizScreenService;

/**
 * 萤石监控大屏服务实现
 * 
 * @author ruoyi
 */
@Service
public class EzvizScreenServiceImpl implements IEzvizScreenService
{
    private static final String TOKEN_API = "/api/lapp/token/get";

    private static final String DEVICE_LIST_API = "/api/lapp/device/list";

    private static final String LIVE_ADDRESS_API = "/api/lapp/v2/live/address/get";

    private static final String DEVICE_INFO_API = "/api/lapp/device/info";

    private static final long TOKEN_REFRESH_BUFFER_MS = 60 * 1000L;

    private static final long DEFAULT_TOKEN_EXPIRE_MS = 6L * 24 * 60 * 60 * 1000;

    @Autowired
    private EzvizProperties ezvizProperties;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    private volatile String cachedAccessToken;

    private volatile long cachedAccessTokenExpireAt;

    private final Object tokenLock = new Object();

    @Override
    public EzvizScreenConfigVo getScreenConfig()
    {
        validateConfig();
        EzvizScreenConfigVo configVo = new EzvizScreenConfigVo();
        configVo.setAccessToken(getAccessToken());
        configVo.setDefaultChannelNo(resolveDefaultChannelNo());
        configVo.setDevices(listDevices(configVo.getAccessToken()));
        return configVo;
    }

    @Override
    public String resolveAnalyzeStreamUrl(String deviceSerial, Integer channelNo, String streamMode, String validCode)
    {
        if (StringUtils.isEmpty(deviceSerial))
        {
            throw new ServiceException("deviceSerial 不能为空");
        }
        if (StringUtils.isEmpty(streamMode))
        {
            throw new ServiceException("streamMode 不能为空");
        }
        int channel = channelNo == null || channelNo < 1 ? resolveDefaultChannelNo() : channelNo.intValue();
        boolean lanRtsp = PresenceLiveStartBo.STREAM_LAN_RTSP.equalsIgnoreCase(streamMode);
        if (lanRtsp)
        {
            return resolveLanRtspUrl(deviceSerial, validCode, channel);
        }
        int protocol = ingestProperties.getLive().getEzvizCloudProtocol();

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("accessToken", getAccessToken());
        params.put("deviceSerial", deviceSerial.trim());
        params.put("channelNo", String.valueOf(channel));
        params.put("protocol", String.valueOf(protocol));
        params.put("type", "1");
        params.put("expireTime", String.valueOf(ingestProperties.getLive().getEzvizStreamExpireSec()));
        params.put("quality", String.valueOf(ingestProperties.getLive().getEzvizCloudQuality()));
        params.put("supportH265", "0");
        if (!StringUtils.isEmpty(validCode))
        {
            params.put("code", validCode.trim());
        }

        JSONObject result = requestEzvizApi(LIVE_ADDRESS_API, params);
        JSONObject data = result.getJSONObject("data");
        if (data == null)
        {
            throw new ServiceException("萤石直播地址响应缺少 data");
        }
        String url = firstNotBlank(data.getString("url"), data.getString("hdUrl"), data.getString("rtmpUrl"));
        if (StringUtils.isEmpty(url))
        {
            throw new ServiceException(lanRtsp ? "萤石未返回 RTSP 地址，请确认设备支持局域网 RTSP"
                    : "萤石未返回公网直播地址，请确认设备已开启直播且验证码正确");
        }
        return url;
    }

    @Override
    public String getOpenApiAccessToken()
    {
        return getAccessToken();
    }

    /**
     * 局域网 RTSP：萤石云取流 API 仅支持 protocol 1~4，RTSP 需直连摄像头局域网地址。
     */
    private String resolveLanRtspUrl(String deviceSerial, String validCode, int channelNo)
    {
        PresenceIngestProperties.LiveIngest live = ingestProperties.getLive();
        if (!StringUtils.isEmpty(live.getLanRtspUrl()))
        {
            return live.getLanRtspUrl().trim();
        }

        Map<String, String> infoParams = new LinkedHashMap<String, String>();
        infoParams.put("accessToken", getAccessToken());
        infoParams.put("deviceSerial", deviceSerial.trim());
        // 设备开启视频加密后，萤石设备信息接口也可能要求携带验证码。
        if (!StringUtils.isEmpty(validCode))
        {
            infoParams.put("code", validCode.trim());
        }
        JSONObject infoResult = requestEzvizApi(DEVICE_INFO_API, infoParams);
        JSONObject data = infoResult.getJSONObject("data");
        if (data == null)
        {
            throw new ServiceException("萤石设备信息响应缺少 data");
        }

        String localAddress = firstNotBlank(data.getString("localAddress"), data.getString("localIp"));
        if (StringUtils.isEmpty(localAddress))
        {
            throw new ServiceException("设备未上报局域网 IP，请确认摄像头在线且与识别服务器同网，或改用公网云转发");
        }

        boolean encrypted = resolveDeviceEncrypted(data);
        String password = StringUtils.nvl(validCode, "");
        if (encrypted && StringUtils.isEmpty(password))
        {
            throw new ServiceException("设备已开启视频加密，局域网 RTSP 需填写验证码");
        }

        String username = StringUtils.nvl(live.getLanRtspUsername(), "admin");
        int port = live.getLanRtspPort() > 0 ? live.getLanRtspPort() : 554;
        String streamPath = resolveLanRtspStreamPath(live.getLanRtspStreamPath(), channelNo);
        String userInfo = StringUtils.isEmpty(password) ? encode(username)
                : encode(username) + ":" + encode(password);
        return "rtsp://" + userInfo + "@" + localAddress + ":" + port + streamPath;
    }

    private String resolveLanRtspStreamPath(String configuredPath, int channelNo)
    {
        String path = StringUtils.isEmpty(configuredPath) ? "/Streaming/Channels/101" : configuredPath.trim();
        if (path.contains("{channel}"))
        {
            path = path.replace("{channel}", String.valueOf(channelNo));
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private boolean resolveDeviceEncrypted(JSONObject data)
    {
        Boolean encrypt = data.getBoolean("isEncrypt");
        if (encrypt != null)
        {
            return encrypt.booleanValue();
        }
        Integer encryptCode = data.getInteger("isEncrypt");
        return encryptCode != null && encryptCode.intValue() == 1;
    }

    private void validateConfig()
    {
        if (StringUtils.isEmpty(ezvizProperties.getAppKey()) || StringUtils.isEmpty(ezvizProperties.getAppSecret()))
        {
            throw new ServiceException("萤石开放平台 appKey 或 appSecret 未配置");
        }
    }

    private String getAccessToken()
    {
        long now = System.currentTimeMillis();
        if (StringUtils.isNotEmpty(cachedAccessToken) && now + TOKEN_REFRESH_BUFFER_MS < cachedAccessTokenExpireAt)
        {
            return cachedAccessToken;
        }

        synchronized (tokenLock)
        {
            now = System.currentTimeMillis();
            if (StringUtils.isNotEmpty(cachedAccessToken) && now + TOKEN_REFRESH_BUFFER_MS < cachedAccessTokenExpireAt)
            {
                return cachedAccessToken;
            }

            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("appKey", ezvizProperties.getAppKey());
            params.put("appSecret", ezvizProperties.getAppSecret());

            JSONObject result = requestEzvizApi(TOKEN_API, params);
            JSONObject data = result.getJSONObject("data");
            if (data == null)
            {
                throw new ServiceException("萤石 accessToken 响应缺少 data 字段");
            }

            String accessToken = firstNotBlank(data.getString("accessToken"), data.getString("access_token"));
            if (StringUtils.isEmpty(accessToken))
            {
                throw new ServiceException("萤石 accessToken 获取失败");
            }

            cachedAccessToken = accessToken;
            cachedAccessTokenExpireAt = resolveExpireAt(data);
            return cachedAccessToken;
        }
    }

    private List<EzvizDeviceVo> listDevices(String accessToken)
    {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("accessToken", accessToken);
        params.put("pageStart", "0");
        params.put("pageSize", "50");

        JSONObject result = requestEzvizApi(DEVICE_LIST_API, params);
        JSONArray deviceArray = extractDeviceArray(result);
        List<EzvizDeviceVo> devices = new ArrayList<EzvizDeviceVo>();
        if (deviceArray == null)
        {
            return devices;
        }

        for (int i = 0; i < deviceArray.size(); i++)
        {
            JSONObject item = deviceArray.getJSONObject(i);
            if (item == null)
            {
                continue;
            }

            String deviceSerial = item.getString("deviceSerial");
            if (StringUtils.isEmpty(deviceSerial))
            {
                continue;
            }

            EzvizDeviceVo deviceVo = new EzvizDeviceVo();
            deviceVo.setDeviceSerial(deviceSerial);
            deviceVo.setDeviceName(firstNotBlank(item.getString("deviceName"), item.getString("cameraName"), deviceSerial));
            deviceVo.setChannelNo(resolveChannelNo(item));
            deviceVo.setStatus(firstNotBlank(item.getString("status"), item.getString("deviceStatus"), "unknown"));
            deviceVo.setEncrypt(resolveEncrypt(item));
            devices.add(deviceVo);
        }

        devices.sort(Comparator.comparing(EzvizDeviceVo::getDeviceName, String.CASE_INSENSITIVE_ORDER));
        return devices;
    }

    private JSONObject requestEzvizApi(String path, Map<String, String> params)
    {
        String responseText = HttpUtils.sendPost(buildRequestUrl(path), buildFormBody(params));
        if (StringUtils.isEmpty(responseText))
        {
            throw new ServiceException("调用萤石接口失败，未获取到响应内容");
        }

        JSONObject result = JSON.parseObject(responseText);
        if (result == null)
        {
            throw new ServiceException("萤石接口返回了无法解析的结果");
        }

        String code = result.getString("code");
        if (!"200".equals(code))
        {
            throw new ServiceException("调用萤石接口失败：" + firstNotBlank(result.getString("msg"), "未知错误"));
        }
        return result;
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

    private String encode(String value)
    {
        return URLEncoder.encode(StringUtils.nvl(value, StringUtils.EMPTY), StandardCharsets.UTF_8);
    }

    private JSONArray extractDeviceArray(JSONObject result)
    {
        JSONArray devices = result.getJSONArray("data");
        if (devices != null)
        {
            return devices;
        }

        JSONObject data = result.getJSONObject("data");
        if (data == null)
        {
            return null;
        }

        devices = data.getJSONArray("list");
        if (devices != null)
        {
            return devices;
        }
        return data.getJSONArray("devices");
    }

    private Integer resolveChannelNo(JSONObject item)
    {
        Integer channelNo = item.getInteger("channelNo");
        if (channelNo != null && channelNo > 0)
        {
            return channelNo;
        }
        return resolveDefaultChannelNo();
    }

    private Boolean resolveEncrypt(JSONObject item)
    {
        Boolean encrypt = item.getBoolean("isEncrypt");
        if (encrypt != null)
        {
            return encrypt;
        }

        Integer encryptCode = item.getInteger("isEncrypt");
        if (encryptCode != null)
        {
            return encryptCode.intValue() == 1;
        }
        return Boolean.FALSE;
    }

    private Integer resolveDefaultChannelNo()
    {
        Integer defaultChannelNo = ezvizProperties.getDefaultChannelNo();
        if (defaultChannelNo == null || defaultChannelNo < 1)
        {
            return 1;
        }
        return defaultChannelNo;
    }

    private long resolveExpireAt(JSONObject data)
    {
        Long expireTime = data.getLong("expireTime");
        if (expireTime == null)
        {
            return System.currentTimeMillis() + DEFAULT_TOKEN_EXPIRE_MS;
        }
        if (expireTime.longValue() > 1000000000000L)
        {
            return expireTime.longValue();
        }
        if (expireTime.longValue() > 1000000000L)
        {
            return expireTime.longValue() * 1000L;
        }
        return System.currentTimeMillis() + expireTime.longValue() * 1000L;
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
}
