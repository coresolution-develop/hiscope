package com.hiscope.evaluation.domain.evaluation.session.dto.view;

import java.time.LocalDate;
import java.util.List;

public record MyEvaluationGroupView(
        Long sessionId,
        String sessionName,
        LocalDate startDate,
        LocalDate endDate,
        List<MyEvaluationItemView> items
) {}
