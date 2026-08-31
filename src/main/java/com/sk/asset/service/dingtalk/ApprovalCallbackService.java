package com.sk.asset.service.dingtalk;

/**
 * 钉钉审批事件回调服务（M10 入口 B：钉钉审批动作 → 实时推进系统单据状态机）。
 *
 * <p>事件来源为 Stream 长连接（{@code DingtalkStreamBootstrap} 订阅全部事件后
 * 按 eventType 路由至此），目前消费 bpms_task_change / bpms_instance_change 两类；
 * 处理不抛异常（业务校验拦截记日志、基础设施异常告警），避免 Stream LATER 无限重推。</p>
 */
public interface ApprovalCallbackService {

    /**
     * 处理钉钉事件。
     *
     * @param eventType 事件类型（如 bpms_task_change / bpms_instance_change）
     * @param dataJson  事件数据 JSON（含 processInstanceId / staffId / result / corpId 等）
     */
    void onEvent(String eventType, String dataJson);
}
