package com.company.prototype.api.catalog;

import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.TagEntity;
import com.company.prototype.persistence.prototype.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public CatalogService(
        CategoryRepository categoryRepository,
        TagRepository tagRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    public record CategoryDto(String code, String name, int sortNo) {}
    public record TagDto(String name, String color) {}

    public record SaveCategoryRequest(String code, String name, Integer sortNo) {}
    public record SaveTagRequest(String name, String color) {}

    public List<CategoryDto> listCategories() {
        return categoryRepository.findAllByOrderBySortNoAsc().stream()
            .map(c -> new CategoryDto(c.getCode(), c.getName(), c.getSortNo()))
            .toList();
    }

    @Transactional
    public CategoryDto createCategory(SaveCategoryRequest req) {
        if (categoryRepository.existsByCode(req.code())) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "分类编码已存在");
        }
        CategoryEntity entity = new CategoryEntity();
        entity.setCode(req.code());
        entity.setName(req.name());
        entity.setSortNo(req.sortNo() != null ? req.sortNo() : 0);
        entity = categoryRepository.save(entity);
        return new CategoryDto(entity.getCode(), entity.getName(), entity.getSortNo());
    }

    @Transactional
    public CategoryDto updateCategory(String code, SaveCategoryRequest req) {
        CategoryEntity entity = categoryRepository.findByCode(code)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "分类不存在"));
        entity.setName(req.name());
        if (req.sortNo() != null) {
            entity.setSortNo(req.sortNo());
        }
        entity = categoryRepository.save(entity);
        return new CategoryDto(entity.getCode(), entity.getName(), entity.getSortNo());
    }

    @Transactional
    public void deleteCategory(String code) {
        CategoryEntity entity = categoryRepository.findByCode(code)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "分类不存在"));
        categoryRepository.delete(entity);
    }

    public List<TagDto> listTags() {
        return tagRepository.findAll().stream()
            .map(t -> new TagDto(t.getName(), t.getColor()))
            .toList();
    }

    @Transactional
    public TagDto createTag(SaveTagRequest req) {
        if (tagRepository.existsByName(req.name())) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "标签名称已存在");
        }
        TagEntity entity = new TagEntity();
        entity.setName(req.name());
        entity.setColor(req.color() != null ? req.color() : "#1677ff");
        entity = tagRepository.save(entity);
        return new TagDto(entity.getName(), entity.getColor());
    }

    @Transactional
    public TagDto updateTag(String name, SaveTagRequest req) {
        TagEntity entity = tagRepository.findByName(name)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "标签不存在"));
        if (req.color() != null) {
            entity.setColor(req.color());
        }
        entity = tagRepository.save(entity);
        return new TagDto(entity.getName(), entity.getColor());
    }

    @Transactional
    public void deleteTag(String name) {
        TagEntity entity = tagRepository.findByName(name)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "标签不存在"));
        tagRepository.delete(entity);
    }
}
