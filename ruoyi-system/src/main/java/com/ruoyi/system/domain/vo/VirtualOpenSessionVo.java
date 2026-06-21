package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频分析预览用的虚拟 open session（由同批进门事件构建，不写库）。
 */
public class VirtualOpenSessionVo
{
    private String trackKey;

    private Long personId;

    private String displayName;

    private String personKind;

    private List<Double> enterBodyEmbedding = new ArrayList<>();

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getPersonKind()
    {
        return personKind;
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public List<Double> getEnterBodyEmbedding()
    {
        return enterBodyEmbedding;
    }

    public void setEnterBodyEmbedding(List<Double> enterBodyEmbedding)
    {
        this.enterBodyEmbedding = enterBodyEmbedding;
    }
}
