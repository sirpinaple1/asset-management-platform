package com.sk.asset.service.basedata;

import com.sk.asset.entity.basedata.Category;

import java.util.List;

/**
 * 资产分类服务
 */
public interface CategoryService {

    /**
     * 查询所有分类（扁平列表，含 parentId）
     */
    List<Category> list();

    /**
     * 按 ID 查询分类
     */
    Category getById(Long id);

    /**
     * 新增分类
     */
    void save(Category category);

    /**
     * 更新分类
     */
    void updateById(Category category);

    /**
     * 删除分类
     */
    void deleteById(Long id);
}
