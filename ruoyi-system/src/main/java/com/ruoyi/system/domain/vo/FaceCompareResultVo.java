package com.ruoyi.system.domain.vo;

import java.util.Map;

/**
 * 两张人脸对比结果（人脸库图 vs 摄像头抓拍图）。
 */
public class FaceCompareResultVo
{
    /** 余弦相似度 0~1，与进门匹配 score 同口径 */
    private Double score;

    /** 是否达到当前 faceMatchThreshold */
    private Boolean matched;

    private Double threshold;

    private String faceEmbedMode;

    private EmbeddingVectorVo galleryEmbedding;

    private EmbeddingVectorVo cameraEmbedding;

    private Map<String, Object> galleryQuality;

    private Map<String, Object> cameraQuality;

    public Double getScore()
    {
        return score;
    }

    public void setScore(Double score)
    {
        this.score = score;
    }

    public Boolean getMatched()
    {
        return matched;
    }

    public void setMatched(Boolean matched)
    {
        this.matched = matched;
    }

    public Double getThreshold()
    {
        return threshold;
    }

    public void setThreshold(Double threshold)
    {
        this.threshold = threshold;
    }

    public String getFaceEmbedMode()
    {
        return faceEmbedMode;
    }

    public void setFaceEmbedMode(String faceEmbedMode)
    {
        this.faceEmbedMode = faceEmbedMode;
    }

    public EmbeddingVectorVo getGalleryEmbedding()
    {
        return galleryEmbedding;
    }

    public void setGalleryEmbedding(EmbeddingVectorVo galleryEmbedding)
    {
        this.galleryEmbedding = galleryEmbedding;
    }

    public EmbeddingVectorVo getCameraEmbedding()
    {
        return cameraEmbedding;
    }

    public void setCameraEmbedding(EmbeddingVectorVo cameraEmbedding)
    {
        this.cameraEmbedding = cameraEmbedding;
    }

    public Map<String, Object> getGalleryQuality()
    {
        return galleryQuality;
    }

    public void setGalleryQuality(Map<String, Object> galleryQuality)
    {
        this.galleryQuality = galleryQuality;
    }

    public Map<String, Object> getCameraQuality()
    {
        return cameraQuality;
    }

    public void setCameraQuality(Map<String, Object> cameraQuality)
    {
        this.cameraQuality = cameraQuality;
    }
}
