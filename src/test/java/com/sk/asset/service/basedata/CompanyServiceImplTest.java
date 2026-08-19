package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.service.basedata.impl.CompanyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock
    private CompanyMapper companyMapper;

    @InjectMocks
    private CompanyServiceImpl companyService;

    @Test
    void list_shouldReturnAllCompanies() {
        // Given
        Company company1 = new Company();
        company1.setId(1L);
        company1.setCode("SK");
        company1.setName("森科五金");

        Company company2 = new Company();
        company2.setId(2L);
        company2.setCode("SF");
        company2.setName("森发塑料");

        when(companyMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(company1, company2));

        // When
        List<Company> result = companyService.list();

        // Then
        assertEquals(2, result.size());
        assertEquals("SK", result.get(0).getCode());
        verify(companyMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void getById_shouldReturnCompany() {
        // Given
        Company company = new Company();
        company.setId(1L);
        company.setCode("SK");

        when(companyMapper.selectById(1L)).thenReturn(company);

        // When
        Company result = companyService.getById(1L);

        // Then
        assertNotNull(result);
        assertEquals("SK", result.getCode());
        verify(companyMapper, times(1)).selectById(1L);
    }
}
