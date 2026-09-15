package com.sk.asset.dingtalk.event;

/**
 * 站内审批已完成、需代执行钉钉侧审批任务的事件（B5 双向同步）。
 *
 * <p>背景：钉钉→系统（Stream 回传）已有；系统→钉钉此前缺失——站内审批后
 * 钉钉实例仍挂待办，审批人需操作两次。业务 Service 在审批事务内发布本事件，
 * 监听方以 AFTER_COMMIT 相位查钉钉当前待办任务并代执行（agree/refuse）。</p>
 *
 * <p>幂等：监听方按实例状态 RUNNING + 该审批人存在待办任务才执行；
 * 已在钉钉侧操作过（任务非 RUNNING）则跳过。失败仅告警不回滚业务单据。</p>
 *
 * @param processInstanceId 钉钉审批实例 id
 * @param actionerUserId    站内审批人用户 id（监听方解析其钉钉 userid）
 * @param result            agree / refuse
 * @param remark            审批意见（拒绝原因等，可空）
 */
public record OaTaskExecuteRequestedEvent(
        String processInstanceId, Long actionerUserId, String result, String remark) {
}
