package com.hiscope.evaluation.domain.evaluation.rule.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "relationship_definition_rules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"set_id", "rule_name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RelationshipDefinitionRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private RelationshipRuleType relationType;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public void updateRule(String ruleName, RelationshipRuleType relationType, int priority) {
        this.ruleName = ruleName;
        this.relationType = relationType;
        this.priority = priority;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
