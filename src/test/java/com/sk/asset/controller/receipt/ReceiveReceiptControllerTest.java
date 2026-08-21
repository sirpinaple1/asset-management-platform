package com.sk.asset.controller.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.receipt.ReceiptQuery;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.receipt.ReceiptType;
import com.sk.asset.service.receipt.ReceiveReceiptService;
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

class ReceiveReceiptControllerTest {

    private final ReceiveReceiptService receiptService = mock(ReceiveReceiptService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ReceiveReceiptControllerTest() {
        ReceiveReceiptController controller = new ReceiveReceiptController(receiptService);

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

    private ReceiveReceipt sampleReceipt() {
        ReceiveReceipt receipt = new ReceiveReceipt();
        receipt.setId(1L);
        receipt.setSerialNo("ARE202608210001");
        receipt.setType("RECEIVE");
        receipt.setStatus("PENDING");
        receipt.setApplicantUserId(100L);
        receipt.setApplicantName("测试用户");
        receipt.setDepartment("PMC部");
        receipt.setReason("产线使用");
        ReceiveReceiptItem item = new ReceiveReceiptItem();
        item.setId(10L);
        item.setReceiptId(1L);
        item.setAssetId(1L);
        item.setAssetBarcode("SKSCDM-0001");
        item.setAssetName("镀膜机");
        item.setAssetSn("SN-001");
        receipt.setItems(List.of(item));
        return receipt;
    }

    // ---- list ----

    @Test
    void list_shouldPassFiltersToService() throws Exception {
        when(receiptService.list(any())).thenReturn(List.of());

        ReceiptQuery expected = new ReceiptQuery();
        expected.setType(ReceiptType.BORROW);
        expected.setStatus(ReceiptStatus.PENDING);
        expected.setUserId(100L);
        expected.setDate(LocalDate.of(2026, 8, 21));

        mockMvc.perform(get("/api/v1/receipts")
                .param("type", "BORROW")
                .param("status", "PENDING")
                .param("userId", "100")
                .param("date", "2026-08-21"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(receiptService, times(1)).list(expected);
    }

    @Test
    void list_shouldReturn400WhenTypeInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/receipts").param("type", "BAD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(receiptService, never()).list(any());
    }

    @Test
    void list_shouldReturnReceiptsWithItems() throws Exception {
        when(receiptService.list(any())).thenReturn(List.of(sampleReceipt()));

        mockMvc.perform(get("/api/v1/receipts").param("type", "RECEIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].serialNo").value("ARE202608210001"))
            .andExpect(jsonPath("$.data[0].statusLabel").value("待审批"))
            .andExpect(jsonPath("$.data[0].typeLabel").value("领用"))
            .andExpect(jsonPath("$.data[0].applicantName").value("测试用户"))
            .andExpect(jsonPath("$.data[0].items[0].assetBarcode").value("SKSCDM-0001"))
            .andExpect(jsonPath("$.data[0].items[0].assetName").value("镀膜机"))
            .andExpect(jsonPath("$.data[0].items[0].assetSn").value("SN-001"));
    }

    // ---- getById ----

    @Test
    void getById_shouldReturnDetail() throws Exception {
        when(receiptService.getById(1L)).thenReturn(sampleReceipt());

        mockMvc.perform(get("/api/v1/receipts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.serialNo").value("ARE202608210001"))
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void getById_shouldReturn404WhenMissing() throws Exception {
        when(receiptService.getById(9L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/receipts/9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    // ---- create ----

    @Test
    void create_shouldReturnCreatedReceiptWithApplicantFromContext() throws Exception {
        loginUser();
        when(receiptService.create(any(ReceiptApplyReq.class), eq(100L), eq("测试用户")))
                .thenReturn(sampleReceipt());

        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType("RECEIVE");
        req.setAssetIds(List.of(1L, 2L));
        req.setLocationId(1L);
        req.setDepartment("PMC部");
        req.setReason("产线使用");

        mockMvc.perform(post("/api/v1/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.serialNo").value("ARE202608210001"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void create_shouldReturn401WhenNotAuthenticated() throws Exception {
        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType("RECEIVE");
        req.setAssetIds(List.of(1L));
        req.setLocationId(1L);
        req.setDepartment("PMC部");
        req.setReason("产线使用");

        mockMvc.perform(post("/api/v1/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));

        verify(receiptService, never()).create(any(), any(), any());
    }

    @Test
    void create_shouldReturn400WhenLocationMissing() throws Exception {
        // 领用区域必填：缺失时 400（审批通过后资产位置更新至此，盘点依据）
        loginUser();
        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType("RECEIVE");
        req.setAssetIds(List.of(1L));
        req.setDepartment("PMC部");
        req.setReason("产线使用");

        mockMvc.perform(post("/api/v1/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("领用区域不能为空"));

        verify(receiptService, never()).create(any(), any(), any());
    }

    @Test
    void create_shouldReturn400WhenAssetIdsEmpty() throws Exception {
        loginUser();
        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType("RECEIVE");
        req.setAssetIds(List.of());
        req.setLocationId(1L);
        req.setDepartment("PMC部");
        req.setReason("产线使用");

        mockMvc.perform(post("/api/v1/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(receiptService, never()).create(any(), any(), any());
    }

    @Test
    void create_shouldReturn400WhenReasonBlank() throws Exception {
        loginUser();
        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType("RECEIVE");
        req.setAssetIds(List.of(1L));
        req.setDepartment("PMC部");
        req.setReason(" ");

        mockMvc.perform(post("/api/v1/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(receiptService, never()).create(any(), any(), any());
    }

    // ---- approve / reject ----

    @Test
    void approve_shouldCallServiceWithCurrentUser() throws Exception {
        loginUser();
        ReceiveReceipt approved = sampleReceipt();
        approved.setStatus("APPROVED");
        when(receiptService.approve(1L, 100L, "测试用户")).thenReturn(approved);

        mockMvc.perform(post("/api/v1/receipts/1/approve"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已批准"));

        verify(receiptService, times(1)).approve(1L, 100L, "测试用户");
    }

    @Test
    void approve_shouldReturn409WhenAlreadyApproved() throws Exception {
        loginUser();
        when(receiptService.approve(1L, 100L, "测试用户"))
                .thenThrow(new BusinessException(409, "单据已审批，不能重复操作"));

        mockMvc.perform(post("/api/v1/receipts/1/approve"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void reject_shouldCallServiceWithReason() throws Exception {
        loginUser();
        ReceiveReceipt rejected = sampleReceipt();
        rejected.setStatus("REJECTED");
        when(receiptService.reject(1L, "不需要", 100L, "测试用户")).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/receipts/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"不需要\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.statusLabel").value("已拒绝"));

        verify(receiptService, times(1)).reject(1L, "不需要", 100L, "测试用户");
    }

    @Test
    void reject_shouldReturn400WhenReasonBlank() throws Exception {
        loginUser();

        mockMvc.perform(post("/api/v1/receipts/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(receiptService, never()).reject(any(), any(), any(), any());
    }
}
