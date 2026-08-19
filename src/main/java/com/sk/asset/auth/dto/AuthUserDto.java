package com.sk.asset.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * comm_public_basic GET /login/getAuth 响应 data（LoginUserVo）映射。
 * 只映射 asset 侧需要的字段，其余字段忽略（契约演进容错）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUserDto {

    private Integer userId;
    private String username;
    private String name;
    private String sex;
    private String dept;
    private String job;
    private String email;
    private String phone;
    private String avatar;

    /** 用户全局角色名列表（LoginUserVo.roles 为 List&lt;String&gt;） */
    private List<String> roles;

    /** 用户全局权限编码列表 */
    private List<String> permissions;
}
