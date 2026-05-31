package com.ruoyi.system.domain.vo;

/**
 * 出门体态 vs open session enter_body_embedding 匹配结果。
 */
public class BodySessionMatchVo
{
    private Long sessionId;

    private Long personId;

    private String trackKey;

    private Float distance;

    private Float score;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getPersonId()
    {
        return personId;
    }

    public void setPersonId(Long personId)
    {
        this.personId = personId;
    }

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public Float getDistance()
    {
        return distance;
    }

    public void setDistance(Float distance)
    {
        this.distance = distance;
    }

    public Float getScore()
    {
        return score;
    }

    public void setScore(Float score)
    {
        this.score = score;
    }
}
