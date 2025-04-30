package com.llmproxy.controller;

import com.llmproxy.model.ModelType;
import com.llmproxy.model.StatusResponse;
import com.llmproxy.service.router.RouterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class WebController {
    
    private final RouterService routerService;
    
    @GetMapping("/")
    public String index(Model model) {
        StatusResponse status = routerService.getAvailability();
        
        model.addAttribute("openaiAvailable", status.isOpenai());
        model.addAttribute("geminiAvailable", status.isGemini());
        model.addAttribute("mistralAvailable", status.isMistral());
        model.addAttribute("claudeAvailable", status.isClaude());
        
        model.addAttribute("modelTypes", ModelType.values());
        
        return "index";
    }
}
