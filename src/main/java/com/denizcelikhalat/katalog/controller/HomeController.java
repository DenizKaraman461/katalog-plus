package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class HomeController {

    private final CategoryService categoryService;
    public HomeController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("page", "index");
        return "index";
    }
}
