package com.sk.asset.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * comm_public_basic POST /login/userSystemAuth（header systemCode）响应 data（UserSystemAuthVo）映射：
 * 用户在 asset 系统内的角色、菜单、按钮权限。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemAuthDto {

    private String systemCode;

    /** 用户在 asset 系统的角色列表 */
    private List<SystemRoleDto> roles;

    /** 菜单权限（type=0），树形 */
    private List<SystemMenuDto> menus;

    /** 按钮权限（type=1） */
    private List<SystemMenuDto> buttons;
}
