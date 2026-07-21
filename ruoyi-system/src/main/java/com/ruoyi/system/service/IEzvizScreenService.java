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

    /**
     * 校验序列号是否对应已绑定到当前萤石开放平台账号的真实设备。
     * 调用萤石 /api/lapp/device/info；校验失败抛出 ServiceException。
     *
     * @param deviceSerial 设备序列号
     * @param verifyCode 设备验证码（加密设备可选传入）
     */
    void assertDeviceBound(String deviceSerial, String verifyCode);
}
