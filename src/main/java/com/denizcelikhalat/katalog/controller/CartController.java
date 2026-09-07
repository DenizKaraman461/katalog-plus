package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.service.CartService;
import com.denizcelikhalat.katalog.service.ReturnUrlSupport;
import com.denizcelikhalat.katalog.service.ShippingCostService;
import com.denizcelikhalat.katalog.service.ShippingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final ShippingService shippingService;
    private final ShippingCostService shippingCostService;

    public CartController(CartService cartService, ShippingService shippingService,
                          ShippingCostService shippingCostService) {
        this.cartService = cartService;
        this.shippingService = shippingService;
        this.shippingCostService = shippingCostService;
    }

    // Sepeti görüntüle. Principal.getName() = giriş yapan kullanıcının e-postası.
    // AŞAMA 3A/4B: sepet ağırlığı + tahmini kargo ücreti (yalnızca GÖRÜNTÜLEME;
    // Order/ödeme/checkout akışı etkilenmez).
    @GetMapping
    public String viewCart(Principal principal, Model model) {
        Cart cart = cartService.getCartByEmail(principal.getName());
        model.addAttribute("cart", cart);

        var shippingCalculation = shippingService.calculateCartWeight(cart);
        model.addAttribute("shippingCalculation", shippingCalculation);
        model.addAttribute("shippingCostResult", shippingCostService.calculateShippingCost(shippingCalculation));

        return "cart"; // cart.html
    }

    // Sepete ürün ekle (durum değiştirdiği için POST).
    // Ölçü alanları moda göre opsiyonel gelir; doğrulama CartService'te yapılır.
    @PostMapping("/add/{productId}")
    public String addToCart(@PathVariable Long productId,
                            @RequestParam(value = "selectedMeasurement", required = false) String selectedMeasurement,
                            @RequestParam(value = "measurementAmount", required = false) BigDecimal measurementAmount,
                            @RequestParam(value = "returnUrl", required = false) String returnUrl,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        try {
            cartService.addProductToCart(principal.getName(), productId, selectedMeasurement, measurementAmount);
            return "redirect:/cart";
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Geçersiz ölçü / para birimi çakışması vb. -> ürün sayfasına hata mesajıyla geri dön.
            // "hangi liste sayfasından geldim" bilgisi (returnUrl) de doğrulanarak taşınır, kaybolmaz.
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("returnUrl", ReturnUrlSupport.sanitize(returnUrl));
            return "redirect:/products/" + productId;
        }
    }

    // Sepetten ürün çıkar (durum değiştirdiği için POST).
    // Aynı üründen farklı ölçü/miktarlarda birden çok kalem olabileceği için ölçü + miktar da gönderilir.
    @PostMapping("/remove/{productId}")
    public String removeFromCart(@PathVariable Long productId,
                                 @RequestParam(value = "selectedMeasurement", required = false) String selectedMeasurement,
                                 @RequestParam(value = "measurementAmount", required = false) BigDecimal measurementAmount,
                                 Principal principal) {
        cartService.removeProductFromCart(principal.getName(), productId, selectedMeasurement, measurementAmount);
        return "redirect:/cart";
    }
}
