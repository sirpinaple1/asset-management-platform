package com.sk.asset.controller.change;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.change.ChangeApplyReq;
import com.sk.asset.dto.change.ChangeQuery;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.change.ChangeOrderItem;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.service.change.ChangeOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChangeOrderControllerTest {

    private final ChangeOrderService changeOrderService = mock(ChangeOrderService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ChangeOrderControllerTest() {
        ChangeOrderController controller = new ChangeOrderController(changeOrderService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 模拟 TokenAuthFilter 已注入的认证上下文（userId=100，姓名=测试用户） */
    private void loginUser() {
        AuthUserDto user = new AuthUserDto();
        user.setUserId(100);
        user.setName("测试用户");
        user.setUsername("assetfe");
        UserContext.set(new AuthContext(user, null));
    }

    private ChangeOrder sampleOrder() {
        ChangeOrder order = new ChangeOrder();
        order.setId(1L);
        order.setSerialNo("AOC202608210001");
        order.setStatus("PENDING");
        order.setApplicantUserId(100L);
        order.setApplicantName("测试用户");
        order.setNewUserId(300L);
        order.setNewUserName("李四");
        order.setNewUserDepartment("品质部");
        order.setNewLocationId(20L);
        order.setNewLocationName("B区");
        order.setReason("使用人信息纠错");
        ChangeOrderItem item = new ChangeOrderItem();
        item.setId(10L);
        item.setOrderId(1L);
        item.setAssetId(1L);
        item.setAssetBarcode("SKSCDM-0001");
        item.setAssetName("镀膜机");
        item.setAssetSn("SN-001");
        item.setFieldName("user_id");
        item.setFieldLabel("使用人");
        item.setValueBefore("张三");
        item.setValueAfter("李四");
        order.setItems(List.of(item));
        return order;
    }

    // ---- list ----

    @Test
    void list_shouldPassFiltersToService() throws Exception {
        when(changeOrderService.list(any())).thenReturn(List.of());

        ChangeQuery expected = new ChangeQuery();
        expected.setStatus(ChangeStatus.PENDING);
        expected.setUserId(100L);
        expected.setAssetId(1L);
        expected.setDate(LocalDate.of(2026, 8, 21));

        mockMvc.perform(get("/api/v1/change-orders")
                .param("status", "PENDING")
                .param("userId", "100")
                .param("assetId", "1")
                .param("date", "2026-08-21"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(changeOrderService, times(1)).list(expected);
    }

    @Test
    void list_shouldReturn400WhenStatusInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/change-orders").param("status", "BAD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(changeOrderService, never()).list(any());
    }

    @Test
    void list_shouldReturnOrdersWithItems() throws Exception {
        when(changeOrderService.list(any())).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/v1/change-orders").param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].serialNo").value("AOC202608210001"))
            .andExpect(jsonPath("$.data[0].statusLabel").value("待确认"))
            .andExpect(jsonPath("$.data[0].applicantName").value("测试用户"))
            .andExpect(jsonPath("$.data[0].newUserName").value("李四"))
            .andExpect(jsonPath("$.data[0].newLocationName").value("B区"))
            .andExpect(jsonPath("$.data[0].items[0].assetBarcode").value("SKSCDM-0001"))
            .andExpect(jsonPath("$.data[0].items[0].fieldName").value("user_id"))
            .andExpect(jsonPath("$.data[0].items[0].fieldLabel").value("使用人"))
            .andExpect(jsonPath("$.data[0].items[0].valueBefore").value("张三"))
            .andExpect(jsonPath("$.data[0].items[0].valueAfter").value("李四"));
    }

    // ---- getById ----

    @Test
    void getById_shouldReturnDetail() throws Exception {
        when(changeOrderService.getById(1L)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/v1/change-orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.serialNo").value("AOC202608210001"))
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void getById_shouldReturn404WhenMissing() throws Exception {
        when(changeOrderService.getById(9L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/change-orders/9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    // ---- create ----

    @Test
    void create_shouldReturnCreatedOrderWithApplicantFromContext() throws Exception {
        loginUser();
        when(changeOrderService.create(any(ChangeApplyReq.class), eq(100L), eq("测试用户")))
                .thenReturn(sampleOrder());

        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(List.of(1L, 2L));
        req.setNewUserId(300L);
        req.setNewUserName("李四");
        req.setNewLocationId(20L);
        req.setReason("使用人信息纠错");

        mockMvc.perform(post("/api/v1/change-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.serialNo").value("AOC202608210001"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void create_shouldReturn401WhenNotAuthenticated() throws Exception {
        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(List.of(1L));
        req.setNewLocationId(20L);

        mockMvc.perform(post("/api/v1/change-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));

        verify(changeOrderService, never()).create(any(), any(), any());
    }

    @Test
    void create_shouldReturn400WhenAssetIdsEmpty() throws Exception {
        loginUser();
        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(List.of());
        req.setNewLocationId(20L);

        mockMvc.perform(post("/api/v1/change-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(changeOrderService, never()).create(any(), any(), any());
    }

    // ---- confirm / cancel ----

    @Test
    void confirm_shouldCallServiceWithCurrentUser() throws Exception {
        loginUser();
        ChangeOrder confirmed = sampleOrder();
        confirmed.setStatus("CONFIRMED");
        when(changeOrderService.confirm(1L, 100L, "测试用户")).thenReturn(confirmed);

        mockMvc.perform(post("/api/v1/change-orders/1/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已执行"));

        verify(changeOrderService, times(1)).confirm(1L, 100L, "测试用户");
    }

    @Test
    void confirm_shouldReturn409WhenAlreadyProcessed() throws Exception {
        loginUser();
        when(changeOrderService.confirm(1L, 100L, "测试用户"))
                .thenThrow(new BusinessException(409, "变更单已处理，不能重复操作"));

        mockMvc.perform(post("/api/v1/change-orders/1/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void cancel_shouldCallServiceWithCurrentUser() throws Exception {
        loginUser();
        ChangeOrder cancelled = sampleOrder();
        cancelled.setStatus("CANCELLED");
        when(changeOrderService.cancel(1L, 100L)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/change-orders/1/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已撤销"));

        verify(changeOrderService, times(1)).cancel(1L, 100L);
    }

    @Test
    void cancel_shouldReturn403WhenNotApplicant() throws Exception {
        loginUser();
        when(changeOrderService.cancel(1L, 100L))
                .thenThrow(new BusinessException(403, "仅发起人可撤销变更单"));

        mockMvc.perform(post("/api/v1/change-orders/1/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403));
    }
}
