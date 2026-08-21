package com.sk.asset.controller.receipt;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.receipt.AllocationQuery;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.service.receipt.AllocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AllocationControllerTest {

    private final AllocationService allocationService = mock(AllocationService.class);
    private final MockMvc mockMvc;

    AllocationControllerTest() {
        AllocationController controller = new AllocationController(allocationService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void loginUser() {
        AuthUserDto user = new AuthUserDto();
        user.setUserId(100);
        UserContext.set(new AuthContext(user, null));
    }

    private AssetAllocation sampleAllocation() {
        AssetAllocation allocation = new AssetAllocation();
        allocation.setId(12L);
        allocation.setAssetId(1L);
        allocation.setUserId(100L);
        allocation.setUserName("张三");
        allocation.setType("RECEIVE");
        allocation.setDepartment("PMC部");
        allocation.setAssetBarcode("SKSCDM-0001");
        allocation.setAssetName("镀膜机");
        allocation.setAssetSn("SN-001");
        return allocation;
    }

    @Test
    void list_shouldPassFiltersToService() throws Exception {
        when(allocationService.list(any())).thenReturn(List.of());

        AllocationQuery expected = new AllocationQuery();
        expected.setAssetId(1L);
        expected.setUserId(100L);
        expected.setType(com.sk.asset.enums.receipt.ReceiptType.BORROW);
        expected.setActive(true);

        mockMvc.perform(get("/api/v1/allocations")
                .param("assetId", "1")
                .param("userId", "100")
                .param("type", "BORROW")
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(allocationService, times(1)).list(expected);
    }

    @Test
    void list_shouldReturnAllocationsWithAssetNames() throws Exception {
        when(allocationService.list(any())).thenReturn(List.of(sampleAllocation()));

        mockMvc.perform(get("/api/v1/allocations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].assetBarcode").value("SKSCDM-0001"))
            .andExpect(jsonPath("$.data[0].userName").value("张三"))
            .andExpect(jsonPath("$.data[0].typeLabel").value("领用"))
            .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void return_shouldCallServiceWithNoteAndOperator() throws Exception {
        loginUser();

        mockMvc.perform(post("/api/v1/allocations/12/return")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"退库\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(allocationService, times(1)).returnAllocation(12L, "退库", 100L);
    }

    @Test
    void return_shouldWorkWithoutBody() throws Exception {
        loginUser();

        mockMvc.perform(post("/api/v1/allocations/12/return"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(allocationService, times(1)).returnAllocation(12L, null, 100L);
    }

    @Test
    void return_shouldReturn409WhenAlreadyReturned() throws Exception {
        loginUser();
        doThrow(new BusinessException(409, "该资产已归还"))
                .when(allocationService).returnAllocation(12L, null, 100L);

        mockMvc.perform(post("/api/v1/allocations/12/return"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }
}
