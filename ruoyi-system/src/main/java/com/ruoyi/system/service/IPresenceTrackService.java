package com.ruoyi.system.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.vo.PresenceTrackMatchPreviewVo;
import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;
import com.ruoyi.system.domain.vo.VirtualOpenSessionVo;

/**
 * 过线事件统一状态机：向量抽取、人脸/体态匹配、session 开闭。
 */
public interface IPresenceTrackService
{
    PresenceTrackProcessResultVo processEnter(Long cameraId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag);

    PresenceTrackProcessResultVo processExit(Long cameraId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag);

    /** 进门预览：人脸库匹配，不写库。 */
    PresenceTrackMatchPreviewVo previewEnterMatch(String trackKey, String faceImageUrl, String bodyImageUrl);

    /** 出门预览：体态 vs 同批虚拟 open session，不写库。 */
    PresenceTrackMatchPreviewVo previewExitMatch(String exitTrackKey, String bodyImageUrl,
            List<VirtualOpenSessionVo> openSessions);
}
