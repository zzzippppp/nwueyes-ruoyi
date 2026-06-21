package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 过线事件匹配预览（不写库、不建 session）。
 */
public class PresenceTrackMatchPreviewVo
{
    private String displayName;

    private Long personId;

    private String personKind;

    private boolean matched;

    private Float faceMatchScore;

    private Float bodyMatchScore;

    private List<Double> bodyEmbedding = new ArrayList<>();

    /** 出门匹配到的虚拟 session trackKey */
    private String matchedSessionTrackKey;

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public String getPersonKind()
    {
        return personKind;
    }

    public void setPersonKind(String personKind)
    {
        this.personKind = personKind;
    }

    public boolean isMatched()
    {
        return matched;
    }

    public void setMatched(boolean matched)
    {
        this.matched = matched;
    }

    public Float getFaceMatchScore()
    {
        return faceMatchScore;
    }

    public void setFaceMatchScore(Float faceMatchScore)
    {
        this.faceMatchScore = faceMatchScore;
    }

    public Float getBodyMatchScore()
    {
        return bodyMatchScore;
    }

    public void setBodyMatchScore(Float bodyMatchScore)
    {
        this.bodyMatchScore = bodyMatchScore;
    }

    public List<Double> getBodyEmbedding()
    {
        return bodyEmbedding;
    }

    public void setBodyEmbedding(List<Double> bodyEmbedding)
    {
        this.bodyEmbedding = bodyEmbedding;
    }

    public String getMatchedSessionTrackKey()
    {
        return matchedSessionTrackKey;
    }

    public void setMatchedSessionTrackKey(String matchedSessionTrackKey)
    {
        this.matchedSessionTrackKey = matchedSessionTrackKey;
    }
}
