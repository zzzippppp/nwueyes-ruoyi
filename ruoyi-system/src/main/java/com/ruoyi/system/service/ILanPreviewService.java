package com.ruoyi.system.service;

import com.ruoyi.system.domain.bo.PresenceLiveStartBo;
import com.ruoyi.system.domain.vo.LanPreviewVo;

public interface ILanPreviewService
{
    /**
     * 确保摄像头 RTSP 已注册到 go2rtc，并返回 go2rtc 暴露的本地 RTSP 地址。
     */
    String ensureLocalRtsp(PresenceLiveStartBo bo);

    LanPreviewVo startPreview(PresenceLiveStartBo bo);

    void stopPreview(Long cameraId);
}
