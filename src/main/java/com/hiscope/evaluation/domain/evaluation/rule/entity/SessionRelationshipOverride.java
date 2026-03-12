package com.hiscope.evaluation.domain.evaluation.rule.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.domain.evaluation.rule.enums.SessionRelationshipOverrideAction;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "session_relationship_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SessionRelationshipOverride extends BaseTimeEntity {

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
    @Column(name = "action", nullable = false, length = 20)
    private SessionRelationshipOverrideAction action;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;
}
