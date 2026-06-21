package com.ruoyi.system.service;

import com.ruoyi.system.domain.vo.EzvizScreenConfigVo;

/**
 * 萤石监控大屏服务
 */
public interface IEzvizScreenService
{
    /**
     * 获取监控大屏所需的萤石配置
     */
    EzvizScreenConfigVo getScreenConfig();

    /**
     * 获取算法识别用直播流地址（RTSP 或 HLS）。
     *
     * @param streamMode lan_rtsp | cloud_hls
     * @param localIpOverride 可选，camera 表已维护的局域网 IP，优先于萤石 API 解析
     */
    String resolveAnalyzeStreamUrl(String deviceSerial, Integer channelNo, String streamMode, String validCode,
            String localIpOverride);

    /**
     * Return a cached OpenAPI access token for internal Ezviz service calls.
     */
    String getOpenApiAccessToken();
}
