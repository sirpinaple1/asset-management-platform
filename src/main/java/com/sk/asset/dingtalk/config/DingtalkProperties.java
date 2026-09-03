package com.sk.asset.dingtalk.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉 OA 审批联动配置（M10，ADR-0007）。
 *
 * <p>实验公司 → 生产公司切换只换配置不改代码：appKey/appSecret/agentId/corpId
 * 与各单据模板 processCode 全部外置（R3 红线：密钥只放 application-local.yml / 环境变量）。
 * {@code enabled=false}（默认）时同步与事件监听整体关闭，系统功能不受影响。</p>
 *
 * <p>processCodes 的 key 为单据类型：receive（领用）/borrow（借用）/transfer（调拨）/change（实物变更）/return（退还）。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.dingtalk")
public class DingtalkProperties {

    /** 总开关：false 时单据不推钉钉、Stream 不启动，全部走站内审批 */
    private boolean enabled = false;

    /** 企业内部应用凭证（开放平台 → 应用开发 → asset-oa） */
    private String appKey = "";
    private String appSecret = "";

    /** 微应用 agentId（topapi processinstance/create 必填） */
    private Long agentId;

    /** 发起人部门 ID（processinstance/create 必填；一人公司/根部门=1，生产按发起人实际部门取值） */
    private Long originatorDeptId = 1L;

    /** 企业 corpId（事件 corpId 校验用，防跨企业事件串扰） */
    private String corpId = "";

    /** Stream 事件订阅开关（长连接；依赖出网公网，仅出站） */
    private boolean streamEnabled = true;

    /** 额外抄送人钉钉 userid 列表（发起人默认始终抄送） */
    private List<String> ccUserIds = new ArrayList<>();

    /**
     * 多级主管链固定一级审批人钉钉 userid（领用/借用审批链首节点）。
     * 为空则领用/借用链解析失败（400 阻止提交 + 告警）；值从 sys_user.dd_user_id 核实后配置，
     * 不硬编码业务代码（R3：环境相关值只放 application-local.yml / 环境变量）。
     */
    private String fixedFirstApproverDdUserId = "";

    /**
     * 特殊部门主管覆盖：部门名 → 主管钉钉 userid。
     * 森丰/锐鑫智能等不在钉钉部门树内的部门，其链上主管经此配置指定
     * （同样命中钉钉树内同名部门，优先于 dept_manager_userid_list）。
     * 注意：环境变量无法承载中文 Map 键（非 ASCII 键在属性名转换中丢失），
     * 生产经 {@link #specialDeptManagersText} 扁平文本配置，@PostConstruct 解析合并进本 Map。
     */
    private Map<String, String> specialDeptManagers = new HashMap<>();

    /**
     * 特殊部门主管覆盖（扁平文本形态，供环境变量配置）：
     * {@code 部门名:钉钉userid} 逗号分隔（中英文逗号/冒号均可），如
     * {@code 森丰:0346334069651060985,锐鑫智能:0346334069651060985}。
     */
    private String specialDeptManagersText = "";

    @PostConstruct
    public void parseSpecialDeptManagersText() {
        if (specialDeptManagersText == null || specialDeptManagersText.isBlank()) {
            return;
        }
        for (String pair : specialDeptManagersText.split("[,，]")) {
            int idx = Math.max(pair.indexOf(':'), pair.indexOf('：'));
            if (idx <= 0 || idx == pair.length() - 1) {
                continue; // 非法片段忽略（配置值仅此一个来源，错误片段静默跳过）
            }
            String key = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                specialDeptManagers.putIfAbsent(key, value);
            }
        }
    }

    /** 部门树缓存每日刷新时刻（cron；同步失败仅告警不阻断业务） */
    private String deptSyncCron = "0 40 2 * * ?";

    /** 单据类型 → 审批模板 processCode */
    private Map<String, String> processCodes = new HashMap<>();

    /** 同步/回调失败告警接收角色（复用审批链配置告警角色） */
    private String adminRole = "systemAdmin";
}
