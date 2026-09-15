package com.sk.asset.dto;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.dto.SystemMenuDto;

import java.util.List;

/**
 * GET /api/v1/me 响应：当前用户信息 + asset 系统角色/按钮权限/菜单树。
 */
public record MeResp(
        Integer userId,
        String username,
        String name,
        String dept,
        String job,
        List<String> roles,
        List<String> permissions,
        List<SystemMenuDto> menus) {

    public static MeResp from(AuthContext context) {
        return new MeResp(
                context.getUser().getUserId(),
                context.getUsername(),
                context.getUser().getName(),
                context.getUser().getDept(),
                context.getUser().getJob(),
                context.assetRoleNames(),
                context.buttonPermissionCodes(),
                context.getSystemAuth().getMenus());
    }
}
