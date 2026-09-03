package com.sk.asset.service.approval;

import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.dingtalk.DingtalkDept;
import com.sk.asset.mapper.dingtalk.DingtalkDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 多级主管链解析器测试：部门树定位（精确/回退/特殊部门）、无主管部门向上回退、
 * 去重去本人、快照两级语义（step1=固定一级、step2=直接主管）与兜底报错。
 */
@ExtendWith(MockitoExtension.class)
class DeptManagerChainResolverTest {

    private static final Long APPLICANT = 100L;
    private static final String APPLICANT_DD = "dd100";
    private static final String FIXED_DD = "dd-gu";
    private static final Long FIXED_USER = 114L;

    @Mock
    private DingtalkDeptMapper deptMapper;
    @Mock
    private UserDirectory userDirectory;

    private DingtalkProperties props;
    private DeptManagerChainResolver resolver;

    @BeforeEach
    void setUp() {
        props = new DingtalkProperties();
        props.setFixedFirstApproverDdUserId(FIXED_DD);
        resolver = new DeptManagerChainResolver(deptMapper, userDirectory, props);
    }

    // ------------------------------------------------------------ 通用 stub

    private void stubApplicant(String dept) {
        lenient().when(userDirectory.namesByIds(any())).thenReturn(
                Map.of(APPLICANT, new UserResp(APPLICANT, "zhangsan", "张三", dept)));
        lenient().when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(APPLICANT, APPLICANT_DD));
    }

    private void stubFixedFirst() {
        lenient().when(userDirectory.findByDdUserId(FIXED_DD)).thenReturn(
                new UserResp(FIXED_USER, "gurs", "谷仍山", "综合管理部"));
    }

    private DingtalkDept dept(long deptId, String name, long parentId, String managerDd) {
        DingtalkDept d = new DingtalkDept();
        d.setDeptId(deptId);
        d.setName(name);
        d.setParentId(parentId);
        d.setManagerDdUserIds(managerDd);
        return d;
    }

    private void stubDepts(DingtalkDept... rows) {
        when(deptMapper.selectList(null)).thenReturn(List.of(rows));
    }

    // ------------------------------------------------------------ 链解析

    @Test
    void 全路径匹配_无主管部门向上回退_多级链() {
        stubApplicant("研发部/项目一科/PD课");
        stubFixedFirst();
        // PD课 未设主管 → 回退 项目一科（张胜瑶）→ 研发部（姚强）→ 根截止
        stubDepts(
                dept(10, "PD课", 20, null),
                dept(20, "项目一科", 30, "dd-zhang"),
                dept(30, "研发部", 1, "dd-yao"));
        when(userDirectory.findByDdUserId("dd-zhang")).thenReturn(
                new UserResp(200L, "zsy", "张胜瑶", "研发部"));
        when(userDirectory.findByDdUserId("dd-yao")).thenReturn(
                new UserResp(300L, "yq", "姚强", "研发部"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertNull(r.error());
        // 钉钉完整节点：固定一级 → 张胜瑶 → 姚强
        assertEquals(List.of(FIXED_DD, "dd-zhang", "dd-yao"), r.allDdUserIds());
        // 站内快照：step1=固定一级，step2=直接主管（回退命中张胜瑶）
        assertEquals(FIXED_USER, r.snapshot().getStep1UserId());
        assertEquals("谷仍山", r.snapshot().getStep1Name());
        assertEquals(200L, r.snapshot().getStep2UserId());
        assertEquals("张胜瑶", r.snapshot().getStep2Name());
        assertEquals("项目一科", r.snapshot().getStep2SourceKey());
        assertFalse(r.snapshot().isMerged());
    }

    @Test
    void 直接主管为固定一级_合并() {
        stubApplicant("研发部/项目一科");
        stubFixedFirst();
        stubDepts(
                dept(20, "项目一科", 30, FIXED_DD),
                dept(30, "研发部", 1, null));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertTrue(r.snapshot().isMerged());
        assertEquals(FIXED_USER, r.snapshot().getStep2UserId());
        // 节点去重：只剩固定一级一人
        assertEquals(List.of(FIXED_DD), r.allDdUserIds());
    }

    @Test
    void 链上去重_上级主管与直接主管同人() {
        stubApplicant("研发部/项目一科/PD课");
        stubFixedFirst();
        stubDepts(
                dept(10, "PD课", 20, "dd-zhang"),
                dept(20, "项目一科", 30, "dd-zhang"),
                dept(30, "研发部", 1, "dd-yao"));
        when(userDirectory.findByDdUserId("dd-zhang")).thenReturn(
                new UserResp(200L, "zsy", "张胜瑶", "研发部"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertEquals(List.of(FIXED_DD, "dd-zhang", "dd-yao"), r.allDdUserIds());
        assertEquals(200L, r.snapshot().getStep2UserId());
    }

    @Test
    void 链上剔除申请人本人() {
        stubApplicant("研发部/项目一科/PD课");
        stubFixedFirst();
        // 申请人是项目一科主管（张三=dd100）：链上剔除本人，回退研发部姚强
        stubDepts(
                dept(10, "PD课", 20, null),
                dept(20, "项目一科", 30, APPLICANT_DD),
                dept(30, "研发部", 1, "dd-yao"));
        when(userDirectory.findByDdUserId("dd-yao")).thenReturn(
                new UserResp(300L, "yq", "姚强", "研发部"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertEquals(List.of(FIXED_DD, "dd-yao"), r.allDdUserIds());
        assertEquals(300L, r.snapshot().getStep2UserId());
    }

    @Test
    void 未绑定主管_节点保留_站内快照跳到下级绑定主管() {
        stubApplicant("研发部/项目一科/PD课");
        stubFixedFirst();
        // 张胜瑶未绑定系统账号：钉钉节点保留，站内 step2 落到姚强
        stubDepts(
                dept(10, "PD课", 20, null),
                dept(20, "项目一科", 30, "dd-zhang"),
                dept(30, "研发部", 1, "dd-yao"));
        when(userDirectory.findByDdUserId("dd-zhang")).thenReturn(null);
        when(userDirectory.findByDdUserId("dd-yao")).thenReturn(
                new UserResp(300L, "yq", "姚强", "研发部"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertEquals(List.of(FIXED_DD, "dd-zhang", "dd-yao"), r.allDdUserIds());
        assertEquals(300L, r.snapshot().getStep2UserId());
        assertEquals("研发部", r.snapshot().getStep2SourceKey());
    }

    @Test
    void 旧横线分隔路径_原始名称精确匹配() {
        stubApplicant("供应链管理中心-森科采购部");
        stubFixedFirst();
        // 部门名本身含「-」：原始路径精确匹配优先于切分
        stubDepts(dept(40, "供应链管理中心-森科采购部", 1, "dd-li"));
        when(userDirectory.findByDdUserId("dd-li")).thenReturn(
                new UserResp(400L, "li", "李四", "供应链管理中心"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertEquals(List.of(FIXED_DD, "dd-li"), r.allDdUserIds());
        assertEquals(400L, r.snapshot().getStep2UserId());
    }

    @Test
    void 路径未命中钉钉树_逐级去末级回退() {
        stubApplicant("研发部/项目一科/已改名课");
        stubFixedFirst();
        // 全路径未命中 → 去末级回退"研发部/项目一科"命中
        stubDepts(
                dept(20, "项目一科", 30, "dd-zhang"),
                dept(30, "研发部", 1, null));
        when(userDirectory.findByDdUserId("dd-zhang")).thenReturn(
                new UserResp(200L, "zsy", "张胜瑶", "研发部"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertEquals(List.of(FIXED_DD, "dd-zhang"), r.allDdUserIds());
    }

    @Test
    void 特殊部门_森丰_走配置主管() {
        stubApplicant("森丰");
        stubFixedFirst();
        // 森丰不在钉钉树内：钉钉树空表，主管经 special-dept-managers 配置
        props.getSpecialDeptManagers().put("森丰", "dd-xiao");
        when(userDirectory.findByDdUserId("dd-xiao")).thenReturn(
                new UserResp(691L, "xp", "肖鹏", "综合管理部/IT科"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertTrue(r.resolvable());
        assertEquals(List.of(FIXED_DD, "dd-xiao"), r.allDdUserIds());
        assertEquals(691L, r.snapshot().getStep2UserId());
        assertEquals("森丰", r.snapshot().getStep2SourceKey());
    }

    // ------------------------------------------------------------ 兜底报错

    @Test
    void 固定一级未配置_报错() {
        props.setFixedFirstApproverDdUserId("");
        stubApplicant("研发部");

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertFalse(r.resolvable());
        assertTrue(r.error().contains("未配置固定一级审批人"));
    }

    @Test
    void 固定一级未绑定系统用户_报错() {
        stubApplicant("研发部");
        when(userDirectory.findByDdUserId(FIXED_DD)).thenReturn(null);

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertFalse(r.resolvable());
        assertTrue(r.error().contains("未绑定系统用户"));
    }

    @Test
    void 申请人无部门_报错() {
        stubFixedFirst();
        when(userDirectory.namesByIds(any())).thenReturn(
                Map.of(APPLICANT, new UserResp(APPLICANT, "zhangsan", "张三", null)));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertFalse(r.resolvable());
        assertTrue(r.error().contains("部门信息"));
    }

    @Test
    void 固定一级为申请人本人_死单防御() {
        stubApplicant("研发部/项目一科");
        when(userDirectory.findByDdUserId(FIXED_DD)).thenReturn(
                new UserResp(APPLICANT, "gurs", "谷仍山", "综合管理部"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertFalse(r.resolvable());
        assertTrue(r.error().contains("不能是同一人"));
    }

    @Test
    void 链上无可绑定主管_报错() {
        stubApplicant("研发部/项目一科");
        stubFixedFirst();
        // 部门均未设主管且无特殊部门配置
        stubDepts(dept(20, "项目一科", 1, null));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertFalse(r.resolvable());
        assertTrue(r.error().contains("主管审批人"));
    }

    @Test
    void 部门路径完全无法定位_报错() {
        stubApplicant("不存在的部门/子部门");
        stubFixedFirst();
        stubDepts(dept(20, "项目一科", 1, "dd-zhang"));

        DeptManagerChainResolver.MultiResolution r = resolver.tryResolveMultiLevel(APPLICANT);

        assertFalse(r.resolvable());
        assertTrue(r.error().contains("主管审批人"));
    }
}
