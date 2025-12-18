package com.ltt.stellaris.controller;

import com.ltt.stellaris.config.StellarisProperties;
import com.ltt.stellaris.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home page controller
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final FileService fileService;
    private final StellarisProperties properties;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("libraries", fileService.getLibraries());
        model.addAttribute("defaultView", properties.getUi().getDefaultView());
        return "index";
    }
}
