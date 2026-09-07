package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> fetchDetail(@Param("id") Long id);

    @Query("""
           select p from Product p
           where lower(p.name) like lower(concat('%', :kw, '%'))
              or lower(p.description) like lower(concat('%', :kw, '%'))
           """)
    Page<Product> searchNameOrDesc(@Param("kw") String keyword, Pageable pageable);

    boolean existsByNameIgnoreCaseAndCategory_Id(String name, Long categoryId);

    // ===== Herkese açık (yalnızca yayında olan) listeleme =====
    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);
}
