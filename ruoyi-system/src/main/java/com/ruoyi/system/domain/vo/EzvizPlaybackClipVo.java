package com.ruoyi.system.domain.vo;

/**
 * 萤石回放片段解析结果。
 */
public class EzvizPlaybackClipVo
{
    private String playbackUrl;
    private String downloadedLocalUrl;
    private String providerStatus;
    private String providerTaskId;
    private String providerSourceUrl;
    private String errorMessage;

    public String getPlaybackUrl()
    {
        return playbackUrl;
    }

    public void setPlaybackUrl(String playbackUrl)
    {
        this.playbackUrl = playbackUrl;
    }

    public String getDownloadedLocalUrl()
    {
        return downloadedLocalUrl;
    }

    public void setDownloadedLocalUrl(String downloadedLocalUrl)
    {
        this.downloadedLocalUrl = downloadedLocalUrl;
    }

    public String getProviderStatus()
    {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus)
    {
        this.providerStatus = providerStatus;
    }

    public String getProviderTaskId()
    {
        return providerTaskId;
    }

    public void setProviderTaskId(String providerTaskId)
    {
        this.providerTaskId = providerTaskId;
    }

    public String getProviderSourceUrl()
    {
        return providerSourceUrl;
    }

    public void setProviderSourceUrl(String providerSourceUrl)
    {
        this.providerSourceUrl = providerSourceUrl;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
