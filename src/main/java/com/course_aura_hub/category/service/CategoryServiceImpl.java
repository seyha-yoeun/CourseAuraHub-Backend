package com.course_aura_hub.category.service;

import com.course_aura_hub.category.dto.CategoryResponse;
import com.course_aura_hub.category.model.Category;
import com.course_aura_hub.category.repository.CategoryRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CategoryResponse create(String name) {
        String trimmed = name.trim();
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new DuplicateResourceException("This category already exists");
        }
        return toResponse(categoryRepository.save(Category.builder().name(trimmed).build()));
    }

    @Override
    @Transactional
    public CategoryResponse rename(Integer id, String name) {
        Category category = find(id);
        String trimmed = name.trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new DuplicateResourceException("This category already exists");
        }
        courseRepository.renameCategory(category.getName(), trimmed);
        category.setName(trimmed);
        return toResponse(category);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Category category = find(id);
        if (courseRepository.countByCategory(category.getName()) > 0) {
            throw new BusinessRuleException("Move this category's courses to another category first");
        }
        categoryRepository.delete(category);
    }

    private Category find(Integer id) {
        return categoryRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), courseRepository.countByCategory(category.getName()));
    }
}
