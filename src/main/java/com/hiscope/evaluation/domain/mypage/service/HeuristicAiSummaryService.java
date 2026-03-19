package com.hiscope.evaluation.domain.mypage.service;

import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class HeuristicAiSummaryService implements AiSummaryService {
    // NOTE: 임시 구현.
    // 실제 운영 LLM 연동 전까지 카테고리 편차 기반 휴리스틱 문장을 생성한다.

    @Override
    public MyPageView.AiSummary summarize(List<MyPageView.CategoryScore> categories,
                                          double myOverall,
                                          double orgOverall,
                                          int commentCount) {
        List<String> strengths = categories.stream()
                .filter(c -> c.delta() != null && c.delta() > 0.15)
                .sorted(Comparator.comparing(MyPageView.CategoryScore::delta).reversed())
                .limit(3)
                .map(c -> String.format("%s 영역이 평균보다 %.2f점 높습니다.", c.category(), c.delta()))
                .toList();

        List<String> improvements = categories.stream()
                .filter(c -> c.delta() != null && c.delta() < -0.15)
                .sorted(Comparator.comparing(MyPageView.CategoryScore::delta))
                .limit(3)
                .map(c -> String.format("%s 영역이 평균보다 %.2f점 낮습니다.", c.category(), Math.abs(c.delta())))
                .toList();

        if (strengths.isEmpty()) {
            strengths = List.of("강점 영역이 아직 명확하지 않아 응답 축적이 더 필요합니다.");
        }
        if (improvements.isEmpty()) {
            improvements = List.of("취약 영역 편차가 크지 않아 현재 수준을 유지하는 것이 좋습니다.");
        }

        String overall = myOverall >= orgOverall
                ? String.format("종합 점수는 평균 대비 +%.2f점입니다. 코멘트 %d건을 바탕으로 강점을 확장하는 전략이 적합합니다.",
                (myOverall - orgOverall), commentCount)
                : String.format("종합 점수는 평균 대비 -%.2f점입니다. 우선 보완 영역 중심으로 개선 계획을 세우는 것을 권장합니다.",
                (orgOverall - myOverall));

        return new MyPageView.AiSummary(strengths, improvements, overall);
    }

    @Override
    public String mode() {
        return "HEURISTIC";
    }
}
