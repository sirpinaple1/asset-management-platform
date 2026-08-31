package com.sk.asset.dto.approval;

import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.enums.approval.ApprovalConfigType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批链配置响应（列表与详情共用）。
 */
@Data
public class ApprovalConfigResp {

    private Long id;

    /** DEPT_SUPERVISOR-部门主管 WAREHOUSE_KEEPER-领料仓管理员 */
    private String configType;

    private String configTypeLabel;

    /** DEPT_SUPERVISOR=部门路径字符串；WAREHOUSE_KEEPER=位置 id（字符串） */
    private String configKey;

    /** 配置键展示名：DEPT=部门路径原文；WAREHOUSE=位置名称（查询回填） */
    private String configKeyLabel;

    private Long approverUserId;

    /** 审批人姓名（UserDirectory 反查回填） */
    private String approverUserName;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ApprovalConfigResp from(ApprovalConfig entity) {
        ApprovalConfigResp resp = new ApprovalConfigResp();
        resp.setId(entity.getId());
        resp.setConfigType(entity.getConfigType());
        resp.setConfigTypeLabel(ApprovalConfigType.of(entity.getConfigType()).getLabel());
        resp.setConfigKey(entity.getConfigKey());
        resp.setConfigKeyLabel(entity.getConfigKeyLabel());
        resp.setApproverUserId(entity.getApproverUserId());
        resp.setApproverUserName(entity.getApproverUserName());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
