package com.sk.asset.service.approval;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.enums.approval.ApprovalConfigType;

/**
 * 审批链配置服务（仅 systemAdmin 可管理，门禁在 Controller 层）。
 */
public interface ApprovalConfigService {

    /** 分页查询（type 筛选 + keyword 匹配 configKey/remark，回填审批人姓名与配置键展示名） */
    IPage<ApprovalConfig> page(long page, long size, ApprovalConfigType type, String keyword);

    /** 详情（含回填），不存在返回 null */
    ApprovalConfig getById(Long id);

    /** 新增：configType/configKey 合法性 + 审批人存在性 + 唯一约束（预检 + DuplicateKeyException 兜底） */
    void save(ApprovalConfig config);

    /** 更新：同新增校验（configType/configKey/审批人均可改） */
    void updateById(ApprovalConfig config);

    /** 逻辑删除（同时置空 config_key 释放唯一键，允许同键重建） */
    void deleteById(Long id);

    /**
     * 解析区域管理员（WAREHOUSE_KEEPER 按位置 id，未配置/位置为空返回 null）。
     * 责任归属编排用：资产位置变更时 admin_user_id 随位置实时解析（B4 决策 2026-08-31）。
     */
    Long keeperUserIdOf(Long locationId);
}
