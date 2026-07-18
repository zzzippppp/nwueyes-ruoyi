package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.DeviceInfo;
import com.ruoyi.system.domain.vo.CameraConfigVo;

public interface ICameraService
{
    /**
     * 监控大屏可选摄像头（已配置萤石序列号）。
     */
    List<CameraConfigVo> listMonitorCameras();

    /**
     * 按萤石序列号+通道查找摄像头，不存在则创建。
     */
    Long resolveOrCreateCamera(String serialNo, Integer channelNo, String defaultName);

    CameraConfigVo getCameraConfig(Long cameraId);

    List<DeviceInfo> selectDeviceInfoList(DeviceInfo query);

    DeviceInfo selectDeviceInfoById(Long id);

    boolean checkDeviceNameUnique(DeviceInfo info);

    boolean checkSerialNoUnique(DeviceInfo info);

    boolean checkDeviceCodeUnique(DeviceInfo info);

    int insertDeviceInfo(DeviceInfo info);

    int updateDeviceInfo(DeviceInfo info);

    int deleteDeviceInfoByIds(Long[] ids);
}
