package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.CameraConfigVo;

public interface CameraMapper
{
    List<CameraConfigVo> selectMonitorList();

    CameraConfigVo selectById(@Param("cameraId") Long cameraId);

    Long selectIdBySerial(@Param("serialNo") String serialNo, @Param("channelNo") int channelNo);

    int insertCamera(@Param("deviceCode") String deviceCode, @Param("serialNo") String serialNo,
            @Param("channelNo") int channelNo, @Param("deviceName") String deviceName,
            @Param("typeId") Long typeId);

    int updateDoorConfig(@Param("cameraId") Long cameraId, @Param("lineY") Integer lineY,
            @Param("roi") String roi);

    int updateCamera(@Param("cameraId") Long cameraId, @Param("deviceName") String deviceName,
            @Param("onlineStatus") String onlineStatus);
}
