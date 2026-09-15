package com.sk.asset.controller.basedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.category.CategoryReq;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.service.basedata.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoryControllerTest {

    private final CategoryService categoryService = mock(CategoryService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    CategoryControllerTest() {
        CategoryController controller = new CategoryController(categoryService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithFlatList() throws Exception {
        // Given
        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("镀膜设备");
        c1.setParentId(null);

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("PVD镀膜");
        c2.setParentId(1L);

        when(categoryService.list()).thenReturn(Arrays.asList(c1, c2));

        // When & Then
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].name").value("镀膜设备"))
            .andExpect(jsonPath("$.data[0].parentId").isEmpty())
            .andExpect(jsonPath("$.data[1].parentId").value(1));
    }

    @Test
    void create_shouldReturnSavedCategory() throws Exception {
        CategoryReq req = new CategoryReq();
        req.setName("清洗设备");
        req.setParentId(13L);
        req.setBarcodePrefix("SKSCQX");

        Category saved = new Category();
        saved.setId(15L);
        saved.setName("清洗设备");
        saved.setParentId(13L);
        saved.setBarcodePrefix("SKSCQX");

        // save 落库后回查（Controller create → service.save → getById 回填 id）
        doAnswer(invocation -> {
            Category arg = invocation.getArgument(0);
            arg.setId(15L);
            return null;
        }).when(categoryService).save(any(Category.class));
        when(categoryService.getById(15L)).thenReturn(saved);

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(15))
            .andExpect(jsonPath("$.data.name").value("清洗设备"))
            .andExpect(jsonPath("$.data.barcodePrefix").value("SKSCQX"));
    }

    @Test
    void create_shouldMapBusiness400ToFriendlyResult() throws Exception {
        CategoryReq req = new CategoryReq();
        req.setName("清洗设备");

        doThrow(new BusinessException(400, "同级已存在同名分类「清洗设备」"))
                .when(categoryService).save(any(Category.class));

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("同级已存在同名分类「清洗设备」"));
    }

    @Test
    void create_shouldRejectBlankName() throws Exception {
        CategoryReq req = new CategoryReq();
        req.setName(" ");

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void update_shouldReturnUpdatedCategory() throws Exception {
        CategoryReq req = new CategoryReq();
        req.setName("清洗设备");
        req.setParentId(13L);

        Category existing = new Category();
        existing.setId(15L);
        existing.setName("清洗设备");

        Category updated = new Category();
        updated.setId(15L);
        updated.setName("清洗设备");
        updated.setParentId(13L);

        when(categoryService.getById(15L)).thenReturn(existing, updated);
        // updateById 为 void，mock 默认空实现，无需打桩

        mockMvc.perform(put("/api/v1/categories/15")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(15))
            .andExpect(jsonPath("$.data.parentId").value(13));
    }

    @Test
    void update_shouldReturn404WhenNotExists() throws Exception {
        when(categoryService.getById(99L)).thenReturn(null);

        CategoryReq req = new CategoryReq();
        req.setName("清洗设备");

        mockMvc.perform(put("/api/v1/categories/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(categoryService, times(1)).deleteById(15L);
    }

    @Test
    void delete_shouldMapBusiness400ToFriendlyResult() throws Exception {
        doThrow(new BusinessException(400, "分类「镀膜设备」已被 10 条资产引用，无法删除"))
                .when(categoryService).deleteById(1L);

        mockMvc.perform(delete("/api/v1/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("分类「镀膜设备」已被 10 条资产引用，无法删除"));
    }
}
