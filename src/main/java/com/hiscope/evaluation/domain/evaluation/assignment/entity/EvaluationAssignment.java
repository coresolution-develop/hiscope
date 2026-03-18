package com.hiscope.evaluation.domain.evaluation.assignment.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationAssignment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "relationship_id")
    private Long relationshipId;

    /** 평가를 수행하는 사람 */
    @Column(name = "evaluator_id", nullable = false)
    private Long evaluatorId;

    /** 평가를 받는 사람 */
    @Column(name = "evaluatee_id", nullable = false)
    private Long evaluateeId;

    /** PENDING | SUBMITTED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "resolved_question_group_code", length = 30)
    private String resolvedQuestionGroupCode;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public void submit() {
        this.status = "SUBMITTED";
        this.submittedAt = LocalDateTime.now();
    }

    public void reopen() {
        this.status = "PENDING";
        this.submittedAt = null;
    }

    public boolean isSubmitted() {
        return "SUBMITTED".equals(this.status);
    }
}
