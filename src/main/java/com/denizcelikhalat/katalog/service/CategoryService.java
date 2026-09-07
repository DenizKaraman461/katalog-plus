package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Category;
import java.util.Optional;

import java.util.List;

public interface CategoryService {
    Optional<Category> getById(Long id);

    List<Category> findAll();

}
