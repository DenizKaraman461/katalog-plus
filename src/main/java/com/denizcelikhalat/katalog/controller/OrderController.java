package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CheckoutForm;
import com.denizcelikhalat.katalog.model.Order;
import com.denizcelikhalat.katalog.model.PaymentStatus;
import com.denizcelikhalat.katalog.model.OrderStatus;
import com.denizcelikhalat.katalog.model.PriceCurrency;
import com.denizcelikhalat.katalog.model.ShippingCostResult;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.service.CartService;
import com.denizcelikhalat.katalog.service.OrderService;
import com.denizcelikhalat.katalog.service.PaymentService;
import com.denizcelikhalat.katalog.service.ShippingCostService;
import com.denizcelikhalat.katalog.service.ShippingService;
import com.denizcelikhalat.katalog.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final ShippingService shippingService;
    private final ShippingCostService shippingCostService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService,
                           CartService cartService,
                           UserRepository userRepository,
                           ShippingService shippingService,
                           ShippingCostService shippingCostService,
                           PaymentService paymentService) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.shippingService = shippingService;
        this.shippingCostService = shippingCostService;
        this.paymentService = paymentService;
    }

    // ===================== CHECKOUT AKIŞI =====================

    /**
     * GET /checkout -> Sipariş onay formu + sepet özeti.
     * Sepet boşsa /cart'a hata ile döner. Form, kullanıcı bilgileriyle ön doldurulur.
     */
    @GetMapping("/checkout")
    public String checkoutForm(Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        Cart cart = cartService.getCartByEmail(principal.getName());
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Sepetiniz boş. Önce ürün ekleyin.");
            return "redirect:/cart";
        }

        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", prefillForm(principal.getName()));
        }
        model.addAttribute("cart", cart);
        // AŞAMA 3A/4B: sepet ağırlığı + tahmini kargo ücreti (yalnızca GÖRÜNTÜLEME;
        // sipariş oluşturma/ödeme akışı etkilenmez).
        var shippingCalculation = shippingService.calculateCartWeight(cart);
        model.addAttribute("shippingCalculation", shippingCalculation);
        var shippingCostResult = shippingCostService.calculateShippingCost(shippingCalculation);
        model.addAttribute("shippingCostResult", shippingCostResult);
        addTryTotalPreview(model, cart, shippingCostResult);
        return "checkout";
    }

    /**
     * POST /checkout -> Formu doğrular, geçerliyse siparişi oluşturur (PRG).
     * Geçersizse hata mesajlarıyla checkout.html'i girilen değerleri koruyarak tekrar gösterir.
     */
    @PostMapping("/checkout")
    public String placeOrder(@ModelAttribute("checkoutForm") CheckoutForm form,
                             Principal principal,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        Cart cart = cartService.getCartByEmail(principal.getName());
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Sepetiniz boş. Sipariş oluşturulamaz.");
            return "redirect:/cart";
        }

        // ---- Form doğrulama ----
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(form.getCustomerName())) errors.add("Ad Soyad zorunludur.");
        if (!StringUtils.hasText(form.getCustomerPhone())) errors.add("Telefon zorunludur.");
        // AŞAMA 10.1: iyzico'nun zorunlu tuttuğu buyer.city alanı için (TC Kimlik No YOK - toplanmıyor).
        if (!StringUtils.hasText(form.getCustomerCity())) errors.add("Şehir zorunludur.");
        if (!StringUtils.hasText(form.getDeliveryAddress())) errors.add("Teslimat adresi zorunludur.");
        if (!form.isPreInfoAccepted()) errors.add("Ön bilgilendirme formunu kabul etmelisiniz.");
        if (!form.isDistanceSalesAccepted()) errors.add("Mesafeli satış sözleşmesini kabul etmelisiniz.");

        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("checkoutForm", form); // girilen değerler korunur
            model.addAttribute("cart", cart);
            var shippingCalculation = shippingService.calculateCartWeight(cart);
            model.addAttribute("shippingCalculation", shippingCalculation);
            var shippingCostResult = shippingCostService.calculateShippingCost(shippingCalculation);
            model.addAttribute("shippingCostResult", shippingCostResult);
            addTryTotalPreview(model, cart, shippingCostResult);
            return "checkout";
        }

        // ---- Sipariş oluştur (PENDING) ----
        try {
            Order order = orderService.placeOrder(principal.getName(), form);
            // PRG: yenilemede tekrar sipariş oluşmasın diye redirect.
            // Kartla ödeme akışına gir: /checkout/pay/{id} tutarı/para birimini doğrulayıp
            // iyzico ödeme sayfasına yönlendirir. Ödeme başlatılamazsa (örn. karışık para
            // birimi, config eksik) sipariş kaybolmaz; PaymentController mevcut sipariş özeti
            // sayfasına (checkout/success) hata mesajıyla geri döner.
            return "redirect:/checkout/pay/" + order.getId();
        } catch (IllegalStateException e) {
            // Ürün pasif/stokta yok gibi durumlar -> formu hata ile tekrar göster
            model.addAttribute("errors", List.of(e.getMessage()));
            model.addAttribute("checkoutForm", form);
            model.addAttribute("cart", cart);
            var shippingCalculation = shippingService.calculateCartWeight(cart);
            model.addAttribute("shippingCalculation", shippingCalculation);
            var shippingCostResult = shippingCostService.calculateShippingCost(shippingCalculation);
            model.addAttribute("shippingCostResult", shippingCostResult);
            addTryTotalPreview(model, cart, shippingCostResult);
            return "checkout";
        }
    }

    /**
     * GET /checkout/success/{orderId} -> Sipariş onay sayfası.
     * Kullanıcı yalnızca kendi siparişini görebilir; admin hepsini görebilir.
     */
    @GetMapping("/checkout/success/{orderId}")
    public String success(@PathVariable Long orderId, Principal principal,
                          Authentication authentication, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/orders";
        }
        boolean admin = isAdmin(authentication);
        String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
        if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
            // Başkasının siparişi -> kendi siparişlerine yönlendir
            return "redirect:/orders";
        }

        // ÖNEMLİ: Bu sayfa yalnızca ödeme GERÇEKTEN tamamlanmışsa (paymentStatus=PAID)
        // gösterilir. Aksi halde kart bilgisi alınmadan "sipariş tamamlandı" izlenimi
        // verilmiş olur. Ödeme PENDING/FAILED ise kullanıcı sipariş detayına yönlendirilir;
        // orada ödeme durumuna uygun banner ("Ödemeye Devam Et" / "Ödemeyi Tekrar Dene") gösterilir.
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return "redirect:/orders/" + orderId;
        }

        model.addAttribute("order", order);
        model.addAttribute("orderId", order.getId());
        model.addAttribute("total", order.getTotalAmount());
        return "checkout_success";
    }

    // ----- yardımcılar -----
    private CheckoutForm prefillForm(String email) {
        CheckoutForm form = new CheckoutForm();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                    + (user.getLastName() != null ? user.getLastName() : "")).trim();
            form.setCustomerName(fullName);
            form.setCustomerPhone(user.getPhone());
            form.setDeliveryAddress(user.getAddress());
        }
        return form;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Checkout sayfasında "Sipariş Özeti"ni TRY karşılıklarıyla zenginleştirmek için modele
     * üç alan ekler:
     * - productTryByCurrency: her para birimi için AYRI TRY karşılığı (Map) — "EUR Toplam ...
     *   (~602,00 TL)" gibi satır bazlı ipuçları için. Orijinal €/$ gösterimi BOZULMAZ.
     * - productTotalTry: tüm para birimlerinin TRY karşılıklarının TOPLAMI (yukarıdaki
     *   haritanın değerlerinin toplamı).
     * - tryGrandTotal: productTotalTry + kargo ücreti (TRY) = "Ödenecek Toplam". Kur
     *   alınamıyorsa VEYA kargo ücreti henüz hesaplanamıyorsa (manuel inceleme) null kalır —
     *   bu durumda checkout.html mevcut (orijinal para birimi bazlı) gösterime GÜVENLİ şekilde
     *   geri döner, YANLIŞ/EKSİK bir rakam ASLA gösterilmez.
     *
     * ÖNEMLİ: paymentService.convertEachCurrencyToTry(...), ödeme başlatılırken (buildCharge)
     * kullanılan BİREBİR AYNI kur kaynağını ve dönüşüm mantığını kullanır — burada gösterilen
     * tutarlar ile iyzico'ya gönderilecek gerçek tutar aynı kaynaktan gelir.
     */
    private void addTryTotalPreview(Model model, Cart cart, ShippingCostResult shippingCostResult) {
        Map<PriceCurrency, BigDecimal> productTryByCurrency = paymentService.convertEachCurrencyToTry(
                cart != null ? cart.getTotalsByCurrency() : null);
        model.addAttribute("productTryByCurrency", productTryByCurrency);

        BigDecimal productTotalTry = null;
        if (productTryByCurrency != null) {
            productTotalTry = BigDecimal.ZERO;
            for (BigDecimal tryAmount : productTryByCurrency.values()) {
                productTotalTry = productTotalTry.add(tryAmount);
            }
        }

        BigDecimal tryGrandTotal = null;
        if (productTotalTry != null && shippingCostResult != null && shippingCostResult.isCostCalculated()) {
            tryGrandTotal = productTotalTry.add(shippingCostResult.getShippingCost());
        }
        model.addAttribute("tryGrandTotal", tryGrandTotal);
    }

    // ===================== PUANLAMA (AJAX) =====================

    @PostMapping("/orders/{id}/rate")
    @ResponseBody
    public String rateOrder(@PathVariable Long id, @RequestParam Integer rating) {
        orderService.rateOrder(id, rating);
        return "OK";
    }

    // ===================== MÜŞTERİ: Siparişlerim =====================

    @GetMapping("/orders")
    public String myOrders(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("orders", orderService.getOrdersByEmail(principal.getName()));
        return "my_orders";
    }

    /**
     * GET /orders/{orderId} -> Müşteri sipariş detayı.
     * Kullanıcı yalnızca kendi siparişini görebilir; admin hepsini görebilir.
     */
    @GetMapping("/orders/{orderId}")
    public String orderDetail(@PathVariable Long orderId, Principal principal,
                              Authentication authentication, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/orders";
        }
        boolean admin = isAdmin(authentication);
        String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
        if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
            return "redirect:/orders";
        }
        model.addAttribute("order", order);
        return "order_detail";
    }

    // ===================== ADMIN: Sipariş Yönetimi =====================

    @GetMapping("/admin/orders")
    public String adminOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        model.addAttribute("statuses", OrderStatus.values());
        return "admin_orders";
    }

    /**
     * GET /admin/orders/{orderId} -> Admin sipariş detayı (tüm snapshot + durum güncelleme formu).
     * /admin/** zaten ADMIN rolü ister (SecurityConfig).
     */
    @GetMapping("/admin/orders/{orderId}")
    public String adminOrderDetail(@PathVariable Long orderId, Model model) {
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("order", order);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin_order_detail";
    }

    @PostMapping("/admin/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam("status") OrderStatus status,
                                    RedirectAttributes redirectAttributes) {
        orderService.updateOrderStatus(id, status);
        redirectAttributes.addFlashAttribute("success", "Sipariş durumu güncellendi.");
        return "redirect:/admin/orders/" + id;
    }

    /**
     * AŞAMA 8: Kargo operasyonu — admin siparişi fiilen kargoya verdiğinde kargo firması +
     * takip numarasını kaydeder, gönderim zamanını (shippedAt) yazar ve durumu SHIPPED yapar.
     * Mevcut ödeme akışına (PaymentService/IyzicoClient) ve mail sistemine HİÇ dokunmaz —
     * bkz. OrderService.markOrderAsShipped. /admin/** zaten ADMIN rolü ister (SecurityConfig).
     */
    @PostMapping("/admin/orders/{id}/ship")
    public String shipOrder(@PathVariable Long id,
                            @RequestParam(value = "shippingCompany", required = false) String shippingCompany,
                            @RequestParam(value = "shippingTrackingNumber", required = false) String shippingTrackingNumber,
                            RedirectAttributes redirectAttributes) {
        orderService.markOrderAsShipped(id, shippingCompany, shippingTrackingNumber);
        redirectAttributes.addFlashAttribute("success", "Sipariş kargoya verildi olarak işaretlendi.");
        return "redirect:/admin/orders/" + id;
    }
}
