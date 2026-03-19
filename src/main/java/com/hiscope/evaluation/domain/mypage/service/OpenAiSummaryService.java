package com.hiscope.evaluation.domain.mypage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiscope.evaluation.config.properties.OpenAiSummaryProperties;
import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import com.hiscope.evaluation.domain.mypage.dto.openai.OpenAiChatCompletionRequest;
import com.hiscope.evaluation.domain.mypage.dto.openai.OpenAiChatCompletionResponse;
import com.hiscope.evaluation.domain.mypage.dto.openai.OpenAiSummaryPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OpenAiSummaryService implements AiSummaryService {

    private final OpenAiSummaryProperties properties;
    private final OpenAiSummaryPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiSummaryService(OpenAiSummaryProperties properties,
                                OpenAiSummaryPromptBuilder promptBuilder,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(buildRequestFactory(properties.getTimeout()))
                .build();
    }

    @Override
    public MyPageView.AiSummary summarize(List<MyPageView.CategoryScore> categories,
                                          double myOverall,
                                          double orgOverall,
                                          int commentCount) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("OpenAI summary provider is disabled.");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("OPENAI_API_KEY is required when OpenAI summary is enabled.");
        }

        OpenAiChatCompletionRequest request = OpenAiChatCompletionRequest.of(
                properties.getModel(),
                promptBuilder.buildSystemPrompt(),
                promptBuilder.buildUserPrompt(categories, myOverall, orgOverall, commentCount)
        );

        String rawContent = executeWithRetry(request);
        OpenAiSummaryPayload payload = parsePayload(rawContent);
        return toAiSummary(payload);
    }

    @Override
    public String mode() {
        return "OPENAI";
    }

    private String executeWithRetry(OpenAiChatCompletionRequest request) {
        int totalAttempts = Math.max(1, properties.getMaxRetries() + 1);
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                OpenAiChatCompletionResponse response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .body(request)
                        .retrieve()
                        .body(OpenAiChatCompletionResponse.class);

                if (response == null || !StringUtils.hasText(response.firstContent())) {
                    throw new IllegalStateException("OpenAI response is empty.");
                }

                return response.firstContent();
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt >= totalAttempts) {
                    break;
                }
                log.warn("OpenAI summary request failed. retryAttempt={}/{}", attempt, totalAttempts, ex);
            }
        }

        throw new IllegalStateException("OpenAI summary request failed after retries.", lastException);
    }

    private OpenAiSummaryPayload parsePayload(String rawContent) {
        try {
            String normalized = stripMarkdownFence(rawContent);
            return objectMapper.readValue(normalized, OpenAiSummaryPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse OpenAI summary response.", ex);
        }
    }

    private MyPageView.AiSummary toAiSummary(OpenAiSummaryPayload payload) {
        List<String> strengths = normalizeList(payload != null ? payload.strengths() : null,
                "강점 분석 결과를 생성하지 못했습니다.");
        List<String> improvements = normalizeList(payload != null ? payload.improvements() : null,
                "보완점 분석 결과를 생성하지 못했습니다.");
        String overallComment = StringUtils.hasText(payload != null ? payload.overallComment() : null)
                ? payload.overallComment().trim()
                : "종합 코멘트를 생성하지 못했습니다.";

        return new MyPageView.AiSummary(strengths, improvements, overallComment);
    }

    private List<String> normalizeList(List<String> source, String fallbackText) {
        if (source == null || source.isEmpty()) {
            return List.of(fallbackText);
        }

        List<String> normalized = new ArrayList<>();
        for (String value : source) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || normalized.contains(trimmed)) {
                continue;
            }
            normalized.add(trimmed);
            if (normalized.size() >= 3) {
                break;
            }
        }

        return normalized.isEmpty() ? List.of(fallbackText) : List.copyOf(normalized);
    }

    private String stripMarkdownFence(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```(?:json)?\\s*", "");
        return withoutStart.replaceFirst("\\s*```\\s*$", "").trim();
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(10)
                : timeout;
        int timeoutMs = Math.toIntExact(safeTimeout.toMillis());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://api.openai.com/v1";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
