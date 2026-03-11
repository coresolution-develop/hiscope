package com.hiscope.evaluation.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    // GET/POST 모두 지원 — POST 폼에서 403 발생 시 forward로 POST 요청이 들어올 수 있음
    @RequestMapping("/error/403")
    public String accessDenied() {
        return "error/403";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }
}
