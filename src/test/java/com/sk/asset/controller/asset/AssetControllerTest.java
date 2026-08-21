package com.sk.asset.controller.asset;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.asset.AssetQuery;
import com.sk.asset.dto.asset.AssetReq;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.service.asset.AssetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssetControllerTest {

    private final AssetService assetService = mock(AssetService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AssetControllerTest() {
        AssetController controller = new AssetController(assetService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 模拟 TokenAuthFilter 已注入的认证上下文（userId=100） */
    private void loginUser() {
        AuthUserDto user = new AuthUserDto();
        user.setUserId(100);
        UserContext.set(new AuthContext(user, null));
    }

    private Asset sampleAsset() {
        Asset asset = new Asset();
        asset.setId(1L);
        asset.setBarcode("SKSCDM-0001");
        asset.setName("镀膜机");
        asset.setStatus(AssetStatus.IDLE.name());
        asset.setCategoryName("镀膜设备");
        return asset;
    }

    @Test
    void page_shouldReturn200WithPagedData() throws Exception {
        Page<Asset> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(sampleAsset()));
        pageResult.setTotal(1);

        when(assetService.page(eq(1L), eq(20L), any(AssetQuery.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/assets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records[0].barcode").value("SKSCDM-0001"))
            .andExpect(jsonPath("$.data.records[0].status").value("IDLE"))
            .andExpect(jsonPath("$.data.records[0].statusLabel").value("闲置"))
            .andExpect(jsonPath("$.data.records[0].categoryName").value("镀膜设备"))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void page_shouldPassFiltersToService() throws Exception {
        Page<Asset> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of());
        pageResult.setTotal(0);

        AssetQuery expected = new AssetQuery();
        expected.setStatus(AssetStatus.IN_USE);
        expected.setCategoryId(1L);
        expected.setLocationId(2L);
        expected.setCompanyId(3L);
        expected.setUserId(4L);
        expected.setAdminUserId(5L);
        expected.setKeyword("SKSCDM");

        when(assetService.page(eq(1L), eq(20L), eq(expected))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/assets")
                .param("status", "IN_USE")
                .param("categoryId", "1")
                .param("locationId", "2")
                .param("companyId", "3")
                .param("userId", "4")
                .param("adminUserId", "5")
                .param("keyword", "SKSCDM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(assetService, times(1)).page(1L, 20L, expected);
    }

    @Test
    void page_shouldReturn400WhenStatusInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                .param("status", "BAD_STATUS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));

        verify(assetService, never()).page(anyLong(), anyLong(), any());
    }

    @Test
    void export_shouldReturnExcelContentType() throws Exception {
        when(assetService.listBy(any(AssetQuery.class))).thenReturn(List.of(sampleAsset()));

        mockMvc.perform(get("/api/v1/assets/export"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        verify(assetService, times(1)).listBy(any(AssetQuery.class));
    }

    @Test
    void getById_shouldReturn200WhenExists() throws Exception {
        when(assetService.getById(1L)).thenReturn(sampleAsset());

        mockMvc.perform(get("/api/v1/assets/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("镀膜机"))
            .andExpect(jsonPath("$.data.statusLabel").value("闲置"));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        when(assetService.getById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/assets/99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("资产不存在"));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        loginUser();
        AssetReq req = new AssetReq();
        req.setBarcode("SKSCDM-0001");
        req.setName("镀膜机");

        doAnswer(invocation -> {
            ((Asset) invocation.getArgument(0)).setId(1L);
            return null;
        }).when(assetService).save(any(Asset.class), eq(100L));
        when(assetService.getById(1L)).thenReturn(sampleAsset());

        mockMvc.perform(post("/api/v1/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("IDLE"));

        verify(assetService, times(1)).save(any(Asset.class), eq(100L));
    }

    @Test
    void create_shouldReturn401WhenNotAuthenticated() throws Exception {
        // 未登录（UserContext 空）→ UserContext.require() 抛 401
        AssetReq req = new AssetReq();
        req.setBarcode("SKSCDM-0001");
        req.setName("镀膜机");

        mockMvc.perform(post("/api/v1/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));

        verify(assetService, never()).save(any(), any());
    }

    @Test
    void create_shouldReturn400WhenBarcodeMissing() throws Exception {
        AssetReq req = new AssetReq();
        req.setName("镀膜机");

        mockMvc.perform(post("/api/v1/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("资产编码不能为空"));

        verify(assetService, never()).save(any(), any());
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        AssetReq req = new AssetReq();
        req.setBarcode("SKSCDM-0001");
        req.setName("镀膜机-改名");

        when(assetService.getById(1L)).thenReturn(sampleAsset());

        mockMvc.perform(put("/api/v1/assets/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(assetService, times(1)).updateById(any(Asset.class));
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        AssetReq req = new AssetReq();
        req.setBarcode("SKSCDM-0001");
        req.setName("镀膜机");

        when(assetService.getById(99L)).thenReturn(null);

        mockMvc.perform(put("/api/v1/assets/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void discard_shouldReturn200AndPassOperator() throws Exception {
        loginUser();

        mockMvc.perform(post("/api/v1/assets/1/discard")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"设备老化\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(assetService, times(1)).discard(1L, "设备老化", 100L);
    }

    @Test
    void discard_shouldReturn409WhenIllegalTransition() throws Exception {
        loginUser();
        doThrow(new BusinessException(409, "资产状态不允许从「报废」变更为「闲置」"))
            .when(assetService).discard(eq(1L), isNull(), eq(100L));

        mockMvc.perform(post("/api/v1/assets/1/discard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("资产状态不允许从「报废」变更为「闲置」"));
    }

    @Test
    void logs_shouldReturn200WithLogList() throws Exception {
        AssetLog log = new AssetLog();
        log.setId(1L);
        log.setAssetId(1L);
        log.setOperationType("新增");
        log.setContent("新增资产「镀膜机」（编码 SKSCDM-0001）");
        log.setOperatorUserId(100L);

        when(assetService.listLogs(1L)).thenReturn(Arrays.asList(log));

        mockMvc.perform(get("/api/v1/assets/1/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].operationType").value("新增"))
            .andExpect(jsonPath("$.data[0].content").value("新增资产「镀膜机」（编码 SKSCDM-0001）"));
    }
}
