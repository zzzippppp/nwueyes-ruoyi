package com.ruoyi.system.service;

import java.time.LocalDate;
import com.ruoyi.system.domain.vo.DataBoardSummaryVo;

/**
 * 数据看板
 */
public interface IDataBoardService
{
    /**
     * 看板汇总（单次请求，便于前端轮询）
     *
     * @param statDate 统计日期，为空则今天
     * @param locationId 地点 ID，可选
     * @param recentLimit 最近记录条数
     */
    DataBoardSummaryVo getSummary(LocalDate statDate, Long locationId, int recentLimit);
}
