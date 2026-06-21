package com.ruoyi.system.service;

import java.time.LocalDate;
import com.ruoyi.system.domain.bo.DataBoardSessionFilterBo;
import com.ruoyi.system.domain.vo.DataBoardSummaryVo;

/**
 * 数据看板
 */
public interface IDataBoardService
{
    /**
     * 看板汇总（单次请求，便于前端轮询）
     */
    DataBoardSummaryVo getSummary(LocalDate statDate, LocalDate beginDate, LocalDate endDate, Long cameraId,
            int recentLimit, DataBoardSessionFilterBo sessionFilter);
}
