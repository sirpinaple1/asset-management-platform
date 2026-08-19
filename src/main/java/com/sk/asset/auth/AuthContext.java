package com.sk.asset.auth;

import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.auth.dto.SystemAuthDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 一次成功认证的完整上下文：用户基础信息（getAuth）+ asset 系统权限（userSystemAuth）。
 * 由 TokenAuthFilter 写入 UserContext，业务层通过 UserContext 读取。
 */
@Getter
@AllArgsConstructor
public class AuthContext {

    private final AuthUserDto user;
    private final SystemAuthDto systemAuth;

    public Integer getUserId() {
        return user.getUserId();
    }

    public String getUsername() {
        return user.getUsername();
    }

    /** asset 系统内角色名列表（用作 Spring Security authority） */
    public List<String> assetRoleNames() {
        if (systemAuth == null || systemAuth.getRoles() == null) {
            return Collections.emptyList();
        }
        return systemAuth.getRoles().stream()
                .map(r -> r.getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** asset 系统按钮权限编码列表（前端按钮级显隐用） */
    public List<String> buttonPermissionCodes() {
        if (systemAuth == null || systemAuth.getButtons() == null) {
            return Collections.emptyList();
        }
        return systemAuth.getButtons().stream()
                .map(b -> b.getCode())
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .collect(Collectors.toList());
    }
}
