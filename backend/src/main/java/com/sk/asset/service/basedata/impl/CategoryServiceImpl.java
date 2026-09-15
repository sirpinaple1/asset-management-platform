package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.service.basedata.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资产分类服务实现。
 * 分类树强制两级（一级大类 + 二级子类），用户会在页面上自行维护分类，
 * 本服务兜住：名称必填、同级重名拒绝、父分类存在且为一级（两级上限）、
 * 防环（不得把自己设为父）、编码冲突友好 400、删除保护（有子分类/被资产引用拒绝）。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final AssetMapper assetMapper;

    @Override
    public List<Category> list() {
        return categoryMapper.selectList(
            new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId)
        );
    }

    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public void save(Category category) {
        validateName(category.getName());
        normalize(category);
        validateParent(category.getParentId());
        validateNameUnique(category.getName(), category.getParentId(), null);
        validateCodeUnique(category.getCode(), null);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            // uk_category_code 含逻辑删除行：预检通过但库唯一键拦截时仍给友好 400
            throw new BusinessException(400, "分类编码「" + category.getCode() + "」已被占用（可能被已删除分类占用）");
        }
        // code 由后端生成（前端契约不提交）：未指定时按主键生成，保证唯一索引下非空
        if (category.getCode() == null) {
            category.setCode("CAT" + category.getId());
            categoryMapper.updateById(category);
        }
    }

    @Override
    public void updateById(Category category) {
        Category existing = categoryMapper.selectById(category.getId());
        if (existing == null) {
            throw new BusinessException(404, "分类不存在（id=" + category.getId() + "）");
        }
        validateName(category.getName());
        normalize(category);
        if (category.getId().equals(category.getParentId())) {
            throw new BusinessException(400, "父分类不能是分类自身");
        }
        validateParent(category.getParentId());
        // 两级上限：自身已带子分类时只能作为一级，不能再挂到其他分类下
        if (category.getParentId() != null && hasChildren(category.getId())) {
            throw new BusinessException(400, "分类「" + existing.getName() + "」下存在子分类，不能再挂到其他分类下（最多两级）");
        }
        validateNameUnique(category.getName(), category.getParentId(), category.getId());
        // 前端契约不提交 code：请求 code 为空时保持库中原值，避免误清存量编码
        if (category.getCode() == null) {
            category.setCode(existing.getCode());
        }
        validateCodeUnique(category.getCode(), category.getId());
        try {
            // 全字段显式 set：允许清空 barcodePrefix/remark（updateById 会跳过 null 列）
            categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                    .eq(Category::getId, category.getId())
                    .set(Category::getName, category.getName())
                    .set(Category::getCode, category.getCode())
                    .set(Category::getBarcodePrefix, category.getBarcodePrefix())
                    .set(Category::getParentId, category.getParentId())
                    .set(Category::getSortOrder, category.getSortOrder())
                    .set(Category::getRemark, category.getRemark()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "分类编码「" + category.getCode() + "」已被占用（可能被已删除分类占用）");
        }
    }

    @Override
    public void deleteById(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分类不存在（id=" + id + "）");
        }
        if (hasChildren(id)) {
            throw new BusinessException(400, "分类「" + existing.getName() + "」下存在子分类，请先删除或移走子分类");
        }
        Long used = assetMapper.selectCount(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getCategoryId, id));
        if (used != null && used > 0) {
            throw new BusinessException(400, "分类「" + existing.getName() + "」已被 " + used + " 条资产引用，无法删除");
        }
        // 逻辑删除同时释放 code（uk_category_code 含已删行，不清会导致同编码分类无法重建）
        categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                .eq(Category::getId, id)
                .set(Category::getCode, null)
                .set(Category::getDeleted, 1));
    }

    /** 名称必填（服务层兜底，HTTP 路径已由 CategoryReq @NotBlank 前置校验） */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "分类名称不能为空");
        }
    }

    /** 文本 trim、空串归一为 null（前端空值不发送/发空串两种风格都兼容），sortOrder 兜底 0 */
    private void normalize(Category category) {
        category.setName(category.getName().trim());
        if (category.getCode() != null && category.getCode().isBlank()) {
            category.setCode(null);
        }
        if (category.getBarcodePrefix() != null && category.getBarcodePrefix().isBlank()) {
            category.setBarcodePrefix(null);
        }
        if (category.getRemark() != null && category.getRemark().isBlank()) {
            category.setRemark(null);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
    }

    /** 父分类必须存在且为一级（强制两级上限） */
    private void validateParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        Category parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "父分类不存在（id=" + parentId + "）");
        }
        if (parent.getParentId() != null) {
            throw new BusinessException(400, "父分类「" + parent.getName() + "」不是一级分类，分类最多两级");
        }
    }

    /** 同级（同父分类下）重名拒绝 */
    private void validateNameUnique(String name, Long parentId, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .eq(parentId != null, Category::getParentId, parentId)
                .isNull(parentId == null, Category::getParentId)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(400, "同级已存在同名分类「" + name + "」");
        }
    }

    /** code 冲突预检（活跃行；逻辑删除行的占用由 DuplicateKeyException 兜底转 400） */
    private void validateCodeUnique(String code, Long excludeId) {
        if (code == null) {
            return;
        }
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getCode, code)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(400, "分类编码「" + code + "」已存在");
        }
    }

    private boolean hasChildren(Long id) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        return count != null && count > 0;
    }
}
