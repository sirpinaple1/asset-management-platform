package com.sk.asset.service.stats;

import com.sk.asset.dto.stats.StatsOverviewResp;

/**
 * 工作台统计聚合（B3）。简单 count 聚合，无缓存（当前量级无需）。
 */
public interface StatsService {

    /** 总览统计（口径见 StatsOverviewResp 字段注释） */
    StatsOverviewResp overview(Long userId);
}
