package com.hiscope.evaluation.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "app.upload.template.department-path=classpath:static/excel-templates/not-found.xlsx"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadTemplateErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 템플릿_파일_누락시_사용자친화_오류_노출_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        mockMvc.perform(get("/admin/uploads/template/departments").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("errorCode", "EXCEL_TEMPLATE_NOT_FOUND"))
                .andExpect(model().attribute("errorMessage",
                        "부서 업로드 템플릿 파일이 준비되지 않았습니다. 관리자에게 문의해주세요."));
    }

    private MockHttpSession loginAs(String loginId, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login")
                        .user("loginId", loginId)
                        .password("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
