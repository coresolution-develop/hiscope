package com.hiscope.evaluation.domain.evaluation.session.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "evaluation_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** PENDING | IN_PROGRESS | CLOSED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "allow_resubmit", nullable = false)
    @Builder.Default
    private boolean allowResubmit = false;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_generation_mode", nullable = false, length = 20)
    @Builder.Default
    private RelationshipGenerationMode relationshipGenerationMode = RelationshipGenerationMode.LEGACY;

    @Column(name = "relationship_definition_set_id")
    private Long relationshipDefinitionSetId;

    public void start() {
        if (!"PENDING".equals(this.status)) {
            throw new BusinessException(ErrorCode.SESSION_STATUS_INVALID, "대기 상태에서만 시작 가능합니다.");
        }
        this.status = "IN_PROGRESS";
    }

    public void close() {
        if (!"IN_PROGRESS".equals(this.status)) {
            throw new BusinessException(ErrorCode.SESSION_STATUS_INVALID, "진행 중 상태에서만 종료 가능합니다.");
        }
        this.status = "CLOSED";
    }

    public void update(String name, String description, LocalDate startDate, LocalDate endDate, Long templateId, boolean allowResubmit) {
        if (!"PENDING".equals(this.status)) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED, "시작된 세션은 수정할 수 없습니다.");
        }
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.templateId = templateId;
        this.allowResubmit = allowResubmit;
    }

    public boolean isPending() { return "PENDING".equals(this.status); }
    public boolean isInProgress() { return "IN_PROGRESS".equals(this.status); }
    public boolean isClosed() { return "CLOSED".equals(this.status); }

    public boolean isRuleBasedGeneration() {
        return relationshipGenerationMode == RelationshipGenerationMode.RULE_BASED;
    }

    public void configureRelationshipDefinition(RelationshipGenerationMode mode, Long definitionSetId) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED, "시작된 세션은 관계 생성 방식을 변경할 수 없습니다.");
        }
        this.relationshipGenerationMode = mode;
        this.relationshipDefinitionSetId = definitionSetId;
    }
}
