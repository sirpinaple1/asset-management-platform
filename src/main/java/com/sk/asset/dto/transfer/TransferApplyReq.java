package com.sk.asset.dto.transfer;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 发起调拨申请请求（POST /api/v1/transfers）。
 * 发起人由后端 UserContext 取，前端不传；
 * 调入区域与调入部门至少填一项（服务端校验）。
 */
@Data
public class TransferApplyReq {

    /** 调拨资产 ID 列表（一次可多台） */
    @NotEmpty(message = "请至少选择一台资产")
    private List<Long> assetIds;

    /** 调入位置（asset_location.id，与 toDepartment 至少一项） */
    private Long toLocationId;

    /** 调入部门（与 toLocationId 至少一项） */
    private String toDepartment;

    /** 调入方负责人 ID（可空 = 调入区域/部门无指定负责人） */
    private Long toUserId;

    /** 调入方负责人姓名（前端已知时快照传入，可空） */
    private String toUserName;

    /** 调拨原因 */
    private String reason;
}
