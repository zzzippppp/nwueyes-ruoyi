package com.ruoyi.system.domain.vo;

/**
 * 轨迹抓拍 + 人脸/体态向量。
 */
public class CaptureTrackEmbedVo
{
    private Integer trackId;

    private String trackKey;

    private String faceImageUrl;

    private String bodyImageUrl;

    private Double faceScore;

    private Double bodyScore;

    private Integer sampledFrames;

    private EmbeddingVectorVo faceEmbedding;

    private EmbeddingVectorVo bodyEmbedding;

    public Integer getTrackId()
    {
        return trackId;
    }

    public void setTrackId(Integer trackId)
    {
        this.trackId = trackId;
    }

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public String getFaceImageUrl()
    {
        return faceImageUrl;
    }

    public void setFaceImageUrl(String faceImageUrl)
    {
        this.faceImageUrl = faceImageUrl;
    }

    public String getBodyImageUrl()
    {
        return bodyImageUrl;
    }

    public void setBodyImageUrl(String bodyImageUrl)
    {
        this.bodyImageUrl = bodyImageUrl;
    }

    public Double getFaceScore()
    {
        return faceScore;
    }

    public void setFaceScore(Double faceScore)
    {
        this.faceScore = faceScore;
    }

    public Double getBodyScore()
    {
        return bodyScore;
    }

    public void setBodyScore(Double bodyScore)
    {
        this.bodyScore = bodyScore;
    }

    public Integer getSampledFrames()
    {
        return sampledFrames;
    }

    public void setSampledFrames(Integer sampledFrames)
    {
        this.sampledFrames = sampledFrames;
    }

    public EmbeddingVectorVo getFaceEmbedding()
    {
        return faceEmbedding;
    }

    public void setFaceEmbedding(EmbeddingVectorVo faceEmbedding)
    {
        this.faceEmbedding = faceEmbedding;
    }

    public EmbeddingVectorVo getBodyEmbedding()
    {
        return bodyEmbedding;
    }

    public void setBodyEmbedding(EmbeddingVectorVo bodyEmbedding)
    {
        this.bodyEmbedding = bodyEmbedding;
    }
}
