package com.hiscope.evaluation.domain.mypage.service;

import com.hiscope.evaluation.config.properties.OpenAiSummaryProperties;
import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RoutingAiSummaryService implements AiSummaryService {

    private final HeuristicAiSummaryService heuristicSummaryService;

    private final OpenAiSummaryService openAiSummaryService;

    private final OpenAiSummaryProperties properties;

    @Override
    public MyPageView.AiSummary summarize(List<MyPageView.CategoryScore> categories,
                                          double myOverall,
                                          double orgOverall,
                                          int commentCount) {
        if (properties.getProvider() != OpenAiSummaryProperties.Provider.OPENAI) {
            return heuristicSummaryService.summarize(categories, myOverall, orgOverall, commentCount);
        }

        try {
            return openAiSummaryService.summarize(categories, myOverall, orgOverall, commentCount);
        } catch (RuntimeException ex) {
            if (!properties.isFallbackToHeuristic()) {
                log.error("OpenAI summary failed and fallback disabled.", ex);
                return unavailableSummary();
            }
            log.warn("OpenAI summary failed. Falling back to heuristic summary.", ex);
            return heuristicSummaryService.summarize(categories, myOverall, orgOverall, commentCount);
        }
    }

    @Override
    public String mode() {
        if (properties.getProvider() != OpenAiSummaryProperties.Provider.OPENAI) {
            return heuristicSummaryService.mode();
        }
        return properties.isEnabled() ? openAiSummaryService.mode() : "OPENAI_DISABLED";
    }

    private MyPageView.AiSummary unavailableSummary() {
        return new MyPageView.AiSummary(
                List.of("요약 서비스를 일시적으로 사용할 수 없습니다."),
                List.of("잠시 후 다시 시도해 주세요."),
                "자동 요약 서비스 연결 상태를 확인 중입니다."
        );
    }
}
