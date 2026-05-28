package com.ruoyi.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 萤石开放平台配置
 * 
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ezviz")
public class EzvizProperties
{
    /**
     * 开放平台基础地址
     */
    private String baseUrl;

    /**
     * 应用 appKey
     */
    private String appKey;

    /**
     * 应用 appSecret
     */
    private String appSecret;

    /**
     * 默认通道号
     */
    private Integer defaultChannelNo = 1;

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getAppKey()
    {
        return appKey;
    }

    public void setAppKey(String appKey)
    {
        this.appKey = appKey;
    }

    public String getAppSecret()
    {
        return appSecret;
    }

    public void setAppSecret(String appSecret)
    {
        this.appSecret = appSecret;
    }

    public Integer getDefaultChannelNo()
    {
        return defaultChannelNo;
    }

    public void setDefaultChannelNo(Integer defaultChannelNo)
    {
        this.defaultChannelNo = defaultChannelNo;
    }
}
