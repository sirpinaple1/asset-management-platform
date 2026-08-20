package com.sk.asset.controller.basedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerReq;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.service.basedata.ManufacturerService;
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

class ManufacturerControllerTest {

    private final ManufacturerService manufacturerService = mock(ManufacturerService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ManufacturerControllerTest() throws Exception {
        ManufacturerController controller = new ManufacturerController();
        Field field = ManufacturerController.class.getDeclaredField("manufacturerService");
        field.setAccessible(true);
        field.set(controller, manufacturerService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200() throws Exception {
        // Given
        Manufacturer m = new Manufacturer();
        m.setId(1L);
        m.setName("厂商A");

        when(manufacturerService.list()).thenReturn(Arrays.asList(m));

        // When & Then
        mockMvc.perform(get("/api/v1/manufacturers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].name").value("厂商A"));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        // Given
        ManufacturerReq req = new ManufacturerReq();
        req.setName("新厂商");
        req.setContact("张三");

        Manufacturer saved = new Manufacturer();
        saved.setId(1L);
        saved.setName("新厂商");

        doAnswer(invocation -> {
            Manufacturer arg = invocation.getArgument(0);
            arg.setId(1L);
            return null;
        }).when(manufacturerService).save(any(Manufacturer.class));

        // When & Then
        mockMvc.perform(post("/api/v1/manufacturers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).save(any(Manufacturer.class));
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        // Given
        ManufacturerReq req = new ManufacturerReq();
        req.setName("更新厂商");

        Manufacturer existing = new Manufacturer();
        existing.setId(1L);
        existing.setName("旧厂商");

        when(manufacturerService.getById(1L)).thenReturn(existing);

        // When & Then
        mockMvc.perform(put("/api/v1/manufacturers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).updateById(any(Manufacturer.class));
    }

    @Test
    void delete_shouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/manufacturers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).deleteById(1L);
    }

    @Test
    void restore_shouldReturn200WhenRestored() throws Exception {
        // Given
        when(manufacturerService.restoreById(1L)).thenReturn(1);

        // When & Then
        mockMvc.perform(put("/api/v1/manufacturers/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).restoreById(1L);
    }

    @Test
    void restore_shouldReturn404WhenNotDeletedOrMissing() throws Exception {
        // Given
        when(manufacturerService.restoreById(1L)).thenReturn(0);

        // When & Then
        mockMvc.perform(put("/api/v1/manufacturers/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("厂商不存在或未被删除"));
    }
}
