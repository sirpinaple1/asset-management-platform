package com.sk.asset.controller.basedata;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.supplier.SupplierReq;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.service.basedata.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupplierControllerTest {

    private final SupplierService supplierService = mock(SupplierService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    SupplierControllerTest() {
        SupplierController controller = new SupplierController(supplierService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_shouldReturn200WithPagedData() throws Exception {
        // Given
        Supplier s = new Supplier();
        s.setId(1L);
        s.setName("供应商A");
        s.setStatus(1);

        Page<Supplier> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList(s));
        pageResult.setTotal(1);

        when(supplierService.page(eq(1L), eq(20L), isNull(), isNull())).thenReturn(pageResult);

        // When & Then
        mockMvc.perform(get("/api/v1/suppliers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records[0].name").value("供应商A"))
            .andExpect(jsonPath("$.data.records[0].status").value(1))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void page_shouldPassFilterParamsToService() throws Exception {
        // Given
        Page<Supplier> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList());
        pageResult.setTotal(0);

        when(supplierService.page(1L, 20L, "五金", 0)).thenReturn(pageResult);

        // When & Then
        mockMvc.perform(get("/api/v1/suppliers")
                .param("keyword", "五金")
                .param("status", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).page(1L, 20L, "五金", 0);
    }

    @Test
    void export_shouldReturnExcelContentType() throws Exception {
        // Given
        Supplier s = new Supplier();
        s.setId(1L);
        s.setName("供应商A");
        s.setStatus(1);

        when(supplierService.listBy(isNull(), isNull())).thenReturn(Arrays.asList(s));

        // When & Then
        mockMvc.perform(get("/api/v1/suppliers/export"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        verify(supplierService, times(1)).listBy(isNull(), isNull());
    }

    @Test
    void getById_shouldReturn200WhenExists() throws Exception {
        // Given
        Supplier s = new Supplier();
        s.setId(1L);
        s.setName("供应商A");

        when(supplierService.getById(1L)).thenReturn(s);

        // When & Then
        mockMvc.perform(get("/api/v1/suppliers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("供应商A"));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        when(supplierService.getById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/suppliers/99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("供应商不存在"));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        // Given
        SupplierReq req = new SupplierReq();
        req.setName("新供应商");
        req.setContact("张三");
        req.setStatus(1);

        doAnswer(invocation -> {
            Supplier arg = invocation.getArgument(0);
            arg.setId(1L);
            return null;
        }).when(supplierService).save(any(Supplier.class));

        // When & Then
        mockMvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).save(any(Supplier.class));
    }

    @Test
    void create_shouldReturn400WhenStatusMissing() throws Exception {
        // Given：status 缺失触发 @NotNull 参数校验失败，全局异常处理器映射为 code=400
        SupplierReq req = new SupplierReq();
        req.setName("新供应商");

        // When & Then
        mockMvc.perform(post("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("状态不能为空"));

        verify(supplierService, never()).save(any());
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        // Given
        SupplierReq req = new SupplierReq();
        req.setName("更新供应商");
        req.setStatus(1);

        Supplier existing = new Supplier();
        existing.setId(1L);
        existing.setName("旧供应商");

        when(supplierService.getById(1L)).thenReturn(existing);

        // When & Then
        mockMvc.perform(put("/api/v1/suppliers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).updateById(any(Supplier.class));
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        SupplierReq req = new SupplierReq();
        req.setName("更新供应商");
        req.setStatus(1);

        when(supplierService.getById(99L)).thenReturn(null);

        mockMvc.perform(put("/api/v1/suppliers/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/suppliers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).deleteById(1L);
    }

    @Test
    void delete_shouldReturn409WhenReferencedByAsset() throws Exception {
        doThrow(new BusinessException(409, "供应商「供应商A」已被资产引用，无法删除"))
            .when(supplierService).deleteById(1L);

        mockMvc.perform(delete("/api/v1/suppliers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("供应商「供应商A」已被资产引用，无法删除"));
    }

    @Test
    void deleteBatch_shouldReturn200AndCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/suppliers")
                .param("ids", "1,2,3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).deleteByIds(Arrays.asList(1L, 2L, 3L));
    }

    @Test
    void deleteBatch_shouldReturn409WhenAnyReferenced() throws Exception {
        doThrow(new BusinessException(409, "供应商「供应商A」已被资产引用，无法删除"))
            .when(supplierService).deleteByIds(any());

        mockMvc.perform(delete("/api/v1/suppliers")
                .param("ids", "1,2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void restore_shouldReturn200WhenRestored() throws Exception {
        // Given
        when(supplierService.restoreById(1L)).thenReturn(1);

        // When & Then
        mockMvc.perform(put("/api/v1/suppliers/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).restoreById(1L);
    }

    @Test
    void restore_shouldReturn404WhenNotDeletedOrMissing() throws Exception {
        // Given
        when(supplierService.restoreById(1L)).thenReturn(0);

        // When & Then
        mockMvc.perform(put("/api/v1/suppliers/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("供应商不存在或未被删除"));
    }
}
