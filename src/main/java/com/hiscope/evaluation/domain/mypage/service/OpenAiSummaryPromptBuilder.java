package com.hiscope.evaluation.domain.mypage.service;

import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OpenAiSummaryPromptBuilder {

    public String buildSystemPrompt() {
        return """
                너는 인사평가 요약 도우미다.
                입력 데이터만 사용해 한국어로 간결하고 중립적으로 작성한다.
                반드시 JSON 객체 하나만 반환한다.
                필드 규격:
                - strengths: 문자열 배열(1~3개)
                - improvements: 문자열 배열(1~3개)
                - overallComment: 문자열
                금지:
                - 마크다운/코드블록/설명문
                - 개인정보 추정/민감정보 생성
                - 입력에 없는 사실 단정
                """;
    }

    public String buildUserPrompt(List<MyPageView.CategoryScore> categories,
                                  double myOverall,
                                  double orgOverall,
                                  int commentCount) {
        String categoryJson = categories == null ? "[]" : categories.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(c -> Math.abs(c.delta() == null ? 0.0 : c.delta()), Comparator.reverseOrder()))
                .limit(8)
                .map(this::toCategoryJson)
                .collect(Collectors.joining(",\n"));

        return """
                아래 입력으로 개인 요약을 생성해줘.
                데이터:
                {
                  "myOverall": %s,
                  "orgOverall": %s,
                  "gap": %s,
                  "commentCount": %d,
                  "categoryScores": [
                %s
                  ]
                }
                규칙:
                - strengths/improvements는 각 1~3개, 중복 표현 없이 작성
                - overallComment는 1문장으로 작성
                - 숫자는 입력값 범위 내에서만 해석
                - 반드시 JSON 객체만 출력
                """.formatted(
                format(myOverall),
                format(orgOverall),
                format(myOverall - orgOverall),
                commentCount,
                categoryJson
        );
    }

    private String toCategoryJson(MyPageView.CategoryScore categoryScore) {
        String category = escapeJson(categoryScore.category() == null ? "기타" : categoryScore.category());
        return """
                    {
                      "category": "%s",
                      "myAverage": %s,
                      "orgAverage": %s,
                      "delta": %s
                    }""".formatted(
                category,
                format(categoryScore.myAverage()),
                format(categoryScore.orgAverage()),
                format(categoryScore.delta())
        );
    }

    private String format(Double value) {
        if (value == null) {
            return "0.00";
        }
        return format(value.doubleValue());
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
