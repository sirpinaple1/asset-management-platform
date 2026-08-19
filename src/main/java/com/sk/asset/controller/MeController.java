package com.sk.asset.controller;

import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.MeResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M01-B 鉴权全链路验证接口：返回当前登录用户信息 + asset 系统角色/权限/菜单。
 * 用户数据不落 asset 库，全部来自 comm_public_basic（ADR-0004，R8）。
 */
@Tag(name = "鉴权验证")
@RestController
@RequestMapping("/api/v1")
public class MeController {

    @Operation(summary = "当前用户信息", description = "验证 token 鉴权全链路：AuthPort 校验 + UserContext 传递")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/me")
    public Result<MeResp> me() {
        return Result.ok(MeResp.from(UserContext.require()));
    }
}
