package com.hiscope.evaluation.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadPolicyProperties {

    private long maxFileSizeBytes = 10 * 1024 * 1024;
    private List<String> allowedExtensions = List.of("xlsx");
    private int maxRows = 5000;
}
