package com.hiscope.evaluation.unit;

import com.hiscope.evaluation.domain.upload.service.UploadTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

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
    void 템플릿_파일_누락시_동적생성_폴백_반환() throws Exception {
        UploadTemplateService service = new UploadTemplateService(new DefaultResourceLoader());
        ReflectionTestUtils.setField(service, "departmentTemplatePath",
                "classpath:static/excel-templates/not-found.xlsx");

        var resource = service.loadDepartmentTemplate();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isGreaterThan(0L);
    }
}
