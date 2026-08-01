package com.ruoyi.system.domain.bo;

/**
 * 陌生人合并到已有人员请求
 */
public class DataBoardStrangerMergeBo
{
    private Long targetPersonId;

    public Long getTargetPersonId()
    {
        return targetPersonId;
    }

    public void setTargetPersonId(Long targetPersonId)
    {
        this.targetPersonId = targetPersonId;
    }
}
