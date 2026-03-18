package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperatorUxGuidanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    void 템플릿_목록_노출_및_다운로드_정상_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        String employeePage = mockMvc.perform(get("/admin/employees").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(employeePage).contains("/admin/uploads/template/employees");
        assertThat(employeePage).contains("/admin/uploads/template/employees/hospital");
        assertThat(employeePage).contains("/admin/uploads/template/employees/affiliate");

        String departmentPage = mockMvc.perform(get("/admin/departments").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(departmentPage).contains("/admin/uploads/template/departments");

        mockMvc.perform(get("/admin/uploads/template/departments").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        mockMvc.perform(get("/admin/uploads/template/employees").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/uploads/template/questions").session(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    void 템플릿_추가후_화면_표시_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String templateName = "운영표시템플릿-" + System.nanoTime();

        mockMvc.perform(post("/admin/evaluation/templates")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", templateName)
                        .param("description", "운영 UI 표시 확인"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates"));

        String html = mockMvc.perform(get("/admin/evaluation/templates").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains(templateName);
    }

    @Test
    void 화면_설명_문구_렌더링_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        assertThat(mockMvc.perform(get("/admin/employees").session(adminSession))
                .andReturn().getResponse().getContentAsString()).contains("직원 관리/업로드 운영 안내");
        assertThat(mockMvc.perform(get("/admin/departments").session(adminSession))
                .andReturn().getResponse().getContentAsString()).contains("부서 관리 운영 안내");
        assertThat(mockMvc.perform(get("/admin/evaluation/templates").session(adminSession))
                .andReturn().getResponse().getContentAsString()).contains("문제은행/템플릿 관리 안내");
        assertThat(mockMvc.perform(get("/admin/settings/relationships").session(adminSession))
                .andReturn().getResponse().getContentAsString()).contains("관계 정의 세트 운영 안내");
        assertThat(mockMvc.perform(get("/admin/evaluation/sessions").session(adminSession))
                .andReturn().getResponse().getContentAsString()).contains("세션 생성/수정 운영 안내");
        assertThat(mockMvc.perform(get("/admin/uploads/history").session(adminSession))
                .andReturn().getResponse().getContentAsString()).contains("실행 이력 화면 안내");
    }

    @Test
    void 운영자_안내_및_오류_메시지_표시_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        Long orgId = accountRepository.findByLoginId("admin").orElseThrow().getOrganizationId();

        departmentRepository.save(Department.builder().organizationId(orgId).name("오류테스트A").code("ERRA").active(true).build());
        departmentRepository.save(Department.builder().organizationId(orgId).name("오류테스트B").code("ERRB").active(true).build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "emp_error.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createEmployeeMismatchWorkbook()
        );

        MvcResult result = mockMvc.perform(multipart("/admin/uploads/employees")
                        .file(file)
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"))
                .andReturn();

        Object uploadResultObj = result.getFlashMap().get("uploadResult");
        assertThat(uploadResultObj).isInstanceOf(UploadResult.class);
        UploadResult uploadResult = (UploadResult) uploadResultObj;
        assertThat(uploadResult.getErrors()).isNotEmpty();
        assertThat(uploadResult.getErrors().stream().map(e -> e.getMessage()))
                .anyMatch(msg -> msg.contains("부서코드와 부서명이 서로 다른 부서를 가리킵니다"));
    }

    private byte[] createEmployeeMismatchWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            String suffix = String.valueOf(System.nanoTime());
            String empNo = "UX-EMP-" + suffix;
            var sheet = workbook.createSheet("직원");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("이름");
            header.createCell(1).setCellValue("사원번호");
            header.createCell(2).setCellValue("부서코드");
            header.createCell(3).setCellValue("부서명");
            header.createCell(4).setCellValue("직위");
            header.createCell(5).setCellValue("직책");
            header.createCell(6).setCellValue("이메일");
            header.createCell(7).setCellValue("로그인ID");
            header.createCell(8).setCellValue("상태");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("오류사용자");
            row.createCell(1).setCellValue(empNo);
            row.createCell(2).setCellValue("ERRA");
            row.createCell(3).setCellValue("오류테스트B");
            row.createCell(4).setCellValue("사원");
            row.createCell(5).setCellValue("팀원");
            row.createCell(6).setCellValue("ux@example.com");
            row.createCell(7).setCellValue("ux_" + suffix.substring(Math.max(0, suffix.length() - 10)));
            row.createCell(8).setCellValue("ACTIVE");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
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
