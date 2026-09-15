package com.sk.asset.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * comm_public_basic MenuVo 映射（/login/userSystemAuth 响应内 menus/buttons 元素）。
 * 保留前端渲染菜单树所需字段（M-FE01 直接复用），其余忽略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemMenuDto {

    private Integer id;
    private String name;
    /** 权限编码（按钮级权限判断用） */
    private String code;
    /** 前端路由路径 */
    private String path;
    private String icon;
    /** 权限类型：0=菜单 1=按钮 */
    private Integer type;
    private Integer sortOrder;
    /** 子菜单（树形结构） */
    private List<SystemMenuDto> children;
}
