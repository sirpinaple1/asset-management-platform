package com.sk.asset.service.basedata;

import com.sk.asset.entity.basedata.Company;

import java.util.List;

/**
 * 公司主体服务（只读）
 */
public interface CompanyService {

    /**
     * 查询所有公司
     */
    List<Company> list();

    /**
     * 按 ID 查询公司
     */
    Company getById(Long id);
}
