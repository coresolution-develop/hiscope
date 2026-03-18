package com.hiscope.evaluation.domain.mypage.controller;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.mypage.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/mypage")
    public String myPage(Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        model.addAttribute("myPage", myPageService.getMyPage(orgId, employeeId));
        return "user/mypage";
    }
}
