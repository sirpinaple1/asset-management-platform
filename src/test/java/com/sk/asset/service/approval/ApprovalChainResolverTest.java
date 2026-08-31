package com.sk.asset.service.approval;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.approval.ApprovalConfigMapper;
import com.sk.asset.service.notification.NotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 两级审批链解析器单测：部门精确匹配 + 逐级向上回退（/ 与 - 分隔符）、
 * 兜底 400 文案（一级/二级缺失、审批人失效、审批人=申请人）、同人合并、超管告警。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalChainResolverTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalConfig.class);
    }

    private static final Long APPLICANT = 100L;
    private static final Long SUPERVISOR = 200L;
    private static final Long KEEPER = 300L;

    private static final String DEPT = "资材管理中心/PMC部";
    private static final Long LOCATION_ID = 1L;

    @Mock
    private ApprovalConfigMapper configMapper;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApprovalChainResolver resolver;

    /** configType|configKey → 配置（selectOne 按 wrapper 参数值路由） */
    private final Map<String, ApprovalConfig> configs = new HashMap<>();

    private void stubConfigLookup() {
        when(configMapper.selectOne(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<ApprovalConfig> wrapper = inv.getArgument(0);
            // 触发条件段求值（eq 参数懒注册，getSqlSegment 后才落入 paramNameValuePairs；
            // 底层为 HashMap 无顺序保证，按「类型枚举名」识别哪个是 type）
            wrapper.getSqlSegment();
            List<Object> values = new ArrayList<>(wrapper.getParamNameValuePairs().values());
            if (values.size() < 2) {
                return null;
            }
            String v0 = String.valueOf(values.get(0));
            String v1 = String.valueOf(values.get(1));
            boolean v0IsType = v0.equals("DEPT_SUPERVISOR") || v0.equals("WAREHOUSE_KEEPER");
            String type = v0IsType ? v0 : v1;
            String key = v0IsType ? v1 : v0;
            return configs.get(type + "|" + key);
        });
    }

    private ApprovalConfig config(String type, String key, Long approverId) {
        ApprovalConfig config = new ApprovalConfig();
        config.setConfigType(type);
        config.setConfigKey(key);
        config.setApproverUserId(approverId);
        return config;
    }

    private void stubUsers(Long applicantId, String dept, Long supervisorId, Long keeperId) {
        Map<Long, UserResp> users = new HashMap<>();
        if (applicantId != null) {
            users.put(applicantId, new UserResp(applicantId, "user" + applicantId, "张三", dept));
        }
        if (supervisorId != null) {
            users.put(supervisorId, new UserResp(supervisorId, "user" + supervisorId, "李四", dept));
        }
        if (keeperId != null) {
            users.put(keeperId, new UserResp(keeperId, "user" + keeperId, "王五", null));
        }
        when(userDirectory.namesByIds(any())).thenReturn(users);
    }

    // ---- 解析成功 ----

    @Test
    void resolve_shouldReturnChainWhenExactDeptConfigured() {
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|" + DEPT, config("DEPT_SUPERVISOR", DEPT, SUPERVISOR));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), KEEPER));
        stubUsers(APPLICANT, DEPT, SUPERVISOR, KEEPER);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertTrue(resolution.resolvable(), resolution.error());
        ApprovalChainResolver.ResolvedChain chain = resolution.chain();
        assertEquals(SUPERVISOR, chain.getStep1UserId());
        assertEquals("李四", chain.getStep1Name());
        assertEquals(DEPT, chain.getStep1SourceKey());
        assertEquals(KEEPER, chain.getStep2UserId());
        assertEquals("王五", chain.getStep2Name());
        assertEquals(String.valueOf(LOCATION_ID), chain.getStep2SourceKey());
        assertFalse(chain.isMerged());
    }

    @Test
    void resolve_shouldFallbackToParentDeptWhenExactMissing() {
        // 部门「资材管理中心/PMC部/仓储组」无精确配置，回退到「资材管理中心」级配置（组织调整留余地）
        String deepDept = DEPT + "/仓储组";
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|资材管理中心", config("DEPT_SUPERVISOR", "资材管理中心", SUPERVISOR));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), KEEPER));
        stubUsers(APPLICANT, deepDept, SUPERVISOR, KEEPER);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertTrue(resolution.resolvable());
        assertEquals(SUPERVISOR, resolution.chain().getStep1UserId());
        // 解析依据快照 = 实际命中的父级部门（审计追溯）
        assertEquals("资材管理中心", resolution.chain().getStep1SourceKey());
    }

    @Test
    void resolve_shouldFallbackWithDashSeparator() {
        // sys_user.dept 实测存在「-」分隔（如 供应链管理中心-森科采购部），回退链兼容
        String dashDept = "供应链管理中心-森科采购部";
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|供应链管理中心", config("DEPT_SUPERVISOR", "供应链管理中心", SUPERVISOR));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), KEEPER));
        stubUsers(APPLICANT, dashDept, SUPERVISOR, KEEPER);

        assertTrue(resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间").resolvable());
    }

    @Test
    void resolve_shouldReturnMergedWhenSameApprover() {
        // 部门主管兼仓管：合并为一次审批
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|" + DEPT, config("DEPT_SUPERVISOR", DEPT, SUPERVISOR));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), SUPERVISOR));
        stubUsers(APPLICANT, DEPT, SUPERVISOR, null);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertTrue(resolution.resolvable());
        assertTrue(resolution.chain().isMerged());
        assertEquals(SUPERVISOR, resolution.chain().getStep2UserId());
    }

    // ---- 兜底 400 ----

    @Test
    void resolve_shouldErrorWhenApplicantDeptMissing() {
        stubUsers(APPLICANT, null, null, null);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertFalse(resolution.resolvable());
        assertTrue(resolution.error().contains("部门信息"));
    }

    @Test
    void resolve_shouldErrorWhenSupervisorConfigMissing() {
        stubConfigLookup();
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), KEEPER));
        stubUsers(APPLICANT, DEPT, null, KEEPER);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertFalse(resolution.resolvable());
        assertTrue(resolution.error().contains("主管审批人"));
        assertTrue(resolution.error().contains(DEPT));
    }

    @Test
    void resolve_shouldErrorWhenKeeperConfigMissing() {
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|" + DEPT, config("DEPT_SUPERVISOR", DEPT, SUPERVISOR));
        stubUsers(APPLICANT, DEPT, SUPERVISOR, null);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertFalse(resolution.resolvable());
        assertTrue(resolution.error().contains("仓管审批人"));
        assertTrue(resolution.error().contains("一号车间"));
    }

    @Test
    void resolve_shouldErrorWhenConfiguredApproverStale() {
        // 配置后用户被删除（namesByIds 不返回）：防失效配置路由出死单
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|" + DEPT, config("DEPT_SUPERVISOR", DEPT, SUPERVISOR));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), KEEPER));
        stubUsers(APPLICANT, DEPT, SUPERVISOR, null);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertFalse(resolution.resolvable());
        assertTrue(resolution.error().contains("已失效"));
        assertTrue(resolution.error().contains(String.valueOf(KEEPER)));
    }

    @Test
    void resolve_shouldErrorWhenStep1ApproverIsApplicant() {
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|" + DEPT, config("DEPT_SUPERVISOR", DEPT, APPLICANT));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), KEEPER));
        stubUsers(APPLICANT, DEPT, APPLICANT, KEEPER);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertFalse(resolution.resolvable());
        assertTrue(resolution.error().contains("主管审批人是申请人自己"));
    }

    @Test
    void resolve_shouldErrorWhenStep2ApproverIsApplicant() {
        stubConfigLookup();
        configs.put("DEPT_SUPERVISOR|" + DEPT, config("DEPT_SUPERVISOR", DEPT, SUPERVISOR));
        configs.put("WAREHOUSE_KEEPER|" + LOCATION_ID, config("WAREHOUSE_KEEPER", String.valueOf(LOCATION_ID), APPLICANT));
        // keeper=申请人本人：申请人条目已覆盖该用户（勿再传 keeper 避免覆盖申请人的部门）
        stubUsers(APPLICANT, DEPT, SUPERVISOR, null);

        ApprovalChainResolver.Resolution resolution = resolver.tryResolve(APPLICANT, LOCATION_ID, "一号车间");

        assertFalse(resolution.resolvable());
        assertTrue(resolution.error().contains("仓管审批人是申请人自己"), resolution.error());
    }

    // ---- 超管告警 ----

    @Test
    void alertAdmins_shouldNotifyEverySystemAdmin() {
        ReflectionTestUtils.setField(resolver, "adminRole", "systemAdmin");
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(1L, 2L));

        resolver.alertAdmins("未找到〔PMC部〕的主管审批人", "张三 发起领用，领用区域：一号车间");

        verify(notificationService).notify(eq(1L), eq(NotificationType.APPROVAL_CONFIG_ALERT),
                contains("主管审批人"), eq("CONFIG"), eq(0L));
        verify(notificationService).notify(eq(2L), eq(NotificationType.APPROVAL_CONFIG_ALERT),
                contains("审批链配置告警"), eq("CONFIG"), eq(0L));
        verify(notificationService).notify(eq(1L), eq(NotificationType.APPROVAL_CONFIG_ALERT),
                contains("张三 发起领用"), eq("CONFIG"), eq(0L));
    }
}
