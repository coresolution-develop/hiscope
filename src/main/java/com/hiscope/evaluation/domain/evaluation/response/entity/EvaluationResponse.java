package com.hiscope.evaluation.domain.evaluation.response.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationResponse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false, unique = true)
    private Long assignmentId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "is_final", nullable = false)
    @Builder.Default
    private boolean finalSubmit = false;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public void finalize() {
        this.finalSubmit = true;
        this.submittedAt = LocalDateTime.now();
    }

    public void reopen() {
        this.finalSubmit = false;
        this.submittedAt = null;
    }
}
