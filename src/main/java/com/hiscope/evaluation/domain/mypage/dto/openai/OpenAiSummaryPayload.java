package com.hiscope.evaluation.domain.mypage.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiSummaryPayload(
        List<String> strengths,
        List<String> improvements,
        String overallComment
) {
}
