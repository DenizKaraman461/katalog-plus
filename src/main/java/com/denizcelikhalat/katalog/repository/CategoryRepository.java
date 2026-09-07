package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
