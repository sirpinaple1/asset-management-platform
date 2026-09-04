package com.sk.asset.service.dingtalk;

import com.sk.asset.dto.dingtalk.ReturnApprovalResp;

import java.util.List;

/**
 * 钉钉退还审批查询（入口 B）：审批中心"我发起的"列表数据源。
 */
public interface ReturnApprovalService {

    /** 全部退还审批实例（按导入时间倒序），前端按申请人过滤"我发起的" */
    List<ReturnApprovalResp> list();
}
