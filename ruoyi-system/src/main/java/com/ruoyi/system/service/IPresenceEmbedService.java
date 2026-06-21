package com.ruoyi.system.service;

import com.ruoyi.system.domain.vo.AnalyzeEmbedResultVo;
import com.ruoyi.system.domain.vo.AnalyzeEventMatchResultVo;

public interface IPresenceEmbedService
{
    /**
     * 对分析任务 captureTracks 中的人脸/体态图抽取 512 维向量。
     */
    AnalyzeEmbedResultVo embedAnalyzeCaptures(String taskId);

    /**
     * 对单张图片 URL 抽取 512 维向量（kind=face|body）。
     */
    com.ruoyi.system.domain.vo.EmbeddingVectorVo embedImage(String kind, String imageUrl);

    /**
     * 视频分析过线事件与人脸库/体态库（及同批进门 session）匹配预览。
     */
    AnalyzeEventMatchResultVo matchAnalyzeEvents(String taskId);
}
