package com.ruoyi.system.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 单张图的 512 维 embedding 结果。
 */
public class EmbeddingVectorVo
{
    private Boolean ok;

    private Integer dim;

    private List<Double> embedding;

    private String model;

    private Map<String, Object> quality;

    private String error;

    private String imagePath;

    public Boolean getOk()
    {
        return ok;
    }

    public void setOk(Boolean ok)
    {
        this.ok = ok;
    }

    public Integer getDim()
    {
        return dim;
    }

    public void setDim(Integer dim)
    {
        this.dim = dim;
    }

    public List<Double> getEmbedding()
    {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding)
    {
        this.embedding = embedding;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Map<String, Object> getQuality()
    {
        return quality;
    }

    public void setQuality(Map<String, Object> quality)
    {
        this.quality = quality;
    }

    public String getError()
    {
        return error;
    }

    public void setError(String error)
    {
        this.error = error;
    }

    public String getImagePath()
    {
        return imagePath;
    }

    public void setImagePath(String imagePath)
    {
        this.imagePath = imagePath;
    }
}
