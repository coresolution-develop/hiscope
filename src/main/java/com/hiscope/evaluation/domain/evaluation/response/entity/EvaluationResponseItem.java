package com.hiscope.evaluation.domain.evaluation.response.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evaluation_response_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"response_id", "question_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationResponseItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "response_id", nullable = false)
    private Long responseId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "score_value")
    private Integer scoreValue;

    @Column(name = "text_value", columnDefinition = "TEXT")
    private String textValue;

    public void update(Integer scoreValue, String textValue) {
        this.scoreValue = scoreValue;
        this.textValue = textValue;
    }
}
