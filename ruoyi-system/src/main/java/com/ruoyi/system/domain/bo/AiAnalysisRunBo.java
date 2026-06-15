package com.ruoyi.system.domain.bo;

import java.util.List;

public class AiAnalysisRunBo
{
    private String targetType;

    private String targetId;

    private List<String> modelKeys;

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public String getTargetId()
    {
        return targetId;
    }

    public void setTargetId(String targetId)
    {
        this.targetId = targetId;
    }

    public List<String> getModelKeys()
    {
        return modelKeys;
    }

    public void setModelKeys(List<String> modelKeys)
    {
        this.modelKeys = modelKeys;
    }
}
