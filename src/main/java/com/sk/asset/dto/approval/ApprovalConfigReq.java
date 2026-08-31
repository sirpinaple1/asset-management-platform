package com.sk.asset.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批链配置保存/更新请求（POST/PUT /api/v1/approval-configs，仅超管）。
 */
@Data
public class ApprovalConfigReq {

    /** 配置类型：DEPT_SUPERVISOR-部门主管 WAREHOUSE_KEEPER-领料仓管理员 */
    @NotBlank(message = "配置类型不能为空")
    private String configType;

    /** DEPT_SUPERVISOR=部门路径字符串；WAREHOUSE_KEEPER=位置 id（字符串） */
    @NotBlank(message = "配置键不能为空")
    @Size(max = 255, message = "配置键长度不能超过 255")
    private String configKey;

    /** 审批人 ID（需存在于 comm_public_basic sys_user） */
    @NotNull(message = "审批人不能为空")
    @Positive(message = "审批人 ID 需为正整数")
    private Long approverUserId;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
