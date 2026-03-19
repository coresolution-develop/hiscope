package com.hiscope.evaluation.domain.mypage.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatCompletionResponse(
        List<Choice> choices
) {
    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0) == null || choices.get(0).message() == null) {
            return null;
        }
        return choices.get(0).message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Message message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String content
    ) {
    }
}
