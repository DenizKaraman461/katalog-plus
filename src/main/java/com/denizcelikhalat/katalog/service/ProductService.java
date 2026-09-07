package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductService {

    // Basit listeleme (aramalı)
    List<Product> listAll(String keyword);

    // Tekil kayıt
    Product getById(Long id);

    // Oluşturma / Güncelleme / Silme
    void save(Product product, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException;

    void update(Long id, Product product, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException;

    void deleteById(Long id);

    // Sayfalı listeleme
    Page<Product> findAll(Pageable pageable);

    // Sayfalı arama
    Page<Product> search(String keyword, Pageable pageable);

    // Kategoriye göre sayfalı liste
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    // İsteyen yerler için int/int overload (Pageable sarmalaması)
    Page<Product> findByCategoryId(Long categoryId, int page, int size);

    // Kategori içinde sayfalı arama (keyword boş/null ise findByCategoryId ile aynı davranır)
    Page<Product> searchByCategory(Long categoryId, String keyword, Pageable pageable);
}
