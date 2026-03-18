package com.hiscope.evaluation.domain.mypage.service;

import com.hiscope.evaluation.config.properties.OpenAiSummaryProperties;
import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAiSummaryService implements AiSummaryService {

    private final OpenAiSummaryProperties properties;

    @Override
    public MyPageView.AiSummary summarize(List<MyPageView.CategoryScore> categories,
                                          double myOverall,
                                          double orgOverall,
                                          int commentCount) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("OpenAI summary provider is disabled.");
        }

        // TODO(next): OpenAI API 실호출 연결
        // - 입력: categories/myOverall/orgOverall/commentCount 기반 프롬프트
        // - 출력: strengths/improvements/overallComment
        throw new UnsupportedOperationException("OpenAI summary provider is not wired yet.");
    }

    @Override
    public String mode() {
        return "OPENAI_PREPARED";
    }
}
