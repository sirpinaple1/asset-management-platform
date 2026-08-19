package com.sk.asset.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * comm_public_basic RoleVo 映射（/login/userSystemAuth 响应内 roles 元素）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemRoleDto {

    private Integer id;

    /** 角色名称 */
    private String name;
}
