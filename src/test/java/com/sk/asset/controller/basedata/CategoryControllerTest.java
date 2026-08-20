package com.sk.asset.controller.basedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.category.CategoryReq;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.service.basedata.CategoryService;
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

class CategoryControllerTest {

    private final CategoryService categoryService = mock(CategoryService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    CategoryControllerTest() throws Exception {
        CategoryController controller = new CategoryController();
        Field field = CategoryController.class.getDeclaredField("categoryService");
        field.setAccessible(true);
        field.set(controller, categoryService);

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
    void create_shouldReturn200AndCallService() throws Exception {
        // Given
        CategoryReq req = new CategoryReq();
        req.setName("新分类");
        req.setCode("NEW");
        req.setSortOrder(10);

        doAnswer(invocation -> {
            Category arg = invocation.getArgument(0);
            arg.setId(1L);
            return null;
        }).when(categoryService).save(any(Category.class));

        // When & Then
        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(categoryService, times(1)).save(any(Category.class));
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        // Given
        CategoryReq req = new CategoryReq();
        req.setName("更新分类");

        Category existing = new Category();
        existing.setId(1L);
        existing.setName("旧分类");

        when(categoryService.getById(1L)).thenReturn(existing);

        // When & Then
        mockMvc.perform(put("/api/v1/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(categoryService, times(1)).updateById(any(Category.class));
    }

    @Test
    void delete_shouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(categoryService, times(1)).deleteById(1L);
    }
}
