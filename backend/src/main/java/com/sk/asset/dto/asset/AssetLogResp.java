package com.sk.asset.dto.asset;

import com.sk.asset.entity.asset.AssetLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产操作日志响应 DTO
 */
@Data
public class AssetLogResp {

    private Long id;
    private Long assetId;

    /** 操作类型：新增/领用/归还/调拨/实物信息变更/盘点处理/报废 */
    private String operationType;

    private Long operatorUserId;

    /** 操作人原始文本（M08 迁移保底） */
    private String operatorLabel;

    private String content;
    private LocalDateTime createdAt;

    public static AssetLogResp from(AssetLog entity) {
        AssetLogResp resp = new AssetLogResp();
        resp.setId(entity.getId());
        resp.setAssetId(entity.getAssetId());
        resp.setOperationType(entity.getOperationType());
        resp.setOperatorUserId(entity.getOperatorUserId());
        resp.setOperatorLabel(entity.getOperatorLabel());
        resp.setContent(entity.getContent());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
