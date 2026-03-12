package com.hiscope.evaluation.domain.evaluation.rule.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "relationship_rule_matchers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RelationshipRuleMatcher extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private RelationshipSubjectType subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "matcher_type", nullable = false, length = 30)
    private RelationshipMatcherType matcherType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 20)
    private RelationshipRuleOperator operator;

    @Column(name = "value_text", length = 300)
    private String valueText;

    @Column(name = "value_json")
    private String valueJson;

    public void update(RelationshipSubjectType subjectType,
                       RelationshipMatcherType matcherType,
                       RelationshipRuleOperator operator,
                       String valueText,
                       String valueJson) {
        this.subjectType = subjectType;
        this.matcherType = matcherType;
        this.operator = operator;
        this.valueText = valueText;
        this.valueJson = valueJson;
    }
}
