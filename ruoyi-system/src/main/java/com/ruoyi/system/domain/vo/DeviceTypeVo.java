package com.ruoyi.system.domain.vo;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;

public class DeviceTypeVo
{
    @Excel(name = "类型编号", cellType = Excel.ColumnType.NUMERIC)
    private Long id;

    @Excel(name = "类型编码")
    private String typeCode;

    @Excel(name = "类型名称")
    private String typeName;

    @Excel(name = "说明")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @NotBlank(message = "类型编码不能为空")
    @Size(max = 32, message = "类型编码长度不能超过32个字符")
    public String getTypeCode()
    {
        return typeCode;
    }

    public void setTypeCode(String typeCode)
    {
        this.typeCode = typeCode;
    }

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 64, message = "类型名称长度不能超过64个字符")
    public String getTypeName()
    {
        return typeName;
    }

    public void setTypeName(String typeName)
    {
        this.typeName = typeName;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
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
}
