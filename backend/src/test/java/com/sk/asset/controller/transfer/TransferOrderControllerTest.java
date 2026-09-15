package com.sk.asset.controller.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.dto.transfer.TransferQuery;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.transfer.TransferSource;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.service.transfer.TransferOrderService;
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

class TransferOrderControllerTest {

    private final TransferOrderService transferService = mock(TransferOrderService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TransferOrderControllerTest() {
        TransferOrderController controller = new TransferOrderController(transferService);

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

    private TransferOrder sampleOrder() {
        TransferOrder order = new TransferOrder();
        order.setId(1L);
        order.setSerialNo("ATR202608210001");
        order.setStatus("PENDING");
        order.setSource("MANUAL");
        order.setApplicantUserId(100L);
        order.setApplicantName("测试用户");
        order.setFromLocationId(10L);
        order.setFromLocationName("A区");
        order.setToLocationId(20L);
        order.setToLocationName("B区");
        order.setToDepartment("品质部");
        order.setToUserId(300L);
        order.setToUserName("李四");
        order.setReason("部门搬迁");
        TransferOrderItem item = new TransferOrderItem();
        item.setId(10L);
        item.setOrderId(1L);
        item.setAssetId(1L);
        item.setAssetBarcode("SKSCDM-0001");
        item.setAssetName("镀膜机");
        item.setAssetSn("SN-001");
        order.setItems(List.of(item));
        return order;
    }

    // ---- list ----

    @Test
    void list_shouldPassFiltersToService() throws Exception {
        when(transferService.list(any())).thenReturn(List.of());

        TransferQuery expected = new TransferQuery();
        expected.setStatus(TransferStatus.PENDING);
        expected.setSource(TransferSource.MANUAL);
        expected.setUserId(100L);
        expected.setDept("品质部");
        expected.setDate(LocalDate.of(2026, 8, 21));

        mockMvc.perform(get("/api/v1/transfers")
                .param("status", "PENDING")
                .param("source", "MANUAL")
                .param("userId", "100")
                .param("dept", "品质部")
                .param("date", "2026-08-21"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(transferService, times(1)).list(expected);
    }

    @Test
    void list_shouldReturn400WhenStatusInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/transfers").param("status", "BAD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(transferService, never()).list(any());
    }

    @Test
    void list_shouldReturnTransfersWithItems() throws Exception {
        when(transferService.list(any())).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/v1/transfers").param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].serialNo").value("ATR202608210001"))
            .andExpect(jsonPath("$.data[0].statusLabel").value("待确认"))
            .andExpect(jsonPath("$.data[0].sourceLabel").value("手动调拨"))
            .andExpect(jsonPath("$.data[0].applicantName").value("测试用户"))
            .andExpect(jsonPath("$.data[0].fromLocationName").value("A区"))
            .andExpect(jsonPath("$.data[0].toLocationName").value("B区"))
            .andExpect(jsonPath("$.data[0].toDepartment").value("品质部"))
            .andExpect(jsonPath("$.data[0].items[0].assetBarcode").value("SKSCDM-0001"))
            .andExpect(jsonPath("$.data[0].items[0].assetName").value("镀膜机"))
            .andExpect(jsonPath("$.data[0].items[0].assetSn").value("SN-001"));
    }

    // ---- getById ----

    @Test
    void getById_shouldReturnDetail() throws Exception {
        when(transferService.getById(1L)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/v1/transfers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.serialNo").value("ATR202608210001"))
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void getById_shouldReturn404WhenMissing() throws Exception {
        when(transferService.getById(9L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/transfers/9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    // ---- create ----

    @Test
    void create_shouldReturnCreatedOrderWithApplicantFromContext() throws Exception {
        loginUser();
        when(transferService.create(any(TransferApplyReq.class), eq(100L), eq("测试用户")))
                .thenReturn(sampleOrder());

        TransferApplyReq req = new TransferApplyReq();
        req.setAssetIds(List.of(1L, 2L));
        req.setToLocationId(20L);
        req.setToDepartment("品质部");
        req.setToUserId(300L);
        req.setToUserName("李四");
        req.setReason("部门搬迁");

        mockMvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.serialNo").value("ATR202608210001"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void create_shouldReturn401WhenNotAuthenticated() throws Exception {
        TransferApplyReq req = new TransferApplyReq();
        req.setAssetIds(List.of(1L));
        req.setToLocationId(20L);

        mockMvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));

        verify(transferService, never()).create(any(), any(), any());
    }

    @Test
    void create_shouldReturn400WhenAssetIdsEmpty() throws Exception {
        loginUser();
        TransferApplyReq req = new TransferApplyReq();
        req.setAssetIds(List.of());
        req.setToLocationId(20L);

        mockMvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(transferService, never()).create(any(), any(), any());
    }

    // ---- confirm / reject / cancel ----

    @Test
    void confirm_shouldCallServiceWithCurrentUser() throws Exception {
        loginUser();
        TransferOrder confirmed = sampleOrder();
        confirmed.setStatus("COMPLETED");
        when(transferService.confirm(1L, 100L, "测试用户")).thenReturn(confirmed);

        mockMvc.perform(post("/api/v1/transfers/1/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已完成"));

        verify(transferService, times(1)).confirm(1L, 100L, "测试用户");
    }

    @Test
    void confirm_shouldReturn409WhenAlreadyProcessed() throws Exception {
        loginUser();
        when(transferService.confirm(1L, 100L, "测试用户"))
                .thenThrow(new BusinessException(409, "调拨单已处理，不能重复操作"));

        mockMvc.perform(post("/api/v1/transfers/1/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void reject_shouldCallServiceWithReason() throws Exception {
        loginUser();
        TransferOrder rejected = sampleOrder();
        rejected.setStatus("REJECTED");
        rejected.setRejectReason("位置不符");
        when(transferService.reject(1L, "位置不符", 100L, "测试用户")).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/transfers/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"位置不符\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已拒绝"))
            .andExpect(jsonPath("$.data.rejectReason").value("位置不符"));

        verify(transferService, times(1)).reject(1L, "位置不符", 100L, "测试用户");
    }

    @Test
    void reject_shouldReturn400WhenReasonBlank() throws Exception {
        loginUser();

        mockMvc.perform(post("/api/v1/transfers/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(transferService, never()).reject(any(), any(), any(), any());
    }

    @Test
    void cancel_shouldCallServiceWithCurrentUser() throws Exception {
        loginUser();
        TransferOrder cancelled = sampleOrder();
        cancelled.setStatus("CANCELLED");
        when(transferService.cancel(1L, 100L)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/transfers/1/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已撤销"));

        verify(transferService, times(1)).cancel(1L, 100L);
    }

    @Test
    void cancel_shouldReturn403WhenNotApplicant() throws Exception {
        loginUser();
        when(transferService.cancel(1L, 100L))
                .thenThrow(new BusinessException(403, "仅发起人可撤销调拨单"));

        mockMvc.perform(post("/api/v1/transfers/1/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403));
    }
}
