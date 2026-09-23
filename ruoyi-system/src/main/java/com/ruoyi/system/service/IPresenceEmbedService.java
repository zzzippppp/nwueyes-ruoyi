package com.ruoyi.system.service;

import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.system.domain.vo.AnalyzeEmbedResultVo;
import com.ruoyi.system.domain.vo.AnalyzeEventMatchResultVo;
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.domain.vo.FaceCompareResultVo;

public interface IPresenceEmbedService
{
    /**
     * 对分析任务 captureTracks 中的人脸/体态图抽取 512 维向量。
     */
    AnalyzeEmbedResultVo embedAnalyzeCaptures(String taskId);

    /**
     * 同上；includeBody=false 时跳过体态(body)向量抽取（门内已废除体态识别）。
     */
    AnalyzeEmbedResultVo embedAnalyzeCaptures(String taskId, boolean includeBody);

    /**
     * 对单张图片 URL 抽取 512 维向量（kind=face|body）。
     */
    EmbeddingVectorVo embedImage(String kind, String imageUrl);

    /**
     * 视频分析过线事件与人脸库/体态库（及同批进门 session）匹配预览。
     */
    AnalyzeEventMatchResultVo matchAnalyzeEvents(String taskId);

    /**
     * 两张图按项目同款人脸向量流程对比（gallery=人脸库，camera=摄像头抓拍）。
     */
    FaceCompareResultVo compareFaces(MultipartFile galleryFile, MultipartFile cameraFile) throws Exception;
}
