package com.ruoyi.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class AiAnalysisResultVo
{
    private Long id;
    private String targetType;
    private String targetId;
    private String modelKey;
    private String modelName;
    private String status;
    private String summary;
    private String appearance;
    private String behavior;
    private String riskLevel;
    private String rawJson;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }
    public String getBehavior() { return behavior; }
    public void setBehavior(String behavior) { this.behavior = behavior; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
