package com.sk.asset.controller;

import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.PageResp;
import com.sk.asset.common.Result;
import com.sk.asset.dto.user.UserResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户搜索（B1 定向待办选人器数据源）。
 * 只读代理 comm_public_basic sys_user（ADR-0004：asset 库不存用户主数据）；
 * 同时解决前端调拨负责人手输数字 ID 的体验问题。
 */
@Tag(name = "用户目录")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserDirectory userDirectory;

    @Operation(summary = "用户搜索（keyword 匹配工号/姓名，分页；仅存活用户）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<PageResp<UserResp>> search(
            @Parameter(description = "关键词（工号/姓名，模糊匹配，空=全部）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 起")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数（1-100）")
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(userDirectory.search(keyword, page, size));
    }
}
