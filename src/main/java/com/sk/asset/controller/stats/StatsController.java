package com.sk.asset.controller.stats;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.stats.StatsOverviewResp;
import com.sk.asset.service.stats.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台统计聚合（B3，F4 图表卡数据源）。
 */
@Tag(name = "统计聚合")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "总览统计（资产状态分布/我的待办/我的持有/进行中盘点）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/overview")
    public Result<StatsOverviewResp> overview() {
        AuthContext user = UserContext.require();
        return Result.ok(statsService.overview(Long.valueOf(user.getUserId())));
    }
}
