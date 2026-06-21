package com.ruoyi.system.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 设备信息实体
 */
@Data
@TableName("sys_device_info")
public class DeviceInfo {

    /** 设备主键ID */
    @TableId(type = IdType.AUTO)
    private Long deviceId;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过100个字符")
    private String deviceName;

    /** 设备SN（全局唯一） */
    @NotBlank(message = "设备SN不能为空")
    @Size(max = 64, message = "设备SN长度不能超过64个字符")
    private String deviceSn;

    /** 设备类型ID（关联DeviceType） */
    private Long typeId;

    /** 设备状态 0正常 1停用 */
    private String status;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;

    /**
     * 非数据库字段：设备类型名称，联表查询回显使用
     */
    @TableField(exist = false)
    private String typeName;
}