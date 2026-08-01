package com.ruoyi.system.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 看板陌生人研判行
 */
public class DataBoardStrangerItemVo
{
    private String trackKey;

    private String displayName;

    private String faceImageUrl;

    private Long mergedPersonId;

    private String locationName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arrivalAt;

    private String personType;

    private String employeeNo;

    private String gender;

    private String phone;

    private String note;

    private String snapshotUrl;

    /** 监控画面绿框坐标 JSON */
    private String snapshotBbox;

    /** 关联视频片段 URL */
    private String clipVideoUrl;

    /** 出现次数（人脸去重后聚合） */
    private Integer sessionCount;

    /** 关联的所有 track_key（人脸去重后聚合） */
    private List<String> relatedTrackKeys = new ArrayList<>();

    /** 人脸向量（仅用于 Java 层分组，不返回前端） */
    @JsonIgnore
    private float[] faceEmbedding;

    public String getTrackKey()
    {
        return trackKey;
    }

    public void setTrackKey(String trackKey)
    {
        this.trackKey = trackKey;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getFaceImageUrl()
    {
        return faceImageUrl;
    }

    public void setFaceImageUrl(String faceImageUrl)
    {
        this.faceImageUrl = faceImageUrl;
    }

    public Long getMergedPersonId()
    {
        return mergedPersonId;
    }

    public void setMergedPersonId(Long mergedPersonId)
    {
        this.mergedPersonId = mergedPersonId;
    }

    public String getLocationName()
    {
        return locationName;
    }

    public void setLocationName(String locationName)
    {
        this.locationName = locationName;
    }

    public Date getArrivalAt()
    {
        return arrivalAt;
    }

    public void setArrivalAt(Date arrivalAt)
    {
        this.arrivalAt = arrivalAt;
    }

    public String getPersonType()
    {
        return personType;
    }

    public void setPersonType(String personType)
    {
        this.personType = personType;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    public String getGender()
    {
        return gender;
    }

    public void setGender(String gender)
    {
        this.gender = gender;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getSnapshotUrl()
    {
        return snapshotUrl;
    }

    public void setSnapshotUrl(String snapshotUrl)
    {
        this.snapshotUrl = snapshotUrl;
    }

    public String getSnapshotBbox()
    {
        return snapshotBbox;
    }

    public void setSnapshotBbox(String snapshotBbox)
    {
        this.snapshotBbox = snapshotBbox;
    }

    public String getClipVideoUrl()
    {
        return clipVideoUrl;
    }

    public void setClipVideoUrl(String clipVideoUrl)
    {
        this.clipVideoUrl = clipVideoUrl;
    }

    public Integer getSessionCount()
    {
        return sessionCount;
    }

    public void setSessionCount(Integer sessionCount)
    {
        this.sessionCount = sessionCount;
    }

    public List<String> getRelatedTrackKeys()
    {
        return relatedTrackKeys;
    }

    public void setRelatedTrackKeys(List<String> relatedTrackKeys)
    {
        this.relatedTrackKeys = relatedTrackKeys;
    }

    public float[] getFaceEmbedding()
    {
        return faceEmbedding;
    }

    public void setFaceEmbedding(float[] faceEmbedding)
    {
        this.faceEmbedding = faceEmbedding;
    }
}
