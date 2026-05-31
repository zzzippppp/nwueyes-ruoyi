package com.ruoyi.system.service;

import com.ruoyi.system.domain.vo.AnalyzeEmbedResultVo;

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
}
