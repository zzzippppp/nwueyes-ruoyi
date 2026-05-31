package com.ruoyi.system.service;

import java.util.Date;
import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;

/**
 * 过线事件统一状态机：向量抽取、人脸/体态匹配、session 开闭。
 */
public interface IPresenceTrackService
{
    PresenceTrackProcessResultVo processEnter(Long locationId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag);

    PresenceTrackProcessResultVo processExit(Long locationId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag);
}
