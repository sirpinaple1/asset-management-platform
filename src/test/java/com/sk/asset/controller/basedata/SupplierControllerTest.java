package com.sk.asset.controller.basedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.supplier.SupplierReq;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.service.basedata.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupplierControllerTest {

    private final SupplierService supplierService = mock(SupplierService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    SupplierControllerTest() throws Exception {
        SupplierController controller = new SupplierController();
        Field field = SupplierController.class.getDeclaredField("supplierService");
        field.setAccessible(true);
        field.set(controller, supplierService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200() throws Exception {
        // Given
        Supplier s = new Supplier();
        s.setId(1L);
        s.setName("供应商A");

        when(supplierService.list()).thenReturn(Arrays.asList(s));

        // When & Then
        mockMvc.perform(get("/api/v1/suppliers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].name").value("供应商A"));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        // Given
        SupplierReq req = new SupplierReq();
        req.setName("新供应商");
        req.setContact("张三");

        Supplier saved = new Supplier();
        saved.setId(1L);
        saved.setName("新供应商");

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
    void update_shouldReturn200WhenExists() throws Exception {
        // Given
        SupplierReq req = new SupplierReq();
        req.setName("更新供应商");

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
    void delete_shouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/suppliers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(supplierService, times(1)).deleteById(1L);
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
