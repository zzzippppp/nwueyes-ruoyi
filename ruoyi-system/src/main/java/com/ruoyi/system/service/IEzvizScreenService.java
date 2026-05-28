package com.ruoyi.system.service;

import com.ruoyi.system.domain.vo.EzvizScreenConfigVo;

/**
 * 萤石监控大屏服务
 * 
 * @author ruoyi
 */
public interface IEzvizScreenService
{
    /**
     * 获取监控大屏所需的萤石配置
     * 
     * @return 配置结果
     */
    public EzvizScreenConfigVo getScreenConfig();
}
