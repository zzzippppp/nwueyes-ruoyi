package com.ruoyi.system.domain.vo;

/**
 * 行为日志导入结果
 */
public class BehaviorLogImportResultVo
{
    private Integer insertedCount;

    private Integer skippedCount;

    private String message;

    public Integer getInsertedCount()
    {
        return insertedCount;
    }

    public void setInsertedCount(Integer insertedCount)
    {
        this.insertedCount = insertedCount;
    }

    public Integer getSkippedCount()
    {
        return skippedCount;
    }

    public void setSkippedCount(Integer skippedCount)
    {
        this.skippedCount = skippedCount;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
