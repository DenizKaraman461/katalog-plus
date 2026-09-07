package com.denizcelikhalat.katalog.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping({"/login", "/admin/login"})
    public String loginPage(Model model, Authentication authentication) {
        boolean isAdmin = false;
        if (authentication != null) {
            isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(role -> role.equals("ROLE_ADMIN"));
        }
        model.addAttribute("isAdmin", isAdmin);
        return "login";  // login.html Thymeleaf template
    }
}
