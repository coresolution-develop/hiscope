package com.hiscope.evaluation.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.bootstrap.super-admin")
public class SuperAdminBootstrapProperties {

    private boolean enabled = false;
    private String loginId = "super";
    private String password = "change-me";
    private String name = "슈퍼관리자";
    private String email = "super@system.local";
}
