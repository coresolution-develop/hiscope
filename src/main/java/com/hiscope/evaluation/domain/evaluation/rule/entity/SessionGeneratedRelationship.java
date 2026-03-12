package com.hiscope.evaluation.domain.evaluation.rule.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "session_generated_relationships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "evaluator_id", "evaluatee_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SessionGeneratedRelationship extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "evaluator_id", nullable = false)
    private Long evaluatorId;

    @Column(name = "evaluatee_id", nullable = false)
    private Long evaluateeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private RelationshipRuleType relationType;

    @Column(name = "source_rule_id")
    private Long sourceRuleId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
