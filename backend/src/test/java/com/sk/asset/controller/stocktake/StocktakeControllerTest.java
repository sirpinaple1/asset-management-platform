package com.sk.asset.controller.stocktake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.stocktake.StocktakeBarcodeScanReq;
import com.sk.asset.dto.stocktake.StocktakeCreateReq;
import com.sk.asset.dto.stocktake.StocktakeQuery;
import com.sk.asset.dto.stocktake.StocktakeReportResp;
import com.sk.asset.dto.stocktake.StocktakeScanReq;
import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.entity.stocktake.StocktakeItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.stocktake.StocktakeItemStatus;
import com.sk.asset.enums.stocktake.StocktakeStatus;
import com.sk.asset.service.stocktake.StocktakeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StocktakeControllerTest {

    private final StocktakeService stocktakeService = mock(StocktakeService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    StocktakeControllerTest() {
        StocktakeController controller = new StocktakeController(stocktakeService);

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

    private Stocktake sampleTask() {
        Stocktake task = new Stocktake();
        task.setId(1L);
        task.setName("2026年中盘点");
        task.setStatus(StocktakeStatus.IN_PROGRESS.name());
        task.setCreatorUserId(100L);
        task.setCreatorName("测试用户");
        StocktakeItem item = new StocktakeItem();
        item.setId(5L);
        item.setStocktakeId(1L);
        item.setAssetId(101L);
        item.setStatus(StocktakeItemStatus.PENDING.name());
        item.setExpectedLocationId(10L);
        task.setItems(List.of(item));
        return task;
    }

    // ---- create ----

    @Test
    void create_shouldPassBodyAndLoginUser() throws Exception {
        loginUser();
        when(stocktakeService.create(any(), eq(100L), eq("测试用户"))).thenReturn(sampleTask());

        StocktakeCreateReq req = new StocktakeCreateReq();
        req.setName("2026年中盘点");
        req.setLocationId(10L);

        mockMvc.perform(post("/api/v1/stocktakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("2026年中盘点"))
                .andExpect(jsonPath("$.data.statusLabel").value("进行中"))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("待盘"));

        ArgumentCaptor<StocktakeCreateReq> captor = ArgumentCaptor.forClass(StocktakeCreateReq.class);
        verify(stocktakeService).create(captor.capture(), eq(100L), eq("测试用户"));
        assertEquals("2026年中盘点", captor.getValue().getName());
    }

    // ---- list ----

    @Test
    void list_shouldPassFiltersToService() throws Exception {
        when(stocktakeService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stocktakes")
                        .param("status", "IN_PROGRESS")
                        .param("userId", "100")
                        .param("date", "2026-08-24"))
                .andExpect(status().isOk());

        ArgumentCaptor<StocktakeQuery> captor = ArgumentCaptor.forClass(StocktakeQuery.class);
        verify(stocktakeService).list(captor.capture());
        assertEquals(StocktakeStatus.IN_PROGRESS, captor.getValue().getStatus());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals(LocalDate.of(2026, 8, 24), captor.getValue().getDate());
    }

    // ---- getById ----

    @Test
    void getById_shouldReturnDetail() throws Exception {
        when(stocktakeService.getById(1L)).thenReturn(sampleTask());

        mockMvc.perform(get("/api/v1/stocktakes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.items[0].assetId").value(101));
    }

    @Test
    void getById_shouldReturn404WhenMissing() throws Exception {
        when(stocktakeService.getById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/stocktakes/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---- start / cancel / complete ----

    @Test
    void start_shouldCallServiceWithLoginUser() throws Exception {
        loginUser();
        when(stocktakeService.start(1L, 100L)).thenReturn(sampleTask());

        mockMvc.perform(post("/api/v1/stocktakes/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        verify(stocktakeService).start(1L, 100L);
    }

    @Test
    void cancel_shouldCallServiceWithLoginUser() throws Exception {
        loginUser();
        when(stocktakeService.cancel(1L, 100L)).thenReturn(sampleTask());

        mockMvc.perform(post("/api/v1/stocktakes/1/cancel"))
                .andExpect(status().isOk());

        verify(stocktakeService).cancel(1L, 100L);
    }

    @Test
    void complete_shouldCallServiceWithLoginUser() throws Exception {
        loginUser();
        when(stocktakeService.complete(1L, 100L)).thenReturn(sampleTask());

        mockMvc.perform(post("/api/v1/stocktakes/1/complete"))
                .andExpect(status().isOk());

        verify(stocktakeService).complete(1L, 100L);
    }

    // ---- items ----

    @Test
    void listItems_shouldPassStatusFilter() throws Exception {
        when(stocktakeService.listItems(1L, StocktakeItemStatus.PENDING))
                .thenReturn(List.of(sampleTask().getItems().get(0)));

        mockMvc.perform(get("/api/v1/stocktakes/1/items")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        verify(stocktakeService).listItems(1L, StocktakeItemStatus.PENDING);
    }

    // ---- scan ----

    @Test
    void scanItem_shouldPassBodyAndLoginUser() throws Exception {
        loginUser();
        StocktakeItem item = sampleTask().getItems().get(0);
        item.setStatus(StocktakeItemStatus.MATCHED.name());
        when(stocktakeService.scanItem(eq(1L), eq(5L), any(), eq(100L))).thenReturn(item);

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(10L);

        mockMvc.perform(post("/api/v1/stocktakes/1/items/5/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusLabel").value("账实相符"));

        ArgumentCaptor<StocktakeScanReq> captor = ArgumentCaptor.forClass(StocktakeScanReq.class);
        verify(stocktakeService).scanItem(eq(1L), eq(5L), captor.capture(), eq(100L));
        assertEquals(10L, captor.getValue().getActualLocationId());
    }

    @Test
    void scanItem_shouldReturn409WhenAlreadyScanned() throws Exception {
        loginUser();
        when(stocktakeService.scanItem(eq(1L), eq(5L), any(), eq(100L)))
                .thenThrow(new BusinessException(409, "该明细已盘点，不能重复盘点"));

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(10L);

        mockMvc.perform(post("/api/v1/stocktakes/1/items/5/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void scanByBarcode_shouldPassBodyAndLoginUser() throws Exception {
        loginUser();
        StocktakeItem extra = new StocktakeItem();
        extra.setId(6L);
        extra.setStocktakeId(1L);
        extra.setAssetId(105L);
        extra.setStatus(StocktakeItemStatus.EXTRA.name());
        when(stocktakeService.scanByBarcode(eq(1L), any(), eq(100L))).thenReturn(extra);

        StocktakeBarcodeScanReq req = new StocktakeBarcodeScanReq();
        req.setBarcode("SKSCDM-0005");
        req.setActualLocationId(10L);

        mockMvc.perform(post("/api/v1/stocktakes/1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXTRA"))
                .andExpect(jsonPath("$.data.statusLabel").value("盘盈"));

        ArgumentCaptor<StocktakeBarcodeScanReq> captor = ArgumentCaptor.forClass(StocktakeBarcodeScanReq.class);
        verify(stocktakeService).scanByBarcode(eq(1L), captor.capture(), eq(100L));
        assertEquals("SKSCDM-0005", captor.getValue().getBarcode());
    }

    // ---- report ----

    @Test
    void report_shouldReturnAggregation() throws Exception {
        StocktakeReportResp resp = new StocktakeReportResp();
        resp.setStocktakeId(1L);
        resp.setName("2026年中盘点");
        resp.setStatus(StocktakeStatus.COMPLETED.name());
        resp.setTotalCount(5L);
        resp.setPendingCount(0L);
        resp.setMatchedCount(3L);
        resp.setMismatchCount(1L);
        resp.setNotFoundCount(1L);
        resp.setExtraCount(0L);
        when(stocktakeService.report(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/stocktakes/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.matchedCount").value(3))
                .andExpect(jsonPath("$.data.mismatchCount").value(1))
                .andExpect(jsonPath("$.data.notFoundCount").value(1));
    }

    // ---- transfer ----

    @Test
    void createTransfers_shouldReturnCreatedOrders() throws Exception {
        loginUser();
        TransferOrder order = new TransferOrder();
        order.setId(11L);
        order.setSerialNo("ATR202608240001");
        order.setStatus("PENDING");
        order.setSource("INVENTORY_TRIGGERED");
        order.setStocktakeId(1L);
        order.setApplicantUserId(100L);
        TransferOrderItem item = new TransferOrderItem();
        item.setId(20L);
        item.setOrderId(11L);
        item.setAssetId(101L);
        order.setItems(List.of(item));
        when(stocktakeService.createTransfers(1L, 100L, "测试用户")).thenReturn(List.of(order));

        mockMvc.perform(post("/api/v1/stocktakes/1/transfer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].source").value("INVENTORY_TRIGGERED"))
                .andExpect(jsonPath("$.data[0].stocktakeId").value(1))
                .andExpect(jsonPath("$.data[0].sourceLabel").value("盘点触发"));

        verify(stocktakeService).createTransfers(1L, 100L, "测试用户");
    }
}
