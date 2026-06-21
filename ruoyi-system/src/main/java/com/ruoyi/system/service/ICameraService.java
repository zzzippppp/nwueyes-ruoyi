package com.ruoyi.system.service;

import com.ruoyi.system.domain.vo.CameraConfigVo;

public interface ICameraService
{
    /**
     * 按萤石序列号+通道查找摄像头，不存在则创建。
     */
    Long resolveOrCreateCamera(String serialNo, Integer channelNo, String defaultName);

    CameraConfigVo getCameraConfig(Long cameraId);
}
