package com.sk.asset.dingtalk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 钉钉 OA 审批 OpenAPI 调用器（M10）。
 *
 * <p>接口契约（《钉钉OA审批集成方案》2026-08-28 已核验，topapi 版）：
 * <ul>
 *   <li>发起审批实例：POST /topapi/processinstance/create（approvers_v2 指定审批人节点，
 *       审批人由系统审批链驱动，钉钉模板只定义表单不配审批人）</li>
 *   <li>审批实例详情：POST /topapi/processinstance/get（status=RUNNING/COMPLETED/TERMINATED，
 *       result=agree/refuse）</li>
 * </ul>
 * access_token 经 {@link DingTalkTokenClient} 缓存复用。所有非 0 errcode 抛
 * {@link DingTalkApiException}，由调用方降级（记 FAILED + 告警），不影响业务事务。</p>
 */
@Slf4j
@Component
public class DingTalkApiClient {

    private static final String OAPI_BASE = "https://oapi.dingtalk.com";

    private final DingtalkProperties props;
    private final DingTalkTokenClient tokenClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DingTalkApiClient(DingtalkProperties props, DingTalkTokenClient tokenClient) {
        this.props = props;
        this.tokenClient = tokenClient;
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 发起审批实例（入口 A：系统建单后推送钉钉）。
     *
     * @param processCode          审批模板唯一码（app.dingtalk.process-codes 按单据类型配置）
     * @param originatorDdUserId   发起人钉钉 userid
     * @param approverDdUserIds    审批人钉钉 userid 列表（有序：两级链 = 两个顺序节点；同人合并 = 一个）
     * @param ccDdUserIds          抄送人钉钉 userid 列表（审批结束时抄送）
     * @param formComponentValues  表单字段（有序 name → value，字段名须与模板完全一致）
     * @return 钉钉审批实例 id
     */
    public String createProcessInstance(String processCode, String originatorDdUserId,
                                        List<String> approverDdUserIds, List<String> ccDdUserIds,
                                        List<Map<String, String>> formComponentValues) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("agent_id", props.getAgentId());
        body.put("process_code", processCode);
        body.put("originator_user_id", originatorDdUserId);
        body.put("dept_id", props.getOriginatorDeptId());
        ArrayNode approvers = body.putArray("approvers_v2");
        for (String ddUserId : approverDdUserIds) {
            ObjectNode node = approvers.addObject();
            ArrayNode ids = node.putArray("user_ids");
            ids.add(ddUserId);
            node.put("task_action_type", "NONE");
        }
        if (ccDdUserIds != null && !ccDdUserIds.isEmpty()) {
            body.put("cc_list", String.join(",", ccDdUserIds));
            body.put("cc_position", "FINISH");
        }
        ArrayNode form = body.putArray("form_component_values");
        for (Map<String, String> field : formComponentValues) {
            ObjectNode f = form.addObject();
            f.put("name", field.get("name"));
            f.put("value", field.get("value"));
        }

        JsonNode resp = post("/topapi/processinstance/create", body);
        String instanceId = resp.path("process_instance_id").asText(null);
        if (instanceId == null || instanceId.isBlank()) {
            throw new DingTalkApiException("发起钉钉审批实例失败：响应无 process_instance_id");
        }
        log.info("发起钉钉审批实例成功：processCode={}, instanceId={}", processCode, instanceId);
        return instanceId;
    }

    /**
     * 查询审批实例详情（事件信息不足时回查 / 对账兜底用）。
     *
     * @return result 节点（含 title/status/result/originator_userid/form_component_values/operation_records）
     */
    public JsonNode getProcessInstance(String processInstanceId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("process_instance_id", processInstanceId);
        JsonNode resp = post("/topapi/processinstance/get", body);
        return resp.path("result");
    }

    /**
     * 列子部门（通讯录只读权限；userid 批量同步用）。
     *
     * @param parentDeptId 父部门 id（根部门=1）
     * @return 子部门 id 列表（无子部门返回空）
     */
    public List<Long> listSubDeptIds(Long parentDeptId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("dept_id", parentDeptId);
        JsonNode resp = post("/topapi/v2/department/listsub", body);
        List<Long> ids = new ArrayList<>();
        for (JsonNode dept : resp.path("result")) {
            ids.add(dept.path("dept_id").asLong());
        }
        return ids;
    }

    /**
     * 按部门分页拉用户（通讯录只读权限；userid 批量同步用）。
     *
     * @return 单页结果（users: userid/name/mobile + nextCursor；末页 nextCursor=null）
     */
    public DeptUserPage listDeptUsers(Long deptId, Long cursor) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("dept_id", deptId);
        body.put("cursor", cursor == null ? 0L : cursor);
        body.put("size", 100);
        JsonNode resp = post("/topapi/v2/user/list", body);
        JsonNode result = resp.path("result");
        List<DeptUser> users = new ArrayList<>();
        for (JsonNode u : result.path("list")) {
            users.add(new DeptUser(
                    u.path("userid").asText(""),
                    u.path("name").asText(""),
                    u.path("mobile").asText("")));
        }
        Long next = result.path("has_more").asBoolean(false)
                ? result.path("next_cursor").asLong(0) : null;
        return new DeptUserPage(users, next);
    }

    /** 钉钉部门用户（userid 批量同步用） */
    public record DeptUser(String userid, String name, String mobile) {
    }

    /** 钉钉部门用户分页（nextCursor=null 表示末页） */
    public record DeptUserPage(List<DeptUser> users, Long nextCursor) {
    }

    /** topapi 通用 POST：access_token 走 query 参数，errcode != 0 统一抛异常 */
    private JsonNode post(String path, JsonNode body) {
        String token = tokenClient.getAccessToken();
        try {
            String respBody = restClient.post()
                    .uri(OAPI_BASE + path + "?access_token=" + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode resp = objectMapper.readTree(respBody == null ? "{}" : respBody);
            int errcode = resp.path("errcode").asInt(-1);
            if (errcode != 0) {
                throw new DingTalkApiException("钉钉 API 调用失败（" + path + "）：errcode="
                        + errcode + "，" + resp.path("errmsg").asText(""));
            }
            return resp;
        } catch (DingTalkApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkApiException("钉钉 API 调用失败（" + path + "）：" + e.getMessage(), e);
        }
    }
}
