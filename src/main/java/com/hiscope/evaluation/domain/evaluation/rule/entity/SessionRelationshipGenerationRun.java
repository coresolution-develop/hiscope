package com.hiscope.evaluation.domain.evaluation.rule.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_relationship_generation_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SessionRelationshipGenerationRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_mode", nullable = false, length = 20)
    private RelationshipGenerationMode generationMode;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "generated_count", nullable = false)
    @Builder.Default
    private long generatedCount = 0L;

    @Column(name = "excluded_count", nullable = false)
    @Builder.Default
    private long excludedCount = 0L;

    @Column(name = "self_removed_count", nullable = false)
    @Builder.Default
    private long selfRemovedCount = 0L;

    @Column(name = "deduplicated_count", nullable = false)
    @Builder.Default
    private long deduplicatedCount = 0L;

    @Column(name = "override_applied_count", nullable = false)
    @Builder.Default
    private long overrideAppliedCount = 0L;

    @Column(name = "final_count", nullable = false)
    @Builder.Default
    private long finalCount = 0L;

    @Column(name = "rule_stats_json")
    private String ruleStatsJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "executed_by")
    private Long executedBy;

    @Column(name = "executed_by_login_id", length = 100)
    private String executedByLoginId;

    @Column(name = "executed_at", nullable = false)
    @Builder.Default
    private LocalDateTime executedAt = LocalDateTime.now();
}
