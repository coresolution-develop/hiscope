package com.hiscope.evaluation.domain.upload.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadTemplateService {

    private final ResourceLoader resourceLoader;

    @Value("${app.upload.template.department-path:classpath:static/excel-templates/dept_template.xlsx}")
    private String departmentTemplatePath;

    @Value("${app.upload.template.employee-path:classpath:static/excel-templates/emp_template.xlsx}")
    private String employeeTemplatePath;

    @Value("${app.upload.template.question-path:classpath:static/excel-templates/question_template.xlsx}")
    private String questionTemplatePath;

    public Resource loadDepartmentTemplate() {
        return loadTemplate(departmentTemplatePath, "부서 업로드 템플릿");
    }

    public Resource loadEmployeeTemplate() {
        return loadTemplate(employeeTemplatePath, "직원 업로드 템플릿");
    }

    public Resource loadQuestionTemplate() {
        return loadTemplate(questionTemplatePath, "평가문항 업로드 템플릿");
    }

    private Resource loadTemplate(String location, String templateName) {
        Resource resource = resourceLoader.getResource(location);
        try {
            if (!resource.exists() || !resource.isReadable() || resource.contentLength() <= 0) {
                log.error("Upload template missing or unreadable. name={}, location={}", templateName, location);
                throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                        templateName + " 파일이 준비되지 않았습니다. 관리자에게 문의해주세요.");
            }
            return resource;
        } catch (IOException e) {
            log.error("Failed to read upload template. name={}, location={}", templateName, location, e);
            throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                    templateName + " 파일을 읽을 수 없습니다. 관리자에게 문의해주세요.");
        }
    }
}
