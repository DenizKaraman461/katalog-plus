package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Category;
import com.denizcelikhalat.katalog.model.PriceCurrency;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.service.CategoryService;
import com.denizcelikhalat.katalog.service.ProductService;
import com.denizcelikhalat.katalog.service.ReturnUrlSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ProductController {

    // Ürün listesi sayfasının (pagination/kategori/arama bağlamıyla birlikte) en son ziyaret
    // edilen halini HttpSession'da saklamak için kullanılan anahtar. Ürün detayına "returnUrl"
    // OLMADAN (örn. doğrudan bir linkten) gelinirse bu değer 2. öncelik kaynağı olarak kullanılır
    // (bkz. productDetail()).
    private static final String LAST_PRODUCT_LIST_URL = "LAST_PRODUCT_LIST_URL";

    private final ProductService productService;
    private final CategoryService categoryService;

    @Autowired
    public ProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    // Kargo Ağırlığı (kg/metre) alanı formda boş bırakılabilir; boş string geldiğinde
    // BigDecimal'a çevirmeye çalışıp hata vermek yerine null olarak bağlanır.
    // YALNIZCA bu alana özeldir; diğer sayısal alanların (price vb.) bağlanma davranışı
    // DEĞİŞMEZ (onlar zaten formda "required" ve her zaman doludur).
    // Kargo Ağırlığı (kg/metre) ve tek-input formundaki Kargo Ağırlığı Değeri alanları formda
    // boş bırakılabilir; boş string geldiğinde BigDecimal'a çevirmeye çalışıp hata vermek
    // yerine null olarak bağlanır. YALNIZCA bu alanlara özeldir; diğer sayısal alanların
    // (price vb.) bağlanma davranışı DEĞİŞMEZ (onlar zaten formda "required" ve her zaman doludur).
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(BigDecimal.class, "shippingWeightPerMeter",
                new CustomNumberEditor(BigDecimal.class, true));
        binder.registerCustomEditor(BigDecimal.class, "shippingWeightValue",
                new CustomNumberEditor(BigDecimal.class, true));
    }

    @GetMapping("/products")
    public String listProducts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model,
            Authentication authentication,
            HttpSession session
    ) {
        if (page < 0) page = 0;
        if (size < 1) size = 12;

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = StringUtils.hasText(keyword)
                ? productService.search(keyword, pageable)
                : productService.findAll(pageable);

        model.addAttribute("productPage", result);            // << products.html uses productPage.content
        model.addAttribute("productList", result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSize", size);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", null);
        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("pageNumbers", getPageNumbers(page, result.getTotalPages()));

        // Ürün detayına giden "hangi liste sayfasından geldim" bilgisi: hem ürün kartı linkine
        // (returnUrl query param) hem de HttpSession'a (LAST_PRODUCT_LIST_URL) yazılır — ikinci
        // returnUrl parametresi olmadan doğrudan detaya girilirse hâlâ geri dönülebilsin diye.
        String currentListUrl = buildListReturnUrl("/products", page, size, keyword);
        model.addAttribute("currentListUrl", currentListUrl);
        session.setAttribute(LAST_PRODUCT_LIST_URL, currentListUrl);

        return "products";
    }

    @GetMapping("/category/{id}")
    public String productsByCategory(
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model,
            Authentication authentication,
            HttpSession session
    ) {
        if (page < 0) page = 0;
        if (size < 1) size = 12;

        Pageable pageable = PageRequest.of(page, size);
        // Kategori içinde arama: keyword doluysa searchByCategory (Türkçe/token/relevance
        // aramalı, bkz. ProductServiceImpl), boşsa mevcut düz kategori listelemesi.
        Page<Product> result = StringUtils.hasText(keyword)
                ? productService.searchByCategory(id, keyword, pageable)
                : productService.findByCategoryId(id, pageable);

        model.addAttribute("productPage", result);            // << products.html uses productPage.content
        model.addAttribute("productList", result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSize", size);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("keyword", keyword);

        Category category = categoryService.getById(id).orElse(null);
        model.addAttribute("category", category);

        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("pageNumbers", getPageNumbers(page, result.getTotalPages()));

        // Ürün detayına giden "hangi liste sayfasından geldim" bilgisi: hem ürün kartı linkine
        // (returnUrl query param) hem de HttpSession'a (LAST_PRODUCT_LIST_URL) yazılır.
        // keyword artık kategori dalında da korunuyor (örn. /category/1150?page=2&keyword=6x36).
        String currentListUrl = buildListReturnUrl("/category/" + id, page, size, keyword);
        model.addAttribute("currentListUrl", currentListUrl);
        session.setAttribute(LAST_PRODUCT_LIST_URL, currentListUrl);

        return "products";
    }

    private List<Integer> getPageNumbers(int currentPage, int totalPages) {
        List<Integer> pageNumbers = new ArrayList<>();
        if (totalPages > 0) {
            int start = Math.max(0, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);

            if (currentPage == 0) {
                end = Math.min(totalPages - 1, 2);
            } else if (currentPage == totalPages - 1) {
                start = Math.max(0, totalPages - 3);
            }

            for (int i = start; i <= end; i++) {
                pageNumbers.add(i);
            }
        }
        return pageNumbers;
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("currencies", PriceCurrency.values());
        return "add_product";
    }

    @PostMapping("/add")
    public String addProduct(
            @ModelAttribute Product product,
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("tableImageFile") MultipartFile tableImageFile
    ) throws IOException {
        productService.save(product, imageFile, tableImageFile);
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Product product = productService.getById(id);
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("currencies", PriceCurrency.values());
        return "edit_product";
    }

    @PostMapping("/edit/{id}")
    public String editProduct(
            @PathVariable Long id,
            @ModelAttribute Product product,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "tableImageFile", required = false) MultipartFile tableImageFile,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        productService.update(id, product, imageFile, tableImageFile);
        redirectAttributes.addFlashAttribute("success", "Ürün başarıyla güncellendi.");
        // Düzenleme sonrası ürünün güncel detay sayfasına dön.
        return "redirect:/products/" + id;
    }

    // GÜVENLİK: Veri silme artık GET ile değil, CSRF korumalı POST ile yapılır.
    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteById(id);
        return "redirect:/products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id,
                                @RequestParam(value = "returnUrl", required = false) String returnUrl,
                                Model model, Authentication authentication,
                                HttpSession session) {
        Product product = productService.getById(id);
        if (product == null) {
            return "redirect:/products";
        }
        boolean admin = isAdmin(authentication);
        // Pasif (yayında olmayan) ürünü yalnızca admin görebilir; diğerleri listeye döner.
        if (Boolean.FALSE.equals(product.getActive()) && !admin) {
            return "redirect:/products";
        }
        model.addAttribute("product", product);
        model.addAttribute("isAdmin", admin);

        // "Ürünlere Dön" hedefi — 3 katmanlı öncelik sırası:
        // 1) Geçerli returnUrl parametresi (kullanıcı bir liste sayfasından tıkladıysa).
        // 2) HttpSession'daki geçerli LAST_PRODUCT_LIST_URL (returnUrl yoksa/şüpheliyse, ama
        //    kullanıcı daha önce bir liste sayfası ziyaret ettiyse).
        // 3) Son çare: "/products".
        // ReturnUrlSupport.sanitizeOrNull(...) GEÇERSİZSE null döner (sanitize()'ın aksine
        // sessizce "/products"a düşmez) -> bu sayede "gerçekten geçerli miydi" bilgisini
        // kaybetmeden bir sonraki kaynağa geçebiliyoruz.
        String backListUrl = ReturnUrlSupport.sanitizeOrNull(returnUrl);
        if (backListUrl == null) {
            Object sessionValue = session.getAttribute(LAST_PRODUCT_LIST_URL);
            if (sessionValue instanceof String sessionUrl) {
                backListUrl = ReturnUrlSupport.sanitizeOrNull(sessionUrl);
            }
        }
        if (backListUrl == null) {
            backListUrl = "/products";
        }

        // backListUrl: FRAGMENT İÇERMEZ -> sepet/teklif formlarındaki gizli returnUrl alanına
        // konur, böylece POST sonrası tekrar ReturnUrlSupport'tan sorunsuz geçer.
        // backUrl: backListUrl + "#product-{id}" -> YALNIZCA görünür "Ürünlere Dön" linki için;
        // kullanıcıyı listede TAM OLARAK bu ürünün kartına götürür. Fragment kullanıcı
        // girdisinden DEĞİL, o an görüntülenen ürünün (path'ten gelen, güvenilir) id'sinden
        // sunucu tarafında üretilir.
        model.addAttribute("backListUrl", backListUrl);
        model.addAttribute("backUrl", backListUrl + "#product-" + id);

        return "product_detail";
    }

    // "/products" veya "/category/{id}" liste sayfasının GÜNCEL sayfa/boyut/arama durumunu
    // taşıyan bir URL üretir (örn. "/category/5?page=2&size=24&keyword=%C3%A7elik+halat").
    // Ürün kartlarının linkine "returnUrl" query param'ı olarak eklenir; kullanıcı ürün
    // detayından geri dönünce aynı sayfaya/filtreye/sayfa-boyutuna ulaşsın diye (bkz.
    // productDetail, "Ürünlere Dön" linki).
    //
    // ÖNEMLİ: keyword, java.net.URLEncoder ile (application/x-www-form-urlencoded kuralına
    // göre) encode edilir — bu, ReturnUrlSupport.sanitize()'ın kullandığı java.net.URLDecoder
    // ile EŞLEŞEN/simetrik bir çift olduğundan "&", "+", boşluk ve Türkçe karakterler
    // (çÇşŞğĞüÜöÖıİ) round-trip'te kaybolmadan/bozulmadan doğru şekilde geri çözülür.
    private String buildListReturnUrl(String basePath, int page, int size, String keyword) {
        StringBuilder sb = new StringBuilder(basePath)
                .append("?page=").append(page)
                .append("&size=").append(size);
        if (StringUtils.hasText(keyword)) {
            sb.append("&keyword=").append(java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
