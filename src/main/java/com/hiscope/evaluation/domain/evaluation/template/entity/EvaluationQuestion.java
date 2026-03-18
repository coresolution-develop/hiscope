package com.hiscope.evaluation.domain.evaluation.template.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evaluation_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** SCALE | DESCRIPTIVE */
    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(name = "question_group_code", length = 30)
    private String questionGroupCode;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public void update(String category, String content, String questionType,
                       String questionGroupCode, Integer maxScore, int sortOrder) {
        this.category = category;
        this.content = content;
        this.questionType = questionType;
        this.questionGroupCode = questionGroupCode;
        this.maxScore = maxScore;
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isScale() {
        return "SCALE".equals(this.questionType);
    }
}
