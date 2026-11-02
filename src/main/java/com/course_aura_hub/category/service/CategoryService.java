package com.course_aura_hub.category.service;

import com.course_aura_hub.category.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> findAll();

    CategoryResponse create(String name);

    /** Renames the category on its courses too, because courses store the name. */
    CategoryResponse rename(Integer id, String name);

    /** Refused while courses still use the category. */
    void delete(Integer id);
}
