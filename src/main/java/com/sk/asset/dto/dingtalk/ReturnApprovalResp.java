package com.sk.asset.dto.dingtalk;

import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 钉钉退还审批响应（入口 B：无系统单据，映射记录 + 资产快照）。
 * 供审批中心"我发起的"列表展示：申请人为发起人（dd_user_id 反查），资产明细为导入时快照。
 */
@Data
public class ReturnApprovalResp {

    /** 映射记录 id（approval_instance.id） */
    private Long id;

    /** 钉钉实例标题（如"肖鹏提交的IT资产退还单"） */
    private String title;

    /** 发起人（系统用户 id；未绑定内部账号时为 null） */
    private Long applicantUserId;

    /** 发起人姓名 */
    private String applicantName;

    /** 钉钉实例状态：RUNNING/COMPLETED/TERMINATED */
    private String status;

    /** 审批结果：agree/refuse（未终审为 null） */
    private String result;

    private LocalDateTime createdAt;

    /** 退还资产明细（导入时快照；存量记录可能为空） */
    private List<AssetBrief> assets;

    @Data
    public static class AssetBrief {

        private Long id;

        private String barcode;

        private String name;

        /** 资产当前状态（IN_USE/IDLE/...，归还执行后回 IDLE） */
        private String status;

        public static AssetBrief from(Asset asset) {
            AssetBrief brief = new AssetBrief();
            brief.setId(asset.getId());
            brief.setBarcode(asset.getBarcode());
            brief.setName(asset.getName());
            brief.setStatus(asset.getStatus());
            return brief;
        }
    }

    public static ReturnApprovalResp from(ApprovalInstance record, Long applicantUserId,
                                          String applicantName, List<AssetBrief> assets) {
        ReturnApprovalResp resp = new ReturnApprovalResp();
        resp.setId(record.getId());
        resp.setTitle(record.getTitle());
        resp.setApplicantUserId(applicantUserId);
        resp.setApplicantName(applicantName);
        resp.setStatus(record.getStatus());
        resp.setResult(record.getResult());
        resp.setCreatedAt(record.getCreatedAt());
        resp.setAssets(assets);
        return resp;
    }
}
