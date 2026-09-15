package com.sk.asset.dto.user;

/**
 * 用户搜索响应（comm_public_basic sys_user 只读投影，B1 选人器数据源）。
 */
public record UserResp(Long id, String username, String name, String dept) {
}
