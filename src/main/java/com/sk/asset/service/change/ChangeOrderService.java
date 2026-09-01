package com.sk.asset.service.change;

import com.sk.asset.dto.change.ChangeApplyReq;
import com.sk.asset.dto.change.ChangeQuery;
import com.sk.asset.entity.change.ChangeOrder;

import java.util.List;

/**
 * 实物信息变更单服务（M06，AOC 单）。
 */
public interface ChangeOrderService {

    /** 发起变更（一单 = 资产列表 + 一组统一新值；明细行记录每台资产实际变更字段的变更前/后展示值） */
    ChangeOrder create(ChangeApplyReq req, Long applicantUserId, String applicantName);

    /**
     * 入口 B（钉钉原生表单发起）建单：校验主体与 create 一致（资产/占用/位置），
     * 但不发布 OA 同步事件（单据源自钉钉，避免回推循环）。
     * 变更在入口 A 契约中钉钉审批人 = 处理人（assigneeUserId），故调用方（import 服务）
     * 应以钉钉实例 tasks 解析的审批人作为 assigneeUserId 传入 req——以钉钉传来的为准。
     */
    ChangeOrder createFromDingtalk(ChangeApplyReq req, Long applicantUserId, String applicantName);

    /** 列表（支持 status/userId/date 筛选；assetId 按明细行反查变更历史），含明细行回填 */
    List<ChangeOrder> list(ChangeQuery query);

    /** 详情（含明细行与变更前/后对比值） */
    ChangeOrder getById(Long id);

    /**
     * 确认执行：更新 asset 归属字段 + 使用人变化时同步持有关系（闭环旧持有 + 新建 CHANGE 持有）
     * + 写 asset_log（operation_type = 实物信息变更）。
     * 变更单为信息修正单据，不要求确认人 ≠ 发起人。
     */
    ChangeOrder confirm(Long id, Long confirmerUserId, String confirmerName);

    /** 撤销（仅 PENDING 状态、仅发起人可撤，资产不变） */
    ChangeOrder cancel(Long id, Long operatorUserId);
}
