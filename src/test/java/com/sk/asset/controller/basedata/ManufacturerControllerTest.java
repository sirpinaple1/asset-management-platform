package com.sk.asset.controller.basedata;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerReq;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.service.basedata.ManufacturerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManufacturerControllerTest {

    private final ManufacturerService manufacturerService = mock(ManufacturerService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ManufacturerControllerTest() {
        ManufacturerController controller = new ManufacturerController(manufacturerService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_shouldReturn200WithPagedData() throws Exception {
        // Given
        Manufacturer m = new Manufacturer();
        m.setId(1L);
        m.setName("厂商A");
        m.setStatus(1);

        Page<Manufacturer> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList(m));
        pageResult.setTotal(1);

        when(manufacturerService.page(eq(1L), eq(20L), isNull(), isNull())).thenReturn(pageResult);

        // When & Then
        mockMvc.perform(get("/api/v1/manufacturers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records[0].name").value("厂商A"))
            .andExpect(jsonPath("$.data.records[0].status").value(1))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void page_shouldPassFilterParamsToService() throws Exception {
        // Given
        Page<Manufacturer> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList());
        pageResult.setTotal(0);

        when(manufacturerService.page(1L, 20L, "深圳", 1)).thenReturn(pageResult);

        // When & Then
        mockMvc.perform(get("/api/v1/manufacturers")
                .param("keyword", "深圳")
                .param("status", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).page(1L, 20L, "深圳", 1);
    }

    @Test
    void export_shouldReturnExcelContentType() throws Exception {
        // Given
        Manufacturer m = new Manufacturer();
        m.setId(1L);
        m.setName("厂商A");
        m.setStatus(1);

        when(manufacturerService.listBy(isNull(), isNull())).thenReturn(Arrays.asList(m));

        // When & Then
        mockMvc.perform(get("/api/v1/manufacturers/export"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        verify(manufacturerService, times(1)).listBy(isNull(), isNull());
    }

    @Test
    void getById_shouldReturn200WhenExists() throws Exception {
        // Given
        Manufacturer m = new Manufacturer();
        m.setId(1L);
        m.setName("厂商A");

        when(manufacturerService.getById(1L)).thenReturn(m);

        // When & Then
        mockMvc.perform(get("/api/v1/manufacturers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("厂商A"));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        when(manufacturerService.getById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/manufacturers/99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("厂商不存在"));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        // Given
        ManufacturerReq req = new ManufacturerReq();
        req.setName("新厂商");
        req.setContact("张三");
        req.setStatus(1);

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
    void create_shouldReturnFailWhenStatusMissing() throws Exception {
        // Given：status 缺失触发 @NotNull 参数校验失败，全局异常处理器映射为 code=400
        ManufacturerReq req = new ManufacturerReq();
        req.setName("新厂商");

        // When & Then
        mockMvc.perform(post("/api/v1/manufacturers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("状态不能为空"));

        verify(manufacturerService, never()).save(any());
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        // Given
        ManufacturerReq req = new ManufacturerReq();
        req.setName("更新厂商");
        req.setStatus(1);

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
    void update_shouldReturn404WhenNotFound() throws Exception {
        ManufacturerReq req = new ManufacturerReq();
        req.setName("更新厂商");
        req.setStatus(1);

        when(manufacturerService.getById(99L)).thenReturn(null);

        mockMvc.perform(put("/api/v1/manufacturers/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/manufacturers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).deleteById(1L);
    }

    @Test
    void delete_shouldReturn409WhenReferencedByModel() throws Exception {
        doThrow(new BusinessException(409, "厂商「厂商A」已被资产型号引用，无法删除"))
            .when(manufacturerService).deleteById(1L);

        mockMvc.perform(delete("/api/v1/manufacturers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("厂商「厂商A」已被资产型号引用，无法删除"));
    }

    @Test
    void deleteBatch_shouldReturn200AndCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/manufacturers")
                .param("ids", "1,2,3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).deleteByIds(Arrays.asList(1L, 2L, 3L));
    }

    @Test
    void deleteBatch_shouldReturn409WhenAnyReferenced() throws Exception {
        doThrow(new BusinessException(409, "厂商「厂商A」已被资产型号引用，无法删除"))
            .when(manufacturerService).deleteByIds(any());

        mockMvc.perform(delete("/api/v1/manufacturers")
                .param("ids", "1,2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void restore_shouldReturn200WhenRestored() throws Exception {
        when(manufacturerService.restoreById(1L)).thenReturn(1);

        mockMvc.perform(put("/api/v1/manufacturers/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(manufacturerService, times(1)).restoreById(1L);
    }

    @Test
    void restore_shouldReturn404WhenNotDeletedOrMissing() throws Exception {
        when(manufacturerService.restoreById(1L)).thenReturn(0);

        mockMvc.perform(put("/api/v1/manufacturers/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("厂商不存在或未被删除"));
    }
}
