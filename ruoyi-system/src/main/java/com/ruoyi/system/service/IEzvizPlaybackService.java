package com.ruoyi.system.service;

import java.util.Date;
import com.ruoyi.system.domain.vo.EzvizPlaybackClipVo;

public interface IEzvizPlaybackService
{
    EzvizPlaybackClipVo resolvePlaybackClip(String deviceSerial, Integer channelNo, String validCode,
            Date startTime, Date endTime, boolean preferLocal);
}
