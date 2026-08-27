package com.sk.asset.dto.change;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 发起实物信息变更申请请求（POST /api/v1/change-orders）。
 * 发起人由后端 UserContext 取，前端不传；
 * 一单 = 指定资产列表 + 一组统一新值，各字段 null = 不变更（至少一项非空，服务端校验）。
 */
@Data
public class ChangeApplyReq {

    /** 变更资产 ID 列表（一次可多台） */
    @NotEmpty(message = "请至少选择一台资产")
    private List<Long> assetIds;

    /** 变更后使用人 ID（null = 不变更；使用人在 comm_public_basic，后端无法回查姓名，需前端快照传入） */
    private Long newUserId;

    /** 变更后使用人姓名（前端已知时快照传入，可空则回退展示用户 ID） */
    private String newUserName;

    /** 变更后使用人部门（null = 不变更） */
    private String newUserDepartment;

    /** 变更后位置（asset_location.id，null = 不变更） */
    private Long newLocationId;

    /** 变更后存放位置明细（null = 不变更） */
    private String newLocationDetail;

    /** 变更后归属公司（company.id，null = 不变更） */
    private Long newCompanyId;

    /** 变更原因 */
    private String reason;

    /** 指定处理人 ID（可选，NULL=共享池；变更允许发起人自审，可指定为自己，需存在于 comm_public_basic） */
    @Positive(message = "指定处理人 ID 需为正整数")
    private Long assigneeUserId;
}
