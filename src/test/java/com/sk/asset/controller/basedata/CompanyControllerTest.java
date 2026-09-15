package com.sk.asset.controller.basedata;

import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.service.basedata.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyControllerTest {

    private final CompanyService companyService = mock(CompanyService.class);
    private final MockMvc mockMvc;

    CompanyControllerTest() {
        CompanyController controller = new CompanyController(companyService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200() throws Exception {
        // Given
        Company company = new Company();
        company.setId(1L);
        company.setCode("SK");
        company.setName("示例科技五金");

        when(companyService.list()).thenReturn(Arrays.asList(company));

        // When & Then
        mockMvc.perform(get("/api/v1/companies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].code").value("SK"));

        verify(companyService, times(1)).list();
    }

    @Test
    void getById_shouldReturn200WhenExists() throws Exception {
        // Given
        Company company = new Company();
        company.setId(1L);
        company.setCode("SK");

        when(companyService.getById(1L)).thenReturn(company);

        // When & Then
        mockMvc.perform(get("/api/v1/companies/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.code").value("SK"));
    }

    @Test
    void getById_shouldReturn404WhenNotExists() throws Exception {
        // Given
        when(companyService.getById(999L)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/companies/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }
}
