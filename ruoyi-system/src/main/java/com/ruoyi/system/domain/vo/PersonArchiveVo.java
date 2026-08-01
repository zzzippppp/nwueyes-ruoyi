package com.ruoyi.system.domain.vo;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 人员档案（人脸库）
 */
public class PersonArchiveVo
{
    private Long personId;

    private String displayName;

    /** student / staff / stranger */
    private String personType;

    private String employeeNo;

    /** 0 正常 1 停用 */
    private String status;

    private String note;

    private String faceImageUrl;

    private Integer faceCount;

    private String phone;

    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** 详情：人脸图列表 */
    private List<String> faceImageUrls;

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getFaceImageUrl()
    {
        return faceImageUrl;
    }

    public void setFaceImageUrl(String faceImageUrl)
    {
        this.faceImageUrl = faceImageUrl;
    }

    public Integer getFaceCount()
    {
        return faceCount;
    }

    public void setFaceCount(Integer faceCount)
    {
        this.faceCount = faceCount;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getGender()
    {
        return gender;
    }

    public void setGender(String gender)
    {
        this.gender = gender;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    public List<String> getFaceImageUrls()
    {
        return faceImageUrls;
    }

    public void setFaceImageUrls(List<String> faceImageUrls)
    {
        this.faceImageUrls = faceImageUrls;
    }
}
