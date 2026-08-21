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
}
