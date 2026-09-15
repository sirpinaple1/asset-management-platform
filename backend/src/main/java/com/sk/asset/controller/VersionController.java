package com.sk.asset.controller;

import com.sk.asset.common.AppVersion;
import com.sk.asset.common.Result;
import com.sk.asset.dto.VersionResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版本信息接口：前端左下角帮助面板读取后端版本 + changelog + 版权。
 * 版本唯一来源 AppVersion（发布纪律见 AGENTS.md「版本号纪律」）。
 */
@Tag(name = "版本信息")
@RestController
@RequestMapping("/api/v1")
public class VersionController {

    @Operation(summary = "后端版本信息", description = "版本号 + 本版本新增功能 + 版权（帮助面板展示）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/version")
    public Result<VersionResp> version() {
        return Result.ok(VersionResp.fromAppVersion());
    }
}
