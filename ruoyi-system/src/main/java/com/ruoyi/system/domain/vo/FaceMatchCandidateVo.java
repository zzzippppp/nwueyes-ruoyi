package com.ruoyi.system.domain.vo;

/**
 * 人脸库 Top-1 匹配候选。
 */
public class FaceMatchCandidateVo
{
    private Long faceProfileId;

    private Long personId;

    private String displayName;

    private String personKind;

    private String imageUrl;

    private Float distance;

    private Float score;

    public Long getFaceProfileId()
    {
        return faceProfileId;
    }

    public void setFaceProfileId(Long faceProfileId)
    {
        this.faceProfileId = faceProfileId;
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

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
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
