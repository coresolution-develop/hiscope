package com.hiscope.evaluation.domain.evaluation.response.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EvaluationSubmitRequest {

    // questionId -> score value (SCALE)
    private Map<Long, Integer> scores;

    // questionId -> text value (DESCRIPTIVE)
    private Map<Long, String> texts;

    private boolean finalSubmit = false;
}
