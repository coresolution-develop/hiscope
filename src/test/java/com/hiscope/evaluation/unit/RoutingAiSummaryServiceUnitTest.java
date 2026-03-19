package com.hiscope.evaluation.unit;

import com.hiscope.evaluation.config.properties.OpenAiSummaryProperties;
import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import com.hiscope.evaluation.domain.mypage.service.HeuristicAiSummaryService;
import com.hiscope.evaluation.domain.mypage.service.OpenAiSummaryService;
import com.hiscope.evaluation.domain.mypage.service.RoutingAiSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingAiSummaryServiceUnitTest {

    @Mock
    private OpenAiSummaryService openAiSummaryService;

    private final HeuristicAiSummaryService heuristicAiSummaryService = new HeuristicAiSummaryService();

    @Test
    void openai_disabled_이면_호출하지_않고_heuristic_요약을_반환한다() {
        OpenAiSummaryProperties properties = new OpenAiSummaryProperties();
        properties.setProvider(OpenAiSummaryProperties.Provider.OPENAI);
        properties.setEnabled(false);
        properties.setFallbackToHeuristic(true);

        RoutingAiSummaryService routingService =
                new RoutingAiSummaryService(heuristicAiSummaryService, openAiSummaryService, properties);

        List<MyPageView.CategoryScore> categories = List.of(
                new MyPageView.CategoryScore("협업", 4.4, 4.1, 0.3)
        );

        MyPageView.AiSummary summary = routingService.summarize(categories, 4.4, 4.1, 2);

        assertThat(summary.strengths()).isNotEmpty();
        assertThat(routingService.mode()).isEqualTo("HEURISTIC");
        verify(openAiSummaryService, never()).summarize(anyList(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void openai_enabled_실패_시_fallback_설정이면_heuristic으로_자동전환한다() {
        OpenAiSummaryProperties properties = new OpenAiSummaryProperties();
        properties.setProvider(OpenAiSummaryProperties.Provider.OPENAI);
        properties.setEnabled(true);
        properties.setFallbackToHeuristic(true);

        RoutingAiSummaryService routingService =
                new RoutingAiSummaryService(heuristicAiSummaryService, openAiSummaryService, properties);

        when(openAiSummaryService.summarize(anyList(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new IllegalStateException("timeout"));

        MyPageView.AiSummary summary = routingService.summarize(List.of(), 3.8, 4.0, 1);

        assertThat(summary.overallComment()).contains("종합 점수");
        verify(openAiSummaryService).summarize(anyList(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void openai_enabled_실패_fallback_false_이면_안전한_기본문구를_반환한다() {
        OpenAiSummaryProperties properties = new OpenAiSummaryProperties();
        properties.setProvider(OpenAiSummaryProperties.Provider.OPENAI);
        properties.setEnabled(true);
        properties.setFallbackToHeuristic(false);

        RoutingAiSummaryService routingService =
                new RoutingAiSummaryService(heuristicAiSummaryService, openAiSummaryService, properties);

        when(openAiSummaryService.summarize(anyList(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new IllegalStateException("api error"));

        MyPageView.AiSummary summary = routingService.summarize(List.of(), 3.9, 4.0, 0);

        assertThat(summary.strengths()).containsExactly("요약 서비스를 일시적으로 사용할 수 없습니다.");
        assertThat(summary.improvements()).containsExactly("잠시 후 다시 시도해 주세요.");
        assertThat(summary.overallComment()).isEqualTo("자동 요약 서비스 연결 상태를 확인 중입니다.");
    }
}
