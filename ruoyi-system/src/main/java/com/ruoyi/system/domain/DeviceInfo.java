package com.ruoyi.system.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 设备信息（camera 表）
 */
public class DeviceInfo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "设备编号", cellType = ColumnType.NUMERIC)
    private Long id;

    @Excel(name = "设备编码")
    private String deviceCode;

    @Excel(name = "设备名称")
    private String deviceName;

    private Long typeId;

    @Excel(name = "设备类型")
    private String typeName;

    @Excel(name = "安装位置")
    private String installLocation;

    @Excel(name = "IP地址")
    private String ipAddr;

    @Excel(name = "设备序列号")
    private String serialNo;

    @Excel(name = "通道号", cellType = ColumnType.NUMERIC)
    private Integer channelNo;

    private String verifyCode;

    /** online / offline */
    @Excel(name = "在线状态")
    private String onlineStatus;

    private Integer lineY;

    private String roi;

    private Integer refWidth;

    private Integer refHeight;

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

    @Size(max = 32, message = "设备编码长度不能超过32个字符")
    public String getDeviceCode()
    {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode)
    {
        this.deviceCode = deviceCode;
    }

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 64, message = "设备名称长度不能超过64个字符")
    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public Long getTypeId()
    {
        return typeId;
    }

    public void setTypeId(Long typeId)
    {
        this.typeId = typeId;
    }

    public String getTypeName()
    {
        return typeName;
    }

    public void setTypeName(String typeName)
    {
        this.typeName = typeName;
    }

    @Size(max = 128, message = "安装位置长度不能超过128个字符")
    public String getInstallLocation()
    {
        return installLocation;
    }

    public void setInstallLocation(String installLocation)
    {
        this.installLocation = installLocation;
    }

    @Size(max = 64, message = "IP地址长度不能超过64个字符")
    public String getIpAddr()
    {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr)
    {
        this.ipAddr = ipAddr;
    }

    @NotBlank(message = "设备序列号不能为空")
    @Size(max = 64, message = "设备序列号长度不能超过64个字符")
    public String getSerialNo()
    {
        return serialNo;
    }

    public void setSerialNo(String serialNo)
    {
        this.serialNo = serialNo;
    }

    /** 萤石兼容别名 */
    public String getDeviceSerial()
    {
        return serialNo;
    }

    public void setDeviceSerial(String deviceSerial)
    {
        this.serialNo = deviceSerial;
    }

    @NotNull(message = "通道号不能为空")
    public Integer getChannelNo()
    {
        return channelNo;
    }

    public void setChannelNo(Integer channelNo)
    {
        this.channelNo = channelNo;
    }

    @Size(max = 32, message = "验证码长度不能超过32个字符")
    public String getVerifyCode()
    {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode)
    {
        this.verifyCode = verifyCode;
    }

    public String getOnlineStatus()
    {
        return onlineStatus;
    }

    public void setOnlineStatus(String onlineStatus)
    {
        this.onlineStatus = onlineStatus;
    }

    public Integer getLineY()
    {
        return lineY;
    }

    public void setLineY(Integer lineY)
    {
        this.lineY = lineY;
    }

    public String getRoi()
    {
        return roi;
    }

    public void setRoi(String roi)
    {
        this.roi = roi;
    }

    public Integer getRefWidth()
    {
        return refWidth;
    }

    public void setRefWidth(Integer refWidth)
    {
        this.refWidth = refWidth;
    }

    public Integer getRefHeight()
    {
        return refHeight;
    }

    public void setRefHeight(Integer refHeight)
    {
        this.refHeight = refHeight;
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
