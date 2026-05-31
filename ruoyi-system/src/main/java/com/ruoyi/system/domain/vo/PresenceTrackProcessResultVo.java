package com.ruoyi.system.domain.vo;

/**
 * 单条过线事件（进门/出门）处理结果。
 */
public class PresenceTrackProcessResultVo
{
    private Long sessionId;

    private String sessionStatus;

    private Long personId;

    private String displayName;

    private String personKind;

    private Float faceMatchScore;

    private Float bodyMatchScore;

    private String qualityFlag;

    private boolean strangerRegistered;

    /** 该 enter 因「此人已在场」等原因被抑制，不写行为日志、不新建 session。 */
    private boolean skippedDuplicateEnter;

    /** 该 exit 无可用 open session（从未进门/孤儿出门），仅写行为日志，不关 session。 */
    private boolean skippedOrphanExit;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getSessionStatus()
    {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus)
    {
        this.sessionStatus = sessionStatus;
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

    public String getQualityFlag()
    {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag)
    {
        this.qualityFlag = qualityFlag;
    }

    public boolean isStrangerRegistered()
    {
        return strangerRegistered;
    }

    public void setStrangerRegistered(boolean strangerRegistered)
    {
        this.strangerRegistered = strangerRegistered;
    }

    public boolean isSkippedDuplicateEnter()
    {
        return skippedDuplicateEnter;
    }

    public void setSkippedDuplicateEnter(boolean skippedDuplicateEnter)
    {
        this.skippedDuplicateEnter = skippedDuplicateEnter;
    }

    public boolean isSkippedOrphanExit()
    {
        return skippedOrphanExit;
    }

    public void setSkippedOrphanExit(boolean skippedOrphanExit)
    {
        this.skippedOrphanExit = skippedOrphanExit;
    }
}
