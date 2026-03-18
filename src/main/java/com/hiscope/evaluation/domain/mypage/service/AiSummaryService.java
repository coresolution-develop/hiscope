package com.hiscope.evaluation.domain.mypage.service;

import com.hiscope.evaluation.domain.mypage.dto.MyPageView;

import java.util.List;

public interface AiSummaryService {

    MyPageView.AiSummary summarize(List<MyPageView.CategoryScore> categories,
                                   double myOverall,
                                   double orgOverall,
                                   int commentCount);

    String mode();
}
