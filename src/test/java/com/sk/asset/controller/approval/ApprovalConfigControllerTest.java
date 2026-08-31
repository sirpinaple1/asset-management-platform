package com.sk.asset.controller.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.auth.dto.SystemAuthDto;
import com.sk.asset.auth.dto.SystemRoleDto;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.approval.ApprovalConfigReq;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.service.approval.ApprovalConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批链配置控制器单测：超管门禁（非 systemAdmin 403 / 未认证 401）、
 * CRUD 透传 service、非法类型 400。
 */
class ApprovalConfigControllerTest {

    private static final String ADMIN_ROLE = "systemAdmin";

    private final ApprovalConfigService configService = mock(ApprovalConfigService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ApprovalConfigControllerTest() {
        ApprovalConfigController controller = new ApprovalConfigController(configService);
        // 纯单测无 Spring 上下文，@Value 不生效，手动注入超管角色名
        ReflectionTestUtils.setField(controller, "adminRole", ADMIN_ROLE);
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 模拟 TokenAuthFilter 已注入的认证上下文（指定 asset 系统角色） */
    private void loginUser(String roleName) {
        AuthUserDto user = new AuthUserDto();
        user.setUserId(1);
        user.setName("测试用户");
        user.setUsername("admin");
        SystemAuthDto systemAuth = new SystemAuthDto();
        SystemRoleDto role = new SystemRoleDto();
        role.setName(roleName);
        systemAuth.setRoles(List.of(role));
        UserContext.set(new AuthContext(user, systemAuth));
    }

    private ApprovalConfig sampleConfig() {
        ApprovalConfig config = new ApprovalConfig();
        config.setId(5L);
        config.setConfigType("DEPT_SUPERVISOR");
        config.setConfigKey("资材管理中心/PMC部");
        config.setConfigKeyLabel("资材管理中心/PMC部");
        config.setApproverUserId(200L);
        config.setApproverUserName("李四");
        return config;
    }

    private ApprovalConfigReq sampleReq() {
        ApprovalConfigReq req = new ApprovalConfigReq();
        req.setConfigType("DEPT_SUPERVISOR");
        req.setConfigKey("资材管理中心/PMC部");
        req.setApproverUserId(200L);
        req.setRemark("测试配置");
        return req;
    }

    // ---- 超管门禁 ----

    @Test
    void page_shouldRejectWhenNotSystemAdmin() throws Exception {
        loginUser("asset-资产管理员");

        mockMvc.perform(get("/api/v1/approval-configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("审批链配置仅系统管理员可管理"));
    }

    @Test
    void create_shouldRejectWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/approval-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ---- CRUD 透传 ----

    @Test
    void page_shouldReturnRecordsForSystemAdmin() throws Exception {
        loginUser(ADMIN_ROLE);
        Page<ApprovalConfig> page = new Page<>(1, 20);
        page.setRecords(List.of(sampleConfig()));
        page.setTotal(1);
        when(configService.page(1, 20, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/approval-configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].configType").value("DEPT_SUPERVISOR"))
                .andExpect(jsonPath("$.data.records[0].approverUserName").value("李四"));
    }

    @Test
    void page_shouldPassTypeAndKeywordToService() throws Exception {
        loginUser(ADMIN_ROLE);
        Page<ApprovalConfig> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(configService.page(1, 20, com.sk.asset.enums.approval.ApprovalConfigType.WAREHOUSE_KEEPER, "一号"))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/approval-configs")
                        .param("type", "WAREHOUSE_KEEPER")
                        .param("keyword", "一号"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void page_shouldRejectInvalidTypeWith400() throws Exception {
        loginUser(ADMIN_ROLE);

        mockMvc.perform(get("/api/v1/approval-configs").param("type", "BAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("非法审批链配置类型：BAD"));
    }

    @Test
    void create_shouldSaveAndReturnConfig() throws Exception {
        loginUser(ADMIN_ROLE);
        doAnswer(inv -> {
            ((ApprovalConfig) inv.getArgument(0)).setId(5L);
            return null;
        }).when(configService).save(any());
        when(configService.getById(5L)).thenReturn(sampleConfig());

        mockMvc.perform(post("/api/v1/approval-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5));

        ArgumentCaptor<ApprovalConfig> captor = ArgumentCaptor.forClass(ApprovalConfig.class);
        verify(configService).save(captor.capture());
        assertEquals("DEPT_SUPERVISOR", captor.getValue().getConfigType());
        assertEquals(200L, captor.getValue().getApproverUserId());
    }

    @Test
    void create_shouldRejectBlankConfigKey() throws Exception {
        loginUser(ADMIN_ROLE);
        ApprovalConfigReq req = sampleReq();
        req.setConfigKey("");

        mockMvc.perform(post("/api/v1/approval-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("配置键不能为空"));
    }

    @Test
    void update_shouldPassFieldsToService() throws Exception {
        loginUser(ADMIN_ROLE);
        when(configService.getById(5L)).thenReturn(sampleConfig());

        mockMvc.perform(put("/api/v1/approval-configs/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ApprovalConfig> captor = ArgumentCaptor.forClass(ApprovalConfig.class);
        verify(configService).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals("DEPT_SUPERVISOR", captor.getValue().getConfigType());
        assertEquals(200L, captor.getValue().getApproverUserId());
    }

    @Test
    void update_shouldReturn404WhenMissing() throws Exception {
        loginUser(ADMIN_ROLE);
        when(configService.getById(9L)).thenReturn(null);

        mockMvc.perform(put("/api/v1/approval-configs/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldPassIdToService() throws Exception {
        loginUser(ADMIN_ROLE);

        mockMvc.perform(delete("/api/v1/approval-configs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(configService).deleteById(5L);
    }

    @Test
    void delete_shouldRejectWhenNotSystemAdmin() throws Exception {
        loginUser("asset-资产管理员");

        mockMvc.perform(delete("/api/v1/approval-configs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }
}
