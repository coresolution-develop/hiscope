package com.hiscope.evaluation.unit;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.upload.service.UploadTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadTemplateServiceUnitTest {

    @Test
    void 부서_템플릿_로딩_성공() {
        UploadTemplateService service = new UploadTemplateService(new DefaultResourceLoader());
        ReflectionTestUtils.setField(service, "departmentTemplatePath",
                "classpath:static/excel-templates/dept_template.xlsx");

        var resource = service.loadDepartmentTemplate();

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    void 템플릿_파일_누락시_친화적_예외_반환() {
        UploadTemplateService service = new UploadTemplateService(new DefaultResourceLoader());
        ReflectionTestUtils.setField(service, "departmentTemplatePath",
                "classpath:static/excel-templates/not-found.xlsx");

        assertThatThrownBy(service::loadDepartmentTemplate)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND);
    }
}
