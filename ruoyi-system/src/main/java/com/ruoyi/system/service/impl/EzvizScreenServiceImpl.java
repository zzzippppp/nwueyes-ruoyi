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

    private static final long TOKEN_REFRESH_BUFFER_MS = 60 * 1000L;

    private static final long DEFAULT_TOKEN_EXPIRE_MS = 6L * 24 * 60 * 60 * 1000;

    @Autowired
    private EzvizProperties ezvizProperties;

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
