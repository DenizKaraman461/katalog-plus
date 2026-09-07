package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.Role;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.repository.CartRepository;
import com.denizcelikhalat.katalog.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          CartRepository cartRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Kayıt formunu göster
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "register"; // register.html
    }

    // Kaydı işle
    @PostMapping("/register")
    @Transactional
    public String registerUser(@ModelAttribute("user") User user,
                               RedirectAttributes redirectAttributes) {

        // Aynı e-posta ile ikinci kayıt engellensin
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Bu e-posta adresi zaten kayıtlı.");
            return "redirect:/register";
        }

        // GÜVENLİK: Rol formdan ASLA alınmaz; her yeni kullanıcı sunucu tarafında USER yapılır.
        // (Form'a gizli "role" alanı eklense bile burada ezilir.)
        user.setRole(Role.USER);

        // KRİTİK: şifreyi DB'ye yazmadan önce BCrypt ile hash'le
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);

        // Yeni kullanıcıya anında boş bir sepet oluştur
        Cart cart = new Cart(savedUser);
        cartRepository.save(cart);

        redirectAttributes.addFlashAttribute("success", "Kayıt başarılı. Giriş yapabilirsiniz.");
        return "redirect:/login";
    }
}
