package com.ruoyi.system.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.LocationConfigVo;

public interface LocationMapper
{
    LocationConfigVo selectById(@Param("locationId") Long locationId);

    Long selectIdByDevice(@Param("deviceSerial") String deviceSerial, @Param("channelNo") int channelNo);

    int insertLocation(@Param("deviceSerial") String deviceSerial, @Param("channelNo") int channelNo,
            @Param("name") String name);

    int updateDoorConfig(@Param("locationId") Long locationId, @Param("lineY") Integer lineY,
            @Param("roi") String roi);
}
