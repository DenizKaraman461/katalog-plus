package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.MeasurementMode;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.ShippingWeightType;
import com.denizcelikhalat.katalog.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    // application.properties -> upload.path=uploads
    @Value("${upload.path:uploads}")
    private String uploadBase;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ---------------- helpers ----------------
    private Path resolveUploadDir() {
        Path base = Paths.get(uploadBase);
        return base.isAbsolute() ? base : Paths.get(System.getProperty("user.dir")).resolve(base);
    }

    /**
     * Admin formundaki TEK "Kargo Ağırlığı (kg)" değerini, seçilen shippingWeightType'a göre
     * DOĞRU gerçek alana (shippingWeightPerMeter veya shippingWeightPerUnit) yazar; DİĞER
     * alanı null'a çeker (böylece bir ürün aynı anda hem PER_METER hem PER_UNIT değerine sahip
     * olamaz — ShippingService'in hangi alanı okuyacağı her zaman netleşir).
     *
     * Boş bırakılabilir; girilmişse 0'dan büyük olmalı (negatif/sıfır kabul edilmez — mevcut
     * kural, tip fark etmeksizin aynen korunur).
     */
    private void applyShippingWeight(Product target, ShippingWeightType type, BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            String label = (type == ShippingWeightType.PER_UNIT) ? "kg/adet" : "kg/metre";
            throw new IllegalArgumentException("Kargo Ağırlığı (" + label + ") girilmişse 0'dan büyük olmalıdır.");
        }
        if (type == ShippingWeightType.PER_UNIT) {
            target.setShippingWeightPerUnit(value);
            target.setShippingWeightPerMeter(null);
        } else {
            target.setShippingWeightPerMeter(value);
            target.setShippingWeightPerUnit(null);
        }
    }

    private String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Dosya boş.");

        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Sadece görsel yükleyebilirsiniz.");
        }

        String original = file.getOriginalFilename();
        String cleaned = StringUtils.cleanPath(original != null ? original : "");
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) throw new IllegalArgumentException("Geçersiz dosya adı/uzantı.");

        String ext = cleaned.substring(dot).toLowerCase(Locale.ROOT);
        if (!(ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".webp"))) {
            throw new IllegalArgumentException("İzin verilen uzantılar: .png .jpg .jpeg .webp");
        }

        String unique = UUID.randomUUID() + ext;
        Path uploadDir = resolveUploadDir();
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
        file.transferTo(uploadDir.resolve(unique).toFile());
        return unique;
    }

    // ---------------- service api ----------------
    @Override
    public List<Product> listAll(String keyword) {
        if (StringUtils.hasText(keyword)) {
            return productRepository.findByNameContainingIgnoreCase(keyword, Pageable.unpaged()).getContent();
        }
        return productRepository.findAll();
    }

    @Override
    public Product getById(Long id) {
        // Admin düzenleme/görüntüleme için pasif ürünler dahil tümünü getirir.
        return productRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void save(Product product, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException {
        // Güvenli varsayılanlar (basit boolean stok)
        if (product.getActive() == null) product.setActive(Boolean.TRUE);
        if (product.getInStock() == null) product.setInStock(Boolean.TRUE);
        if (product.getCurrency() == null) product.setCurrency(com.denizcelikhalat.katalog.model.PriceCurrency.USD);

        // Kargo Ağırlığı — tek input, tipe göre doğru gerçek alana yönlendirilir.
        // AŞAMA 1: yalnızca doğrulama/kayıt, hesaplama YOK.
        ShippingWeightType weightType = product.getShippingWeightType() != null
                ? product.getShippingWeightType() : ShippingWeightType.PER_METER;
        product.setShippingWeightType(weightType);
        applyShippingWeight(product, weightType, product.getShippingWeightValue());

        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = saveFile(imageFile);
            product.setImagePath("/uploads/" + imagePath);
        }
        if (tableImageFile != null && !tableImageFile.isEmpty()) {
            String tablePath = saveFile(tableImageFile);
            product.setTableImagePath("/uploads/" + tablePath);
        }
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void update(Long id, Product updatedProduct, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        if (StringUtils.hasText(updatedProduct.getName())) {
            existing.setName(updatedProduct.getName());
        }
        if (updatedProduct.getPrice() != null) {
            existing.setPrice(updatedProduct.getPrice());
        }
        // null da set edilsin istiyorsan aşağıyı koru:
        existing.setDescription(updatedProduct.getDescription());

        if (updatedProduct.getCategory() != null) {
            existing.setCategory(updatedProduct.getCategory());
        }

        // Ölçü / fiyatlandırma alanlarını da güncelle (DEĞİŞMEDİ)
        existing.setMeasurementMode(updatedProduct.getMeasurementMode() != null
                ? updatedProduct.getMeasurementMode() : MeasurementMode.NONE);
        existing.setMeasurementUnitLabel(updatedProduct.getMeasurementUnitLabel());
        existing.setMeasurementOptionsText(updatedProduct.getMeasurementOptionsText());

        // Yayın & basit stok (sayısal stok artık kullanılmıyor; stockQuantity'ye dokunulmaz)
        existing.setActive(updatedProduct.getActive() != null
                ? updatedProduct.getActive() : Boolean.TRUE);
        existing.setInStock(updatedProduct.getInStock() != null
                ? updatedProduct.getInStock() : Boolean.TRUE);

        // Para birimi (dönüşüm yok). Gelmezse mevcut korunur, o da yoksa USD.
        if (updatedProduct.getCurrency() != null) {
            existing.setCurrency(updatedProduct.getCurrency());
        } else if (existing.getCurrency() == null) {
            existing.setCurrency(com.denizcelikhalat.katalog.model.PriceCurrency.USD);
        }

        // Kargo Ağırlığı — tek input, tipe göre doğru gerçek alana yönlendirilir.
        // AŞAMA 1: yalnızca doğrulama/kayıt, hesaplama YOK. Form alanı boş bırakılırsa
        // null olarak kaydedilir (temizlenmiş sayılır).
        ShippingWeightType weightType = updatedProduct.getShippingWeightType() != null
                ? updatedProduct.getShippingWeightType() : ShippingWeightType.PER_METER;
        existing.setShippingWeightType(weightType);
        applyShippingWeight(existing, weightType, updatedProduct.getShippingWeightValue());

        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = saveFile(imageFile);
            existing.setImagePath("/uploads/" + imagePath);
        }
        if (tableImageFile != null && !tableImageFile.isEmpty()) {
            String tablePath = saveFile(tableImageFile);
            existing.setTableImagePath("/uploads/" + tablePath);
        }

        productRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    // ===== Herkese açık listelemeler: yalnızca YAYINDA olan ürünler =====
    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    @Override
    public Page<Product> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) return productRepository.findByActiveTrue(pageable);
        List<Product> candidates = productRepository.findByActiveTrue(Pageable.unpaged()).getContent();
        return scoreFilterAndPaginate(candidates, keyword, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, int page, int size) {
        return findByCategoryId(categoryId, PageRequest.of(page, size));
    }

    @Override
    public Page<Product> searchByCategory(Long categoryId, String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
        }
        List<Product> candidates =
                productRepository.findByCategoryIdAndActiveTrue(categoryId, Pageable.unpaged()).getContent();
        return scoreFilterAndPaginate(candidates, keyword, pageable);
    }

    // ===== Arama yardımcıları (AŞAMA: Türkçe/token/relevance aramayı — bkz. ProductSearchSupport) =====

    // candidates listesini keyword'e göre filtreler (TÜM token'lar AND ile aranır), relevance
    // skoruna göre büyükten küçüğe sıralar (eşit skorda ada göre alfabetik), sonra pageable'a
    // göre BELLEKTE dilimleyip PageImpl ile doğru totalElements/pageNumber/pageSize ile döner.
    // Not: ~160 ürünlük katalog ölçeğinde bu yaklaşım (DB'den tüm adayları çekip Java'da
    // puanlamak) performans açısından sorunsuzdur; katalog çok büyürse yeniden değerlendirilmeli.
    private Page<Product> scoreFilterAndPaginate(List<Product> candidates, String keyword, Pageable pageable) {
        List<String> queryTokens = ProductSearchSupport.tokenize(keyword);
        String wholeQueryCompact = ProductSearchSupport.toCompact(keyword);

        List<Product> matched = new ArrayList<>();
        Map<Long, Integer> scoresById = new HashMap<>();
        for (Product product : candidates) {
            ProductSearchSupport.SearchableFields fields = toSearchableFields(product);
            if (ProductSearchSupport.matches(fields, queryTokens)) {
                matched.add(product);
                scoresById.put(product.getId(), ProductSearchSupport.score(fields, queryTokens, wholeQueryCompact));
            }
        }

        matched.sort((a, b) -> {
            int scoreA = scoresById.getOrDefault(a.getId(), 0);
            int scoreB = scoresById.getOrDefault(b.getId(), 0);
            if (scoreA != scoreB) return scoreB - scoreA; // yüksek skor önce
            String nameA = a.getName() != null ? a.getName() : "";
            String nameB = b.getName() != null ? b.getName() : "";
            return nameA.compareToIgnoreCase(nameB);
        });

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int total = matched.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Product> pageContent = matched.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, pageable, total);
    }

    // Product entity'sini (JPA/Spring'e bağımlı olmayan) ProductSearchSupport.SearchableFields'e
    // eşler. Kategori null olabilir (ör. henüz kategori atanmamış ürün) -> null-safe.
    private ProductSearchSupport.SearchableFields toSearchableFields(Product product) {
        String categoryName = (product.getCategory() != null) ? product.getCategory().getName() : null;
        return new ProductSearchSupport.SearchableFields(
                product.getName(),
                product.getDescription(),
                categoryName,
                product.getMeasurementOptionsText(),
                product.getMeasurementUnitLabel()
        );
    }
}
