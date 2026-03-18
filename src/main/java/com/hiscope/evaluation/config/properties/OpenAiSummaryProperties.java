package com.hiscope.evaluation.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mypage.ai")
public class OpenAiSummaryProperties {

    private Provider provider = Provider.HEURISTIC;
    private boolean enabled = false;
    private boolean fallbackToHeuristic = true;
    private String apiKey;
    private String model = "gpt-5-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private Duration timeout = Duration.ofSeconds(10);
    private int maxRetries = 1;

    public enum Provider {
        HEURISTIC,
        OPENAI
    }
}
