package com.sk.asset.controller.basedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.model.AssetModelReq;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.service.basedata.AssetModelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssetModelControllerTest {

    private final AssetModelService assetModelService = mock(AssetModelService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AssetModelControllerTest() {
        AssetModelController controller = new AssetModelController(assetModelService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithRelationNames() throws Exception {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");
        m.setCategoryName("IT设备");
        m.setManufacturerName("苹果");

        when(assetModelService.list()).thenReturn(Arrays.asList(m));

        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("MacBook Pro"))
                .andExpect(jsonPath("$.data[0].categoryName").value("IT设备"))
                .andExpect(jsonPath("$.data[0].manufacturerName").value("苹果"));
    }

    @Test
    void list_shouldFilterByCategoryIdWhenProvided() throws Exception {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");
        m.setCategoryId(4L);

        when(assetModelService.listByCategoryId(4L)).thenReturn(List.of(m));

        mockMvc.perform(get("/api/v1/models?categoryId=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("MacBook Pro"));

        verify(assetModelService, times(1)).listByCategoryId(4L);
        verify(assetModelService, never()).list();
    }

    @Test
    void getById_shouldReturn200WhenExists() throws Exception {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");

        when(assetModelService.getById(1L)).thenReturn(m);

        mockMvc.perform(get("/api/v1/models/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("MacBook Pro"));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        when(assetModelService.getById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/models/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        AssetModelReq req = new AssetModelReq();
        req.setName("新型号");
        req.setCategoryId(1L);

        doAnswer(invocation -> {
            AssetModel arg = invocation.getArgument(0);
            arg.setId(1L);
            return null;
        }).when(assetModelService).save(any(AssetModel.class));

        mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assetModelService, times(1)).save(any(AssetModel.class));
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        AssetModelReq req = new AssetModelReq();
        req.setName("更新型号");

        AssetModel existing = new AssetModel();
        existing.setId(1L);
        existing.setName("旧型号");

        when(assetModelService.getById(1L)).thenReturn(existing);

        mockMvc.perform(put("/api/v1/models/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assetModelService, times(1)).updateById(any(AssetModel.class));
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        AssetModelReq req = new AssetModelReq();
        req.setName("更新型号");

        when(assetModelService.getById(99L)).thenReturn(null);

        mockMvc.perform(put("/api/v1/models/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/models/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assetModelService, times(1)).deleteById(1L);
    }
}
