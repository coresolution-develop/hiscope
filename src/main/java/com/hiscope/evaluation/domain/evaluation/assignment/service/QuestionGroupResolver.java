package com.hiscope.evaluation.domain.evaluation.assignment.service;

import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class QuestionGroupResolver {

    public Set<String> allowedQuestionGroupCodes(OrganizationProfile profile) {
        return switch (profile) {
            case HOSPITAL_DEFAULT -> Set.of("AA", "AB");
            case AFFILIATE_HOSPITAL -> Set.of("AC", "AD");
            case AFFILIATE_GENERAL -> Set.of("AC", "AE");
        };
    }

    public boolean isAllowed(OrganizationProfile profile, String questionGroupCode) {
        if (questionGroupCode == null || questionGroupCode.isBlank()) {
            return true;
        }
        return allowedQuestionGroupCodes(profile).contains(questionGroupCode.trim().toUpperCase());
    }

    public String resolveForRuleBased(OrganizationProfile profile,
                                      String relationType,
                                      boolean evaluatorDepartmentHead,
                                      boolean evaluateeDepartmentHead,
                                      boolean evaluateeLeader) {
        if (profile == null) {
            return null;
        }
        return switch (profile) {
            case HOSPITAL_DEFAULT -> resolveHospitalRule(relationType, evaluatorDepartmentHead, evaluateeDepartmentHead);
            case AFFILIATE_HOSPITAL -> evaluateeLeader ? "AC" : "AD";
            case AFFILIATE_GENERAL -> evaluateeLeader ? "AC" : "AE";
        };
    }

    private String resolveHospitalRule(String relationType,
                                       boolean evaluatorDepartmentHead,
                                       boolean evaluateeDepartmentHead) {
        String normalizedType = relationType == null ? "" : relationType.trim().toUpperCase();
        if ("UPWARD".equals(normalizedType)) {
            return "AA";
        }
        if ("DOWNWARD".equals(normalizedType)) {
            return "AB";
        }
        if (evaluatorDepartmentHead && !evaluateeDepartmentHead) {
            return "AB";
        }
        if (evaluatorDepartmentHead && evaluateeDepartmentHead) {
            return "AA";
        }
        if (!evaluatorDepartmentHead && !evaluateeDepartmentHead) {
            return "AB";
        }
        // 부서원 -> 부서장 케이스는 병원 정책상 상향 문항군(AA)로 분류
        return "AA";
    }
}
