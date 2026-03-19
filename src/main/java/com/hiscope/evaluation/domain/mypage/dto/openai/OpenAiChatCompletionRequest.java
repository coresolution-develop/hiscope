package com.hiscope.evaluation.domain.mypage.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatCompletionRequest(
        String model,
        List<Message> messages,
        Double temperature
) {
    public static OpenAiChatCompletionRequest of(String model, String systemPrompt, String userPrompt) {
        return new OpenAiChatCompletionRequest(
                model,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                ),
                0.2
        );
    }

    public record Message(
            String role,
            String content
    ) {
    }
}
